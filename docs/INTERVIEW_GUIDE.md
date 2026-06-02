# 项目面试讲解指南

这份文档用于把 `ruoyi-cloud-iot-safety` 讲成一个清晰、可信、可追问的工程项目。适用于 Java 开发、运维开发、现场实施工程师、物联网系统集成等岗位。

## 一句话介绍

这是一个基于 RuoYi-Cloud 二次开发的物联网水域安全告警系统，核心链路是：终端上报告警数据，RabbitMQ 做异步削峰，Redis 保存实时活跃告警，MySQL 保存历史记录，Vue3 大屏做实时展示，Docker Compose 负责本地多服务联调。

## 适合投递的岗位

- Java 后端开发工程师
- 初中级 Java 开发工程师
- 运维开发工程师
- 现场实施工程师
- 物联网平台开发 / 系统集成
- 技术支持 / 实施交付工程师

## 面试讲解顺序

### 1. 先讲业务场景

这个项目模拟的是水域安全监控场景，例如泳池、浴场、渔业水域。设备会持续上报水深、电量、状态和时间等数据。系统需要解决两类问题：

- 实时性：一旦设备出现告警，前端大屏要尽快看到。
- 可追溯：每次设备上报都要留历史记录，方便后续查询和复盘。

所以我把数据拆成了两条路径：

- 实时告警状态：写入 Redis，方便大屏高频读取。
- 历史告警记录：批量写入 MySQL，方便长期查询。

### 2. 再讲架构选择

我没有直接让接口请求写数据库，而是通过 RabbitMQ 解耦：

```text
设备/模拟终端 -> RabbitMQ -> AlarmConsumer -> Redis + MySQL -> Vue3 大屏
```

这样做的原因是：

- 设备上报可能存在突发流量，直接写库容易造成数据库压力。
- RabbitMQ 可以把接收和处理拆开，消费端可以按自己的节奏处理。
- Redis 适合放实时状态，MySQL 适合放历史记录。

### 3. 讲清楚自己写了什么

可以重点讲这几个文件：

- `ruoyi-modules/ruoyi-iot/src/main/java/com/ruoyi/iot/mq/AlarmConsumer.java`
  - 监听 `water.safety.alarm.queue`
  - 解析 JSON 报文
  - status=1 时写入 Redis ZSET
  - 将数据放入 `LinkedBlockingQueue`
  - 每 5 秒批量写入 MySQL

- `ruoyi-modules/ruoyi-iot/src/main/java/com/ruoyi/iot/controller/SysDeviceAlarmDataController.java`
  - `/iot/alarm/realtime`：给前端大屏查询当前活跃告警
  - `/iot/alarm/history`：按设备编码和时间范围查询历史记录

- `ruoyi-modules/ruoyi-iot/src/main/resources/mapper/iot/SysDeviceAlarmDataMapper.xml`
  - 单条插入
  - 批量插入
  - 时间范围查询

- `docker-compose.yml`
  - 编排 Nacos、MySQL、Redis、RabbitMQ 和微服务模块

### 4. 讲 Redis ZSET 设计

实时告警我没有用普通 key，也没有用 `KEYS` 扫描，而是使用 Redis ZSET：

- member 是设备编码
- score 是告警过期时间戳
- 查询前先删除过期 score
- 再查询当前时间之后仍有效的设备

这样前端轮询接口可以拿到当前活跃告警设备，也避免了使用 `KEYS` 模糊搜索导致 Redis 阻塞的问题。

### 5. 讲 MySQL 索引

历史表建立了两个索引：

```sql
KEY idx_device_time (device_code, report_time),
KEY idx_status (status)
```

`idx_device_time` 用于按设备和时间范围查历史记录。查询时我没有在 `report_time` 上套 `DATE()` 这类函数，而是直接使用范围比较：

```sql
report_time >= #{beginTime}
report_time <= #{endTime}
```

这样可以降低索引失效的风险。

### 6. 讲部署和排查

我用 Docker Compose 做本地联调环境，核心是把基础服务一次性拉起来：

- Nacos：注册和配置
- MySQL：历史数据
- Redis：实时状态
- RabbitMQ：消息队列
- Gateway/Auth/System/IoT：业务服务

排查时可以按这个顺序：

```text
服务是否启动 -> 端口是否开放 -> Nacos 是否注册 -> RabbitMQ 是否有消息 -> Redis 是否写入 -> MySQL 是否入库 -> 接口是否返回 -> 前端是否展示
```

这能展示我不只是会写代码，也能做联调、部署和问题闭环。

## 面试官可能会追问

### Q1：为什么用 RabbitMQ，不直接写数据库？

因为设备上报可能在短时间内集中到来，直接写数据库会把接收端和数据库写入强绑定。使用 RabbitMQ 后，请求接收和业务处理解耦，消费端可以批量处理，系统在突发场景下更稳。

### Q2：为什么实时告警用 Redis？

实时告警是高频读取场景，大屏只关心当前有哪些设备处于告警状态。Redis 适合保存短期状态，查询速度快；历史数据仍然进入 MySQL，保证可追溯。

### Q3：为什么用 ZSET？

因为告警状态需要过期时间。ZSET 的 score 可以存过期时间戳，查询前删除过期数据，再拉取仍有效的告警设备。比用普通 key 后再 `KEYS` 扫描更适合高频轮询。

### Q4：批量写入会不会丢数据？

当前项目是工程实践版本，做了有界队列、定时批量入库、异常时尝试回写队列。生产环境还可以继续补充更严格的幂等键、死信队列、消费重试策略、落库失败告警和持久化缓冲。

### Q5：这个项目有什么不足？

可以诚实回答：

- 目前主要是本地联调和作品集项目，真实硬件协议接入还可以继续扩展 MQTT/WebSocket。
- 还缺少完整压测报告。
- 告警通知链路还可以补充短信、邮件或企业微信机器人。
- 生产环境需要进一步补充鉴权、幂等、监控、告警和链路追踪。

这种回答反而更可信，说明你知道工程还有边界。

## 简历写法建议

可以写成：

```text
基于 RuoYi-Cloud 的智能水域安全监控与告警系统
技术栈：Java、Spring Cloud Alibaba、Spring Boot、MyBatis、RabbitMQ、Redis、MySQL、Docker Compose、Vue3

1. 基于 RuoYi-Cloud 二次开发 IoT 告警模块，围绕设备告警、实时状态、历史记录和监控大屏拆分接口。
2. 使用 RabbitMQ 承接设备告警报文，消费端通过有界队列与定时任务批量写入 MySQL，降低突发写库压力。
3. 使用 Redis ZSET 保存实时活跃告警状态，前端大屏通过轮询接口获取当前告警设备，避免使用 KEYS 模糊扫描。
4. 针对历史查询建立 device_code + report_time 复合索引，查询 SQL 保持时间字段直接范围比较，降低索引失效风险。
5. 使用 Docker Compose 编排 Nacos、MySQL、Redis、RabbitMQ 和微服务模块，完成本地部署、联调和日志排查。
```

## 项目价值总结

这个项目最重要的价值不是“功能多”，而是展示了完整工程链路：

```text
业务理解 -> 架构拆分 -> 接口实现 -> 消息队列 -> 缓存设计 -> SQL优化 -> 容器部署 -> 前端展示 -> 问题排查 -> 文档沉淀
```

这正是初中级 Java 开发、运维开发和现场实施岗位真正需要的能力。
