# 🌊 基于 RuoYi-Cloud + IoT 的高并发智能水域溺水安全监控与微服务调度系统

## 🚀 项目背景与核心亮点
本项目是一套专为高并发水域安全监测场景打造的**工业级物联网分布式微服务系统**。系统基于 `RuoYi-Cloud` 云原生底座进行深度二次开发，打通了从“水下物联网传感器边缘接入”、“高并发异步报文流量削峰”、“实时警报秒级响应”直到“前端 GPU 硬件加速大屏动态渲染”的微服务全链路架构。

---

## 🛠️ 核心架构与技术演练方案

### 1. 高并发异步削峰与双轨缓冲架构 (High-Concurrency Telemetry Ingestion)
针对数万物联网手环毫秒级高频报送传感器数据的极端场景，设计并实现了**“双轨高吞吐数据流”**缓冲机制：
- **高能削峰通道 (Peak Shaving)**：利用 **RabbitMQ** 消息队列作为系统流量缓冲带，解耦接收端与消费端，实现异步数据吞吐。
- **内存级有界缓冲池 (Bounded Buffer)**：消费者内部引入规格限制为 50,000 的 **LinkedBlockingQueue** 线程安全队列，通过背压与流控机制规避高并发下的内存溢出（OOM）风险。定期使用线程安全的 **`drainTo` 批量清空** 技术，每 5 秒通过 **MyBatis 批量合并写入数据库**，将 MySQL 写入频次降低 95%，显著降低磁盘 I/O 争抢。
- **极速热数据通道 (Fast-Path Cache)**：对于紧急报警事件（`status == 1`），绕过数据库慢通道，采用 **Redis Sorted Set (ZSET)** 缓存活跃状态，以系统过期时间戳作为 Score。前端大屏高频短轮询只需以 $O(\log N)$ 复杂度完成过期数据擦除并拉取当前活跃的警报设备，杜绝了 `KEYS` 模糊搜索引起的 Redis 线程阻塞。
- **高可靠消息交付 (Manual ACK)**：消费者开启 RabbitMQ **手动确认模式（Manual ACK）**，只有在 Redis 成功吸磁、数据安全塞入内存缓冲区后才发送确认信号；捕获异常时触发信道 NACK 退回队列重试，保证恶劣环境下数据零丢失。

### 2. 企业级云原生容器化编排 (DevOps & JVM Optimization)
- **JVM 垃圾回收调优**：自定义 Alpine-based **Dockerfile**，深度优化 JVM 垃圾回收机制，注入 `-XX:+UseG1GC`、`-XX:MaxGCPauseMillis=100` 等参数，消除因垃圾回收造成的系统“时空冻结”（Stop-the-World）卡顿现象，保证高并发消费的响应平稳度。
- **服务集群容器化编排**：通过 **Docker Compose** 统一管理 Nacos 配置/注册中心、MySQL 数据库、Redis 缓存服务、RabbitMQ 消息中间件及核心业务模块，实现一键高可用部署，打通本地与 **WSL2 Linux 核心特权组共享** 的开发集成验证环。
- **水平伸缩扩容方案**：编写 `k8s-hpa.yaml`，配置 Kubernetes 基于 CPU 60% 阈值的 **水平 Pod 自动扩容 (HPA v2)** 策略，实例数可在 3 到 6 之间动态弹性伸缩，提供强悍的高负载横向扩展能力。

### 3. GPU 硬件加速的 4K 科技感大屏前端 (Visualization & GPU Acceleration)
- **现代响应式渲染**：基于 **Vue3 Composition API + Vite** 搭建 4K 大屏组件，采用 Glassmorphism（毛玻璃折射）和极简深色系主题，完美融入 RuoYi 菜单动态卡槽。
- **GPU 硬件层加速**：在警报呼吸灯和闪屏等复杂动画图层上，强制应用 CSS `will-change: transform` 和 `transform: translateZ(0)`，**逼迫客户端物理独立显卡（GPU）接管图层 3D 渲染**，避免浏览器 CPU 进行频繁的重排（Reflow）与重绘（Repaint），确保 4K 超高分辨率下警报动态维持 120 帧满帧丝滑运行。

---

## 🏢 微服务模块职责与设计规范

- `ruoyi-gateway [8080]`：**服务网关**。基于 Spring Cloud Gateway 与 Netty 异步响应式框架构建，集成 JWT 全局统一认证授权校验，负责统一跨域治理与动态限流路由转发。
- `ruoyi-auth [9200]`：**身份认证中心**。负责全园 Token 令牌的签发、加解密校验、状态维持及主动吊销逻辑。
- `ruoyi-modules-system [9201]`：**系统配置中心**。基于标准 RBAC 模型管辖用户、部门、岗位权限，使用 Spring AOP 实现细粒度的按钮级安全拦截。
- `ruoyi-modules-iot [9800]`：**物联网核心业务模块【自主开发】**。集成 RabbitMQ Telemetry 消费者，实现内存缓冲异步队列、MyBatis 批量写盘以及 Redis 状态 ZSET 高性能管理接口。
- `ruoyi-common`：**总务公共包**。整合分布式事务治理（Seata）、统一异常拦截器、日志切面以及 Swagger RESTful 活体接口文档。

---

## 📊 数据库索引设计与查询性能保障

针对高频物联网报文的范围查询与状态过滤，对 MySQL 执行了专门的 **B+ 树索引调优**：
- **联合索引 `(device_code, report_time)`**：专为根据设备编码进行历史轨迹、趋势及范围调阅设计，避免全表扫描。
- **单列索引 `(status)`**：用于瞬时捕获处于溺水警报状态的异常设备。
- **避免索引失效规范**：在 `SysDeviceAlarmDataMapper.xml` 范围过滤 SQL 中，严格维持 `report_time >= #{beginTime}` 的纯净比较格式，拒绝在查询列上套用 `DATE()` 或 `FORMAT()` 等各类破坏索引的函数，确保 B+ 树索引能够发挥最优性能。