<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import {
  createAutomationRule,
  deleteAutomationRule,
  getAutomationExecutions,
  getAutomationRules,
  updateAutomationRule,
} from '../api/automation'

const rules = ref([])
const executions = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref(createEmptyForm())

const metricOptions = [
  { label: '温度（℃）', value: 'temperature' },
  { label: '湿度（%）', value: 'humidity' },
  { label: '水位（ADC）', value: 'water' },
]

const actionOptions = [
  { label: '启动设备', value: 'start' },
  { label: '停止设备', value: 'stop' },
  { label: '记录通知', value: 'notify' },
]

// 创建规则表单默认值
function createEmptyForm() {
  return {
    name: '',
    deviceId: 'device001',
    metric: 'temperature',
    operator: 'gt',
    threshold: 30,
    action: 'start',
    enabled: true,
    debounceCount: 2,
    cooldownSeconds: 300,
  }
}

// 将比较运算符转换为页面文字
function operatorLabel(operator) {
  return { gt: '大于', lt: '小于', eq: '等于' }[operator] || operator
}

// 将动作代码转换为页面文字
function actionLabel(action) {
  return actionOptions.find((item) => item.value === action)?.label || action
}

// 将指标代码转换为包含单位的页面文字
function metricLabel(metric) {
  return metricOptions.find((item) => item.value === metric)?.label || metric
}

// 将执行状态转换为页面文字
function statusLabel(status) {
  return status === 'SUCCESS' ? '成功' : '失败'
}

// 格式化服务端时间用于执行记录展示
function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

// 同时加载持久化规则和最近执行记录
async function loadData() {
  loading.value = true
  try {
    const [ruleData, executionData] = await Promise.all([
      getAutomationRules(),
      getAutomationExecutions(),
    ])
    rules.value = ruleData || []
    executions.value = executionData || []
  } finally {
    loading.value = false
  }
}

// 打开新增规则弹窗
function openAdd() {
  editingId.value = null
  form.value = createEmptyForm()
  dialogVisible.value = true
}

// 打开编辑规则弹窗并复制当前规则值
function openEdit(rule) {
  editingId.value = rule.id
  form.value = {
    name: rule.name,
    deviceId: rule.deviceId,
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    action: rule.action,
    enabled: rule.enabled,
    debounceCount: rule.debounceCount,
    cooldownSeconds: rule.cooldownSeconds,
  }
  dialogVisible.value = true
}

// 校验表单并创建或更新持久化规则
async function saveRule() {
  if (!form.value.name.trim() || !form.value.deviceId.trim()) {
    ElMessage.warning('请填写规则名称和设备ID')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value, name: form.value.name.trim(), deviceId: form.value.deviceId.trim() }
    if (editingId.value) {
      await updateAutomationRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createAutomationRule(payload)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

// 二次确认后删除规则
async function removeRule(rule) {
  try {
    await ElMessageBox.confirm(`确认删除“${rule.name}”？`, '删除规则', { type: 'warning' })
  } catch (error) {
    return
  }
  await deleteAutomationRule(rule.id)
  ElMessage.success('规则已删除')
  await loadData()
}

// 将规则启用状态立即同步到服务端
async function toggleRule(rule, enabled) {
  const previous = rule.enabled
  rule.enabled = enabled
  try {
    await updateAutomationRule(rule.id, { ...rule, enabled })
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  } catch (error) {
    rule.enabled = previous
  }
}

onMounted(loadData)
</script>

<template>
  <div class="automation" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>自动化规则</h1>
        <p>规则由服务端持续评估，防抖和冷却时间用于抑制传感器波动导致的频繁启停。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加规则</el-button>
    </div>

    <el-card class="section-card" header="规则列表">
      <el-table v-if="rules.length" :data="rules" stripe size="small">
        <el-table-column prop="name" label="规则名称" min-width="140" />
        <el-table-column prop="deviceId" label="设备" width="120" />
        <el-table-column label="条件" min-width="240">
          <template #default="{ row }">
            {{ metricLabel(row.metric) }} {{ operatorLabel(row.operator) }} {{ row.threshold }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="110">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="防抖/冷却" width="130">
          <template #default="{ row }">{{ row.debounceCount }} 次 / {{ row.cooldownSeconds }} 秒</template>
        </el-table-column>
        <el-table-column label="启用" width="75">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" size="small" @change="toggleRule(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Edit" @click="openEdit(row)" />
            <el-button type="danger" text :icon="Delete" @click="removeRule(row)" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无自动化规则" />
    </el-card>

    <el-card class="section-card" header="最近执行记录">
      <el-table v-if="executions.length" :data="executions" stripe size="small">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="ruleId" label="规则ID" width="90" />
        <el-table-column prop="deviceId" label="设备" width="120" />
        <el-table-column label="动作" width="110">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="actualValue" label="触发值" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="结果" min-width="180" />
      </el-table>
      <el-empty v-else description="暂无执行记录" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑自动化规则' : '添加自动化规则'" width="520px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="规则名称">
              <el-input v-model="form.name" maxlength="100" placeholder="例如：高温自动通风" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="设备ID">
              <el-input v-model="form.deviceId" placeholder="device001" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="9">
            <el-form-item label="指标">
              <el-select v-model="form.metric">
                <el-option v-for="item in metricOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="条件">
              <el-select v-model="form.operator">
                <el-option label="大于" value="gt" />
                <el-option label="小于" value="lt" />
                <el-option label="等于" value="eq" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="阈值">
              <el-input-number v-model="form.threshold" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="动作">
          <el-select v-model="form.action" style="width: 100%">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="连续命中次数">
              <el-input-number v-model="form.debounceCount" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="冷却时间（秒）">
              <el-input-number v-model="form.cooldownSeconds" :min="0" :max="86400" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }
.section-card { margin-bottom: 1rem; }

@media (max-width: 640px) {
  .page-header { align-items: stretch; flex-direction: column; }
  .page-header .el-button { width: 100%; }
}
</style>
