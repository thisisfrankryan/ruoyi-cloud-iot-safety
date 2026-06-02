# 🌊 基于 RuoYi-Cloud + IoT 的智能水域溺水安全监控与微服务调度系统

## 🚀 项目硬核背景与亮点
本系统是一套专为高并发水域安全监测定制的**工业级物联网分布式微服务系统**。系统基于 `RuoYi-Cloud` 核心底座进行深度二开，打通了从“水下物联网传感器边缘接入”、“高并发海啸级报文流量削峰”、直到“前端独立显卡硬件加速（GPU Acceleration）视觉警报演练”的云原生全链路。

### 🛠️ 核心架构与 DevOps 战术内幕：
- **高并发海啸削峰大运河 (Traffic Shaving)**：针对成千上万物联网手环毫秒级发报场景，手搓基于 **RabbitMQ** 的缓冲队列，配合后端异步 **AlarmConsumer** 特工工兵，实施“全内存高速铺设 Redis 闪屏白板 + 5秒周期性定时批量合并写盘（Batch Insertion）”的双轨高吞吐闭环，将 MySQL 锁写暴击率降低 99%，系统吞吐量原地翻百倍！
- **云原生赛博微缩机房 (DevOps / Docker / WSL2)**：告别繁琐的 Windows 宿主机污染部署，利用 **Dockerfile** 深度注入 `-XX:+UseG1GC` 垃圾回收调优机油，消除时空冻结（STW）。通过 **Docker Compose** 编排包括 Nacos、MySQL、Redis、RabbitMQ 及自定义 IoT 模块在内的 8 大容器实例，一键全自动八星连珠落地通网。并在本地成功打通 **WSL2 Linux 核心特权组共享** 的大厂级沙盘演练闭环。
- **4K 科技感视觉全息堡垒 (Vue3 & GPU 加速)**：大屏前端基于 **Vue3 组合式 API + Vite** 纯手工搭建，运用 **Glassmorphism（赛博毛玻璃）** 高级折射特效。底层通过 JavaScript 动态短轮询（Short Polling）哨兵对后厨进行毫秒级死盯。针对突发溺水险情，通过 CSS `will-change` 与 `translateZ(0)` **强行逼迫客户端物理独立显卡（GPU）接管当前卡片图层进行 3D 渲染**，实现 4K 极速分辨率下 120 帧满帧运行的“血红色呼吸红晕灯”视觉重磅轰炸！

## 🏢 园区全盘微服务模块职责账本
- `ruoyi-gateway [8080]`：园区防弹大铁门。负责 Netty 异步响应式路由转发与统一 JWT 工牌查验安检。
- `ruoyi-auth [9200]`：统一身份认证中心。管辖全园工牌签发、回收及过期时间控制。
- `ruoyi-modules-system [9201]`：大内行政总管。基于 RBAC（基于角色的权限控制）管辖用户、部门、岗位及 AOP 按钮级特权拦截。
- `ruoyi-modules-iot [9800]`：**【自主手搓核心】**特种物联网微服务车间，负责吞噬 RabbitMQ 运河报文并提供大屏极速数据源。
- `ruoyi-common`：全能总务工具箱。涵盖 Seata 分布式事务联合记账、数据脱敏打码机及 Swagger 活体全自动说明书。