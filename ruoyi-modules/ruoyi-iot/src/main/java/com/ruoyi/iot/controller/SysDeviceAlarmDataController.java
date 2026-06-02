package com.ruoyi.iot.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.iot.domain.SysDeviceAlarmData;
import com.ruoyi.iot.service.ISysDeviceAlarmDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 传感器设备溺水报警数据控制层
 *
 * @author ruoyi
 * @date 2026-06-01
 */
@Slf4j
@RestController
@RequestMapping("/iot/alarm")
@RequiredArgsConstructor
public class SysDeviceAlarmDataController {

    private final ISysDeviceAlarmDataService alarmDataService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 根据设备编码与干净的时间范围极速拉取历史记录 (Nginx 负载均衡分流进入)
     *
     * @param deviceCode 设备编码
     * @param beginTime 开始时间 (ISO-8601 格式，如: 2026-06-01T12:00:00)
     * @param endTime 结束时间
     * @return 统一响应封装体 R 包裹的数组
     */
    @GetMapping("/history")
    public R<List<SysDeviceAlarmData>> getHistory(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("拉取设备报文历史数据, deviceCode: {}, beginTime: {}, endTime: {}", deviceCode, beginTime, endTime);
        List<SysDeviceAlarmData> list = alarmDataService.selectHistoryByRange(deviceCode, beginTime, endTime);
        return R.ok(list);
    }

    /**
     * 大屏专用极速实时数据轮询接口
     * 从 Redis 临时吸磁大白板直接获取 10 秒内产生过溺水警报的设备列表及其状态
     *
     * @return 统一响应封装体 R 包裹的异常设备 Map
     */
    @GetMapping("/realtime")
    public R<Map<String, String>> getRealtimeAlarms() {
        Map<String, String> activeAlarms = new HashMap<>();
        try {
            String activeAlarmKey = "alarm:active_zset";
            long now = System.currentTimeMillis();
            // 1. 自动擦除已过期的报警设备记录 (清除 score 介于 0 到当前时间戳的过期记录)
            redisTemplate.opsForZSet().removeRangeByScore(activeAlarmKey, 0, now);
            // 2. 拉取所有仍处于活跃警报状态的设备 (score 介于当前时间戳到正无穷)
            Set<String> activeDevices = redisTemplate.opsForZSet().rangeByScore(activeAlarmKey, now, Double.MAX_VALUE);
            if (activeDevices != null && !activeDevices.isEmpty()) {
                for (String deviceCode : activeDevices) {
                    activeAlarms.put(deviceCode, "1");
                }
            }
        } catch (Exception e) {
            log.error("高频轮询 Redis ZSET 实时警报异常: ", e);
        }
        return R.ok(activeAlarms);
    }

}
