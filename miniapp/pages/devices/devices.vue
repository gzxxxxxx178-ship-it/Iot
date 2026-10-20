<template>
  <view class="page">
    <!-- 页面顶部：标题与当前账号设备总数 -->
    <PageHeader title="设备" :subtitle="'共 ' + devices.length + ' 台设备'" />
    <LiveStatus />

    <view v-if="session.token" class="device-toolbar">
      <button class="btn-secondary toolbar-btn" :disabled="refreshing" @tap="refreshPage">{{ refreshing ? '刷新中…' : '刷新状态' }}</button>
      <button class="btn-primary toolbar-btn" @tap="openCreate">注册设备</button>
      <button class="btn-secondary toolbar-btn" @tap="toggleArchived">
        {{ includeArchived ? '隐藏归档设备' : '显示归档设备' }}
      </button>
    </view>
    <text v-if="deviceError" class="error-text">{{ deviceError }}</text>

    <!-- 状态筛选 -->
    <view class="filter-bar">
      <view
        v-for="f in filters"
        :key="f.key"
        class="filter-item"
        :class="{ active: filter === f.key }"
        @tap="setFilter(f.key)"
      >
        <text class="filter-text">{{ f.label }}</text>
      </view>
    </view>

    <!-- 设备列表 -->
    <view v-if="filteredDevices.length" class="list">
      <view v-for="device in filteredDevices" :key="device.id" class="device-card" @tap="openDetail(device)">
        <view class="dc-top">
          <view class="dc-name-wrap">
            <text class="dc-name">{{ device.name }}</text>
            <text class="dc-type">{{ device.type }}</text>
          </view>
          <text v-if="device.location" class="dc-location">{{ device.location }}</text>
          <StatusTag :text="device.lifecycleStatus === 'ARCHIVED' ? '已归档' : device.enabled === false ? '已停用' : device.status === 'online' ? '在线' : '离线'" :kind="device.lifecycleStatus === 'ARCHIVED' ? 'blue' : device.enabled === false ? 'blue' : device.status === 'online' ? 'green' : 'red'" />
        </view>
        <view class="dc-metrics">
          <view class="dc-metric"><text class="dc-metric-value">{{ formatReading(device.temp) }}℃</text><text class="dc-metric-label">温度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ formatReading(device.hum) }}%</text><text class="dc-metric-label">湿度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.water ?? '--' }}</text><text class="dc-metric-label">水位原始值</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ formatSignal(device.signal) }}</text><text class="dc-metric-label">信号强度</text></view>
        </view>
        <view class="dc-bottom">
          <text class="hint">最近上报：{{ showTime(device.lastSeen) }}</text>
          <StatusTag v-if="!device.ownedByCurrentUser" text="共享设备" kind="blue" />
          <StatusTag v-else-if="device.controlled" text="运行中" kind="blue" />
        </view>
        <view v-if="device.ownedByCurrentUser" class="owner-actions" @tap.stop>
          <button v-if="device.lifecycleStatus !== 'ARCHIVED'" class="btn-secondary action-btn" @tap="openEdit(device)">编辑</button>
          <button v-if="device.lifecycleStatus !== 'ARCHIVED'" class="btn-secondary action-btn" :disabled="updatingDeviceId === device.id" @tap="toggleEnabled(device)">{{ device.enabled === false ? '启用' : '停用' }}</button>
          <button class="btn-secondary action-btn" @tap="openShares(device)">共享名单</button>
          <button v-if="device.lifecycleStatus === 'ARCHIVED'" class="btn-secondary action-btn" @tap="restoreDevice(device)">恢复</button>
          <button v-else class="btn-secondary action-btn archive-btn" @tap="archiveDevice(device)">归档</button>
        </view>
      </view>
    </view>
    <EmptyState v-else :text="devicesEmptyText" />
    <view v-if="live.commands.length" class="card">
      <text class="section-title">控制回执</text>
      <view v-for="receipt in displayedReceipts" :key="receipt.commandId" class="receipt">
        <text>{{ receipt.deviceId }} · {{ ({ start: '启动', stop: '停止', read: '立即读取', status: '查询状态' })[receipt.command] || receipt.command }}</text>
        <text>{{ commandStatus(receipt.status) }}</text>
        <text class="hint">{{ receipt.message }}</text>
      </view>
      <button v-if="live.commands.length > 10" class="btn-secondary receipt-toggle" @tap="receiptExpanded = !receiptExpanded">
        {{ receiptExpanded ? '收起命令回执' : '查看全部 ' + live.commands.length + ' 条命令回执' }}
      </button>
    </view>

    <!-- 设备详情弹层（含控制确认） -->
    <view v-if="detailDevice" class="mask" @tap="closeDetail">
      <view class="detail-sheet" @tap.stop>
        <view class="ds-header">
          <view>
            <text class="ds-name">{{ detailDevice.name }}</text>
            <text class="ds-type">{{ detailDevice.type }}</text>
          </view>
          <StatusTag :text="detailDevice.lifecycleStatus === 'ARCHIVED' ? '已归档' : detailDevice.enabled === false ? '已停用' : detailDevice.status === 'online' ? '在线' : '离线'" :kind="detailDevice.lifecycleStatus === 'ARCHIVED' ? 'blue' : detailDevice.enabled === false ? 'blue' : detailDevice.status === 'online' ? 'green' : 'red'" />
        </view>
        <view class="ds-metrics">
          <view class="ds-metric"><text class="ds-metric-value">{{ formatReading(detailDevice.temp) }}℃</text><text class="ds-metric-label">温度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ formatReading(detailDevice.hum) }}%</text><text class="ds-metric-label">湿度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ detailDevice.water ?? '--' }}</text><text class="ds-metric-label">水位原始值</text></view>
        </view>
        <text class="hint">最近上报：{{ showTime(detailDevice.lastSeen) }}</text>
        <text class="hint detail-meta">设备 ID：{{ detailDevice.id }}</text>
        <text v-if="detailDevice.location" class="hint detail-meta">部署位置：{{ detailDevice.location }}</text>
        <text v-if="detailDevice.description" class="hint detail-meta">备注：{{ detailDevice.description }}</text>
        <text class="hint measurement-note">水位为传感器 ADC 原始读数，尚未换算为实际高度。</text>
        <text v-if="!detailDevice.ownedByCurrentUser" class="shared-access-note">这是共享设备。你可以查看数据并发送设备控制指令；设备档案和共享名单由所有者管理。</text>
        <view v-if="detailDevice.lifecycleStatus !== 'ARCHIVED'" class="ds-actions">
          <button class="btn-primary ds-btn" :disabled="!canControl" @tap="startDevice">启动</button>
          <button class="btn-danger ds-btn" :disabled="!canControl" @tap="stopDevice">停止</button>
          <button class="btn-secondary ds-btn" :disabled="!canControl" @tap="readDevice">立即读取</button>
          <button class="btn-secondary ds-btn" :disabled="!canControl" @tap="queryDeviceStatus">查询状态</button>
        </view>
        <button class="btn-secondary ds-close" @tap="closeDetail">关闭</button>
      </view>
    </view>

    <!-- 注册或编辑设备档案；档案注册不会自动创建MQTT凭据。 -->
    <view v-if="formVisible" class="mask" @tap="closeForm">
      <view class="detail-sheet form-sheet" @tap.stop>
        <text class="ds-name">{{ editingDeviceId ? '编辑设备档案' : '注册设备档案' }}</text>
        <text v-if="!editingDeviceId" class="hint form-note">注册档案不会创建 MQTT 账号；设备仍需使用已配置的独立凭据连接。</text>
        <text class="field-label">设备 ID</text>
        <input v-if="!editingDeviceId" v-model="form.deviceId" class="field-input" maxlength="64" placeholder="字母、数字、下划线或短横线" />
        <text v-else class="field-readonly">{{ form.deviceId }}</text>
        <text class="field-label">设备名称</text>
        <input v-model="form.deviceName" class="field-input" maxlength="100" placeholder="请输入设备名称" />
        <text class="field-label">设备类型</text>
        <input v-model="form.deviceType" class="field-input" maxlength="50" placeholder="例如 ESP8266" />
        <text class="field-label">部署位置</text>
        <input v-model="form.location" class="field-input" maxlength="150" placeholder="可选" />
        <text class="field-label">备注</text>
        <textarea v-model="form.description" class="field-textarea" maxlength="500" placeholder="可选，最多500字" />
        <view class="enabled-row" @tap="form.enabled = !form.enabled">
          <text>启用设备</text><text>{{ form.enabled ? '是' : '否' }}</text>
        </view>
        <view class="form-actions">
          <button class="btn-secondary" @tap="closeForm">取消</button>
          <button class="btn-primary" :disabled="saving" @tap="submitDevice">{{ saving ? '保存中…' : '保存档案' }}</button>
        </view>
      </view>
    </view>

    <!-- 仅设备所有者可以查看和修改共享名单。 -->
    <view v-if="shareVisible" class="mask" @tap="closeShares">
      <view class="detail-sheet share-sheet" @tap.stop>
        <text class="ds-name">共享名单 · {{ shareDevice?.name }}</text>
        <text class="hint form-note">已授权账号可查看设备和历史数据，并发送控制指令。</text>
        <view class="share-entry">
          <input v-model="shareUsername" class="field-input" maxlength="100" placeholder="输入已注册账号名" />
          <button class="btn-primary share-add" :disabled="sharing" @tap="addShare">添加</button>
        </view>
        <view v-if="shareError" class="error-text">{{ shareError }}</view>
        <view v-for="username in sharedUsers" :key="username" class="share-row">
          <text>{{ username }}</text>
          <button class="btn-secondary action-btn" :disabled="sharing" @tap="removeShare(username)">撤销</button>
        </view>
        <EmptyState v-if="!sharedUsers.length && !shareError" text="尚未共享给其他账号" />
        <button class="btn-secondary ds-close" @tap="closeShares">完成</button>
      </view>
    </view>
  </view>
