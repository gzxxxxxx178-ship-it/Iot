<script setup>
import { onMounted, onUnmounted, ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh, WarningFilled } from '@element-plus/icons-vue'
import SimulationTrendChart from '../components/simulation/SimulationTrendChart.vue'
import {
  getLatestTelemetry,
  getTelemetryHistory,
  getSimulationRules,
  createSimulationRule,
  updateSimulationRule,
  deleteSimulationRule,
  getSimulationAlarms,
  acknowledgeSimulationAlarm,
  getPendingCommands,
} from '../api/simulation'
import { formatDateTime } from '../utils/format'

// ---- 设备ID与轮询控制 ----
const deviceId = ref('SIM-PADDY-001')
const pollingTimer = ref(null)
let pollingInFlight = false
const pollingStatus = ref('idle') // idle | polling | error
const pollingError = ref('')

// ---- 遥测数据 ----
const latestTelemetry = ref(null)
const telemetryHistory = ref([])
const trendTimeLabels = ref([])
const trendSeries = ref([])

// ---- 规则 ----
const rules = ref([])
const ruleDialogVisible = ref(false)
const editingRuleId = ref(null)
const ruleForm = ref(createEmptyRuleForm())
const ruleSaving = ref(false)

// ---- 报警 ----
const alarms = ref([])
const alarmAcknowledging = ref(null)

// ---- 待执行命令 ----
const pendingCommands = ref([])

// ---- 指标与选项配置 ----
const metricOptions = [
  { label: '水位 (mm)', value: 'waterLevelMm' },
  { label: '流量 (L/min)', value: 'flowRateLMin' },
  { label: 'EC (mS/cm)', value: 'ecMsCm' },
  { label: '土壤含水率 (%)', value: 'soilMoisturePct' },
  { label: '降雨量 (mm)', value: 'rainfallMm' },
]

const severityOptions = [
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'CRITICAL', value: 'CRITICAL' },
]

const actionOptions = [
  { label: '通知', value: 'NOTIFY' },
  { label: '停止灌溉', value: 'STOP_IRRIGATION' },
  { label: '停止施肥', value: 'STOP_FERTILIZER' },
  { label: '停止全部', value: 'STOP_ALL' },
]

// ---- 遥测卡片定义 ----
const telemetryCards = [
  { key: 'waterLevelMm', label: '水位', unit: 'mm', icon: '💧' },
  { key: 'flowRateLMin', label: '流量', unit: 'L/min', icon: '🌊' },
  { key: 'ecMsCm', label: 'EC', unit: 'mS/cm', icon: '⚡' },
  { key: 'soilMoisturePct', label: '土壤含水率', unit: '%', icon: '🌱' },
  { key: 'rainfallMm', label: '降雨量', unit: 'mm', icon: '🌧️' },
]

// ---- 趋势图系列颜色 ----
const trendColors = ['#10b981', '#3b82f6', '#f59e0b', '#8b5cf6', '#06b6d4']

// ---- 规则表单辅助 ----
// 创建规则表单默认值
function createEmptyRuleForm() {
  return {
    name: '',
    deviceId: deviceId.value,
    metric: 'waterLevelMm',
    operator: 'gt',
    threshold: 100,
    recoveryThreshold: 80,
    debounceCount: 2,
    severity: 'WARN',
    action: 'NOTIFY',
    enabled: true,
  }
}

// 将操作符代码转换为页面文字
function operatorLabel(op) {
  return op === 'gt' ? '大于' : '小于'
}

// 将指标代码转换为页面文字
function metricLabel(metric) {
  return metricOptions.find((m) => m.value === metric)?.label || metric
}

// 将等级代码转换为页面标签类型
function severityType(severity) {
  return { INFO: 'info', WARN: 'warning', CRITICAL: 'danger' }[severity] || 'info'
}

// 将动作代码转换为页面文字
function actionLabel(action) {
  return actionOptions.find((a) => a.value === action)?.label || action
}

// 将报警状态转换为页面文字
function alarmStatusLabel(status) {
  return { ACTIVE: '活跃', ACKNOWLEDGED: '已确认', RESOLVED: '已恢复' }[status] || status
}

// 将命令状态转换为页面文字
function commandStatusLabel(status) {
  return { PENDING: '待执行', SUCCESS: '执行成功', FAILED: '执行失败' }[status] || status
}

