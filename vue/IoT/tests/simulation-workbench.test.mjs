import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const projectRoot = resolve(import.meta.dirname, '..')

// 验证路由中存在 /simulation
test('路由新增 /simulation 且不影响原有13条路由', () => {
  const source = readFileSync(resolve(projectRoot, 'src/router/index.js'), 'utf8')
  assert.match(source, /path:\s*['"]\/simulation['"]/)
  assert.match(source, /Simulation\.vue/)
  // 原路由数：/ + /login + /register + /oauth-callback + /dashboard + /monitor + /devices + /history + /alarm + /automation + /chat + /pay + /screen = 13条 + 重定向 = 14 个路由记录
  const pathMatches = source.match(/path:\s*['"]\/[^'"]*['"]/g) || []
  assert.equal(pathMatches.length, 15, '应有14条路由（含重定向）加新增/simulation共15条')
})

// 验证侧栏菜单包含仿真验证入口
test('侧栏菜单新增仿真验证入口', () => {
  const source = readFileSync(resolve(projectRoot, 'src/components/common/SideMenu.vue'), 'utf8')
  assert.match(source, /\/simulation/)
  assert.match(source, /仿真验证/)
})

// 验证页面始终显示仿真真实性警示标签
test('仿真页面包含仿真数据不代表田间实测的警示标志', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/Simulation.vue'), 'utf8')
  assert.match(source, /不代表田间实测/)
  assert.match(source, /仿真数据/)
})

// 验证页面没有"田间实测"肯定表述（警示语中的否定表述允许）
test('仿真页面不包含田间实测的肯定表述', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/Simulation.vue'), 'utf8')
  // 移除警示语（包含否定的），检查其余部分
  const withoutWarning = source.replace(/不代表田间实测/g, '')
  assert.doesNotMatch(withoutWarning, /田间实测/)
})

// 验证2秒轮询及unmount清理
test('仿真页面配置2秒间隔轮询并在组件卸载时清理定时器', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/Simulation.vue'), 'utf8')
  assert.match(source, /setInterval\(poll,\s*2000\)/)
  assert.match(source, /unmounted/i)
  assert.match(source, /clearInterval/)
})

// 验证API路径正确（deviceId和id使用encodeURIComponent编码）
test('仿真API路径使用encodeURIComponent编码URL参数', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.match(source, /encodeURIComponent/)
})

// 验证API直接返回request调用，没有二次解包
test('仿真API不执行二次响应解包', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.doesNotMatch(source, /\.then\s*\(\s*\(?\s*(?:res|r)\s*\)?\s*=>\s*(?:res|r)\.data/)
})

// 验证遥测API路径符合后端契约
test('仿真遥测接口路径匹配后端契约', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.match(source, /\/api\/simulation\/telemetry\/latest/)
  assert.match(source, /\/api\/simulation\/telemetry\/history/)
})

// 验证规则CRUD路径符合后端契约
test('仿真规则CRUD接口路径匹配后端契约', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.match(source, /\/api\/simulation\/rules['"]/)
  assert.match(source, /\/api\/simulation\/rules\/\$\{encodeURIComponent/)
})

// 验证报警接口路径符合后端契约
test('仿真报警接口路径匹配后端契约', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.match(source, /\/api\/simulation\/alarms/)
  assert.match(source, /\/api\/simulation\/alarms\/\$\{encodeURIComponent\(id\)\}\/acknowledge/)
})

// 验证命令接口路径符合后端契约
test('仿真命令接口路径匹配后端契约', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/simulation.js'), 'utf8')
  assert.match(source, /\/api\/simulation\/commands\/pending/)
})

// 验证页面包含命令仅供MATLAB轮询的说明
test('仿真页面提示命令仅供MATLAB轮询不发布到真实MQTT设备', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/Simulation.vue'), 'utf8')
  assert.match(source, /不发布到真实MQTT设备/)
})
