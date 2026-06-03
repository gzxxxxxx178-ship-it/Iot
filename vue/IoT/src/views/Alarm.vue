<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Warning } from '@element-plus/icons-vue'
import { getAlarmRules, saveAlarmRule, deleteAlarmRule, getAlarmRecords } from '../api/alarm'
import { formatDateTime } from '../utils/format'

const rules = ref([])
const records = ref([])
const dialogVisible = ref(false)
const form = ref({ metric: 'temperature', operator: 'gt', threshold: 30, enabled: true })

const metricOptions = [
  { label: '温度', value: 'temperature' },
  { label: '湿度', value: 'humidity' },
  { label: '水位', value: 'water' },
]

const operatorOptions = [
  { label: '大于', value: 'gt' },
  { label: '小于', value: 'lt' },
  { label: '等于', value: 'eq' },
]

const operatorLabel = (op) => ({ gt: '>', lt: '<', eq: '=' }[op] || op)

async function fetchRules() {
  try { rules.value = await getAlarmRules() } catch {}
}

async function fetchRecords() {
  try { records.value = await getAlarmRecords() } catch {}
}

function openAdd() {
  form.value = { metric: 'temperature', operator: 'gt', threshold: 30, enabled: true }
  dialogVisible.value = true
}

async function submitRule() {
  try {
    await saveAlarmRule(form.value)
    ElMessage.success('规则保存成功')
    dialogVisible.value = false
    fetchRules()
  } catch {}
}

async function handleDelete(rule) {
  try {
    await ElMessageBox.confirm('确认删除该报警规则？', '确认', { type: 'warning' })
    await deleteAlarmRule(rule.id)
    ElMessage.success('已删除')
    fetchRules()
  } catch {}
}

onMounted(() => { fetchRules(); fetchRecords() })
</script>

<template>
  <div class="alarm">
    <div class="page-header">
      <h1>报警管理</h1>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加规则</el-button>
    </div>

    <div class="alarm-grid">
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
          <el-table-column prop="enabled" label="启用" width="80">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" disabled size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无报警规则" :image-size="80" />
      </el-card>

      <el-card>
        <template #header>
          <div class="card-title-row">
            <span>报警记录</span>
            <el-icon color="var(--color-yellow)" :size="16"><Warning /></el-icon>
          </div>
        </template>
        <el-table :data="records" v-if="records.length" max-height="400">
          <el-table-column prop="deviceId" label="设备 ID" width="140" />
          <el-table-column prop="message" label="报警内容" min-width="180" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.timestamp) }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无报警记录" :image-size="80" />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" title="添加报警规则" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="监控指标">
          <el-select v-model="form.metric" class="w-full">
            <el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-select v-model="form.operator" class="w-full">
            <el-option v-for="o in operatorOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="form.threshold" :step="0.5" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRule">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.alarm {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-xl);
}

.page-header h1 {
  font-size: 1.5rem;
  margin: 0;
}

.alarm-grid {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.w-full {
  width: 100%;
}
</style>
