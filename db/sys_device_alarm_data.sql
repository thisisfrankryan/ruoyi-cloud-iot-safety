-- ----------------------------
-- Table structure for sys_device_alarm_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_device_alarm_data`;
CREATE TABLE `sys_device_alarm_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键自增ID',
  `device_code` varchar(64) NOT NULL COMMENT '设备编码',
  `water_depth` decimal(5,2) NOT NULL COMMENT '当前水面深度(米)，采用BigDecimal类型处理',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '异常状态标记：0-正常状态，1-溺水报警',
  `battery` tinyint(4) NOT NULL COMMENT '设备剩余电量百分比(0-100)',
  `report_time` datetime NOT NULL COMMENT '传感器报文上报时间(精确到毫秒级)',
  PRIMARY KEY (`id`),
  -- 高频范围查询索引：针对设备编码与时间段建立复合索引，减少历史查询全表扫描
  KEY `idx_device_time` (`device_code`, `report_time`),
  -- 告警状态索引：支持按 status 过滤异常设备
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='传感器设备溺水报警报文历史表';
