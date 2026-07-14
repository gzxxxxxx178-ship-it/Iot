<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveDevice as archiveDeviceApi,
  controlDevice,
  createDevice,
  getDeviceList,
  restoreDevice as restoreDeviceApi,
  updateDevice,
} from '../api/device'
import { formatDateTime } from '../utils/format'

const devices = ref([])
const loading = ref(false)
const saving = ref(false)
const includeArchived = ref(false)
const dialogVisible = ref(false)
const editingDeviceId = ref('')
const formRef = ref(null)
let refreshTimer = null
const form = reactive({
  deviceId: '',
  deviceName: '',
  deviceType: 'ESP8266',
  location: '',
  description: '',
  enabled: true,
})

const rules = {
  deviceId: [
    { required: true, message: '请输入设备 ID', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{1,64}$/, message: '只能包含字母、数字、下划线和短横线', trigger: 'blur' },
  ],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
}

const dialogTitle = computed(() => editingDeviceId.value ? '编辑设备' : '注册设备')
const activeCount = computed(() => devices.value.filter((device) => device.lifecycleStatus === 'ACTIVE').length)
const onlineCount = computed(() => devices.value.filter((device) => device.status === 'online').length)

// 查询设备档案及其最新传感器状态
async function loadDevices(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    devices.value = await getDeviceList({ includeArchived: includeArchived.value }) || []
  } finally {
    if (showLoading) loading.value = false
  }
}

// 重置设备编辑表单
function resetForm() {
  editingDeviceId.value = ''
  Object.assign(form, {
    deviceId: '',
    deviceName: '',
    deviceType: 'ESP8266',
    location: '',
    description: '',
    enabled: true,
  })
  formRef.value?.clearValidate()
}

// 打开新设备注册窗口
function openCreate() {
  resetForm()
  dialogVisible.value = true
}

// 使用现有档案打开设备编辑窗口
function openEdit(device) {
  editingDeviceId.value = device.deviceId
  Object.assign(form, {
    deviceId: device.deviceId,
    deviceName: device.deviceName,
    deviceType: device.deviceType || 'ESP8266',
    location: device.location || '',
    description: device.description || '',
    enabled: device.enabled !== false,
  })
  dialogVisible.value = true
}

// 校验并创建或更新设备档案
async function submitDevice() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      deviceName: form.deviceName,
      deviceType: form.deviceType,
      location: form.location,
      description: form.description,
      enabled: form.enabled,
    }
    if (editingDeviceId.value) {
      await updateDevice(editingDeviceId.value, payload)
      ElMessage.success('设备档案已更新')
    } else {
      await createDevice({ deviceId: form.deviceId, ...payload })
      ElMessage.success('设备已注册')
    }
    dialogVisible.value = false
    await loadDevices()
  } finally {
    saving.value = false
  }
}

// 更新设备启用状态并保留其他档案字段
async function toggleEnabled(device) {
  const nextEnabled = !device.enabled
  await updateDevice(device.deviceId, {
    deviceName: device.deviceName,
    deviceType: device.deviceType,
    location: device.location,
    description: device.description,
    enabled: nextEnabled,
  })
  ElMessage.success(nextEnabled ? '设备已启用' : '设备已停用')
  await loadDevices()
}

