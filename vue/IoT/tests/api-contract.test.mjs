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

// 验证401会同步Pinia状态并跳转登录页，不再因内存状态残留返回仪表盘
test('未认证响应统一清理登录态并保留原页面', () => {
  const requestSource = readFileSync(resolve(projectRoot, 'src/api/request.js'), 'utf8')
  const mainSource = readFileSync(resolve(projectRoot, 'src/main.js'), 'utf8')
  const authApiSource = readFileSync(resolve(projectRoot, 'src/api/auth.js'), 'utf8')

  assert.match(requestSource, /export function setUnauthorizedHandler\(handler\)/)
  assert.match(requestSource, /unauthorizedHandler\?\.\(\)/)
  assert.doesNotMatch(requestSource, /window\.location\.hash\s*=\s*['"]#\/login['"]/)
  assert.match(mainSource, /authStore\.clearAuthentication\(\)/)
  assert.match(mainSource, /router\.replace\(\{ path: ['"]\/login['"], query: \{ redirect: currentRoute\.fullPath \} \}\)/)
  assert.match(authApiSource, /skipAuthRedirect:\s*true/)
})

// 验证路由切换复用首次认证结果，实时监控等页面不会重复请求/me
test('认证恢复结果在页面切换间复用', () => {
  const storeSource = readFileSync(resolve(projectRoot, 'src/stores/auth.js'), 'utf8')
  const routerSource = readFileSync(resolve(projectRoot, 'src/router/index.js'), 'utf8')

  assert.match(storeSource, /const initialized = ref\(false\)/)
  assert.match(storeSource, /if \(initialized\.value && !force\) return authenticated\.value/)
  assert.match(storeSource, /function clearAuthentication\(\)/)
  assert.match(routerSource, /await authStore\.restore\(isOAuthCallback\)/)
  assert.match(routerSource, /query: \{ redirect: to\.fullPath \}/)
})

// 验证跨站Cookie被浏览器阻止时使用页面内存令牌，且令牌不写入Web Storage
test('用户名登录提供仅当前页面有效的Bearer兼容认证', () => {
  const requestSource = readFileSync(resolve(projectRoot, 'src/api/request.js'), 'utf8')
  const storeSource = readFileSync(resolve(projectRoot, 'src/stores/auth.js'), 'utf8')
  const authUtilsSource = readFileSync(resolve(projectRoot, 'src/utils/auth.js'), 'utf8')

  assert.match(requestSource, /export function setMemoryAccessToken\(token\)/)
  assert.match(requestSource, /config\.headers\.Authorization = `Bearer \$\{memoryAccessToken\}`/)
  assert.match(storeSource, /setMemoryAccessToken\(res\.token\)/)
  assert.doesNotMatch(requestSource, /localStorage|sessionStorage/)
  assert.doesNotMatch(authUtilsSource, /setItem\(TOKEN_KEY/)
})

// 验证设备控制和AI发送逻辑没有直接执行页面跳转
test('设备控制和AI发送保持在当前业务页面', () => {
  const deviceViewSource = readFileSync(resolve(projectRoot, 'src/views/DeviceList.vue'), 'utf8')
  const chatViewSource = readFileSync(resolve(projectRoot, 'src/views/Chat.vue'), 'utf8')

  const sendCommandBody = deviceViewSource.match(/async function sendCommand[\s\S]*?\n\}/)?.[0] || ''
  const handleSendBody = chatViewSource.match(/async function handleSend[\s\S]*?\n\}/)?.[0] || ''
  assert.match(sendCommandBody, /controlDevice\(command, device\.deviceId\)/)
  assert.doesNotMatch(sendCommandBody, /router|dashboard/)
  assert.match(handleSendBody, /sendMessage\(history\)/)
  assert.doesNotMatch(handleSendBody, /router|dashboard/)
})
