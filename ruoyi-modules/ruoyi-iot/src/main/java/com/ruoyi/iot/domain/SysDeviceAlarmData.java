package com.ruoyi.iot.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 传感器设备溺水报警实体对象 sys_device_alarm_data
 *
 * @author ruoyi
 * @date 2026-06-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysDeviceAlarmData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键自增ID */
    private Long id;

    /** 设备编码 */
    private String deviceCode;

    /** 当前水面深度(米)，使用 BigDecimal 保留数值精度 */
    private BigDecimal waterDepth;

    /** 异常状态标记：0-正常状态，1-溺水报警 */
    private Integer status;

    /** 设备剩余电量百分比 */
    private Integer battery;

    /** 传感器报文上报时间 */
    private LocalDateTime reportTime;
}
