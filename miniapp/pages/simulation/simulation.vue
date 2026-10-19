<template>
  <view class="page simulation-page">
    <PageHeader title="仿真验证" subtitle="MATLAB 软件在环数据与规则" badge="仿真数据" />
    <view class="warning-card"><text>仿真数据不代表田间实测。此页面只读取仿真接口；待执行命令仅供 MATLAB 轮询，不会下发真实 MQTT 设备。</text></view>

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看仿真数据" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>

    <template v-else>
      <view class="card device-card">
        <text class="field-label">仿真设备 ID</text>
        <view class="device-controls">
          <input class="text-field device-input" v-model="deviceId" maxlength="64" placeholder="SIM-PADDY-001" confirm-type="done" />
          <button class="btn-secondary refresh-button" :disabled="polling" @tap="poll">{{ polling ? '读取中…' : '立即刷新' }}</button>
        </view>
        <view class="poll-status"><view class="status-dot" :class="'status-' + pollStatus"></view><text>{{ pollStatusLabel }}</text><text v-if="updatedAt" class="hint">{{ updatedAt }}</text></view>
        <text v-if="pollError" class="error-text">{{ pollError }}</text>
      </view>

      <view class="card summary-grid">
        <view class="summary-item"><text class="hint">最后数据时间</text><text class="summary-value">{{ formatDate(latest?.occurredAt) }}</text></view>
        <view class="summary-item"><text class="hint">场景编号</text><text class="summary-value">{{ latest?.scenarioCode || '--' }}</text></view>
        <view class="summary-item"><text class="hint">生育期</text><text class="summary-value">{{ latest?.growthStage || '--' }}</text></view>
        <view class="summary-item"><text class="hint">数据来源</text><text class="summary-value">{{ latest?.sourceType || '--' }}</text></view>
      </view>

      <view v-if="latest" class="metrics-grid">
        <view v-for="metric in telemetryMetrics" :key="metric.key" class="card telemetry-card">
          <text class="metric-label">{{ metric.label }}</text>
          <text class="telemetry-value">{{ formatValue(latest[metric.key]) }}<text v-if="latest[metric.key] !== null && latest[metric.key] !== undefined" class="unit"> {{ metric.unit }}</text></text>
        </view>
      </view>
      <view v-else-if="!polling && !pollError" class="card"><EmptyState text="该设备暂无仿真遥测数据" /></view>

      <view v-if="latest" class="card status-card">
        <text class="section-title">设备状态</text>
        <view v-for="item in deviceStatuses" :key="item.key" class="state-row">
          <text>{{ item.label }}</text><text class="state-value" :class="stateClass(latest[item.key])">{{ booleanLabel(latest[item.key]) }}</text>
        </view>
      </view>

      <view class="section-heading"><text class="section-title">遥测趋势（最近100条）</text><text class="hint">{{ history.length }} 条</text></view>
      <view v-if="history.length" class="card trend-card">
        <view v-for="metric in telemetryMetrics" :key="metric.key" class="trend-block">
          <text class="metric-label">{{ metric.label }}（{{ metric.unit }}）</text>
          <BarTrend :items="trendFor(metric.key)" :color="metric.color" :max-bar-height="130" />
        </view>
      </view>
      <view v-else-if="!polling" class="card"><EmptyState text="暂无仿真趋势数据" /></view>

      <view class="section-heading">
        <text class="section-title">仿真规则（{{ rules.length }}）</text>
        <button class="btn-primary small-button" @tap="openCreate">添加规则</button>
      </view>
      <view v-if="rules.length" class="rule-list">
        <view v-for="rule in rules" :key="rule.id" class="card rule-card">
          <view class="rule-heading"><text class="rule-name">{{ rule.name }}</text><text class="severity-tag" :class="severityClass(rule.severity)">{{ rule.severity }}</text></view>
          <text class="hint rule-line">{{ rule.deviceId }} · {{ metricLabel(rule.metric) }} · {{ operatorLabel(rule.operator) }} {{ formatValue(rule.threshold) }}</text>
          <text class="hint rule-line">恢复阈值 {{ formatValue(rule.recoveryThreshold) }} · 连续 {{ rule.debounceCount }} 次 · {{ actionLabel(rule.action) }}</text>
          <view class="rule-actions">
            <text class="rule-enabled" :class="rule.enabled ? 'enabled' : 'disabled'">{{ rule.enabled ? '已启用' : '已停用' }}</text>
            <button class="btn-secondary tiny-button" :disabled="busyRuleIds.has(rule.id)" @tap="toggleRule(rule)">{{ rule.enabled ? '停用' : '启用' }}</button>
            <button class="btn-secondary tiny-button" :disabled="busyRuleIds.has(rule.id)" @tap="openEdit(rule)">编辑</button>
            <button class="btn-danger tiny-button" :disabled="busyRuleIds.has(rule.id)" @tap="confirmDelete(rule)">删除</button>
          </view>
        </view>
      </view>
      <view v-else-if="!polling" class="card"><EmptyState text="暂未配置仿真规则" /></view>

      <view class="section-heading"><text class="section-title">仿真报警（{{ alarms.length }}）</text></view>
      <view v-if="alarms.length" class="rule-list">
        <view v-for="alarm in alarms" :key="alarm.id" class="card alarm-card">
          <view class="rule-heading"><text class="rule-name">{{ metricLabel(alarm.metric) }} · {{ alarm.severity }}</text><text class="alarm-status" :class="'alarm-' + String(alarm.status || '').toLowerCase()">{{ alarmStatusLabel(alarm.status) }}</text></view>
          <text class="hint rule-line">实测 {{ formatValue(alarm.actualValue) }} / 阈值 {{ formatValue(alarm.threshold) }}</text>
          <text class="hint rule-line">触发 {{ formatDate(alarm.triggeredAt) }} · 确认 {{ formatDate(alarm.acknowledgedAt) }} · 恢复 {{ formatDate(alarm.resolvedAt) }}</text>
          <button v-if="alarm.status === 'ACTIVE'" class="btn-primary small-button acknowledge-button" :disabled="acknowledgingId === alarm.id" @tap="acknowledgeAlarm(alarm)">{{ acknowledgingId === alarm.id ? '确认中…' : '确认报警' }}</button>
        </view>
      </view>
      <view v-else-if="!polling" class="card"><EmptyState text="暂无仿真报警" /></view>

      <view class="section-heading"><text class="section-title">待处理仿真命令（{{ commands.length }}）</text></view>
      <view v-if="commands.length" class="rule-list">
        <view v-for="command in commands" :key="command.id" class="card command-card">
          <view class="rule-heading"><text class="rule-name">{{ actionLabel(command.action) }}</text><text class="command-state">{{ commandStatusLabel(command.status) }}</text></view>
          <text class="hint rule-line">{{ formatDate(command.createdAt) }}</text>
          <text class="hint rule-line">{{ command.message || '等待仿真端处理' }}</text>
        </view>
      </view>
      <view v-else-if="!polling" class="card"><EmptyState text="暂无待处理仿真命令" /></view>
    </template>

    <view v-if="formVisible" class="mask" @tap="closeForm">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-heading"><text class="sheet-title">{{ editingRuleId !== null ? '编辑仿真规则' : '添加仿真规则' }}</text><text class="sheet-close" @tap="closeForm">关闭</text></view>
        <text class="field-label">规则名称</text>
        <input class="text-field" v-model="form.name" maxlength="100" placeholder="例如：高水位警报" />
        <text class="field-label">设备 ID（* 表示全部仿真设备）</text>
        <input class="text-field" v-model="form.deviceId" maxlength="64" placeholder="SIM-PADDY-001 或 *" />
        <text class="field-label">监控指标</text>
        <picker :range="metricOptions" range-key="label" :value="form.metricIndex" @change="selectMetric"><view class="picker-field">{{ metricOptions[form.metricIndex].label }}　›</view></picker>
        <text class="field-label">比较操作符</text>
        <picker :range="operatorOptions" range-key="label" :value="form.operatorIndex" @change="selectOperator"><view class="picker-field">{{ operatorOptions[form.operatorIndex].label }}　›</view></picker>
        <text class="field-label">触发阈值（{{ selectedMetric.unit }}）</text>
        <input class="text-field" type="digit" v-model="form.threshold" placeholder="输入触发阈值" />
        <text class="field-label">恢复阈值（{{ selectedMetric.unit }}）</text>
        <input class="text-field" type="digit" v-model="form.recoveryThreshold" placeholder="输入滞回恢复阈值" />
        <text class="hint form-hint">{{ form.operatorIndex === 0 ? '大于规则的恢复阈值必须低于触发阈值。' : '小于规则的恢复阈值必须高于触发阈值。' }}</text>
        <text class="field-label">连续命中次数（1–100）</text>
        <input class="text-field" type="number" v-model="form.debounceCount" />
        <text class="field-label">严重等级</text>
        <picker :range="severityOptions" range-key="label" :value="form.severityIndex" @change="selectSeverity"><view class="picker-field">{{ severityOptions[form.severityIndex].label }}　›</view></picker>
        <text class="field-label">触发动作</text>
        <picker :range="actionOptions" range-key="label" :value="form.actionIndex" @change="selectAction"><view class="picker-field">{{ actionOptions[form.actionIndex].label }}　›</view></picker>
        <view class="enabled-row" @tap="form.enabled = !form.enabled"><text class="field-label enabled-label">保存后启用规则</text><text class="enabled-control">{{ form.enabled ? '是' : '否' }}</text></view>
        <button class="btn-primary action-button" :disabled="saving" @tap="saveRule">{{ saving ? '保存中…' : '保存规则' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import BarTrend from '../../components/BarTrend.vue'
import { session } from '../../utils/session.js'
import { buildTrend } from '../../utils/trend.js'
import { acknowledgeSimulationAlarm as acknowledgeAlarmRequest, createSimulationRule, deleteSimulationRule, getLatestSimulationTelemetry, getSimulationTelemetryHistory, listPendingSimulationCommands, listSimulationAlarms, listSimulationRules, updateSimulationRule } from '../../utils/simulation-store.js'

const metricOptions = [
  { value: 'waterLevelMm', label: '水位', unit: 'mm', color: '#1F6B4F' },
  { value: 'flowRateLMin', label: '流量', unit: 'L/min', color: '#3484A8' },
  { value: 'ecMsCm', label: 'EC', unit: 'mS/cm', color: '#D97706' },
  { value: 'soilMoisturePct', label: '土壤含水率', unit: '%', color: '#7C3AED' },
  { value: 'rainfallMm', label: '降雨量', unit: 'mm', color: '#2563EB' }
]
const operatorOptions = [{ value: 'gt', label: '大于（>）' }, { value: 'lt', label: '小于（<）' }]
const severityOptions = [{ value: 'INFO', label: 'INFO' }, { value: 'WARN', label: 'WARN' }, { value: 'CRITICAL', label: 'CRITICAL' }]
const actionOptions = [{ value: 'NOTIFY', label: '通知' }, { value: 'STOP_IRRIGATION', label: '停止灌溉' }, { value: 'STOP_FERTILIZER', label: '停止施肥' }, { value: 'STOP_ALL', label: '停止全部' }]
const telemetryMetrics = metricOptions
const deviceStatuses = [{ key: 'pumpOn', label: '水泵' }, { key: 'irrigationValveOpen', label: '灌溉阀' }, { key: 'fertilizerPumpOn', label: '施肥泵' }]
const deviceId = ref('SIM-PADDY-001')
const latest = ref(null)
const history = ref([])
const rules = ref([])
const alarms = ref([])
const commands = ref([])
const polling = ref(false)
const pollStatus = ref('idle')
const pollError = ref('')
const updatedAt = ref('')
const formVisible = ref(false)
const editingRuleId = ref(null)
const saving = ref(false)
const acknowledgingId = ref(null)
const busyRuleIds = ref(new Set())
const form = reactive(createEmptyForm())
const trendRows = computed(() => history.value.map((item) => ({ ...item, time: item.occurredAt ? new Date(item.occurredAt).getTime() : null })))
const selectedMetric = computed(() => metricOptions[form.metricIndex] || metricOptions[0])
const pollStatusLabel = computed(() => ({ idle: '就绪', polling: '读取中', error: '读取异常' })[pollStatus.value] || '状态未知')

let pageVisible = false
let pollTimer = null
let deviceChangeTimer = null
let pollRequestId = 0
let pollInFlight = false
let pollQueued = false

// 页面显示时启动真实仿真数据轮询。
onShow(startPolling)

// 页面隐藏时停止轮询并作废未完成的旧页面请求。
onHide(stopPolling)

// 账号切换时清空旧账号数据并仅对新会话重新加载。
watch(() => session.generation, () => {
  invalidatePoll()
  latest.value = null; history.value = []; rules.value = []; alarms.value = []; commands.value = []
  pollError.value = ''; updatedAt.value = ''; formVisible.value = false
  if (pageVisible && session.token) startPolling()
  else stopPolling()
})

// 设备ID停止输入后立即刷新对应仿真数据。
watch(deviceId, () => {
  if (!pageVisible || !session.token) return
  clearTimeout(deviceChangeTimer)
  deviceChangeTimer = setTimeout(() => poll(), 400)
})

// 创建规则表单默认值。
function createEmptyForm() {
  return { name: '', deviceId: deviceId.value || 'SIM-PADDY-001', metricIndex: 0, operatorIndex: 0, threshold: '', recoveryThreshold: '', debounceCount: '2', severityIndex: 1, actionIndex: 0, enabled: true }
}

// 启动页面可见期间每2秒进行一次仿真数据轮询。
function startPolling() {
  pageVisible = true
  if (!session.token) return
  clearInterval(pollTimer)
  poll(); pollTimer = setInterval(poll, 2000)
}

// 停止定时轮询、设备输入防抖和迟到响应。
function stopPolling() {
  pageVisible = false
  clearInterval(pollTimer); pollTimer = null
  clearTimeout(deviceChangeTimer); deviceChangeTimer = null
  invalidatePoll()
}

// 使当前请求失效并复位轮询状态，防止页面隐藏后旧数据回写。
function invalidatePoll() { pollRequestId += 1; pollInFlight = false; pollQueued = false; polling.value = false }

// 并行读取遥测、历史、规则、报警和待处理仿真命令。
async function poll() {
  if (!pageVisible || !session.token) return
  if (pollInFlight) { pollQueued = true; return }
  const id = ++pollRequestId
  const generation = session.generation
  const currentDevice = deviceId.value.trim()
  if (!currentDevice || currentDevice.length > 64) { pollStatus.value = 'error'; pollError.value = '设备ID必须为1–64个字符'; return }
  pollInFlight = true; polling.value = true; pollStatus.value = 'polling'; pollError.value = ''
  const results = await Promise.allSettled([
    getLatestSimulationTelemetry(currentDevice),
    getSimulationTelemetryHistory(currentDevice, 100),
    listSimulationRules(),
    listSimulationAlarms(currentDevice),
    listPendingSimulationCommands(currentDevice)
  ])
  if (id !== pollRequestId || generation !== session.generation || currentDevice !== deviceId.value.trim()) {
    finishPolling(id)
    return
  }
  const failures = []
  // 按请求结果更新单个数据区，接口短暂失败时保留其上一次有效值。
  const accept = (index, target, fallback, expectedType) => {
    const result = results[index]
    if (result.status === 'fulfilled') {
      if (expectedType === 'array' && !Array.isArray(result.value)) failures.push('后端数据格式不正确')
      else target(result.value ?? fallback)
    } else if (!result.reason?.stale) failures.push(result.reason?.message || '仿真数据读取失败')
  }
  accept(0, (value) => { latest.value = value }, null)
  accept(1, (value) => { history.value = value }, [], 'array')
  accept(2, (value) => { rules.value = value }, [], 'array')
  accept(3, (value) => { alarms.value = value }, [], 'array')
  accept(4, (value) => { commands.value = value }, [], 'array')
  pollError.value = [...new Set(failures)].join('；')
  pollStatus.value = failures.length ? 'error' : 'idle'
  updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  finishPolling(id)
}

// 释放当前轮询并在设备切换期间补发最新一次查询。
function finishPolling(id) {
  if (id !== pollRequestId) return
  pollInFlight = false; polling.value = false
  if (pollQueued && pageVisible && session.token) { pollQueued = false; poll() }
}

// 将遥测历史生成指定指标的最多12个趋势点。
function trendFor(field) {
  return buildTrend(trendRows.value, field, (time) => new Date(time).toLocaleTimeString('zh-CN', { hour12: false }).slice(0, 5), 12)
}

// 打开空白规则表单并默认使用当前仿真设备ID。
function openCreate() {
  editingRuleId.value = null
  Object.assign(form, createEmptyForm())
  form.deviceId = deviceId.value.trim() || 'SIM-PADDY-001'
  formVisible.value = true
}

// 将现有规则的完整字段载入编辑表单。
function openEdit(rule) {
  editingRuleId.value = rule.id
  Object.assign(form, {
    name: rule.name || '', deviceId: rule.deviceId || '',
    metricIndex: Math.max(0, metricOptions.findIndex((item) => item.value === rule.metric)),
    operatorIndex: Math.max(0, operatorOptions.findIndex((item) => item.value === rule.operator)),
    threshold: String(rule.threshold ?? ''), recoveryThreshold: String(rule.recoveryThreshold ?? ''),
    debounceCount: String(rule.debounceCount ?? 1),
    severityIndex: Math.max(0, severityOptions.findIndex((item) => item.value === rule.severity)),
    actionIndex: Math.max(0, actionOptions.findIndex((item) => item.value === rule.action)), enabled: Boolean(rule.enabled)
  })
  formVisible.value = true
}

// 关闭规则表单并清除当前编辑标识。
function closeForm() { formVisible.value = false; editingRuleId.value = null }

// 记录用户选择的仿真指标。
function selectMetric(event) { form.metricIndex = Number(event.detail.value) }

// 记录用户选择的比较操作符。
function selectOperator(event) { form.operatorIndex = Number(event.detail.value) }

// 记录用户选择的仿真报警等级。
function selectSeverity(event) { form.severityIndex = Number(event.detail.value) }

// 记录用户选择的仿真规则动作。
function selectAction(event) { form.actionIndex = Number(event.detail.value) }

// 校验完整规则字段并调用新增或更新接口。
async function saveRule() {
  const threshold = Number(form.threshold); const recoveryThreshold = Number(form.recoveryThreshold); const debounceCount = Number(form.debounceCount)
  if (!form.name.trim()) return uni.showToast({ title: '请填写规则名称', icon: 'none' })
  if (!form.deviceId.trim() || form.deviceId.trim().length > 64) return uni.showToast({ title: '设备ID需为1–64个字符', icon: 'none' })
  if (!Number.isFinite(threshold) || !Number.isFinite(recoveryThreshold)) return uni.showToast({ title: '请输入有效的触发和恢复阈值', icon: 'none' })
  if (form.operatorIndex === 0 ? recoveryThreshold >= threshold : recoveryThreshold <= threshold) return uni.showToast({ title: form.operatorIndex === 0 ? '恢复阈值必须小于触发阈值' : '恢复阈值必须大于触发阈值', icon: 'none' })
  if (!Number.isInteger(debounceCount) || debounceCount < 1 || debounceCount > 100) return uni.showToast({ title: '防抖次数需为1–100的整数', icon: 'none' })
  const payload = {
    name: form.name.trim(), deviceId: form.deviceId.trim(), metric: selectedMetric.value.value,
    operator: operatorOptions[form.operatorIndex].value, threshold, recoveryThreshold, debounceCount,
    severity: severityOptions[form.severityIndex].value, action: actionOptions[form.actionIndex].value, enabled: form.enabled
  }
  saving.value = true
  try {
    if (editingRuleId.value !== null) await updateSimulationRule(editingRuleId.value, payload)
    else await createSimulationRule(payload)
    closeForm(); uni.showToast({ title: '规则已保存', icon: 'success' }); await poll()
  } catch (error) {
    if (!error?.stale) uni.showToast({ title: error.message || '规则保存失败', icon: 'none' })
  } finally { saving.value = false }
}

// 二次确认后删除当前账号拥有的仿真规则。
function confirmDelete(rule) {
  uni.showModal({ title: '删除仿真规则', content: '确定删除“' + rule.name + '”吗？', success: (result) => { if (result.confirm) removeRule(rule) } })
}

// 执行仿真规则删除请求并刷新当前数据。
async function removeRule(rule) {
  if (busyRuleIds.value.has(rule.id)) return
  busyRuleIds.value.add(rule.id)
  try { await deleteSimulationRule(rule.id); uni.showToast({ title: '规则已删除', icon: 'success' }); await poll() }
  catch (error) { if (!error?.stale) uni.showToast({ title: error.message || '规则删除失败', icon: 'none' }) }
  finally { busyRuleIds.value.delete(rule.id) }
}

// 更新规则启用状态并在失败时保留服务端原值。
async function toggleRule(rule) {
  if (busyRuleIds.value.has(rule.id)) return
  const previous = rule.enabled; const enabled = !previous
  busyRuleIds.value.add(rule.id)
  try {
    const payload = { name: rule.name, deviceId: rule.deviceId, metric: rule.metric, operator: rule.operator, threshold: rule.threshold, recoveryThreshold: rule.recoveryThreshold, debounceCount: rule.debounceCount, severity: rule.severity, action: rule.action, enabled }
    const result = await updateSimulationRule(rule.id, payload)
    const index = rules.value.findIndex((item) => item.id === rule.id)
    if (index >= 0) rules.value.splice(index, 1, result)
  } catch (error) { if (!error?.stale) uni.showToast({ title: error.message || '规则状态更新失败', icon: 'none' }) }
  finally { busyRuleIds.value.delete(rule.id) }
}

// 二次确认并确认仍处于ACTIVE状态的仿真报警。
function acknowledgeAlarm(alarm) {
  uni.showModal({ title: '确认仿真报警', content: '确认将该报警标记为已确认？', success: (result) => { if (result.confirm) performAcknowledge(alarm) } })
}

// 向仿真报警接口提交确认并刷新结果。
async function performAcknowledge(alarm) {
  if (alarm.status !== 'ACTIVE') return
  acknowledgingId.value = alarm.id
  try { await acknowledgeAlarmRequest(alarm.id); uni.showToast({ title: '报警已确认', icon: 'success' }); await poll() }
  catch (error) { if (!error?.stale) uni.showToast({ title: error.message || '报警确认失败', icon: 'none' }) }
  finally { acknowledgingId.value = null }
}

// 将仿真指标代码转换成包含工程单位的名称。
function metricLabel(value) { return metricOptions.find((item) => item.value === value)?.label || value || '--' }

// 将仿真规则动作转换为页面文字。
function actionLabel(value) { return actionOptions.find((item) => item.value === value)?.label || value || '--' }

// 将规则比较操作符转换为页面文字。
function operatorLabel(value) { return value === 'gt' ? '大于' : value === 'lt' ? '小于' : value || '--' }

// 将仿真报警状态转换为页面文字。
function alarmStatusLabel(value) { return ({ ACTIVE: '活跃', ACKNOWLEDGED: '已确认', RESOLVED: '已恢复' })[value] || value || '--' }

// 将仿真命令状态转换为页面文字。
function commandStatusLabel(value) { return ({ PENDING: '待 MATLAB 处理', SUCCESS: '仿真执行成功', FAILED: '仿真执行失败' })[value] || value || '--' }

// 为报警等级提供颜色样式。
function severityClass(value) { return ({ INFO: 'severity-info', WARN: 'severity-warn', CRITICAL: 'severity-critical' })[value] || 'severity-info' }

// 展示仿真设备状态的可读文字。
function booleanLabel(value) { return value === true ? '开启' : value === false ? '关闭' : '未知' }

// 为仿真设备开关状态提供颜色样式。
function stateClass(value) { return value === true ? 'state-on' : value === false ? 'state-off' : 'state-unknown' }

// 格式化数值和缺测值，保留原始精度语义。
function formatValue(value) { return typeof value === 'number' && Number.isFinite(value) ? String(Number(value.toFixed(3))) : value === null || value === undefined || value === '' ? '--' : String(value) }

// 格式化后端时间并处理无效日期。
function formatDate(value) { if (!value) return '--'; const date = new Date(value); return Number.isNaN(date.getTime()) ? '--' : date.toLocaleString('zh-CN', { hour12: false }) }

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.simulation-page { padding-bottom: 48rpx; }
.warning-card { margin: 18rpx 0; padding: 20rpx 24rpx; border-radius: 14rpx; background: #fff7e8; color: #875b16; font-size: 24rpx; line-height: 1.6; }
.device-card { margin-bottom: 16rpx; }
.field-label { display: block; margin: 12rpx 0; color: $color-text-secondary; font-size: 23rpx; }
.device-controls { display: flex; align-items: center; gap: 12rpx; }
.device-input { flex: 1; min-width: 0; }
.refresh-button { flex: none; width: 170rpx; margin: 0; font-size: 23rpx; }
.poll-status { display: flex; align-items: center; gap: 10rpx; margin-top: 16rpx; font-size: 22rpx; color: $color-text-secondary; }
.status-dot { width: 13rpx; height: 13rpx; border-radius: 50%; background: #94a3b8; }
.status-polling { background: #d99a24; }
.status-idle { background: #1f8a5b; }
.status-error { background: #c84747; }
.error-text { display: block; margin-top: 12rpx; color: $color-red; font-size: 23rpx; line-height: 1.5; }
.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20rpx 12rpx; }
.summary-item { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }
.summary-value { overflow-wrap: anywhere; color: $color-text; font-size: 24rpx; font-weight: 600; }
.metrics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; margin: 14rpx 0; }
.telemetry-card { display: flex; flex-direction: column; gap: 12rpx; margin: 0; padding: 20rpx; }
.metric-label { color: $color-text-secondary; font-size: 22rpx; }
.telemetry-value { color: $color-text; font-size: 32rpx; font-weight: 600; overflow-wrap: anywhere; }
.unit { font-size: 21rpx; font-weight: 400; color: $color-text-secondary; }
.status-card { margin-top: 16rpx; }
.state-row { display: flex; justify-content: space-between; padding: 18rpx 0; border-bottom: 1rpx solid $color-border; color: $color-text-secondary; font-size: 24rpx; }
.state-row:last-child { border-bottom: 0; }
.state-value { font-weight: 600; }
.state-on { color: $color-primary; }
.state-off { color: $color-text-secondary; }
.state-unknown { color: $color-amber; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; padding: 24rpx 2rpx 12rpx; }
.small-button { flex: none; min-width: 126rpx; height: 58rpx; line-height: 58rpx; margin: 0; padding: 0 16rpx; font-size: 22rpx; }
.trend-card { padding: 20rpx; }
.trend-block { padding: 12rpx 0 20rpx; border-bottom: 1rpx solid $color-border; }
.trend-block:last-child { border-bottom: 0; }
.rule-list { display: flex; flex-direction: column; gap: 14rpx; }
.rule-card, .alarm-card, .command-card { margin: 0; padding: 20rpx; }
.rule-heading { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; }
.rule-name { flex: 1; color: $color-text; font-size: 25rpx; font-weight: 600; }
.severity-tag, .alarm-status, .command-state { flex: none; padding: 6rpx 12rpx; border-radius: 18rpx; font-size: 20rpx; }
.severity-info { color: #386287; background: #eaf3fa; }
.severity-warn, .alarm-acknowledged { color: #8a5a12; background: #fff3d9; }
.severity-critical, .alarm-active { color: #a43434; background: #fff0f0; }
.alarm-resolved { color: #17633e; background: #e8f5ed; }
.rule-line { display: block; margin-top: 10rpx; line-height: 1.5; }
.rule-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8rpx; margin-top: 18rpx; flex-wrap: wrap; }
.rule-enabled { margin-right: auto; font-size: 22rpx; }
.enabled { color: $color-primary; }
.disabled { color: $color-text-light; }
.tiny-button { min-width: 88rpx; height: 52rpx; line-height: 52rpx; margin: 0; padding: 0 12rpx; font-size: 20rpx; }
.acknowledge-button { margin: 16rpx 0 0 auto; }
.mask { position: fixed; z-index: 100; inset: 0; display: flex; align-items: flex-end; background: rgba(15, 23, 42, .42); }
.form-sheet { box-sizing: border-box; width: 100%; max-height: 88vh; overflow-y: auto; padding: 30rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; }
.sheet-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.sheet-title { color: $color-text; font-size: 30rpx; font-weight: 600; }
.sheet-close { color: $color-primary; font-size: 24rpx; }
.picker-field { padding: 20rpx; border: 1rpx solid $color-border; border-radius: 12rpx; color: $color-text; font-size: 24rpx; }
.form-hint { display: block; margin-top: 8rpx; line-height: 1.5; }
.enabled-row { display: flex; justify-content: space-between; align-items: center; margin-top: 24rpx; }
.enabled-label { margin: 0; }
.enabled-control { color: $color-primary; font-size: 24rpx; }
.action-button { width: 100%; margin-top: 20rpx; }
</style>
