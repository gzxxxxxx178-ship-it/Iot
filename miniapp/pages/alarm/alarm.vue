<template>
  <view class="page">
    <PageHeader title="报警管理" :subtitle="session.token ? '规则与最近触发记录' : '登录后查看账号报警数据'" />

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看报警数据" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>

    <template v-else>
      <view class="section-heading">
        <text class="section-title">报警规则</text>
        <button class="btn-secondary small-button" :disabled="loading" @tap="loadData">刷新</button>
      </view>
      <view v-if="error" class="card error-card">
        <text class="error-text">{{ error }}</text>
        <button class="btn-secondary small-button retry-button" @tap="loadData">重试</button>
      </view>
      <view v-if="loading && !loaded" class="card loading-card"><text class="hint">正在加载报警数据…</text></view>

      <view v-if="loaded && !error" class="card">
        <view v-if="rules.length" class="rule-list">
          <view v-for="rule in rules" :key="rule.id" class="rule-item">
            <view class="rule-main">
              <view class="rule-title-row">
                <text class="rule-title">{{ metricLabel(rule.metric) }} {{ operatorLabel(rule.operator) }} {{ rule.threshold }}{{ metricUnit(rule.metric) }}</text>
                <text class="rule-state" :class="rule.enabled ? 'is-enabled' : 'is-disabled'">{{ rule.enabled ? '已启用' : '已停用' }}</text>
              </view>
              <text class="hint">{{ rule.deviceId === '*' ? '全部设备' : '设备 ' + rule.deviceId }} · 冷却 {{ rule.cooldownSeconds }} 秒</text>
            </view>
            <view class="rule-actions">
              <button class="btn-secondary small-button" :disabled="busyIds.has(rule.id)" @tap="toggleRule(rule)">{{ rule.enabled ? '停用' : '启用' }}</button>
              <button class="btn-secondary small-button" :disabled="busyIds.has(rule.id) || saving" @tap="openEdit(rule)">编辑</button>
              <button class="btn-danger small-button" :disabled="busyIds.has(rule.id)" @tap="confirmDelete(rule)">删除</button>
            </view>
          </view>
        </view>
        <EmptyState v-else text="暂无报警规则" />
        <button class="btn-primary action-button" :disabled="saving" @tap="formVisible ? closeForm() : openCreate()">{{ formVisible ? '收起规则表单' : '新增报警规则' }}</button>

        <view v-if="formVisible" class="rule-form">
          <text class="form-title">{{ editingId ? '编辑报警规则' : '新增报警规则' }}</text>
          <text class="field-label">监控指标</text>
          <picker :range="metricOptions" range-key="label" :value="metricIndex" @change="selectMetric">
            <view class="picker-field">{{ metricOptions[metricIndex].label }}　›</view>
          </picker>
          <text class="field-label">触发条件</text>
          <picker :range="operatorOptions" range-key="label" :value="operatorIndex" @change="selectOperator">
            <view class="picker-field">{{ operatorOptions[operatorIndex].label }}　›</view>
          </picker>
          <text class="field-label">阈值（{{ metricUnit(selectedMetric) || '数值' }}）</text>
          <input class="text-field" type="digit" v-model="threshold" placeholder="输入报警阈值" />
          <text class="field-label">适用设备</text>
          <picker :range="deviceOptions" range-key="label" :value="deviceIndex" @change="selectDevice">
            <view class="picker-field">{{ deviceOptions[deviceIndex].label }}　›</view>
          </picker>
          <text class="field-label">冷却时间（秒，0–86400）</text>
          <input class="text-field" type="number" v-model="cooldown" placeholder="默认 300 秒" />
          <view class="field-switch" @tap="ruleEnabled = !ruleEnabled">
            <text class="field-label">规则状态</text>
            <text :class="ruleEnabled ? 'is-enabled' : 'is-disabled'">{{ ruleEnabled ? '已启用' : '已停用' }}　›</text>
          </view>
          <view class="form-actions">
            <button class="btn-secondary action-button" :disabled="saving" @tap="closeForm">取消</button>
            <button class="btn-primary action-button" :disabled="saving" @tap="saveRule">{{ saving ? '正在保存…' : '保存规则' }}</button>
          </view>
        </view>
      </view>

      <view class="section-heading records-heading">
        <text class="section-title">最近报警记录</text>
        <text class="hint">最多显示 100 条</text>
      </view>
      <view v-if="loaded && !error" class="card">
        <view v-if="records.length" class="record-list">
          <view v-for="record in records" :key="record.id" class="record-item">
            <view class="record-title-row">
              <text class="record-title">{{ metricLabel(record.metric) }} · {{ record.deviceId }}</text>
              <text class="record-time">{{ formatDateTime(record.createdAt) }}</text>
            </view>
            <text class="record-reading">{{ record.actualValue }}{{ metricUnit(record.metric) }} / 阈值 {{ operatorLabel(record.operator) }} {{ record.threshold }}{{ metricUnit(record.metric) }}</text>
            <text class="hint">{{ record.message }}</text>
          </view>
        </view>
        <EmptyState v-else text="暂无报警记录" />
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { request } from '../../api/request.js'
import { session } from '../../utils/session.js'
import { live, refreshLive } from '../../utils/live-store.js'
import { formatDateTime } from '../../utils/format.js'