// 只提取后端规则DTO允许的字段，避免把实体审计字段回传
function toRulePayload(rule) {
  return {
    name: rule.name,
    deviceId: rule.deviceId,
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    recoveryThreshold: rule.recoveryThreshold,
    debounceCount: rule.debounceCount,
    severity: rule.severity,
    action: rule.action,
    enabled: rule.enabled,
  }
}

// 将布尔设备状态转换为页面文字
function deviceStatusLabel(val) {
  if (val === true) return '开启'
  if (val === false) return '关闭'
  return '未知'
}

// 将设备状态文字转换为标签类型
function deviceStatusTagType(val) {
  if (val === true) return 'success'
  if (val === false) return 'info'
  return 'warning'
}

// ---- 规则恢复阈值关系提示 ----
// 根据当前操作符返回对应的恢复阈值关系提示
const recoveryThresholdHint = computed(() => {
  if (ruleForm.value.operator === 'gt') {
    return '触发后需低于此值才恢复（通常小于触发阈值）'
  }
  return '触发后需高于此值才恢复（通常大于触发阈值）'
})

// ---- 轮询逻辑 ----
// 执行单次轮询：并行请求遥测、历史、报警、命令；单个接口失败不清空已有数据
async function poll() {
  const did = deviceId.value
  if (!did || pollingInFlight) return

  pollingInFlight = true
  pollingStatus.value = 'polling'
  pollingError.value = ''

  // 并行请求所有数据源
  let results
  try {
    results = await Promise.allSettled([
      getLatestTelemetry(did),
      getTelemetryHistory(did, 100),
      getSimulationRules(),
      getSimulationAlarms(did),
      getPendingCommands(did),
    ])
  } finally {
    pollingInFlight = false
  }

  // 处理遥测最新数据
  if (results[0].status === 'fulfilled') {
    latestTelemetry.value = results[0].value
  }

  // 处理遥测历史与趋势图数据
  if (results[1].status === 'fulfilled') {
    const history = results[1].value
    if (Array.isArray(history)) {
      telemetryHistory.value = history
      const chronologicalHistory = history.slice().reverse()
      trendTimeLabels.value = chronologicalHistory.map((p) => {
        const d = new Date(p.occurredAt)
        return d.toLocaleTimeString('zh-CN', { hour12: false })
      })
      trendSeries.value = [
        { name: '水位(mm)', data: chronologicalHistory.map((p) => p.waterLevelMm), color: trendColors[0] },
        { name: '流量(L/min)', data: chronologicalHistory.map((p) => p.flowRateLMin), color: trendColors[1] },
        { name: 'EC(mS/cm)', data: chronologicalHistory.map((p) => p.ecMsCm), color: trendColors[2] },
        { name: '含水率(%)', data: chronologicalHistory.map((p) => p.soilMoisturePct), color: trendColors[3] },
        { name: '降雨(mm)', data: chronologicalHistory.map((p) => p.rainfallMm), color: trendColors[4] },
      ]
    }
  }

  // 处理规则
  if (results[2].status === 'fulfilled') {
    rules.value = results[2].value || []
  }

  // 处理报警
  if (results[3].status === 'fulfilled') {
    alarms.value = results[3].value || []
  }

  // 处理待执行命令
  if (results[4].status === 'fulfilled') {
    pendingCommands.value = results[4].value || []
  }

  // 收集错误信息
  const errors = results
    .filter((r) => r.status === 'rejected')
    .map((r) => r.reason?.message || '未知错误')

  if (errors.length > 0) {
    pollingError.value = errors.join('；')
    pollingStatus.value = 'error'
  } else {
    pollingError.value = ''
    pollingStatus.value = 'idle'
  }
}

// 启动2秒定时轮询
function startPolling() {
  stopPolling()
  poll() // 立即执行一次
  pollingTimer.value = setInterval(poll, 2000)
}

// 停止轮询并清理定时器
function stopPolling() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

// ---- 规则 CRUD ----
// 打开新增规则弹窗
function openRuleAdd() {
  editingRuleId.value = null
  ruleForm.value = { ...createEmptyRuleForm(), deviceId: deviceId.value }
  ruleDialogVisible.value = true
}

