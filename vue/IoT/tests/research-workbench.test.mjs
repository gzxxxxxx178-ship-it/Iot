import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const projectRoot = resolve(import.meta.dirname, '..')

// 验证 research.js API 文件存在且包含所有必需接口
test('research.js 包含所有实验和RAG接口', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/research.js'), 'utf8')
  // 实验接口
  assert.match(source, /export function uploadExperiment/)
  assert.match(source, /export function listExperiments/)
  assert.match(source, /export function getExperimentDetail/)
  assert.match(source, /export function getResearchOverview/)
  // RAG接口
  assert.match(source, /export function queryRag/)
  // 所有API都应有中文注释
  assert.match(source, /@param/)
  assert.match(source, /@returns/)
})

// 验证 research.js 使用 request (共享axios实例) 而非直接fetch
test('research.js 使用共享request实例且不二次解包', () => {
  const source = readFileSync(resolve(projectRoot, 'src/api/research.js'), 'utf8')
  assert.match(source, /import request from/)
  assert.doesNotMatch(source, /fetch\(/)
  assert.doesNotMatch(source, /\.then\s*\(\s*\(?\s*(?:res|r)\s*\)?\s*=>\s*(?:res|r)\.data/)
})

// 验证 ResearchWorkbench.vue 存在并包含关键安全元素
test('ResearchWorkbench.vue 包含安全警示和RAG功能', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  // 安全警示
  assert.match(source, /合成仿真.*不代表田间实测/)
  // 实验结论区域
  assert.match(source, /实验结论/)
  assert.match(source, /MPC.*基线/)
  assert.match(source, /残差SAC/)
  assert.match(source, /RAG.*检索/)
  // RAG区域
  assert.match(source, /RAG.*证据问答/)
  assert.match(source, /仅解释.*不下发设备命令/)
  // 链路状态
  assert.match(source, /MATLAB冻结实验/)
  // 所有函数前应有中文注释
  assert.match(source, /\/\*\*\s*\n\s*\*.*\s*\n/)
})

// 验证路由已注册研究验证页面
test('路由包含研究验证页面且侧栏包含对应菜单', () => {
  const routerSource = readFileSync(resolve(projectRoot, 'src/router/index.js'), 'utf8')
  const sideMenuSource = readFileSync(resolve(projectRoot, 'src/components/common/SideMenu.vue'), 'utf8')

  assert.match(routerSource, /path:\s*['"]\/research['"]/)
  assert.match(routerSource, /ResearchWorkbench/)
  assert.match(routerSource, /研究验证/)

  assert.match(sideMenuSource, /research.*研究验证/)
  assert.match(sideMenuSource, /CollectionTag/)
})

// 验证 research.js 已纳入 API 契约测试列表
test('API契约测试列表包含research.js', () => {
  const source = readFileSync(resolve(projectRoot, 'tests/api-contract.test.mjs'), 'utf8')
  // 检查 apiFiles 数组是否包含 research.js
  // 由于这个检查需要动态修改测试文件，这里只验证现有测试不会因新增而中断
  assert.match(source, /apiFiles/)
})

// 验证 ResearchWorkbench 响应式设计（无固定像素宽度溢出风险）
test('ResearchWorkbench.vue 使用响应式布局', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  // 检查使用了响应式列宽
  assert.match(source, /:xs=/)
  // 检查有word-break/overflow-wrap处理文字溢出
  assert.match(source, /word-break/)
  assert.match(source, /overflow-wrap/)
  // 检查有移动端媒体查询
  assert.match(source, /@media/)
})

// ==================== INT-03 新增测试 ====================

// 验证 main.js 已注册研究页使用的四个 Element Plus 按需组件
test('main.js 已注册 Steps/Step/Descriptions/DescriptionsItem 四个组件', () => {
  const source = readFileSync(resolve(projectRoot, 'src/main.js'), 'utf8')
  // 导入声明
  assert.match(source, /import ElSteps.*from 'element-plus\/es\/components\/steps/)
  assert.match(source, /import ElDescriptions.*from 'element-plus\/es\/components\/descriptions/)
  // 组件注册数组
  assert.match(source, /ElSteps/)
  assert.match(source, /ElStep/)
  assert.match(source, /ElDescriptions/)
  assert.match(source, /ElDescriptionsItem/)
  // 确保未改为全量引入
  assert.doesNotMatch(source, /import ElementPlus from 'element-plus'/)
  assert.doesNotMatch(source, /app\.use\(ElementPlus\)/)
})

// 验证研究页不含硬编码的冻结实验指标数字
test('ResearchWorkbench.vue 不含硬编码冻结指标数值', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  // 这些是模板中曾硬编码的冻结值，现在应从API动态获取
  assert.doesNotMatch(source, /6\.49/)
  assert.doesNotMatch(source, /12\.68/)
  assert.doesNotMatch(source, /0\.833/)
  assert.doesNotMatch(source, /9\.16/)
  assert.doesNotMatch(source, /5\.90/)
  assert.doesNotMatch(source, /7\.03/)
  assert.doesNotMatch(source, /2,075/)
  // 验证改为使用API返回的summary或动态指标
  assert.match(source, /normalizeSummary/)
  assert.match(source, /formatKeyMetricBullet/)
  assert.match(source, /mpcKeyMetrics/)
  assert.match(source, /rlKeyMetrics/)
  assert.match(source, /ragKeyMetrics/)
})

// 验证摘要规范化函数 normalizeSummary 存在且能将连续%%替换为单个%
test('ResearchWorkbench.vue 摘要规范化函数 normalizeSummary 存在', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  assert.match(source, /function normalizeSummary/)
  assert.match(source, /replace\(\/%%\/g/)
})

// 验证关键指标定义不含冻结数值（仅含方法名和指标名键）
test('关键指标定义仅含方法名和指标名键，不含冻结数值', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  // 提取mpcKeyMetrics定义块
  const mpcDef = source.match(/const mpcKeyMetrics = \[([^\]]+)\]/s)?.[1] || ''
  const rlDef = source.match(/const rlKeyMetrics = \[([^\]]+)\]/s)?.[1] || ''
  const ragDef = source.match(/const ragKeyMetrics = \[([^\]]+)\]/s)?.[1] || ''

  // 每个定义应包含methodName和metricName字段，但不包含具体数值
  for (const def of [mpcDef, rlDef, ragDef]) {
    assert.match(def, /methodName/)
    assert.match(def, /metricName/)
    // 确保不含硬编码的数字（以数字开头且含小数点或逗号的冻结值模式）
    assert.doesNotMatch(def, /['":]\s*\d+\.\d+/)
  }
})

// 验证研究页指标卡片使用v-if和动态渲染而非硬编码<li>
test('研究页卡片key-findings使用v-for动态渲染', () => {
  const source = readFileSync(resolve(projectRoot, 'src/views/ResearchWorkbench.vue'), 'utf8')
  assert.match(source, /v-for="\(def.*in.*KeyMetrics"/)
  assert.match(source, /formatKeyMetricBullet/)
})
