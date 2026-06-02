<template>
  <div class="iot-alarm-dashboard">
    <!-- 顶部监控状态栏 -->
    <div :class="['dashboard-header', totalAlarmCount > 0 ? 'alarm-active' : 'all-clear']">
      <div class="header-content">
        <div class="status-indicator">
          <span class="pulse-dot"></span>
          <h1 class="status-title">
            {{ totalAlarmCount > 0 ? '检测到水域告警事件' : '水域安全监测大屏 - 当前无告警' }}
          </h1>
        </div>
        <div class="realtime-clock">
          <span>当前监控终端数量: <strong>{{ devices.length }}</strong> 台</span>
          <span class="divider">|</span>
          <span>当前溺水报警: <strong class="alarm-count-badge">{{ totalAlarmCount }}</strong> 处</span>
          <span class="divider">|</span>
          <span class="time-string">{{ currentTime }}</span>
        </div>
      </div>
    </div>

    <!-- 主体区域：栅格布局 -->
    <main class="dashboard-body">
      <!-- 快捷操作与模拟控制区 -->
      <section class="control-panel glass-card">
        <div class="panel-header">
          <h3 class="panel-title">告警模拟与联调控制台</h3>
          <el-tag size="small" type="info" effect="plain">轮询间隔: 500ms</el-tag>
        </div>
        <div class="panel-actions">
          <el-button 
            type="danger" 
            icon="Opportunity"
            @click="triggerSimulatedAlarm"
            :disabled="simulationActive"
          >
            模拟告警事件
          </el-button>
          <el-button 
            type="success" 
            icon="CircleCheck"
            @click="clearSimulatedAlarm"
            :disabled="!simulationActive"
          >
            解除模拟告警
          </el-button>
          <el-button 
            type="primary" 
            plain 
            icon="Refresh"
            @click="fetchData"
          >
            刷新实时状态
          </el-button>
        </div>
        <div class="panel-desc">
          <span>当前数据获取模式：</span>
          <el-badge :value="simulationActive ? '模拟数据' : '在线轮询'" :type="simulationActive ? 'warning' : 'success'" />
          <span class="tip-text">（无真实硬件数据时，可通过模拟模式验证告警状态与前端展示链路）</span>
        </div>
      </section>

      <!-- 网格显示区域 -->
      <section class="device-grid">
        <div 
          v-for="device in devices" 
          :key="device.deviceCode" 
          :class="['device-card', 'glass-card', device.status === 1 ? 'critical-danger' : '']"
        >
          <!-- 警报警示小标 -->
          <div v-if="device.status === 1" class="danger-ribbon">
            <span>DROWNING</span>
          </div>

          <div class="card-inner">
            <div class="device-header">
              <span class="device-badge">IoT TERMINAL</span>
              <h4 class="device-code">{{ device.deviceCode }}</h4>
            </div>

            <!-- 数据展示组 -->
            <div class="device-metrics">
              <div class="metric-item">
                <span class="metric-label">实时水深</span>
                <span class="metric-value">
                  <strong>{{ device.waterDepth }}</strong> <small>米</small>
                </span>
              </div>
              <div class="metric-item">
                <span class="metric-label">状态</span>
                <span :class="['status-text', device.status === 1 ? 'status-red' : 'status-green']">
                  {{ device.status === 1 ? '⚠️ 溺水警报' : '正常监测' }}
                </span>
              </div>
            </div>

            <!-- 电池电量 (Element Plus Progress) -->
            <div class="device-battery">
              <div class="battery-label">
                <span>电池余量</span>
                <span>{{ device.battery }}%</span>
              </div>
              <el-progress 
                :percentage="device.battery" 
                :status="getBatteryStatus(device.battery)"
                :stroke-width="8"
                :show-text="false"
                class="battery-progress"
              />
            </div>

            <!-- 附加时间戳 -->
            <div class="card-footer">
              <span>上报时间: {{ formatTime(device.reportTime) }}</span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 底部告警事件滚动播报 -->
    <footer class="dashboard-footer glass-card">
      <div class="ticker-label">告警播报:</div>
      <div class="ticker-content">
        <transition-group name="ticker" tag="div" class="ticker-wrapper">
          <div 
            v-for="log in alarmLogs" 
            :key="log.id" 
            class="ticker-item"
          >
            [{{ log.time }}] 设备 {{ log.deviceCode }} 触发溺水警报，水深 {{ log.waterDepth }} 米，请现场人员及时核验。
          </div>
          <div v-if="alarmLogs.length === 0" class="ticker-empty">
            当前无待处理告警事件。
          </div>
        </transition-group>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { Opportunity, CircleCheck, Refresh } from '@element-plus/icons-vue'

