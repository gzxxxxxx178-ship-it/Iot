// utils/demo-store.js
// 集中管理小程序的演示数据与状态（仅内存态：不落盘、不联网、不保存密码、不发起真实控制）

import { reactive } from 'vue'

// 将时间戳格式化为 YYYY-MM-DD HH:mm:ss（本地演示展示用）
export function formatDateTime(ts) {
  if (!ts) return '--'
  const d = new Date(ts)
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return (
    d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
  )
}

// 将时间戳格式化为 HH:mm（用于短趋势与列表）
export function formatTime(ts) {
  if (!ts) return '--'
  const d = new Date(ts)
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return p(d.getHours()) + ':' + p(d.getMinutes())
}

// 生成相对当前时刻 N 分钟前的时间戳（保证演示数据看起来“新鲜”）
function minutesAgo(m) {
  return Date.now() - m * 60000
}

// 生成演示历史数据点（含时间、温湿度、水位、设备号，覆盖约 20 天）
function makeDataPoints() {
  const deviceIds = ['gh-01', 'gh-02', 'pd-01', 'wh-01']
  const points = []
  for (let i = 0; i < 40; i++) {
    const deviceId = deviceIds[i % deviceIds.length]
    points.push({
      id: 'dp-' + i,
      deviceId,
      time: minutesAgo((40 - i) * 12 * 60),
      temp: Math.round((23 + Math.sin(i / 3) * 3 + (i % 5) * 0.4) * 10) / 10,
      hum: Math.round((58 + Math.cos(i / 4) * 8 + (i % 3) * 2) * 10) / 10,
      water: 20 + (i % 6) * 4,
      signal: -(50 + (i % 9) * 3)
    })
  }
  return points
}

// 生成近七日平均温度趋势（概览页短趋势柱状图）
function makeTrend() {
  const base = [23.2, 24.1, 25.6, 24.8, 26.3, 25.9, 26.1]
  // 根据本地日期生成最近七天的标签，避免演示日期固定在建页当天。
  return base.map((value, i) => {
    const date = new Date()
    date.setDate(date.getDate() - (6 - i))
    return { label: formatDateTime(date.getTime()).slice(5, 10), value }
  })
}

// 本地固定问答回复：根据关键词返回演示回复，不调用任何后端/LLM
function localReply(text) {
  const t = (text || '').trim()
  if (t.includes('温度') || t.includes('气温')) {
    const average = state.dataPoints.length
      ? (state.dataPoints.reduce((sum, point) => sum + point.temp, 0) / state.dataPoints.length).toFixed(1)
      : '--'
    return '演示回复：示例温度记录均值为 ' + average + '℃，与概览页一致。尚未接入真实传感器，不能据此判断作物生长状态。'
  }
  if (t.includes('水位') || t.includes('灌溉') || t.includes('补水')) {
    return '演示回复：示例灌溉策略为水位低于 15cm 时开启补水。此策略为演示规则，未接入真实设备。'
  }
  if (t.includes('报警') || t.includes('告警')) {
    return '演示回复：报警模块支持按状态筛选与确认，当前为演示记录，不触发真实告警通知。'
  }
  if (t.includes('自动化') || t.includes('规则')) {
    return '演示回复：自动化规则采用“条件→动作”结构，仅在演示状态下切换，不向设备下发真实命令。'
  }
  return '演示回复：我是本地固定的演示助手，只能返回示例问答，未接入真实 AI 服务。'
}

// 初始演示设备列表
const initialDevices = [
  { id: 'gh-01', name: '温室一号', type: '温室环境', status: 'online', temp: 26.3, hum: 64, water: 38, signal: -58, controlled: false, lastSeen: minutesAgo(2) },
  { id: 'gh-02', name: '温室二号', type: '温室环境', status: 'online', temp: 25.1, hum: 61, water: 42, signal: -61, controlled: false, lastSeen: minutesAgo(5) },
  { id: 'pd-01', name: '水田东区', type: '大田水位', status: 'online', temp: 27.8, hum: 72, water: 21, signal: -55, controlled: false, lastSeen: minutesAgo(1) },
  { id: 'pd-02', name: '水田西区', type: '大田水位', status: 'offline', temp: 24.5, hum: 68, water: 18, signal: -70, controlled: false, lastSeen: minutesAgo(120) },
  { id: 'wh-01', name: '仓储环境', type: '仓储监测', status: 'online', temp: 22.4, hum: 52, water: 0, signal: -66, controlled: false, lastSeen: minutesAgo(8) }
]