// 打开编辑规则弹窗
function openRuleEdit(rule) {
  editingRuleId.value = rule.id
  ruleForm.value = {
    name: rule.name,
    deviceId: rule.deviceId,
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    recoveryThreshold: rule.recoveryThreshold,
    debounceCount: rule.debounceCount,
    severity: rule.severity,
    action: rule.action,
    enabled: rule.enabled,
  }
  ruleDialogVisible.value = true
}

// 保存规则（新增或更新）
async function saveRule() {
  if (!ruleForm.value.name.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  if (!ruleForm.value.deviceId.trim()) {
    ElMessage.warning('请填写设备ID或使用 * 表示全部仿真设备')
    return
  }
  const invalidRecovery = ruleForm.value.operator === 'gt'
    ? ruleForm.value.recoveryThreshold >= ruleForm.value.threshold
    : ruleForm.value.recoveryThreshold <= ruleForm.value.threshold
  if (invalidRecovery) {
    ElMessage.warning(ruleForm.value.operator === 'gt'
      ? '大于规则的恢复阈值必须小于触发阈值'
      : '小于规则的恢复阈值必须大于触发阈值')
    return
  }
  ruleSaving.value = true
  try {
    const payload = toRulePayload({
      ...ruleForm.value,
      name: ruleForm.value.name.trim(),
      deviceId: ruleForm.value.deviceId.trim(),
    })
    if (editingRuleId.value) {
      await updateSimulationRule(editingRuleId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createSimulationRule(payload)
      ElMessage.success('规则已创建')
    }
    ruleDialogVisible.value = false
    await poll()
  } finally {
    ruleSaving.value = false
  }
}

// 二次确认后删除规则
async function removeRule(rule) {
  try {
    await ElMessageBox.confirm(`确认删除规则"${rule.name}"？`, '删除规则', { type: 'warning' })
  } catch {
    return
  }
  await deleteSimulationRule(rule.id)
  ElMessage.success('规则已删除')
  await poll()
}

// 切换规则启用状态
async function toggleRule(rule, enabled) {
  const previous = rule.enabled
  rule.enabled = enabled
  try {
    await updateSimulationRule(rule.id, toRulePayload({ ...rule, enabled }))
  } catch {
    rule.enabled = previous
  }
}

// ---- 报警确认 ----
// 确认指定报警，只有 ACTIVE 状态可以确认
async function acknowledgeAlarm(alarm) {
  if (alarm.status !== 'ACTIVE') return
  alarmAcknowledging.value = alarm.id
  try {
    await acknowledgeSimulationAlarm(alarm.id)
    ElMessage.success('报警已确认')
    await poll()
  } finally {
    alarmAcknowledging.value = null
  }
}

// ---- 设备ID切换 ----
// 设备ID变化后立即刷新全部数据
watch(deviceId, () => {
  poll()
})

// ---- 生命周期 ----
onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="simulation">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1>MATLAB软件在环验证</h1>
        <div class="header-warning">
          <el-tag type="warning" size="large" effect="dark">
            <el-icon style="margin-right: 4px"><WarningFilled /></el-icon>
            仿真数据｜不代表田间实测
          </el-tag>
        </div>
      </div>
      <div class="header-controls">
        <el-input
          v-model="deviceId"
          placeholder="输入仿真设备ID"
          style="width: 220px"
          clearable
        />
        <el-tag
          :type="pollingStatus === 'error' ? 'danger' : pollingStatus === 'polling' ? 'warning' : 'success'"
          size="small"
          effect="dark"
        >
          {{ pollingStatus === 'polling' ? '刷新中' : pollingStatus === 'error' ? '异常' : '就绪' }}
        </el-tag>
      </div>
    </div>

    <!-- 轮询错误提示 -->
    <el-alert
      v-if="pollingError"
      :title="pollingError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 1rem"
    />

    <!-- 状态摘要 -->
    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :sm="6">
        <div class="summary-item">
          <span class="summary-label">最后数据时间</span>
          <span class="summary-value">
            {{ latestTelemetry?.occurredAt ? formatDateTime(latestTelemetry.occurredAt) : '--' }}
          </span>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-item">
          <span class="summary-label">场景编号</span>
          <span class="summary-value">{{ latestTelemetry?.scenarioCode || '--' }}</span>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-item">
          <span class="summary-label">生育期</span>
          <span class="summary-value">{{ latestTelemetry?.growthStage || '--' }}</span>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-item">
          <span class="summary-label">数据来源</span>
          <span class="summary-value">{{ latestTelemetry?.sourceType || '--' }}</span>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!latestTelemetry && pollingStatus !== 'polling' && !pollingError" description="暂无遥测数据，请确认设备ID是否正确" />

    <!-- 遥测卡片 -->
    <el-row v-if="latestTelemetry" :gutter="16" class="telemetry-row">
      <el-col v-for="card in telemetryCards" :key="card.key" :xs="12" :sm="8" :md="8" :lg="4" class="telemetry-col">
        <el-card shadow="hover" class="telemetry-card">
          <div class="card-icon">{{ card.icon }}</div>
          <div class="card-info">
            <span class="card-label">{{ card.label }}</span>
            <span class="card-value">
              {{ latestTelemetry[card.key] != null ? latestTelemetry[card.key] + ' ' + card.unit : '--' }}
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备状态 -->
    <el-row v-if="latestTelemetry" :gutter="16" class="status-row">
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="device-status-card">
          <span class="device-status-label">水泵</span>
          <el-tag :type="deviceStatusTagType(latestTelemetry.pumpOn)" effect="dark">
            {{ deviceStatusLabel(latestTelemetry.pumpOn) }}
          </el-tag>
        </el-card>
      </el-col>
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="device-status-card">
          <span class="device-status-label">灌溉阀</span>
          <el-tag :type="deviceStatusTagType(latestTelemetry.irrigationValveOpen)" effect="dark">
            {{ deviceStatusLabel(latestTelemetry.irrigationValveOpen) }}
          </el-tag>
        </el-card>
      </el-col>
      <el-col :xs="8" :sm="8" :md="8">
        <el-card shadow="hover" class="device-status-card">
          <span class="device-status-label">施肥泵</span>
          <el-tag :type="deviceStatusTagType(latestTelemetry.fertilizerPumpOn)" effect="dark">
            {{ deviceStatusLabel(latestTelemetry.fertilizerPumpOn) }}
          </el-tag>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-card v-if="trendTimeLabels.length" class="section-card" header="遥测趋势（最近100条）">
      <SimulationTrendChart
        :time-labels="trendTimeLabels"
        :series="trendSeries"
        height="320px"
      />
    </el-card>

    <!-- 规则列表 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header-row">
          <span>仿真规则</span>
          <el-button type="primary" size="small" :icon="Plus" @click="openRuleAdd">添加规则</el-button>
        </div>
      </template>
      <el-table v-if="rules.length" :data="rules" stripe size="small">
        <el-table-column prop="name" label="规则名称" min-width="130" />
        <el-table-column label="指标" width="120">
          <template #default="{ row }">{{ metricLabel(row.metric) }}</template>
        </el-table-column>
        <el-table-column label="触发条件" min-width="180">
          <template #default="{ row }">
            {{ operatorLabel(row.operator) }} {{ row.threshold }}
            <span v-if="row.recoveryThreshold != null">（恢复: {{ operatorLabel(row.operator === 'gt' ? 'lt' : 'gt') }} {{ row.recoveryThreshold }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="防抖" width="70">
          <template #default="{ row }">{{ row.debounceCount }}次</template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="110">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="65">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" size="small" @change="toggleRule(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Edit" @click="openRuleEdit(row)" />
            <el-button type="danger" text :icon="Delete" @click="removeRule(row)" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂未配置仿真规则" />
    </el-card>

    <!-- 报警列表 -->
    <el-card class="section-card" header="仿真报警">
      <el-table v-if="alarms.length" :data="alarms" stripe size="small">
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指标" width="120">
          <template #default="{ row }">{{ metricLabel(row.metric) }}</template>
        </el-table-column>
        <el-table-column label="实测值" width="100">
          <template #default="{ row }">{{ row.actualValue != null ? row.actualValue : '--' }}</template>
        </el-table-column>
        <el-table-column label="阈值" width="100">
          <template #default="{ row }">{{ row.threshold != null ? row.threshold : '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'danger' : row.status === 'ACKNOWLEDGED' ? 'warning' : 'success'" size="small">
              {{ alarmStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" width="170">
          <template #default="{ row }">{{ row.triggeredAt ? formatDateTime(row.triggeredAt) : '--' }}</template>
        </el-table-column>
        <el-table-column label="确认时间" width="170">
          <template #default="{ row }">{{ row.acknowledgedAt ? formatDateTime(row.acknowledgedAt) : '--' }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="170">
          <template #default="{ row }">{{ row.resolvedAt ? formatDateTime(row.resolvedAt) : '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="primary"
              size="small"
              :loading="alarmAcknowledging === row.id"
              :disabled="alarmAcknowledging !== null"
              @click="acknowledgeAlarm(row)"
            >
              确认
            </el-button>
            <span v-else style="color: var(--text-secondary); font-size: 0.8rem">--</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无仿真报警" />
    </el-card>

    <!-- 待执行命令 -->
    <el-card class="section-card">
      <template #header>
        <div class="card-header-row">
          <span>待执行命令</span>
          <span class="card-header-note">命令仅供MATLAB轮询，不发布到真实MQTT设备</span>
        </div>
      </template>
      <el-table v-if="pendingCommands.length" :data="pendingCommands" stripe size="small">
        <el-table-column label="动作" width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'info' : row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ commandStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ row.createdAt ? formatDateTime(row.createdAt) : '--' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="180" />
      </el-table>
      <el-empty v-else description="暂无待执行命令" />
    </el-card>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? '编辑仿真规则' : '添加仿真规则'" width="600px">
      <el-form :model="ruleForm" label-position="top">
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="规则名称">
              <el-input v-model="ruleForm.name" maxlength="100" placeholder="例如：高水位警报" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="设备ID">
              <el-input v-model="ruleForm.deviceId" placeholder="SIM-PADDY-001" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="指标">
              <el-select v-model="ruleForm.metric" style="width: 100%">
                <el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="操作符">
              <el-select v-model="ruleForm.operator" style="width: 100%">
                <el-option label="大于 (gt)" value="gt" />
                <el-option label="小于 (lt)" value="lt" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="触发阈值">
              <el-input-number v-model="ruleForm.threshold" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="恢复阈值">
              <el-input-number v-model="ruleForm.recoveryThreshold" controls-position="right" style="width: 100%" />
              <div class="form-hint">{{ recoveryThresholdHint }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="防抖次数">
              <el-input-number v-model="ruleForm.debounceCount" :min="1" :max="20" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="等级">
              <el-select v-model="ruleForm.severity" style="width: 100%">
                <el-option v-for="s in severityOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="动作">
              <el-select v-model="ruleForm.action" style="width: 100%">
                <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.simulation {
  padding-bottom: 2rem;
}

/* ---- 页面头部 ---- */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.page-header h1 {
  font-size: 1.5rem;
  margin: 0 0 0.5rem 0;
}
.header-warning {
  margin-top: 0.25rem;
}
.header-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}

/* ---- 状态摘要 ---- */
.summary-row {
  margin-bottom: 1rem;
}
.summary-item {
  background: var(--bg-secondary, rgba(255, 255, 255, 0.03));
  border: 1px solid var(--border-color, rgba(255, 255, 255, 0.06));
  border-radius: var(--radius-sm, 6px);
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.5rem;
}
.summary-label {
  font-size: 0.78rem;
  color: var(--text-secondary, #94a3b8);
}
.summary-value {
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-primary, #f1f5f9);
  word-break: break-all;
}

/* ---- 遥测卡片 ---- */
.telemetry-row {
  margin-bottom: 0.5rem;
}
.telemetry-col {
  margin-bottom: 1rem;
}
.telemetry-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
}
.card-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}
.card-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}
.card-label {
  font-size: 0.78rem;
  color: var(--text-secondary, #94a3b8);
}
.card-value {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text-primary, #f1f5f9);
  word-break: break-all;
}

/* ---- 设备状态 ---- */
.status-row {
  margin-bottom: 1rem;
}
.device-status-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
}
.device-status-label {
  font-size: 0.9rem;
  color: var(--text-secondary, #94a3b8);
}

/* ---- 通用卡片 ---- */
.section-card {
  margin-bottom: 1rem;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header-note {
  font-size: 0.78rem;
  color: var(--text-secondary, #94a3b8);
}

/* ---- 表单提示 ---- */
.form-hint {
  font-size: 0.72rem;
  color: var(--text-secondary, #94a3b8);
  margin-top: 0.2rem;
  line-height: 1.3;
}

/* ---- 响应式 ---- */
@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
  .header-controls {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
  .header-controls .el-input {
    width: 100% !important;
  }
}
</style>
