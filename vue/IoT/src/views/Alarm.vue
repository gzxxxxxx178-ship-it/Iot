<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAlarmRules, saveAlarmRule, updateAlarmRule, deleteAlarmRule, getAlarmRecords } from '../api/alarm'
import { formatDateTime } from '../utils/format'

const rules = ref([])
const records = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = ref({
  metric: 'temperature',
  operator: 'gt',
  threshold: 30,
  deviceId: '*',
  cooldownSeconds: 300,
  enabled: true,
})

// 报警规则表单校验
const alarmRules = {
  threshold: [
    { required: true, message: '请输入阈值', trigger: 'blur' },
    { type: 'number', message: '阈值必须是数值', trigger: 'blur' },
  ],
  cooldownSeconds: [
    { required: true, message: '请输入冷却时间', trigger: 'blur' },
    { type: 'number', min: 0, max: 86400, message: '冷却时间范围为0～86400秒', trigger: 'blur' },
  ],
}

const metricOptions = [
  { label: '温度', value: 'temperature' },
  { label: '湿度', value: 'humidity' },
  { label: '水位（ADC）', value: 'water' },
]

const operatorOptions = [
  { label: '大于', value: 'gt' },
  { label: '小于', value: 'lt' },
  { label: '等于', value: 'eq' },
]

// 操作符中文映射
const operatorLabel = (op) => ({ gt: '>', lt: '<', eq: '=' }[op] || op)

// 加载报警规则列表
async function fetchRules() {
  try { rules.value = await getAlarmRules() } catch {}
}

// 加载报警记录
async function fetchRecords() {
  try { records.value = await getAlarmRecords() } catch {}
}

// 打开添加规则弹窗，重置表单
function openAdd() {
  editingId.value = null
  form.value = {
    metric: 'temperature',
    operator: 'gt',
    threshold: 30,
    deviceId: '*',
    cooldownSeconds: 300,
    enabled: true,
  }
  dialogVisible.value = true
}

// 打开编辑规则弹窗并载入现有配置
function openEdit(rule) {
  editingId.value = rule.id
  form.value = {
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    deviceId: rule.deviceId,
    cooldownSeconds: rule.cooldownSeconds,
    enabled: rule.enabled,
  }
  dialogVisible.value = true
}

// 提交保存报警规则：先校验表单，通过后调用 API
async function submitRule() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return // 校验不通过
  }
  try {
    if (editingId.value) {
      await updateAlarmRule(editingId.value, form.value)
    } else {
      await saveAlarmRule(form.value)
    }
    ElMessage.success(editingId.value ? '规则更新成功' : '规则保存成功')
    dialogVisible.value = false
    fetchRules()
  } catch {}
}

// 删除报警规则（确认弹窗）
async function handleDelete(rule) {
  try {
    await ElMessageBox.confirm('确认删除该报警规则？', '确认', { type: 'warning' })
    await deleteAlarmRule(rule.id)
    ElMessage.success('已删除')
    fetchRules()
  } catch {}
}

// 初始加载规则和记录
onMounted(() => { fetchRules(); fetchRecords() })
</script>

<template>
  <div class="alarm">
    <div class="page-header">
      <h1>报警管理</h1>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加规则</el-button>
    </div>

    <div class="alarm-grid">
      <!-- 报警规则表格 -->
      <el-card>
        <template #header><span>报警规则</span></template>
        <el-table :data="rules" v-if="rules.length">
          <el-table-column prop="metric" label="监控指标" width="100">
            <template #default="{ row }">
              {{ metricOptions.find((m) => m.value === row.metric)?.label || row.metric }}
            </template>
          </el-table-column>
          <el-table-column label="条件" width="140">
            <template #default="{ row }">
              {{ operatorLabel(row.operator) }} {{ row.threshold }}
            </template>
          </el-table-column>
          <el-table-column prop="enabled" label="启用" width="70">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceId" label="设备范围" width="120">
            <template #default="{ row }">{{ row.deviceId === '*' ? '全部设备' : row.deviceId }}</template>
          </el-table-column>
          <el-table-column prop="cooldownSeconds" label="冷却时间" width="100">
            <template #default="{ row }">{{ row.cooldownSeconds }} 秒</template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="openEdit(row)">编辑</el-button>
              <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无报警规则" />
      </el-card>

      <!-- 报警记录表格 -->
      <el-card>
        <template #header><span>报警记录</span></template>
        <el-table :data="records" v-if="records.length">
          <el-table-column prop="deviceId" label="设备 ID" width="120" />
          <el-table-column prop="message" label="报警内容" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无报警记录" />
      </el-card>
    </div>

    <!-- 添加规则弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑报警规则' : '添加报警规则'" width="420px">
      <el-form ref="formRef" :model="form" :rules="alarmRules" label-position="top">
        <el-form-item label="监控指标">
          <el-select v-model="form.metric" style="width:100%">
            <el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-row :gutter="10">
            <el-col :span="10">
              <el-select v-model="form.operator">
                <el-option v-for="o in operatorOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-col>
            <el-col :span="14">
              <el-form-item prop="threshold">
                <el-input-number
                  v-model="form.threshold"
                  :min="form.metric === 'temperature' ? -50 : 0"
                  :max="form.metric === 'water' ? 4095 : 100"
                  :step="1"
                  controls-position="right"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="适用设备">
          <el-input v-model="form.deviceId" placeholder="* 表示全部设备，或填写具体 deviceId" />
        </el-form-item>
        <el-form-item label="重复报警冷却时间" prop="cooldownSeconds">
          <el-input-number
            v-model="form.cooldownSeconds"
            :min="0"
            :max="86400"
            :step="30"
            controls-position="right"
          />
          <span style="margin-left: 8px; color: var(--text-muted);">秒</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.alarm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}
@media (max-width: 768px) { .alarm-grid { grid-template-columns: 1fr; } }
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}
.page-header h1 { font-size: 1.5rem; margin: 0; }
</style>