// 初始演示报警记录
const initialAlarms = [
  { id: 'al-1', deviceName: '温室一号', metric: '温度', message: '温度 32.5℃ 超过阈值 30℃', level: 'high', status: 'unconfirmed', time: minutesAgo(12) },
  { id: 'al-2', deviceName: '水田东区', metric: '水位', message: '水位 12cm 低于阈值 15cm', level: 'medium', status: 'unconfirmed', time: minutesAgo(40) },
  { id: 'al-3', deviceName: '水田西区', metric: '设备', message: '设备离线超过 2 小时', level: 'high', status: 'confirmed', time: minutesAgo(125) },
  { id: 'al-4', deviceName: '温室二号', metric: '湿度', message: '湿度 48% 低于阈值 55%', level: 'low', status: 'confirmed', time: minutesAgo(200) }
]

// 初始演示自动化规则
const initialRules = [
  { id: 'ru-1', name: '高温自动通风', condition: '温度 > 30℃', action: '打开通风设备', enabled: true },
  { id: 'ru-2', name: '低水位自动补水', condition: '水位 < 15cm', action: '打开灌溉水泵', enabled: true },
  { id: 'ru-3', name: '低湿度自动加湿', condition: '湿度 < 55%', action: '打开加湿器', enabled: false }
]

// 初始演示研究运行（示例指标，非冻结实验结论）
const initialResearchRuns = [
  {
    id: 'run-1', name: '示例运行 · 控制器A', status: 'completed', time: minutesAgo(60),
    metrics: [
      { key: '稳态误差', value: '3.2%' },
      { key: '调节时间', value: '128s' },
      { key: '超调量', value: '4.1%' }
    ]
  },
  {
    id: 'run-2', name: '示例运行 · 检测方法B', status: 'mixed', time: minutesAgo(180),
    metrics: [
      { key: '检出率', value: '78%' },
      { key: '误报率', value: '6.5%' },
      { key: '样本量', value: '16' }
    ]
  }
]

// 集中演示状态（reactive 保证各页面共享同一份响应式数据）
const state = reactive({
  demoMode: true,
  user: { username: '演示用户', loggedIn: true },
  devices: JSON.parse(JSON.stringify(initialDevices)),
  alarms: JSON.parse(JSON.stringify(initialAlarms)),
  rules: JSON.parse(JSON.stringify(initialRules)),
  dataPoints: makeDataPoints(),
  trend: makeTrend(),
  chatMessages: [
    { role: 'assistant', text: '你好，我是演示助手，可提供本地固定回复。', time: minutesAgo(1) }
  ],
  payments: [],
  simulation: { status: 'idle', progress: 0, result: null },
  researchRuns: JSON.parse(JSON.stringify(initialResearchRuns))
})

// 进入演示模式：设置本地演示用户（不保存密码、不宣称真实认证）
export function enterDemo() {
  state.demoMode = true
  state.user = { username: '演示用户', loggedIn: true }
}

// 退出演示登录（仅清除本地演示登录态，无任何真实凭证）
export function exitDemo() {
  state.user = { username: '', loggedIn: false }
}

// 重置全部演示数据到初始状态（供“我的”页重置演示数据）
export function resetDemo() {
  state.devices = JSON.parse(JSON.stringify(initialDevices))
  state.alarms = JSON.parse(JSON.stringify(initialAlarms))
  state.rules = JSON.parse(JSON.stringify(initialRules))
  state.dataPoints = makeDataPoints()
  state.trend = makeTrend()
  state.chatMessages = [
    { role: 'assistant', text: '你好，我是演示助手，可提供本地固定回复。', time: minutesAgo(1) }
  ]
  state.payments = []
  state.simulation = { status: 'idle', progress: 0, result: null }
  state.researchRuns = JSON.parse(JSON.stringify(initialResearchRuns))
}

