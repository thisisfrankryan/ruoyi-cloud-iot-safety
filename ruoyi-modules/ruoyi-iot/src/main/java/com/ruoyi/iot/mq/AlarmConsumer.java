package com.ruoyi.iot.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ruoyi.iot.domain.SysDeviceAlarmData;
import com.ruoyi.iot.service.ISysDeviceAlarmDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * IoT 告警消息消费者。
 * 使用 RabbitMQ 解耦终端报文接收与业务处理，并通过有界队列批量写入数据库。
 *
 * @author ruoyi
 * @date 2026-06-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "water.safety.alarm.queue")
public class AlarmConsumer {

    private final ISysDeviceAlarmDataService alarmDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 有界内存缓冲区：限制堆积上限，避免极端情况下无限占用内存
    private final LinkedBlockingQueue<SysDeviceAlarmData> bufferQueue = new LinkedBlockingQueue<>(50000);

    // Redis 警报活跃 ZSET Key
    private static final String ACTIVE_ALARM_KEY = "alarm:active_zset";

    /**
     * 监听 water.safety.alarm.queue，使用手动 ACK 控制消息确认时机。
     *
     * @param messageBody 消息体 String 报文
     * @param channel     RabbitMQ 信道
     * @param message     AMQP 消息封装
     */
    @RabbitHandler
    public void onMessage(String messageBody, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 快速反序列化
            SysDeviceAlarmData alarmData = objectMapper.readValue(messageBody, SysDeviceAlarmData.class);
            if (alarmData == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 溺水报警实时状态写入 Redis (status == 1)
            if (alarmData.getStatus() != null && alarmData.getStatus() == 1) {
                // 使用 ZSET 存储设备报警及其过期时间戳，规避 KEYS 模糊搜索造成的 Redis 线程阻塞
                long expireTime = System.currentTimeMillis() + 10000; // 10 秒 TTL 缓存
                redisTemplate.opsForZSet().add(ACTIVE_ALARM_KEY, alarmData.getDeviceCode(), expireTime);
                log.warn("[IoT 告警] 设备编码: {}, 实时水深: {}m, 电池电量: {}%, 已写入 Redis 活跃告警集合",
                        alarmData.getDeviceCode(), alarmData.getWaterDepth(), alarmData.getBattery());
            }

            // 3. 将所有上报报文送入有界缓冲池，后续由定时任务批量写入数据库
            boolean added = bufferQueue.offer(alarmData);
            if (!added) {
                log.error("[IoT 告警] 内存缓冲队列已满，当前报文无法进入批量写入队列，设备: {}", alarmData.getDeviceCode());
            }

            // 4. 手动确认消息消费成功
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("解析并消费 IoT 告警队列报文发生异常, 原始消息: {}", messageBody, e);
            try {
                // 发生未知异常时，将消息退回队列重试，防止丢数据
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                log.error("RabbitMQ NACK 回滚信道异常: ", ex);
            }
        }
    }

    /**
     * 每 5 秒批量写入 MySQL 历史表，降低高频单条写入带来的数据库压力。
     */
    @Scheduled(fixedRate = 5000)
    public void flushBufferToDatabase() {
        if (bufferQueue.isEmpty()) {
            return;
        }

        log.info("[IoT 告警] 开始执行 5s 周期性批量入库任务，当前内存队列深度: {}", bufferQueue.size());

        List<SysDeviceAlarmData> batchList = new ArrayList<>();
        // 线程安全且高性能地一次性排干缓冲队列
        bufferQueue.drainTo(batchList);

        if (!batchList.isEmpty()) {
            try {
                // 批量持久化到 MySQL
                int rows = alarmDataService.insertBatch(batchList);
                log.info("[IoT 告警] 成功向 MySQL 批量写入 {} 条设备上报记录", rows);
            } catch (Exception e) {
                log.error("[IoT 告警] 批量持久化到 MySQL 失败，尝试将数据放回内存队列", e);
                // 异常回滚处理，安全放回有界队列
                for (SysDeviceAlarmData data : batchList) {
                    if (!bufferQueue.offer(data)) {
                        log.error("[IoT 告警] 回写内存队列失败，队列已满，设备: {}, 时间: {}", data.getDeviceCode(), data.getReportTime());
                    }
                }
            }
        }
    }
}
