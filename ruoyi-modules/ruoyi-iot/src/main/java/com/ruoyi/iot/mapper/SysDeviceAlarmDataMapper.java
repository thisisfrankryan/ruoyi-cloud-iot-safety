package com.ruoyi.iot.mapper;

import com.ruoyi.iot.domain.SysDeviceAlarmData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 传感器设备溺水报警Mapper接口
 *
 * @author ruoyi
 * @date 2026-06-01
 */
@Mapper
public interface SysDeviceAlarmDataMapper {

    /**
     * 新增设备报警记录
     *
     * @param alarmData 报警数据
     * @return 影响行数
     */
    int insert(SysDeviceAlarmData alarmData);

    /**
     * 批量写入设备报警记录
     *
     * @param list 待插入的数据集
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<SysDeviceAlarmData> list);

    /**
     * 根据设备编码与时间范围查询历史记录。
     * report_time 索引列保持直接范围比较，避免套用 DATE() 等函数导致索引失效。
     *
     * @param deviceCode 设备编码
     * @param beginTime 开始时间
     * @param endTime 结束时间
     * @return 历史上报记录列表
     */
    List<SysDeviceAlarmData> selectHistoryByRange(@Param("deviceCode") String deviceCode, 
                                                 @Param("beginTime") LocalDateTime beginTime, 
                                                 @Param("endTime") LocalDateTime endTime);
}