// 控制设备：仅修改演示状态（controlled 标志与最后在线时间），不发送真实命令
export function controlDevice(deviceId, command) {
  const device = state.devices.find((d) => d.id === deviceId)
  if (!device || device.status !== 'online' || !['start', 'stop'].includes(command)) return false
  device.controlled = command === 'start'
  device.lastSeen = Date.now()
  return true
}

// 确认单条报警：将状态置为已确认
export function confirmAlarm(alarmId) {
  const alarm = state.alarms.find((a) => a.id === alarmId)
  if (alarm) alarm.status = 'confirmed'
}

// 批量确认报警（按筛选后的 ID 列表）
export function confirmAlarms(ids) {
  state.alarms.forEach((a) => {
    if (ids.includes(a.id)) a.status = 'confirmed'
  })
}

// 新增自动化规则
export function addRule(rule) {
  state.rules.push({
    id: 'ru-' + Date.now(),
    name: rule.name,
    condition: rule.condition,
    action: rule.action,
    enabled: true
  })
}

// 删除自动化规则
export function deleteRule(ruleId) {
  state.rules = state.rules.filter((r) => r.id !== ruleId)
}

// 切换自动化规则启用状态
export function toggleRule(ruleId) {
  const rule = state.rules.find((r) => r.id === ruleId)
  if (rule) rule.enabled = !rule.enabled
}

// 发送一条本地固定回复：追加用户消息与助手回复，返回助手回复文本
export function sendChat(text) {
  const clean = (text || '').trim()
  if (!clean) return ''
  state.chatMessages.push({ role: 'user', text: clean, time: Date.now() })
  const reply = localReply(clean)
  state.chatMessages.push({ role: 'assistant', text: reply, time: Date.now() })
  return reply
}

// 创建一笔模拟支付记录：仅生成演示订单号与成功状态，无真实扣款
export function createPayment(amount, subject) {
  const record = {
    id: 'pay-' + Date.now(),
    outTradeNo: 'DEMO' + Date.now(),
    amount,
    subject,
    status: 'success',
    time: Date.now()
  }
  state.payments.unshift(record)
  return record
}

// 启动仿真：清空上次结果并进入运行状态
export function simulationStart() {
  state.simulation = { status: 'running', progress: 0, result: null }
}

// 推进仿真进度：达到 100% 时生成演示结果
export function simulationAdvance(step) {
  if (state.simulation.status !== 'running') return
  state.simulation.progress = Math.min(100, state.simulation.progress + step)
  if (state.simulation.progress >= 100) {
    state.simulation.status = 'done'
    state.simulation.result = {
      steadyError: '3.2%',
      settleTime: '128s',
      overshoot: '4.1%',
      conclusion: '示例仿真完成：各项指标均为演示占位值，不代表真实控制效果。'
    }
  }
}

// 重置仿真：恢复空闲状态
export function simulationReset() {
  state.simulation = { status: 'idle', progress: 0, result: null }
}

// 新增一条示例研究运行（指标为演示占位值）
export function addResearchRun() {
  const n = state.researchRuns.length + 1
  const run = {
    id: 'run-' + Date.now(),
    name: '示例运行 · 方法' + String.fromCharCode(64 + n),
    status: n % 2 === 0 ? 'mixed' : 'completed',
    time: Date.now(),
    metrics: [
      { key: '指标一', value: (3 + n * 0.3).toFixed(1) + '%' },
      { key: '指标二', value: (100 + n * 8) + 's' },
      { key: '样本量', value: '16' }
    ]
  }
  state.researchRuns.unshift(run)
  return run
}

// 导出共享的演示状态（各页面通过该对象读取/渲染）
export function useDemoStore() {
  return state
}