</template>

<script setup>
// 设备页：按模式展示设备、人工控制和命令回执。
import { ref, reactive, computed, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { session } from '../../utils/session.js'
import {
  live, refreshLive, issueCommand, commandStatus, parseServerTime, formatReading,
  listDeviceProfiles, createDeviceProfile, updateDeviceProfile, archiveDeviceProfile,
  restoreDeviceProfile, listDeviceShares, addDeviceShare, removeDeviceShare
} from '../../utils/live-store.js'
import LiveStatus from '../../components/LiveStatus.vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { formatDateTime } from '../../utils/format.js'

// 当前账号可访问的设备档案列表。
const devices = ref([])
const refreshing = ref(false)
const sending = ref(false)
const deviceError = ref('')
const includeArchived = ref(false)
const formVisible = ref(false)
const shareVisible = ref(false)
const saving = ref(false)
const sharing = ref(false)
const updatingDeviceId = ref('')
const receiptExpanded = ref(false)
const editingDeviceId = ref('')
const shareDevice = ref(null)
const shareUsername = ref('')
const sharedUsers = ref([])
const shareError = ref('')
const form = reactive({ deviceId: '', deviceName: '', deviceType: 'ESP8266', location: '', description: '', enabled: true })
let pageVisible = false
let refreshTimer = null
let refreshInFlight = false
let refreshQueued = false

// 查询设备档案并同步设备状态和命令回执。
async function refreshPage() {
  if (!session.token) { devices.value = []; return }
  clearTimeout(refreshTimer); refreshTimer = null
  if (refreshInFlight) { refreshQueued = true; refreshing.value = true; return }
  refreshInFlight = true; refreshing.value = true
  const generation = session.generation
  deviceError.value = ''
  try {
    const [records] = await Promise.all([listDeviceProfiles(includeArchived.value), refreshLive()])
    if (generation === session.generation) devices.value = records
  } catch (error) {
    if (generation === session.generation && !error.stale) deviceError.value = error.message || '设备档案读取失败'
  } finally {
    refreshInFlight = false
    if (generation !== session.generation) {
      if (session.token && pageVisible) { refreshQueued = false; refreshPage(); return }
      refreshing.value = false
      return
    }
    if (refreshQueued) {
      refreshQueued = false
      if (pageVisible && session.token) { refreshPage(); return }
    }
    refreshing.value = false
    if (pageVisible && session.token) refreshTimer = setTimeout(refreshPage, 10000)
  }
}

// 页面显示后立即同步，并按 Web 端周期每10秒刷新设备状态。
function startDevicePolling() { pageVisible = true; refreshPage() }

// 页面隐藏时停止自动刷新，避免后台持续请求。
function stopDevicePolling() { pageVisible = false; clearTimeout(refreshTimer); refreshTimer = null }

// 页面可见时刷新设备和控制回执。
onShow(startDevicePolling)
onHide(stopDevicePolling)

// 缺失上报时间显示占位文本。
function showTime(value) { return value ? formatDateTime(parseServerTime(value)) : '--' }

// 缺失信号强度时不附加误导性的 dBm 单位。
function formatSignal(value) { return value === null || value === undefined ? '--' : formatReading(value) + ' dBm' }

// 筛选条件列表
const filters = [
  { key: 'all', label: '全部' },
  { key: 'online', label: '在线' },
  { key: 'offline', label: '离线' }
]

// 当前筛选条件
const filter = ref('all')

// 按登录状态和筛选条件说明设备列表为空的原因。
const devicesEmptyText = computed(() => {
  if (!session.token) return '登录后查看账号可访问的设备'
  if (filter.value === 'online') return '当前没有在线设备'
  if (filter.value === 'offline') return '当前没有离线设备'
  return '当前账号暂无可访问设备'
})

// 默认收起较早命令回执，用户可展开当前账号最近的全部回执。
const displayedReceipts = computed(() => receiptExpanded.value ? live.commands : live.commands.slice(0, 10))

// 详情弹层中选中的设备
const detailId = ref(null)
// 从当前数据源定位详情，避免刷新后保留旧设备对象。
const detailDevice = computed(() => devices.value.find((item) => item.id === detailId.value) || null)
// 会话切换时关闭详情，旧请求不能解除新会话的发送锁。
watch(() => session.generation, () => {
  clearTimeout(refreshTimer); refreshTimer = null; refreshQueued = false
  devices.value = []; deviceError.value = ''; detailId.value = null; sending.value = false; saving.value = false; sharing.value = false; updatingDeviceId.value = ''; formVisible.value = false; shareVisible.value = false
  shareDevice.value = null; sharedUsers.value = []; shareUsername.value = ''
  refreshing.value = Boolean(refreshInFlight)
  if (session.token && pageVisible) refreshPage()
}, { flush: 'sync' })
// 离线、禁用、待回执与发送中的设备均不可再次控制。
const canControl = computed(() => {
  const d = detailDevice.value
  return d && d.status === 'online' && !sending.value && session.token && d.enabled !== false && d.lifecycleStatus !== 'ARCHIVED' && !live.commands.some((c) => c.deviceId === d.id && ['PENDING', 'DISPATCHED'].includes(c.status))
})

// 按筛选条件过滤设备列表
const filteredDevices = computed(() => {
  if (filter.value === 'all') return devices.value
  return devices.value.filter((d) => d.status === filter.value)
})

// 设置筛选条件
function setFilter(key) {
  filter.value = key
}

// 打开设备详情弹层
function openDetail(device) {
  detailId.value = device.id
}

// 关闭设备详情弹层
function closeDetail() {
  detailId.value = null
}

// 打开启动命令确认。
function startDevice() {
  confirmControl('start')
}

// 打开停止命令确认。
function stopDevice() {
  confirmControl('stop')
}

// 打开设备立即读取确认。
function readDevice() { confirmControl('read') }

// 打开设备状态查询确认。
function queryDeviceStatus() { confirmControl('status') }

// 二次确认后发送命令，保留设备回执的真实执行语义。
function confirmControl(command) {
  const device = detailDevice.value
  if (!device || !canControl.value) return
  const generation = session.generation
  const labels = { start: '启动', stop: '停止', read: '立即读取', status: '查询状态' }
  sending.value = true
  uni.showModal({
    title: '设备控制',
    content: '确认向「' + device.name + '」发送“' + labels[command] + '”指令？将操作真实设备。',
    success: async (res) => {
      try {
        if (!res.confirm || generation !== session.generation) return
        const receipt = await issueCommand(device.id, command)
        uni.showToast({ title: commandStatus(receipt.status), icon: 'none' })
      } catch (err) { if (!err.stale) uni.showToast({ title: err.message, icon: 'none' }) }
      finally { if (generation === session.generation) sending.value = false }
    },
    // 弹窗打开失败时释放操作锁。
    fail: () => { if (generation === session.generation) sending.value = false }
  })
}

// 重置设备注册或编辑表单。
function resetForm() {
  editingDeviceId.value = ''
  Object.assign(form, { deviceId: '', deviceName: '', deviceType: 'ESP8266', location: '', description: '', enabled: true })
}

// 打开新设备档案注册表单。
function openCreate() { resetForm(); formVisible.value = true }

// 使用现有设备档案填充编辑表单。
function openEdit(device) {
  editingDeviceId.value = device.id
  Object.assign(form, { deviceId: device.id, deviceName: device.name, deviceType: device.type, location: device.location, description: device.description, enabled: device.enabled !== false })
  formVisible.value = true
}

// 快捷启用或停用设备档案，不向硬件下发控制指令。
async function toggleEnabled(device) {
  if (updatingDeviceId.value) return
  const enabled = device.enabled === false
  updatingDeviceId.value = device.id
  try {
    await updateDeviceProfile(device.id, { deviceName: device.name, deviceType: device.type, location: device.location, description: device.description, enabled })
    uni.showToast({ title: enabled ? '设备已启用' : '设备已停用', icon: 'success' })
    await refreshPage()
  } catch (error) {
    if (!error.stale) uni.showToast({ title: error.message || '设备状态更新失败', icon: 'none' })
  } finally { updatingDeviceId.value = '' }
}

// 关闭设备档案表单。
function closeForm() { if (!saving.value) formVisible.value = false }

// 校验并保存设备档案，不触发MQTT或硬件操作。
async function submitDevice() {
  const deviceId = form.deviceId.trim()
  const deviceName = form.deviceName.trim()
  if (!editingDeviceId.value && !/^[A-Za-z0-9_-]{1,64}$/.test(deviceId)) return uni.showToast({ title: '设备ID格式无效', icon: 'none' })
  if (!deviceName) return uni.showToast({ title: '请输入设备名称', icon: 'none' })
  saving.value = true
  try {
    const payload = { deviceName, deviceType: form.deviceType.trim(), location: form.location.trim(), description: form.description.trim(), enabled: form.enabled }
    if (editingDeviceId.value) await updateDeviceProfile(editingDeviceId.value, payload)
    else await createDeviceProfile({ deviceId, ...payload })
    formVisible.value = false
    uni.showToast({ title: editingDeviceId.value ? '设备档案已更新' : '设备档案已注册', icon: 'success' })
    await refreshPage()
  } catch (error) {
    if (!error.stale) uni.showToast({ title: error.message || '设备档案保存失败', icon: 'none' })
  } finally { saving.value = false }
}

// 切换列表是否包含已归档设备。
function toggleArchived() { includeArchived.value = !includeArchived.value; refreshPage() }

// 二次确认后归档设备并保留其历史数据。
function archiveDevice(device) {
  uni.showModal({
    title: '归档设备', content: '归档“' + device.name + '”后会停止上报和远程控制，但历史数据仍会保留。',
    confirmText: '确认归档', confirmColor: '#B5473C',
    success: async (result) => {
      if (!result.confirm) return
      try { await archiveDeviceProfile(device.id); uni.showToast({ title: '设备已归档', icon: 'success' }); await refreshPage() }
      catch (error) { if (!error.stale) uni.showToast({ title: error.message || '归档失败', icon: 'none' }) }
    }
  })
}

// 恢复归档设备并刷新档案列表。
async function restoreDevice(device) {
  try { await restoreDeviceProfile(device.id); uni.showToast({ title: '设备已恢复', icon: 'success' }); await refreshPage() }
  catch (error) { if (!error.stale) uni.showToast({ title: error.message || '恢复失败', icon: 'none' }) }
}

// 打开设备共享名单并读取当前授权账号。
async function openShares(device) {
  shareDevice.value = device; sharedUsers.value = []; shareUsername.value = ''; shareError.value = ''; shareVisible.value = true
  try { sharedUsers.value = await listDeviceShares(device.id) || [] }
  catch (error) { if (!error.stale) shareError.value = error.message || '共享名单读取失败' }
}

// 将设备授权给一个已注册账号。
async function addShare() {
  const username = shareUsername.value.trim()
  if (!shareDevice.value || !username || sharing.value) return
  sharing.value = true; shareError.value = ''
  try { sharedUsers.value = await addDeviceShare(shareDevice.value.id, username) || []; shareUsername.value = ''; uni.showToast({ title: '共享名单已更新', icon: 'success' }) }
  catch (error) { if (!error.stale) shareError.value = error.message || '添加共享账号失败' }
  finally { sharing.value = false }
}

// 撤销指定账号的设备共享权限。
async function removeShare(username) {
  if (!shareDevice.value || sharing.value) return
  sharing.value = true; shareError.value = ''
  try { sharedUsers.value = await removeDeviceShare(shareDevice.value.id, username) || []; uni.showToast({ title: '共享权限已撤销', icon: 'success' }) }
  catch (error) { if (!error.stale) shareError.value = error.message || '撤销共享失败' }
  finally { sharing.value = false }
}

// 关闭共享名单弹层。
function closeShares() { if (!sharing.value) shareVisible.value = false }
</script>

<style lang="scss" scoped>
.receipt { display: flex; flex-direction: column; gap: 8rpx; padding: 20rpx 0; border-bottom: 1rpx solid $color-border; font-size: 26rpx; overflow-wrap: anywhere; }
.device-toolbar { display: flex; gap: 14rpx; margin: 0 $space-page 20rpx; }
.toolbar-btn { flex: 1; margin: 0; font-size: 25rpx; }
.receipt-toggle { width: 100%; margin: 14rpx 0 0; font-size: 24rpx; }
.owner-actions { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 20rpx; padding-top: 18rpx; border-top: 1rpx solid $color-border; }
.action-btn { margin: 0; padding: 0 18rpx; font-size: 23rpx; }
.archive-btn { color: $color-red; }
.error-text { display: block; margin: 0 $space-page 16rpx; color: $color-red; font-size: 24rpx; overflow-wrap: anywhere; }
.form-sheet .ds-name, .share-sheet .ds-name { display: block; margin-bottom: 12rpx; }
.form-note { display: block; margin: 0 0 18rpx; line-height: 1.5; }
.field-label { display: block; margin: 18rpx 0 8rpx; color: $color-text-secondary; font-size: 24rpx; }
.field-input, .field-textarea { width: 100%; box-sizing: border-box; padding: 18rpx 20rpx; border: 1rpx solid $color-border; border-radius: $radius-card; background: $color-card; color: $color-text; font-size: 26rpx; }
.field-textarea { min-height: 140rpx; }
.field-readonly { display: block; padding: 18rpx 20rpx; color: $color-text-secondary; font-size: 26rpx; }
.enabled-row, .share-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 0; border-bottom: 1rpx solid $color-border; font-size: 26rpx; }
.form-actions { display: flex; gap: 16rpx; margin-top: 24rpx; }
.form-actions button { flex: 1; margin: 0; }
.share-entry { display: flex; align-items: center; gap: 12rpx; margin: 14rpx 0; }
.share-entry .field-input { flex: 1; min-width: 0; }
.share-add { flex: 0 0 140rpx; margin: 0; }
.page {
  padding-bottom: 40rpx;
}
.filter-bar {
  display: flex;
  flex-direction: row;
  margin: 0 $space-page 20rpx;
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-pill;
  padding: 6rpx;
}
.filter-item {
  flex: 1;
  text-align: center;
  padding: 12rpx 0;
  border-radius: $radius-pill;
}
.filter-item.active {
  background-color: $color-primary;
}
.filter-text {
  font-size: 26rpx;
  color: $color-text-secondary;
}
.filter-item.active .filter-text {
  color: #FFFFFF;
}
.list {
  padding-bottom: 20rpx;
}
.device-card {
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 24rpx;
  margin: 0 $space-page 20rpx;
}
.dc-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.dc-name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  min-width: 0;
}
.dc-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $color-text;
}
.dc-type {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
.dc-location { margin-left: auto; padding-left: 10rpx; color: $color-text-light; font-size: 21rpx; text-align: right; }
.dc-metrics {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 20rpx;
}
.dc-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dc-metric-value {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-text;
}
.dc-metric-label {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $color-text-secondary;
}
.dc-bottom {
  margin-top: 20rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.measurement-note { display: block; margin-top: 16rpx; }
.detail-meta { display: block; margin-top: 10rpx; overflow-wrap: anywhere; }
.shared-access-note { display: block; margin-top: 20rpx; padding: 18rpx 20rpx; border-radius: $radius-card; background: $color-blue-soft; color: $color-blue; font-size: 24rpx; }
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.detail-sheet {
  width: 100%;
  background-color: $color-bg;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
  max-height: 85vh;
  overflow-y: auto;
}
.ds-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.ds-name {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: $color-text;
}
.ds-type {
  font-size: 24rpx;
  color: $color-text-secondary;
}
.ds-metrics {
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  margin: 32rpx 0;
}
.ds-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ds-metric-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $color-text;
}
.ds-metric-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
.ds-actions {
  display: flex;
  flex-direction: row;
  margin-top: 32rpx;
}
.ds-btn {
  flex: 1;
  margin: 0 8rpx;
}
.ds-close {
  margin-top: 16rpx;
}
</style>