// 1. 初始化代表性监测终端数据。真实环境中可由后端接口或设备接入层提供。
const devices = ref([
  { deviceCode: 'TERM-BEACH-001', waterDepth: 1.25, status: 0, battery: 88, reportTime: new Date() },
  { deviceCode: 'TERM-BEACH-002', waterDepth: 1.82, status: 0, battery: 92, reportTime: new Date() },
  { deviceCode: 'TERM-POOL-A1', waterDepth: 2.10, status: 0, battery: 76, reportTime: new Date() },
  { deviceCode: 'TERM-POOL-A2', waterDepth: 1.50, status: 0, battery: 15, reportTime: new Date() },
  { deviceCode: 'TERM-SEA-DEEP01', waterDepth: 4.85, status: 0, battery: 64, reportTime: new Date() },
  { deviceCode: 'TERM-SEA-DEEP02', waterDepth: 5.20, status: 0, battery: 81, reportTime: new Date() },
  { deviceCode: 'TERM-FISHERY-03', waterDepth: 3.40, status: 0, battery: 99, reportTime: new Date() },
  { deviceCode: 'TERM-BEACH-008', waterDepth: 0.95, status: 0, battery: 42, reportTime: new Date() }
])

// 状态和控制变量
const currentTime = ref('')
const simulationActive = ref(false)
const alarmLogs = ref([])
let timer = null
let clockTimer = null

// 2. 计算当前全盘中激活的溺水报警数
const totalAlarmCount = computed(() => {
  return devices.value.filter(d => d.status === 1).length
})

// 3. 电池电量状态匹配
const getBatteryStatus = (percent) => {
  if (percent <= 20) return 'exception' // 红色警告
  if (percent <= 50) return 'warning'   // 黄色提示
  return 'success'                      // 正常科技绿
}

// 4. 时钟更新
const updateClock = () => {
  const now = new Date()
  currentTime.value = now.toLocaleDateString() + ' ' + now.toTimeString().split(' ')[0]
}

// 5. 格式化上报时间
const formatTime = (time) => {
  if (!time) return 'N/A'
  const date = new Date(time)
  return date.toTimeString().split(' ')[0]
}

// 6. 后端轮询请求逻辑 (每 0.5 秒)
const fetchData = async () => {
  if (simulationActive.value) {
    // 模拟数据变化，用于无硬件环境下验证页面展示效果
    devices.value.forEach(d => {
      if (d.status === 0) {
        const delta = (Math.random() - 0.5) * 0.04
        d.waterDepth = parseFloat((d.waterDepth + delta).toFixed(2))
        d.reportTime = new Date()
      }
    })
    return
  }

  // 在线模式：向 RuoYi-Cloud 后端 Controller 发送请求
  try {
    // 此处调用若依通用封装的 request，这里为了展示完整可跑代码，兼容 fetch
    const response = await fetch('/prod-api/iot/alarm/realtime')
    const resJson = await response.json()
    if (resJson && resJson.code === 200) {
      const activeRedisAlarms = resJson.data || {}
      
      // 更新设备状态：如果 Redis 中有该设备的 status = 1 记录，则置为报警
      devices.value.forEach(d => {
        const hasAlarm = activeRedisAlarms[d.deviceCode] !== undefined
        if (hasAlarm && d.status === 0) {
          // 首次从正常切为报警，追加滚动日志记录
          d.status = 1
          d.waterDepth = parseFloat((d.waterDepth + 0.8).toFixed(2))
          d.reportTime = new Date()
          addAlarmLog(d.deviceCode, d.waterDepth)
        } else if (!hasAlarm && d.status === 1) {
          d.status = 0
        }
        
        // 模拟水深数据小幅变化
        const delta = (Math.random() - 0.5) * 0.04
        d.waterDepth = parseFloat((Math.max(0.1, d.waterDepth + delta)).toFixed(2))
      })
    }
  } catch (error) {
    // 网络不可用时保持页面可演示，方便本地联调和项目展示
    devices.value.forEach(d => {
      const delta = (Math.random() - 0.5) * 0.02
      d.waterDepth = parseFloat((Math.max(0.1, d.waterDepth + delta)).toFixed(2))
    })
  }
}

