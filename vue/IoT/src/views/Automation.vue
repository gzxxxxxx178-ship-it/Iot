<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'

// 自动化规则列表（前端 mock，不持久化到后端）
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

// 辅助函数：选项值 → 显示文字
const operatorLabel = (op) => ({ gt: '大于', lt: '小于', eq: '等于' }[op] || op)
const actionLabel = (act) => actionOptions.find((a) => a.value === act)?.label || act
const metricLabel = (m) => metricOptions.find((x) => x.value === m)?.label || m

// 打开添加规则弹窗
function openAdd() {
  form.value = { name: '', metric: 'temperature', operator: 'gt', threshold: 30, action: 'start', enabled: true }
  dialogVisible.value = true
}

// 添加规则到本地列表
function addRule() {
  if (!form.value.name) {
    ElMessage.warning('请输入规则名称')
    return
  }
  rules.value.push({ ...form.value, id: Date.now() })
  dialogVisible.value = false
}

// 从列表中移除规则
function removeRule(id) {
  rules.value = rules.value.filter((r) => r.id !== id)
}

// 切换规则启用/禁用状态
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
        <p>根据传感器数据自动执行操作</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">添加规则</el-button>
    </div>

    <!-- 规则列表 -->
    <el-card v-if="rules.length">
      <el-table :data="rules" stripe size="small">
        <el-table-column prop="name" label="规则名称" width="160" />
        <el-table-column label="条件" width="220">
          <template #default="{ row }">
            当 {{ metricLabel(row.metric) }} {{ operatorLabel(row.operator) }} {{ row.threshold }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggleRule(row.id)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="danger" text :icon="Delete" @click="removeRule(row.id)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-empty v-else description="暂无自动化规则" />

    <!-- 添加规则弹窗 -->
    <el-dialog v-model="dialogVisible" title="添加自动化规则" width="420px">
      <el-form :model="form" label-position="top">
        <el-form-item label="规则名称">
          <el-input v-model="form.name" placeholder="例如：高温自动通风" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="7">
            <el-form-item label="指标"><el-select v-model="form.metric"><el-option v-for="m in metricOptions" :key="m.value" :label="m.label" :value="m.value" /></el-select></el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="条件"><el-select v-model="form.operator"><el-option label="大于" value="gt" /><el-option label="小于" value="lt" /><el-option label="等于" value="eq" /></el-select></el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="阈值"><el-input-number v-model="form.threshold" :min="0" controls-position="right" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="动作"><el-select v-model="form.action" style="width:100%"><el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" /></el-select></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addRule">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 1.5rem; }
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }
</style>
