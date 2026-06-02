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
 * 高并发削峰缓冲 MQ 消费者 (基于 RabbitMQ 监听救命大运河队列)
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

    // 内存级高速削峰缓冲水闸：收纳秒级爆发而来的传感器数据，规避 OOM 引入 50000 规格有界队列保护
    private final LinkedBlockingQueue<SysDeviceAlarmData> bufferQueue = new LinkedBlockingQueue<>(50000);

    // Redis 警报活跃 ZSET Key
    private static final String ACTIVE_ALARM_KEY = "alarm:active_zset";

    /**
     * 核心高频收水入口：监听 "water.safety.alarm.queue" (手动 ACK 模式防丢失)
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

            // 2. 突发溺水秒级闪屏逻辑 (status == 1 十万火急溺水)
            if (alarmData.getStatus() != null && alarmData.getStatus() == 1) {
                // 使用 ZSET 存储设备报警及其过期时间戳，规避 KEYS 模糊搜索造成的 Redis 线程阻塞
                long expireTime = System.currentTimeMillis() + 10000; // 10 秒 TTL 缓存
                redisTemplate.opsForZSet().add(ACTIVE_ALARM_KEY, alarmData.getDeviceCode(), expireTime);
                log.warn("🚨 [突发溺水警报] 设备编码: {}, 实时水深: {}m, 电池电量: {}%, 已瞬时写入 Redis ZSET 活跃面板！",
                        alarmData.getDeviceCode(), alarmData.getWaterDepth(), alarmData.getBattery());
            }

            // 3. 将所有上报报文送入有界缓冲池，实现内存级异步收纳，不直接写数据库
            boolean added = bufferQueue.offer(alarmData);
            if (!added) {
                log.error("💥 [MQ 吞吐告警] 内存缓冲队列已满（深度限额 50000），为防止 OOM 触发背压限流，该条报文已被丢弃！设备: {}", alarmData.getDeviceCode());
            }

            // 4. 手动确认消息消费成功
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("解析并消费 IoT 救命队列报文发生异常, 原始消息: {}", messageBody, e);
            try {
                // 发生未知异常时，将消息退回队列重试，防止丢数据
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                log.error("RabbitMQ NACK 回滚信道异常: ", ex);
            }
        }
    }

    /**
     * 节流阀分批放水策略：每 5 秒启动定时任务大扫除，批量拍入 MySQL 历史表，减轻磁盘 I/O 写入负担
     */
    @Scheduled(fixedRate = 5000)
    public void flushBufferToDatabase() {
        if (bufferQueue.isEmpty()) {
            return;
        }

        log.info("🌊 [大运河节流阀] 开启 5s 周期性数据放水。当前内存队列堆积深度: {}", bufferQueue.size());

        List<SysDeviceAlarmData> batchList = new ArrayList<>();
        // 线程安全且高性能地一次性排干缓冲队列
        bufferQueue.drainTo(batchList);

        if (!batchList.isEmpty()) {
            try {
                // 批量持久化到 MySQL
                int rows = alarmDataService.insertBatch(batchList);
                log.info("✅ [大运河节流阀] 成功向 MySQL 批量入库 {} 条设备上报记录。", rows);
            } catch (Exception e) {
                log.error("💥 [大运河节流阀] 批量持久化到 MySQL 发生严重异常！尝试把数据安全回滚写回内存队列", e);
                // 异常回滚处理，安全放回有界队列
                for (SysDeviceAlarmData data : batchList) {
                    if (!bufferQueue.offer(data)) {
                        log.error("💥 [内存队列重回滚失败] 队列已满，数据被迫丢弃：设备 {}, 时间 {}", data.getDeviceCode(), data.getReportTime());
                    }
                }
            }
        }
    }
}