const rules = ref([])
const records = ref([])
const loading = ref(false)
const loaded = ref(false)
const saving = ref(false)
const error = ref('')
const formVisible = ref(false)
const editingId = ref(null)
const busyIds = ref(new Set())
const metricOptions = [{ value: 'temperature', label: '温度' }, { value: 'humidity', label: '湿度' }, { value: 'water', label: '水位 ADC' }]
const operatorOptions = [{ value: 'gt', label: '高于（>）' }, { value: 'lt', label: '低于（<）' }, { value: 'eq', label: '接近（≈）' }]
const metricIndex = ref(0)
const operatorIndex = ref(0)
const selectedDeviceId = ref('*')
const threshold = ref('')
const cooldown = ref('300')
const ruleEnabled = ref(true)
const selectedMetric = computed(() => metricOptions[metricIndex.value].value)
const deviceOptions = computed(() => {
  const devices = [{ id: '*', label: '全部设备' }, ...live.devices.map((device) => ({ id: String(device.id), label: device.name + '（' + device.id + '）' }))]
  if (selectedDeviceId.value !== '*' && !devices.some((device) => device.id === selectedDeviceId.value)) {
    devices.push({ id: selectedDeviceId.value, label: '设备 ' + selectedDeviceId.value + '（当前规则）' })
  }
  return devices
})
const deviceIndex = computed(() => Math.max(0, deviceOptions.value.findIndex((device) => device.id === selectedDeviceId.value)))

// 页面显示时刷新设备选项和当前账号的报警数据。
onShow(() => { refreshLive(); loadData() })

// 切换账号时清除旧账号数据，并为新会话重新加载。
watch(() => session.generation, () => {
  rules.value = []
  records.value = []
  loaded.value = false
  error.value = ''
  if (session.token) loadData()
})

// 获取当前账号规则与最近报警记录，并保留可重试的错误状态。
async function loadData() {
  if (!session.token) return
  loading.value = true
  error.value = ''
  try {
    const [ruleData, recordData] = await Promise.all([
      request('/api/alarm/rules'),
      request('/api/alarm/records')
    ])
    rules.value = Array.isArray(ruleData) ? ruleData : []
    records.value = Array.isArray(recordData) ? recordData : []
    loaded.value = true
  } catch (err) {
    if (!err?.stale) error.value = err.message || '报警数据加载失败'
  } finally {
    loading.value = false
  }
}

// 将指标代码转换为页面中文名称。
function metricLabel(metric) { return metricOptions.find((item) => item.value === metric)?.label || metric }

// 将后端运算符转换为页面展示符号。
function operatorLabel(operator) { return operatorOptions.find((item) => item.value === operator)?.label || operator }

// 根据指标返回对应单位，水位保留原始 ADC 口径。
function metricUnit(metric) { return metric === 'temperature' ? '℃' : metric === 'humidity' ? '%' : metric === 'water' ? ' ADC' : '' }

// 根据规则切换启用状态，并在后端确认后更新列表。
async function toggleRule(rule) {
  if (busyIds.value.has(rule.id)) return
  busyIds.value.add(rule.id)
  try {
    const result = await request('/api/alarm/rules/' + rule.id, {
      method: 'PUT',
      data: { metric: rule.metric, operator: rule.operator, threshold: rule.threshold, enabled: !rule.enabled, deviceId: rule.deviceId, cooldownSeconds: rule.cooldownSeconds }
    })
    const index = rules.value.findIndex((item) => item.id === rule.id)
    if (index >= 0) rules.value[index] = result
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则更新失败', icon: 'none' })
  } finally {
    busyIds.value.delete(rule.id)
  }
}

// 删除前展示确认，并在后端成功后移除规则。
function confirmDelete(rule) {
  uni.showModal({ title: '删除报警规则', content: '确定删除这条规则吗？', success: (result) => { if (result.confirm) deleteRule(rule) } })
}

// 调用后端删除指定报警规则。
async function deleteRule(rule) {
  if (busyIds.value.has(rule.id)) return
  busyIds.value.add(rule.id)
  try {
    await request('/api/alarm/rules/' + rule.id, { method: 'DELETE' })
    rules.value = rules.value.filter((item) => item.id !== rule.id)
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则删除失败', icon: 'none' })
  } finally {
    busyIds.value.delete(rule.id)
  }
}

// 记录用户选择的监控指标。
function selectMetric(event) { metricIndex.value = Number(event.detail.value) }

// 记录用户选择的比较条件。
function selectOperator(event) { operatorIndex.value = Number(event.detail.value) }

// 记录用户选择的适用设备。
function selectDevice(event) { selectedDeviceId.value = deviceOptions.value[Number(event.detail.value)]?.id || '*' }

// 重置表单并进入新增模式。
function openCreate() {
  editingId.value = null
  metricIndex.value = 0
  operatorIndex.value = 0
  selectedDeviceId.value = '*'
  threshold.value = ''
  cooldown.value = '300'
  ruleEnabled.value = true
  formVisible.value = true
}

