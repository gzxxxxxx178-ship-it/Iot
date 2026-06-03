<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'

const rules = ref([])
const dialogVisible = ref(false)
const form = ref({
  name: '',
  metric: 'temperature',
  operator: 'gt',
  threshold: 30,
  action: 'start',
  enabled: true,
})

const metricOptions = [
  { label: '温度', value: 'temperature' },
  { label: '湿度', value: 'humidity' },
  { label: '水位', value: 'water' },
]

const actionOptions = [
  { label: '启动设备', value: 'start' },
  { label: '停止设备', value: 'stop' },
  { label: '发送通知', value: 'notify' },
]

const operatorLabel = (op) => ({ gt: '大于', lt: '小于', eq: '等于' }[op] || op)
const actionLabel = (act) => actionOptions.find((a) => a.value === act)?.label || act
const metricLabel = (m) => metricOptions.find((x) => x.value === m)?.label || m

function openAdd() {
  form.value = { name: '', metric: 'temperature', operator: 'gt', threshold: 30, action: 'start', enabled: true }
  dialogVisible.value = true
}

function addRule() {
  if (!form.value.name) {
    ElMessage.warning('请输入规则名称')
    return
  }
  rules.value.push({ ...form.value, id: Date.now() })
  dialogVisible.value = false
}

function removeRule(id) {
  rules.value = rules.value.filter((r) => r.id !== id)
}

function toggleRule(id) {
  const rule = rules.value.find((r) => r.id === id)
  if (rule) rule.enabled = !rule.enabled
}
</script>

<template>
  <div class="automation">
    <div class="page-header">
      <div>
        <h1>自动化规则</h1>
        <p>根据传感器数据自动触发设备控制指令</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加规则</el-button>
    </div>

    <el-card v-if="rules.length">
      <el-table :data="rules">
        <el-table-column prop="name" label="规则名称" min-width="150" />
        <el-table-column label="条件" min-width="200">
          <template #default="{ row }">
            当 {{ metricLabel(row.metric) }} {{ operatorLabel(row.operator) }} {{ row.threshold }} 时
          </template>
        </el-table-column>
        <el-table-column label="执行动作" width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggleRule(row.id)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" text type="danger" :icon="Delete" @click="removeRule(row.id)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-empty v-else description="暂无自动化规则，点击上方按钮创建" />

    <el-dialog v-model="dialogVisible" title="添加自动化规则" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="规则名称">
          <el-input v-model="form.name" placeholder="如 高温自动通风" />
        </el-form-item>
        <el-form-item label="监控指标">
          <el-select v-model="form.metric" class="w-full">
            <el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-select v-model="form.operator" class="w-full">
            <el-option label="大于" value="gt" />
            <el-option label="小于" value="lt" />
            <el-option label="等于" value="eq" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="form.threshold" :step="0.5" />
        </el-form-item>
        <el-form-item label="执行动作">
          <el-select v-model="form.action" class="w-full">
            <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addRule">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.automation {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--spacing-xl);
}

.page-header h1 {
  font-size: 1.5rem;
  margin: 0;
}

.page-header p {
  color: var(--text-secondary);
  margin: 0.25rem 0 0;
  font-size: 0.9rem;
}

.w-full {
  width: 100%;
}
</style>
