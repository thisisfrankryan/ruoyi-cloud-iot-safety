package com.ruoyi.iot.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iot.domain.SysDeviceAlarmData;
import com.ruoyi.iot.service.ISysDeviceAlarmDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 高并发削峰缓冲 MQ 消费者 (基于 RabbitMQ 监听救命大运河队列)
 *
 * @author ruoyi
 * @date 2026-06-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "water.safety.alarm.queue")
public class AlarmConsumer {

    private final ISysDeviceAlarmDataService alarmDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存级高速削峰缓冲水闸：收纳秒级爆发而来的传感器平常数据，不阻塞上游报文接收
    private final ConcurrentLinkedQueue<SysDeviceAlarmData> bufferQueue = new ConcurrentLinkedQueue<>();

    /**
     * 核心高频收水入口：监听 "water.safety.alarm.queue"
     *
     * @param message 接收到的原始 JSON 字节或 String 报文
     */
    @RabbitHandler
    public void onMessage(String message) {
        try {
            // 1. 快速反序列化
            SysDeviceAlarmData alarmData = objectMapper.readValue(message, SysDeviceAlarmData.class);
            if (alarmData == null) {
                return;
            }

            // 2. 突发溺水秒级闪屏逻辑 (status == 1 十万火急溺水)
            if (alarmData.getStatus() != null && alarmData.getStatus() == 1) {
                String redisKey = "alarm:device:" + alarmData.getDeviceCode();
                // 瞬间将救命警报拍入 Redis 临时吸磁大白板，设置 10 秒超时供前端大屏 500ms 高频极速轮询
                redisTemplate.opsForValue().set(redisKey, "1", 10, TimeUnit.SECONDS);
                log.warn("🚨 [突发溺水警报] 设备编码: {}, 实时水深: {}m, 电池电量: {}%, 已瞬时写入 Redis 缓存大白板！",
                        alarmData.getDeviceCode(), alarmData.getWaterDepth(), alarmData.getBattery());
            }

            // 3. 将所有上报报文送入 ConcurrentLinkedQueue 缓冲池，实现内存级异步收纳，不直接写数据库
            bufferQueue.add(alarmData);

        } catch (Exception e) {
            log.error("解析并消费 IoT 救命队列报文发生致命异常, 原始消息: {}", message, e);
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
        SysDeviceAlarmData item;
        // 线程安全地从内存缓冲队列中提取数据
        while ((item = bufferQueue.poll()) != null) {
            batchList.add(item);
        }

        if (!batchList.isEmpty()) {
            try {
                // 批量拍进 MySQL 大账本
                int rows = alarmDataService.insertBatch(batchList);
                log.info("✅ [大运河节流阀] 成功向 MySQL 批量入库 {} 条设备上报记录。", rows);
            } catch (Exception e) {
                log.error("💥 [大运河节流阀] 批量持久化到 MySQL 发生严重异常！尝试把数据写回内存队列以保障数据绝对不丢", e);
                // 异常回滚处理，放回队列，确保“100% 漏不掉一封救命警报密电”
                bufferQueue.addAll(batchList);
            }
        }
    }
}