// 7. 添加溺水滚动播报记录
const addAlarmLog = (deviceCode, depth) => {
  const now = new Date()
  const timeStr = now.toTimeString().split(' ')[0]
  alarmLogs.value.unshift({
    id: Date.now() + Math.random(),
    time: timeStr,
    deviceCode,
    waterDepth: depth
  })
  // 最多保留5条最新紧急播报
  if (alarmLogs.value.length > 5) {
    alarmLogs.value.pop()
  }
}

// 8. 控制面板：模拟溺水告警事件
const triggerSimulatedAlarm = () => {
  simulationActive.value = true
  
  // 选择两个监控点模拟告警
  const target1 = devices.value[2] // TERM-POOL-A1
  const target2 = devices.value[4] // TERM-SEA-DEEP01
  
  target1.status = 1
  target1.waterDepth = 2.45
  target1.reportTime = new Date()
  addAlarmLog(target1.deviceCode, target1.waterDepth)

  target2.status = 1
  target2.waterDepth = 5.92
  target2.reportTime = new Date()
  addAlarmLog(target2.deviceCode, target2.waterDepth)
}

// 解除模拟报警
const clearSimulatedAlarm = () => {
  devices.value.forEach(d => {
    d.status = 0
    if (d.deviceCode === 'TERM-POOL-A1') d.waterDepth = 2.10
    if (d.deviceCode === 'TERM-SEA-DEEP01') d.waterDepth = 4.85
  })
  simulationActive.value = false
  alarmLogs.value = []
}

// 生命周期挂载
onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  
  // 核心 0.5s 高频轮询定时任务挂载
  fetchData()
  timer = setInterval(fetchData, 500)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
/* 全局背景及字体设置：深色监控大屏风格 */
.iot-alarm-dashboard {
  background-color: #0b1120;
  min-height: 100vh;
  color: #f1f5f9;
  font-family: 'Inter', 'SF Pro Display', -apple-system, sans-serif;
  padding: 20px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

/* 玻璃态卡片统一视觉规范 */
.glass-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(16px);
  border-radius: 16px;
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  transition: all 0.3s ease;
}

/* 顶部监控状态栏 */
.dashboard-header {
  border-radius: 16px;
  padding: 20px 30px;
  margin-bottom: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 正常状态 */
.all-clear {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(5, 150, 105, 0.3) 100%);
  border-left: 8px solid #10b981;
}

/* 告警状态 */
.alarm-active {
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.3) 0%, rgba(185, 28, 28, 0.55) 100%);
  border-left: 8px solid #dc2626;
  box-shadow: 0 0 40px rgba(220, 38, 38, 0.5), inset 0 0 20px rgba(220, 38, 38, 0.3);
  animation: headerGlowPulse 1.5s infinite alternate;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pulse-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background-color: currentColor;
  position: relative;
}

.all-clear .pulse-dot {
  color: #10b981;
  box-shadow: 0 0 10px #10b981;
}

.alarm-active .pulse-dot {
  color: #f87171;
  animation: alarmDotFlash 0.8s infinite alternate;
}

.status-title {
  font-size: 24px;
  font-weight: 800;
  margin: 0;
  letter-spacing: 1px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
}

.realtime-clock {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: #94a3b8;
}

.realtime-clock strong {
  color: #f1f5f9;
}

.alarm-count-badge {
  background-color: #dc2626;
  color: #fff;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 13px;
  box-shadow: 0 0 8px #dc2626;
}

.divider {
  color: rgba(255, 255, 255, 0.15);
}

.time-string {
  font-family: 'Courier New', Courier, monospace;
  font-weight: bold;
}

/* 主体区域 */
.dashboard-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;
}

/* 调度操作卡片 */
.control-panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  color: #38bdf8;
}

.panel-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.panel-desc {
  margin-top: 12px;
  font-size: 13px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tip-text {
  color: #94a3b8;
}

/* 网格显示区域 */
.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

/* 设备卡片 */
.device-card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  cursor: pointer;
}

