package com.ruoyi.iot.service;

import com.ruoyi.iot.domain.SysDeviceAlarmData;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 传感器设备溺水报警服务接口
 *
 * @author ruoyi
 * @date 2026-06-01
 */
public interface ISysDeviceAlarmDataService {

    /**
     * 新增单条设备报警记录
     *
     * @param alarmData 报警数据
     * @return 影响行数
     */
    int insert(SysDeviceAlarmData alarmData);

    /**
     * 批量写入设备报警记录
     *
     * @param list 数据集
     * @return 影响行数
     */
    int insertBatch(List<SysDeviceAlarmData> list);

    /**
     * 根据设备编码与时间范围查询历史记录
     *
     * @param deviceCode 设备编码
     * @param beginTime 开始时间
     * @param endTime 结束时间
     * @return 历史上报记录列表
     */
    List<SysDeviceAlarmData> selectHistoryByRange(String deviceCode, LocalDateTime beginTime, LocalDateTime endTime);
}
