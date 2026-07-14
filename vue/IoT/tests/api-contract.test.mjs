import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const projectRoot = resolve(import.meta.dirname, '..')
const apiFiles = [
  'alarm.js',
  'auth.js',
  'chat.js',
  'dashboard.js',
  'device.js',
  'pay.js',
]

// 验证统一响应拦截器负责返回 ApiResponse.data
test('响应拦截器统一解包业务数据', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/request.js'), 'utf8')
  assert.match(source, /return body\.data/)
})

// 验证业务 API 不会对已经解包的数据再次访问 res.data
test('业务 API 不执行二次响应解包', () => {
  for (const file of apiFiles) {
    const source = readFileSync(resolve(projectRoot, 'src/api', file), 'utf8')
    assert.doesNotMatch(source, /\.then\s*\(\s*\(?\s*(?:res|r)\s*\)?\s*=>\s*(?:res|r)\.data/)
  }
})

// 验证历史数据导出由后端按完整筛选条件生成CSV文件
test('历史数据CSV导出使用Blob响应且不依赖当前分页', () => {
  const apiSource = readFileSync(resolve(projectRoot, 'src/api/device.js'), 'utf8')
  const viewSource = readFileSync(resolve(projectRoot, 'src/views/History.vue'), 'utf8')
  assert.match(apiSource, /request\.get\(['"]\/esp\/history\/export['"],\s*\{\s*params,\s*responseType:\s*['"]blob['"]\s*\}\)/)
  assert.match(viewSource, /exportSensorHistoryCsv\(params\)/)
  assert.match(viewSource, /start:\s*toLocalIsoString\(dateRange\.value\[0\]\)/)
  assert.doesNotMatch(viewSource, /tableData\.value\.map\([^)]*CSV/)
})

// 验证设备管理使用独立档案接口并以归档替代历史数据物理删除
test('设备管理具备注册编辑归档恢复接口', () => {
  const apiSource = readFileSync(resolve(projectRoot, 'src/api/device.js'), 'utf8')
  const viewSource = readFileSync(resolve(projectRoot, 'src/views/DeviceList.vue'), 'utf8')
  assert.match(apiSource, /request\.post\(['"]\/api\/devices['"],\s*data\)/)
  assert.match(apiSource, /request\.put\(`\/api\/devices\/\$\{encodeURIComponent\(deviceId\)\}`/)
  assert.match(apiSource, /request\.delete\(`\/api\/devices\/\$\{encodeURIComponent\(deviceId\)\}`/)
  assert.match(apiSource, /\/restore`/)
  assert.match(viewSource, /历史数据会继续保留/)
})