// 确认后将设备归档但保留全部历史数据
async function archiveDevice(device) {
  try {
    await ElMessageBox.confirm(
      `归档设备“${device.deviceName}”后将停止接收上报和远程控制，历史数据会继续保留。`,
      '归档设备',
      { confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await archiveDeviceApi(device.deviceId)
  ElMessage.success('设备已归档')
  await loadDevices()
}

// 恢复归档设备并重新启用
async function restoreDevice(device) {
  await restoreDeviceApi(device.deviceId)
  ElMessage.success('设备已恢复')
  await loadDevices()
}

// 向启用设备发送远程控制指令
async function sendCommand(device, command) {
  await controlDevice(command, device.deviceId)
  const labels = { start: '开始上报', stop: '停止上报', read: '立即读取', status: '查询状态' }
  ElMessage.success(`${labels[command]}指令已发送`)
}

// 切换是否显示归档设备并重新查询
function changeArchivedVisibility() {
  loadDevices()
}

// 页面挂载时加载设备档案
onMounted(() => {
  loadDevices()
  refreshTimer = window.setInterval(() => loadDevices(false), 10000)
})

// 页面卸载时停止设备状态轮询
onUnmounted(() => window.clearInterval(refreshTimer))
</script>

<template>
  <div class="device-list">
    <div class="page-header">
      <div>
        <h1>设备管理</h1>
        <p>管理设备档案、生命周期、实时状态和远程控制</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadDevices()">刷新状态</el-button>
        <el-button type="primary" @click="openCreate">注册设备</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <el-card shadow="never"><span>当前档案</span><b>{{ devices.length }}</b></el-card>
      <el-card shadow="never"><span>正常设备</span><b>{{ activeCount }}</b></el-card>
      <el-card shadow="never"><span>在线设备</span><b>{{ onlineCount }}</b></el-card>
      <el-card shadow="never">
        <span>显示归档</span>
        <el-switch v-model="includeArchived" @change="changeArchivedVisibility" />
      </el-card>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="devices" class="device-table">
        <el-table-column label="设备" min-width="180">
          <template #default="{ row }">
            <div class="device-title">
              <span class="status-dot" :class="{ online: row.status === 'online' }"></span>
              <div><b>{{ row.deviceName }}</b><small>{{ row.deviceId }}</small></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="deviceType" label="类型" width="110" />
        <el-table-column prop="location" label="部署位置" min-width="130">
          <template #default="{ row }">{{ row.location || '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.lifecycleStatus === 'ARCHIVED'" type="info">已归档</el-tag>
            <el-tag v-else-if="!row.enabled" type="warning">已停用</el-tag>
            <el-tag v-else :type="row.status === 'online' ? 'success' : 'danger'">
              {{ row.status === 'online' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最新读数" min-width="190">
          <template #default="{ row }">
            {{ row.temperature ?? '--' }} ℃ / {{ row.humidity ?? '--' }} % / {{ row.water ?? '--' }} ADC
          </template>
        </el-table-column>
        <el-table-column label="最后上报" min-width="165">
          <template #default="{ row }">{{ row.lastSeen ? formatDateTime(row.lastSeen) : '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <template v-if="row.lifecycleStatus !== 'ARCHIVED'">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-dropdown :disabled="!row.enabled" @command="sendCommand(row, $event)">
                <el-button link type="primary">远程控制</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="start">开始上报</el-dropdown-item>
                    <el-dropdown-item command="stop">停止上报</el-dropdown-item>
                    <el-dropdown-item command="read">立即读取</el-dropdown-item>
                    <el-dropdown-item command="status">查询状态</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button link type="danger" @click="archiveDevice(row)">归档</el-button>
            </template>
            <el-button v-else link type="success" @click="restoreDevice(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !devices.length" description="暂无设备档案" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" @closed="resetForm">
      <el-alert
        v-if="!editingDeviceId"
        title="注册档案不会自动创建MQTT账号；设备仍需使用已配置的独立凭据连接。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="device-form">
        <el-form-item label="设备 ID" prop="deviceId">
          <el-input v-model="form.deviceId" :disabled="Boolean(editingDeviceId)" placeholder="例如 device002" />
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="例如 1号棚环境节点" maxlength="100" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="form.deviceType" allow-create filterable>
            <el-option label="ESP8266" value="ESP8266" />
            <el-option label="ESP32" value="ESP32" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署位置">
          <el-input v-model="form.location" placeholder="例如 1号温室东侧" maxlength="150" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDevice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.device-list { max-width: 1500px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 1.25rem; }
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }
.header-actions { display: flex; gap: 0.5rem; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 0.75rem; margin-bottom: 1rem; }
.summary-grid :deep(.el-card) { background: #182438 !important; border-color: #2a394e !important; }
.summary-grid :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; }
.summary-grid span { color: var(--text-secondary); font-size: 0.85rem; }
.summary-grid b { font-size: 1.35rem; }
.table-card { background: #172235 !important; border-color: #28364a !important; }
.table-card :deep(.el-card__body) { padding: 0; }
.device-table {
  --el-table-bg-color: #172235;
  --el-table-tr-bg-color: #172235;
  --el-table-header-bg-color: #1d2a3d;
  --el-table-row-hover-bg-color: #223149;
  --el-table-border-color: #2a394e;
  --el-table-text-color: #c5cfdd;
  --el-table-header-text-color: #93a4ba;
  background: #172235;
}
.device-table :deep(.el-table__inner-wrapper::before) { background-color: #2a394e; }
.device-table :deep(th.el-table__cell) {
  background: #1d2a3d !important;
  border-bottom-color: #314158;
  font-weight: 600;
}
.device-table :deep(td.el-table__cell) {
  background: #172235;
  border-bottom-color: #26354a;
}
.device-table :deep(.el-table__row:hover > td.el-table__cell) { background: #223149 !important; }
.device-table :deep(td.el-table-fixed-column--right) { background: #172235; }
.device-table :deep(.el-table__row:hover > td.el-table-fixed-column--right) { background: #223149 !important; }
.device-table :deep(.el-table__empty-block) { background: #172235; }
.device-table :deep(.el-table__empty-text) { color: #7f90a7; }
.device-title { display: flex; align-items: center; gap: 0.65rem; }
.device-title div { display: flex; flex-direction: column; gap: 0.15rem; }
.device-title small { color: var(--text-muted); }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: #ef4444; box-shadow: 0 0 7px rgba(239, 68, 68, 0.45); }
.status-dot.online { background: #10b981; box-shadow: 0 0 7px rgba(16, 185, 129, 0.55); }
.device-form { margin-top: 1rem; }
@media (max-width: 800px) {
  .summary-grid { grid-template-columns: repeat(2, 1fr); }
  .page-header { gap: 1rem; }
}
</style>
