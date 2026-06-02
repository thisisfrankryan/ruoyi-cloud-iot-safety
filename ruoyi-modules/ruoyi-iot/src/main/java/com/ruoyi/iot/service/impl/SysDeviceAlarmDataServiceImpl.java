package com.ruoyi.iot.service.impl;

import com.ruoyi.iot.domain.SysDeviceAlarmData;
import com.ruoyi.iot.mapper.SysDeviceAlarmDataMapper;
import com.ruoyi.iot.service.ISysDeviceAlarmDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 传感器设备溺水报警服务实现
 *
 * @author ruoyi
 * @date 2026-06-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeviceAlarmDataServiceImpl implements ISysDeviceAlarmDataService {

    private final SysDeviceAlarmDataMapper alarmDataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(SysDeviceAlarmData alarmData) {
        return alarmDataMapper.insert(alarmData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertBatch(List<SysDeviceAlarmData> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        return alarmDataMapper.insertBatch(list);
    }

    @Override
    public List<SysDeviceAlarmData> selectHistoryByRange(String deviceCode, LocalDateTime beginTime, LocalDateTime endTime) {
        return alarmDataMapper.selectHistoryByRange(deviceCode, beginTime, endTime);
    }
}
