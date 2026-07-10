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
  form.value = { metric: 'temperature', operator: 'gt', threshold: 30, enabled: true }
  dialogVisible.value = true
}

// 提交保存报警规则
async function submitRule() {
  try {
    await saveAlarmRule(form.value)
    ElMessage.success('规则保存成功')
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
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
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
    <el-dialog v-model="dialogVisible" title="添加报警规则" width="420px">
      <el-form :model="form" label-position="top">
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
              <el-input-number v-model="form.threshold" :min="0" :step="1" controls-position="right" />
            </el-col>
          </el-row>
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