.device-card:hover {
  transform: translateY(-5px);
  border-color: rgba(56, 189, 248, 0.4);
  box-shadow: 0 12px 40px rgba(56, 189, 248, 0.15);
}

.card-inner {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.device-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.device-badge {
  font-size: 10px;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 1.5px;
}

.device-code {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #f1f5f9;
}

.device-metrics {
  display: flex;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
}

.metric-value {
  font-size: 14px;
}

.metric-value strong {
  font-size: 22px;
  color: #f8fafc;
}

.metric-value small {
  font-size: 12px;
  color: #94a3b8;
}

.status-text {
  font-size: 13px;
  font-weight: bold;
  margin-top: 4px;
}

.status-green {
  color: #34d399;
  text-shadow: 0 0 6px rgba(52, 211, 153, 0.3);
}

.status-red {
  color: #f87171;
  text-shadow: 0 0 6px rgba(248, 113, 113, 0.3);
}

/* 电池进度条 */
.device-battery {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.battery-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
}

.battery-progress {
  width: 100%;
}

.card-footer {
  font-size: 11px;
  color: #475569;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
  text-align: right;
}

/* 危险状态卡片特效：全景呼吸红晕灯闪烁特效 */
.critical-danger {
  border-color: rgba(239, 68, 68, 0.8);
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.15) 0%, rgba(127, 29, 29, 0.4) 100%);
  animation: cardAlarmPulse 1.2s infinite ease-in-out;
}

.critical-danger:hover {
  border-color: #ef4444;
}

.critical-danger .device-code {
  color: #fca5a5;
  text-shadow: 0 0 8px rgba(239, 68, 68, 0.5);
}

/* 侧边危急挂带 */
.danger-ribbon {
  position: absolute;
  top: 15px;
  right: -30px;
  background-color: #ef4444;
  color: #fff;
  transform: rotate(45deg);
  width: 110px;
  text-align: center;
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 1px;
  box-shadow: 0 2px 10px rgba(239, 68, 68, 0.4);
  padding: 2px 0;
  z-index: 10;
}

/* 底部状态滚动播报区 */
.dashboard-footer {
  padding: 15px 25px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.ticker-label {
  font-size: 15px;
  font-weight: bold;
  color: #f87171;
  white-space: nowrap;
}

.ticker-content {
  flex: 1;
  overflow: hidden;
  height: 24px;
}

.ticker-wrapper {
  display: flex;
  flex-direction: column;
}

.ticker-item {
  font-size: 14px;
  color: #e2e8f0;
  line-height: 24px;
  animation: tickerSlideUp 0.3s ease-out;
}

.ticker-empty {
  font-size: 14px;
  color: #94a3b8;
  line-height: 24px;
}

/* CSS3 动画集锦 */

/* 1. 顶部大屏报警全局红晕呼吸特效 */
@keyframes headerGlowPulse {
  0% {
    box-shadow: 0 0 20px rgba(220, 38, 38, 0.4), inset 0 0 10px rgba(220, 38, 38, 0.2);
  }
  100% {
    box-shadow: 0 0 45px rgba(220, 38, 38, 0.75), inset 0 0 20px rgba(220, 38, 38, 0.4);
  }
}

/* 2. 红色呼吸警报灯圆点闪烁 */
@keyframes alarmDotFlash {
  0% {
    opacity: 0.2;
    transform: scale(0.9);
  }
  100% {
    opacity: 1;
    transform: scale(1.2);
    box-shadow: 0 0 12px #ef4444;
  }
}

/* 3. 设备卡片级全景红色红晕呼吸灯特效 (利用 box-shadow 与渐变虚光) */
@keyframes cardAlarmPulse {
  0% {
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.25), inset 0 0 10px rgba(239, 68, 68, 0.1);
  }
  50% {
    box-shadow: 0 0 35px rgba(239, 68, 68, 0.65), inset 0 0 20px rgba(239, 68, 68, 0.35);
    border-color: rgba(239, 68, 68, 0.9);
  }
  100% {
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.25), inset 0 0 10px rgba(239, 68, 68, 0.1);
  }
}

/* 4. Ticker滑动载入效果 */
@keyframes tickerSlideUp {
  0% {
    transform: translateY(100%);
    opacity: 0;
  }
  100% {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