// 将已有规则完整回填到表单，进入编辑模式。
function openEdit(rule) {
  editingId.value = rule.id
  metricIndex.value = Math.max(0, metricOptions.findIndex((item) => item.value === rule.metric))
  operatorIndex.value = Math.max(0, operatorOptions.findIndex((item) => item.value === rule.operator))
  selectedDeviceId.value = String(rule.deviceId || '*')
  threshold.value = String(rule.threshold)
  cooldown.value = String(rule.cooldownSeconds ?? 300)
  ruleEnabled.value = Boolean(rule.enabled)
  formVisible.value = true
}

// 关闭表单并清除编辑目标，避免下次新增误更新旧规则。
function closeForm() {
  formVisible.value = false
  editingId.value = null
}

// 校验表单并通过真实接口创建或更新报警规则。
async function saveRule() {
  if (threshold.value.trim() === '') return uni.showToast({ title: '请输入有效阈值', icon: 'none' })
  const value = Number(threshold.value)
  const cooldownValue = cooldown.value === '' ? 300 : Number(cooldown.value)
  if (!Number.isFinite(value)) return uni.showToast({ title: '请输入有效阈值', icon: 'none' })
  if (!Number.isInteger(cooldownValue) || cooldownValue < 0 || cooldownValue > 86400) return uni.showToast({ title: '冷却时间需为0–86400秒整数', icon: 'none' })
  if (selectedMetric.value === 'temperature' && (value < -50 || value > 100)) return uni.showToast({ title: '温度阈值需在-50到100℃之间', icon: 'none' })
  if (selectedMetric.value === 'humidity' && (value < 0 || value > 100)) return uni.showToast({ title: '湿度阈值需在0到100%之间', icon: 'none' })
  if (selectedMetric.value === 'water' && value < 0) return uni.showToast({ title: '水位 ADC 阈值不能为负数', icon: 'none' })
  saving.value = true
  try {
    const isEditing = editingId.value !== null
    const path = isEditing ? '/api/alarm/rules/' + editingId.value : '/api/alarm/rules'
    const saved = await request(path, {
      method: isEditing ? 'PUT' : 'POST',
      data: { metric: selectedMetric.value, operator: operatorOptions[operatorIndex.value].value, threshold: value, enabled: ruleEnabled.value, deviceId: selectedDeviceId.value, cooldownSeconds: cooldownValue }
    })
    if (isEditing) {
      const index = rules.value.findIndex((rule) => rule.id === editingId.value)
      if (index >= 0) rules.value[index] = saved
    } else {
      rules.value.unshift(saved)
    }
    closeForm()
    uni.showToast({ title: isEditing ? '规则已更新' : '规则已创建', icon: 'success' })
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '规则保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0 14rpx; }
.records-heading { margin-top: 18rpx; }
.rule-list, .record-list { display: flex; flex-direction: column; }
.rule-item, .record-item { padding: 22rpx 0; border-bottom: 1rpx solid $color-border; }
.rule-item:last-child, .record-item:last-child { border-bottom: 0; }
.rule-main { display: flex; flex-direction: column; gap: 10rpx; }
.rule-title-row, .record-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.rule-title, .record-title { color: $color-text; font-size: 28rpx; font-weight: 600; }
.rule-state { flex: none; font-size: 22rpx; }
.is-enabled { color: $color-primary; }
.is-disabled { color: $color-text-light; }
.rule-actions { display: flex; justify-content: flex-end; gap: 14rpx; margin-top: 16rpx; }
.small-button { min-width: 116rpx; height: 60rpx; line-height: 60rpx; margin: 0; padding: 0 18rpx; font-size: 23rpx; }
.action-button { width: 100%; margin: 22rpx 0 0; }
.rule-form { margin-top: 24rpx; padding-top: 22rpx; border-top: 1rpx solid $color-border; }
.form-title { color: $color-text; font-size: 28rpx; font-weight: 600; }
.field-label { display: block; margin: 18rpx 0 10rpx; color: $color-text-secondary; font-size: 24rpx; }
.field-switch { display: flex; align-items: center; justify-content: space-between; margin-top: 20rpx; }
.field-switch .field-label { margin: 0; }
.form-actions { display: flex; gap: 16rpx; }
.form-actions .action-button { flex: 1; width: auto; }
.picker-field, .text-field { box-sizing: border-box; width: 100%; min-height: 76rpx; padding: 18rpx 20rpx; border: 1rpx solid $color-border; border-radius: $radius-card; color: $color-text; font-size: 26rpx; background: #fff; }
.text-field { height: 76rpx; }
.record-reading { display: block; margin: 12rpx 0 8rpx; color: $color-text; font-size: 25rpx; }
.record-time { flex: none; color: $color-text-light; font-size: 21rpx; }
.error-card { display: flex; flex-direction: column; align-items: flex-start; gap: 18rpx; }
.error-text { color: $color-red; font-size: 25rpx; }
.retry-button { align-self: flex-end; }
.loading-card { padding: 32rpx; }
</style>
