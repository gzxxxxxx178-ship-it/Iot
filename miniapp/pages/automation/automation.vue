<template>
  <view class="page">
    <PageHeader title="自动化规则" :subtitle="session.token ? '服务端持续评估传感器条件' : '登录后管理自动化规则'" />
    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看自动化规则" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>
    <template v-else>
      <view class="notice-card"><text class="notice-text">启动或停止动作会在条件满足时向设备发送硬件命令。规则按连续命中次数和冷却时间执行。</text></view>
      <view class="section-heading">
        <text class="section-title">规则列表</text>
        <view class="heading-actions">
          <button class="btn-secondary small-button" :disabled="loading" @tap="loadData">刷新</button>
          <button class="btn-primary small-button" @tap="openCreate">新增</button>
        </view>
      </view>
      <view v-if="error" class="card error-card">
        <text class="error-text">{{ error }}</text>
        <button class="btn-secondary small-button retry-button" @tap="loadData">重试</button>
      </view>
      <view v-if="loading && !loaded" class="card loading-card"><text class="hint">正在加载自动化数据…</text></view>
      <view v-if="loaded && !error" class="card">
        <view v-if="rules.length" class="rule-list">
          <view v-for="rule in rules" :key="rule.id" class="rule-item">
            <view class="rule-top">
              <view class="rule-description">
                <text class="rule-name">{{ rule.name }}</text>
                <text class="hint">{{ deviceName(rule.deviceId) }} · {{ metricLabel(rule.metric) }} {{ operatorLabel(rule.operator) }} {{ rule.threshold }}{{ metricUnit(rule.metric) }}</text>
              </view>
              <text class="rule-state" :class="rule.enabled ? 'is-enabled' : 'is-disabled'">{{ rule.enabled ? '已启用' : '已停用' }}</text>
            </view>
            <view class="rule-bottom">
              <text class="hint">{{ actionLabel(rule.action) }} · 连续 {{ rule.debounceCount }} 次 · 冷却 {{ rule.cooldownSeconds }} 秒</text>
              <view class="rule-actions">
                <button class="btn-secondary small-button" :disabled="busyIds.has(rule.id)" @tap="toggleRule(rule)">{{ rule.enabled ? '停用' : '启用' }}</button>
                <button class="btn-secondary small-button" :disabled="busyIds.has(rule.id)" @tap="openEdit(rule)">编辑</button>
                <button class="btn-danger small-button" :disabled="busyIds.has(rule.id)" @tap="confirmDelete(rule)">删除</button>
              </view>
            </view>
          </view>
        </view>
        <EmptyState v-else text="暂无自动化规则" />
      </view>

      <view class="section-heading execution-heading">
        <text class="section-title">最近执行记录</text>
        <text class="hint">最多显示 100 条</text>
      </view>
      <view v-if="loaded && !error" class="card">
        <view v-if="executions.length" class="execution-list">
          <view v-for="item in executions" :key="item.id" class="execution-item">
            <view class="execution-top">
              <text class="execution-title">{{ deviceName(item.deviceId) }} · {{ actionLabel(item.action) }}</text>
              <text class="execution-status" :class="item.status === 'SUCCESS' || item.status === 'ACKNOWLEDGED' ? 'is-enabled' : 'is-failed'">{{ statusLabel(item.status) }}</text>
            </view>
            <text class="hint">{{ formatDateTime(item.createdAt) }} · 触发值 {{ item.actualValue ?? '--' }}</text>
            <text class="execution-message">{{ item.message }}</text>
          </view>
        </view>
        <EmptyState v-else text="暂无执行记录" />
      </view>
    </template>

    <view v-if="formVisible" class="mask" @tap="closeForm">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-heading">
          <text class="sheet-title">{{ editingId ? '编辑自动化规则' : '新增自动化规则' }}</text>
          <text class="sheet-close" @tap="closeForm">关闭</text>
        </view>
        <text class="field-label">规则名称</text>
        <input class="text-field" v-model="form.name" maxlength="100" placeholder="例如：高温自动通风" />
        <text class="field-label">目标设备（仅限本人拥有）</text>
        <picker :range="ownedDevices" range-key="label" :value="formDeviceIndex" @change="selectDevice">
          <view class="picker-field">{{ ownedDevices[formDeviceIndex]?.label || '暂无本人拥有的设备' }}　›</view>
        </picker>
        <text class="field-label">监控指标</text>
        <picker :range="metricOptions" range-key="label" :value="form.metricIndex" @change="selectMetric">
          <view class="picker-field">{{ metricOptions[form.metricIndex].label }}　›</view>
        </picker>
        <text class="field-label">比较条件</text>
        <picker :range="operatorOptions" range-key="label" :value="form.operatorIndex" @change="selectOperator">
          <view class="picker-field">{{ operatorOptions[form.operatorIndex].label }}　›</view>
        </picker>
        <text class="field-label">触发阈值（{{ metricUnit(selectedMetric) || '数值' }}）</text>
        <input class="text-field" type="digit" v-model="form.threshold" placeholder="输入触发阈值" />
        <text class="field-label">执行动作</text>
        <picker :range="actionOptions" range-key="label" :value="form.actionIndex" @change="selectAction">
          <view class="picker-field">{{ actionOptions[form.actionIndex].label }}　›</view>
        </picker>
        <text v-if="form.actionIndex < 2" class="control-warning">启用后会在条件连续命中时自动向设备下发{{ form.actionIndex === 0 ? '启动' : '停止' }}命令。</text>
        <text class="field-label">连续命中次数（1–20）</text>
        <input class="text-field" type="number" v-model="form.debounceCount" />
        <text class="field-label">冷却时间（秒，0–86400）</text>
        <input class="text-field" type="number" v-model="form.cooldownSeconds" />
        <view class="enabled-row" @tap="form.enabled = !form.enabled">
          <text class="field-label enabled-label">保存后启用规则</text>
          <text class="enabled-control">{{ form.enabled ? '是' : '否' }}</text>
        </view>
        <button class="btn-primary action-button" :disabled="saving || !ownedDevices.length" @tap="saveRule">{{ saving ? '正在保存…' : '保存规则' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { request } from '../../api/request.js'
import { session } from '../../utils/session.js'
import { live, refreshLive } from '../../utils/live-store.js'
import { formatDateTime } from '../../utils/format.js'

const metricOptions = [{ value: 'temperature', label: '温度（℃）' }, { value: 'humidity', label: '湿度（%）' }, { value: 'water', label: '水位（ADC）' }]
const operatorOptions = [{ value: 'gt', label: '大于（>）' }, { value: 'lt', label: '小于（<）' }, { value: 'eq', label: '接近（容差±0.05）' }]
const actionOptions = [{ value: 'start', label: '启动设备' }, { value: 'stop', label: '停止设备' }, { value: 'notify', label: '记录通知' }]
const rules = ref([])
const executions = ref([])
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const busyIds = ref(new Set())
const form = reactive(createEmptyForm())
const ownedDevices = computed(() => live.devices.filter((device) => device.ownedByCurrentUser).map((device) => ({ id: String(device.id), label: device.name + '（' + device.id + '）' })))
const formDeviceIndex = computed(() => Math.max(0, ownedDevices.value.findIndex((device) => device.id === form.deviceId)))
const selectedMetric = computed(() => metricOptions[form.metricIndex].value)

// 页面显示时刷新设备列表、规则与执行记录。
onShow(() => { refreshLive(); loadData() })

// 账号切换时丢弃旧账号的自动化数据并加载新账号内容。
watch(() => session.generation, () => {
  rules.value = []
  executions.value = []
  loaded.value = false
  error.value = ''
  formVisible.value = false
  if (session.token) loadData()
})

// 创建自动化规则表单的初始值。
function createEmptyForm() {
  return { name: '', deviceId: '', metricIndex: 0, operatorIndex: 0, threshold: '', actionIndex: 0, enabled: true, debounceCount: '2', cooldownSeconds: '300' }
}

// 同时加载当前账号的规则和最近执行记录。
async function loadData() {
  if (!session.token) return
  loading.value = true
  error.value = ''
  try {
    const [ruleData, executionData] = await Promise.all([
      request('/api/automation/rules'),
      request('/api/automation/executions')
    ])
    rules.value = Array.isArray(ruleData) ? ruleData : []
    executions.value = Array.isArray(executionData) ? executionData : []
    loaded.value = true
  } catch (err) {
    if (!err?.stale) error.value = err.message || '自动化数据加载失败'
  } finally {
    loading.value = false
  }
}

// 打开空白规则表单。
function openCreate() {
  editingId.value = null
  Object.assign(form, createEmptyForm(), { deviceId: ownedDevices.value[0]?.id || '' })
  formVisible.value = true
}

// 将已有规则载入编辑表单。
function openEdit(rule) {
  const deviceIndex = ownedDevices.value.findIndex((device) => device.id === String(rule.deviceId))
  if (deviceIndex < 0) return uni.showToast({ title: '只能编辑目标为本人设备的规则', icon: 'none' })
  editingId.value = rule.id
  Object.assign(form, {
    name: rule.name,
    deviceId: String(rule.deviceId),
    metricIndex: Math.max(0, metricOptions.findIndex((item) => item.value === rule.metric)),
    operatorIndex: Math.max(0, operatorOptions.findIndex((item) => item.value === rule.operator)),
    threshold: String(rule.threshold),
    actionIndex: Math.max(0, actionOptions.findIndex((item) => item.value === rule.action)),
    enabled: Boolean(rule.enabled),
    debounceCount: String(rule.debounceCount ?? 2),
    cooldownSeconds: String(rule.cooldownSeconds ?? 300)
  })
  formVisible.value = true
}

// 关闭并重置当前编辑表单。
function closeForm() { formVisible.value = false; editingId.value = null }

// 记录用户选择的目标设备。
function selectDevice(event) { form.deviceId = ownedDevices.value[Number(event.detail.value)]?.id || '' }

// 记录用户选择的监控指标。
function selectMetric(event) { form.metricIndex = Number(event.detail.value) }

// 记录用户选择的比较条件。
function selectOperator(event) { form.operatorIndex = Number(event.detail.value) }

// 记录用户选择的执行动作。
function selectAction(event) { form.actionIndex = Number(event.detail.value) }

// 校验规则约束并在创建或更新前确认硬件自动控制行为。
async function saveRule() {
  const threshold = Number(form.threshold)
  const debounceCount = Number(form.debounceCount)
  const cooldownSeconds = Number(form.cooldownSeconds)
  if (!form.name.trim()) return uni.showToast({ title: '请填写规则名称', icon: 'none' })
  if (form.threshold === '') return uni.showToast({ title: '请输入触发阈值', icon: 'none' })
  if (!form.deviceId || !ownedDevices.value.some((device) => device.id === form.deviceId)) return uni.showToast({ title: '请选择本人拥有的设备', icon: 'none' })
  if (!Number.isFinite(threshold)) return uni.showToast({ title: '请输入有效阈值', icon: 'none' })
  if (!Number.isInteger(debounceCount) || debounceCount < 1 || debounceCount > 20) return uni.showToast({ title: '连续命中次数需为1–20的整数', icon: 'none' })
  if (!Number.isInteger(cooldownSeconds) || cooldownSeconds < 0 || cooldownSeconds > 86400) return uni.showToast({ title: '冷却时间需为0–86400秒整数', icon: 'none' })
  if (selectedMetric.value === 'temperature' && (threshold < -50 || threshold > 100)) return uni.showToast({ title: '温度阈值需在-50到100℃之间', icon: 'none' })
  if (selectedMetric.value === 'humidity' && (threshold < 0 || threshold > 100)) return uni.showToast({ title: '湿度阈值需在0到100%之间', icon: 'none' })
  if (selectedMetric.value === 'water' && threshold < 0) return uni.showToast({ title: '水位 ADC 阈值不能为负数', icon: 'none' })
  if (form.enabled && form.actionIndex < 2) {
    const confirmed = await confirmHardwareAutomation()
    if (!confirmed) return
  }
  saving.value = true
  const payload = {
    name: form.name.trim(), deviceId: form.deviceId,
    metric: selectedMetric.value, operator: operatorOptions[form.operatorIndex].value,
    threshold, action: actionOptions[form.actionIndex].value, enabled: form.enabled,
    debounceCount, cooldownSeconds
  }
  try {
    const path = editingId.value ? '/api/automation/rules/' + editingId.value : '/api/automation/rules'
    const result = await request(path, { method: editingId.value ? 'PUT' : 'POST', data: payload })
    const index = rules.value.findIndex((rule) => rule.id === result.id)
    if (index >= 0) rules.value.splice(index, 1, result)
    else rules.value.unshift(result)
    closeForm()
    uni.showToast({ title: '规则已保存', icon: 'success' })
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

// 对会自动控制硬件的规则展示二次确认。
function confirmHardwareAutomation() {
  return new Promise((resolve) => {
    uni.showModal({ title: '确认自动控制设备', content: '规则启用后，后端会在条件连续命中时自动向所选设备发送启动或停止命令。确定继续吗？', confirmText: '启用规则', success: (result) => resolve(result.confirm), fail: () => resolve(false) })
  })
}

// 启用硬件控制规则前确认其自动发送命令的行为。
async function toggleRule(rule) {
  if (busyIds.value.has(rule.id)) return
  const enabled = !rule.enabled
  if (enabled && ['start', 'stop'].includes(rule.action)) {
    const confirmed = await confirmHardwareAutomation()
    if (!confirmed) return
  }
  busyIds.value.add(rule.id)
  try {
    const result = await request('/api/automation/rules/' + rule.id, {
      method: 'PUT',
      data: { name: rule.name, deviceId: rule.deviceId, metric: rule.metric, operator: rule.operator, threshold: rule.threshold, action: rule.action, enabled, debounceCount: rule.debounceCount, cooldownSeconds: rule.cooldownSeconds }
    })
    const index = rules.value.findIndex((item) => item.id === rule.id)
    if (index >= 0) rules.value.splice(index, 1, result)
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则状态更新失败', icon: 'none' })
  } finally {
    busyIds.value.delete(rule.id)
  }
}

// 删除规则前取得用户确认。
function confirmDelete(rule) {
  uni.showModal({ title: '删除自动化规则', content: '确定删除“' + rule.name + '”吗？', success: (result) => { if (result.confirm) deleteRule(rule) } })
}

// 调用后端删除规则，并同步移除页面记录。
async function deleteRule(rule) {
  if (busyIds.value.has(rule.id)) return
  busyIds.value.add(rule.id)
  try {
    await request('/api/automation/rules/' + rule.id, { method: 'DELETE' })
    rules.value = rules.value.filter((item) => item.id !== rule.id)
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则删除失败', icon: 'none' })
  } finally {
    busyIds.value.delete(rule.id)
  }
}

// 将指标代码转换为页面中文名称。
function metricLabel(metric) { return metricOptions.find((item) => item.value === metric)?.label || metric }

// 将运算符转换为页面中文文字。
function operatorLabel(operator) { return { gt: '大于', lt: '小于', eq: '接近（容差±0.05）' }[operator] || operator }

// 将动作代码转换为页面中文名称。
function actionLabel(action) { return actionOptions.find((item) => item.value === action)?.label || action }

// 根据指标返回单位并明确水位使用 ADC 原始值。
function metricUnit(metric) { return metric === 'temperature' ? '℃' : metric === 'humidity' ? '%' : metric === 'water' ? ' ADC' : '' }

// 根据设备标识读取当前设备名称。
function deviceName(deviceId) { return live.devices.find((device) => String(device.id) === String(deviceId))?.name || String(deviceId) }

// 将后端执行状态转换为可理解的状态文字。
function statusLabel(status) { return status === 'SUCCESS' ? '成功' : status === 'ACKNOWLEDGED' ? '设备已确认' : status === 'DISPATCHED' ? '等待设备确认' : status === 'TIMED_OUT' ? '设备超时' : status === 'REJECTED' ? '设备拒绝' : '失败' }

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.notice-card { margin-bottom: 18rpx; padding: 20rpx 24rpx; border-radius: $radius-card; background: $color-amber-soft; }
.notice-text { color: $color-amber; font-size: 23rpx; line-height: 1.5; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 12rpx 0 14rpx; }
.heading-actions { display: flex; gap: 12rpx; }
.execution-heading { margin-top: 18rpx; }
.small-button { min-width: 104rpx; height: 60rpx; line-height: 60rpx; margin: 0; padding: 0 16rpx; font-size: 23rpx; }
.rule-list, .execution-list { display: flex; flex-direction: column; }
.rule-item, .execution-item { padding: 22rpx 0; border-bottom: 1rpx solid $color-border; }
.rule-item:last-child, .execution-item:last-child { border-bottom: 0; }
.rule-top, .execution-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.rule-description { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 8rpx; }
.rule-name, .execution-title { color: $color-text; font-size: 28rpx; font-weight: 600; }
.rule-state, .execution-status { flex: none; font-size: 22rpx; }
.is-enabled { color: $color-primary; }
.is-disabled { color: $color-text-light; }
.is-failed { color: $color-red; }
.rule-bottom { display: flex; flex-direction: column; gap: 14rpx; margin-top: 14rpx; }
.rule-actions { display: flex; justify-content: flex-end; gap: 10rpx; }
.execution-item > .hint { display: block; margin-top: 10rpx; }
.execution-message { display: block; margin-top: 8rpx; color: $color-text-secondary; font-size: 24rpx; line-height: 1.5; word-break: break-all; }
.action-button { width: 100%; margin: 22rpx 0 0; }
.loading-card { padding: 32rpx; }
.error-card { display: flex; flex-direction: column; align-items: flex-start; gap: 16rpx; }
.error-text { color: $color-red; font-size: 25rpx; }
.retry-button { align-self: flex-end; }
.mask { position: fixed; inset: 0; z-index: 20; display: flex; align-items: flex-end; background: rgba(17, 24, 39, .42); }
.form-sheet { box-sizing: border-box; width: 100%; max-height: 88vh; overflow-y: auto; padding: 30rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: $color-card; }
.sheet-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.sheet-title { color: $color-text; font-size: 32rpx; font-weight: 700; }
.sheet-close { padding: 10rpx; color: $color-text-secondary; font-size: 24rpx; }
.field-label { display: block; margin: 16rpx 0 9rpx; color: $color-text-secondary; font-size: 23rpx; }
.picker-field, .text-field { box-sizing: border-box; width: 100%; min-height: 72rpx; padding: 16rpx 18rpx; border: 1rpx solid $color-border; border-radius: $radius-card; color: $color-text; background: #fff; font-size: 25rpx; }
.text-field { height: 72rpx; }
.control-warning { display: block; margin-top: 12rpx; padding: 14rpx 16rpx; border-radius: 10rpx; color: $color-red; background: $color-red-soft; font-size: 22rpx; line-height: 1.5; }
.enabled-row { display: flex; align-items: center; justify-content: space-between; margin-top: 16rpx; padding: 16rpx 0; border-bottom: 1rpx solid $color-border; }
.enabled-label { margin: 0; }
.enabled-control { color: $color-primary; font-size: 25rpx; }
</style>
