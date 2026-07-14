<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { exportSensorHistoryCsv, getDeviceList, getSensorHistoryPage } from '../api/device'
import { formatTime, formatDateTime } from '../utils/format'
import TempHumChart from '../components/charts/TempHumChart.vue'

const dateRange = ref([])
const tableData = ref([])
const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const loading = ref(false)
const exporting = ref(false)
const deviceId = ref('')
const devices = ref([])

// 分页状态
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)

// 将日期数字补齐为两位文本
function padDatePart(value) {
  return String(value).padStart(2, '0')
}

// 将浏览器本地时间序列化为与后端LocalDateTime一致的ISO文本
function toLocalIsoString(date) {
  return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}`
    + `T${padDatePart(date.getHours())}:${padDatePart(date.getMinutes())}:${padDatePart(date.getSeconds())}`
}

// 将查询结果构建为图表数据序列
function buildChartData(data) {
  timeLabels.value = []
  tempSeries.value = []
  humSeries.value = []
  data.filter((item) => item.qualityValid !== false).forEach((item) => {
    timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
    tempSeries.value.push(item.temperature)
    humSeries.value.push(item.humidity)
  })
}

// 按时间范围分页查询传感器历史
async function query() {
  if (!dateRange.value?.length) return
  loading.value = true
  try {
    const params = {
      start: toLocalIsoString(dateRange.value[0]),
      end: toLocalIsoString(dateRange.value[1]),
      page: currentPage.value - 1, // Element Plus 页码从1开始，后端从0开始
      size: pageSize.value,
    }
    if (deviceId.value) params.deviceId = deviceId.value
    const result = await getSensorHistoryPage(params)
    // ApiResponse 拦截器已解包 → result = {content, page, size, totalElements, totalPages}
    tableData.value = result.content || []
    totalElements.value = result.totalElements || 0
    buildChartData(result.content || [])
  } catch {} finally {
    loading.value = false
  }
}

// 切换历史数据页码并重新查询
function onPageChange(page) {
  currentPage.value = page
  query()
}

// 切换每页条数后返回第一页重新查询
function onSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  query()
}

// 从后端导出所选设备与时间范围内的完整CSV而非当前分页
async function exportCSV() {
  if (!dateRange.value?.length) {
    ElMessage.warning('请先选择导出时间范围')
    return
  }
  exporting.value = true
  try {
    const params = {
      start: toLocalIsoString(dateRange.value[0]),
      end: toLocalIsoString(dateRange.value[1]),
    }
    if (deviceId.value) params.deviceId = deviceId.value
    const blob = await exportSensorHistoryCsv(params)
    if (blob.type?.includes('application/json')) {
      const body = JSON.parse(await blob.text())
      throw new Error(body.message || '导出失败')
    }
    downloadBlob(blob)
    ElMessage.success('CSV导出完成')
  } catch (error) {
    ElMessage.error(error.message || 'CSV导出失败')
  } finally {
    exporting.value = false
  }
}

// 将后端返回的CSV Blob保存为本地文件
function downloadBlob(blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `sensor-history-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

// 加载历史筛选器使用的设备列表
async function loadDevices() {
  try {
    devices.value = await getDeviceList() || []
  } catch {
    devices.value = []
  }
}

// 默认查询最近 24 小时
onMounted(() => {
  const end = new Date()
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  dateRange.value = [start, end]
  loadDevices()
  query()
})
</script>

<template>
  <div class="history">
    <div class="page-header">
      <div>
        <h1>历史数据</h1>
        <p>查询和导出传感器历史数据</p>
      </div>
      <div class="header-actions">
        <el-select v-model="deviceId" clearable placeholder="全部设备" class="device-filter">
          <el-option
            v-for="device in devices"
            :key="device.deviceId"
            :label="device.deviceId"
            :value="device.deviceId"
          />
        </el-select>
        <!-- 时间范围选择器 -->
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm"
        />
        <el-button type="primary" @click="query" :loading="loading">查询</el-button>
        <el-button :icon="Download" @click="exportCSV" :loading="exporting">导出 CSV</el-button>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="chart-wrap" v-if="tableData.length">
      <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="300px" />
    </div>
    <el-empty v-else description="暂无数据，请调整时间范围" />

    <!-- 数据表格 + 分页 -->
    <el-card v-if="tableData.length" style="margin-top: 1.5rem;">
      <el-table :data="tableData" max-height="450" stripe size="small">
        <el-table-column prop="deviceId" label="设备 ID" width="140" />
        <el-table-column prop="temperature" label="温度 (°C)" width="120" />
        <el-table-column prop="humidity" label="湿度 (%)" width="120" />
        <el-table-column prop="water" label="水位" width="100" />
        <el-table-column prop="rssi" label="RSSI (dBm)" width="120" />
        <el-table-column label="联动" width="80">
          <template #default="{ row }">
            <el-tag :type="row.linkage ? 'success' : 'info'" size="small">{{ row.linkage ? 'ON' : 'OFF' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendCount" label="发送次数" width="100" />
        <el-table-column label="数据质量" width="110">
          <template #default="{ row }">
            <el-tooltip v-if="row.qualityValid === false" :content="row.qualityIssues || '超出有效范围'">
              <el-tag type="danger" size="small">异常</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.qualityValid === true" type="success" size="small">有效</el-tag>
            <el-tag v-else type="info" size="small">未评估</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.serverReceivedTime || row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页组件 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }
.header-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }
.device-filter { width: 150px; }

.chart-wrap {
  background: rgba(30, 41, 59, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 1rem;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
  padding-top: 0.5rem;
}
</style>
