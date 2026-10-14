// miniapp/scripts/verify.mjs
// 静态校验脚本：校验 JSON 结构、路由文件与资源存在、tabBar 有效性、
// 相对导入可解析、导航目标合法，并检查禁用依赖（不伪装为微信编译）
// 仅使用 Node 内置模块运行：node miniapp/scripts/verify.mjs

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const miniappRoot = resolve(scriptDir, '..')

let failures = 0
const errors = []

// 记录校验结果并统计失败数
function check(cond, msg) {
  if (cond) {
    console.log('  PASS  ' + msg)
  } else {
    console.log('  FAIL  ' + msg)
    failures += 1
    errors.push(msg)
  }
}

// 读取文本文件（不存在时返回 null）
function readText(relPath) {
  const abs = join(miniappRoot, relPath)
  if (!existsSync(abs)) return null
  return readFileSync(abs, 'utf8')
}

// 递归收集目录下指定扩展名的文件相对路径
function walk(dir, exts, base = miniappRoot) {
  let out = []
  let entries = []
  try {
    entries = readdirSync(dir)
  } catch (e) {
    return out
  }
  for (const name of entries) {
    const abs = join(dir, name)
    const rel = resolve(abs).replace(resolve(base) + '/', '')
    if (name === 'unpackage' || name === 'node_modules' || name === 'dist') continue
    if (statSync(abs).isDirectory()) {
      out = out.concat(walk(abs, exts, base))
    } else if (exts.some((ext) => name.endsWith(ext))) {
      out.push(rel)
    }
  }
  return out
}

// 提取 .vue 文件中所有 <script> 块内容
function extractScriptBlocks(content) {
  const blocks = []
  const re = /<script[^>]*>([\s\S]*?)<\/script>/g
  let m
  while ((m = re.exec(content)) !== null) {
    blocks.push(m[1])
  }
  return blocks.join('\n')
}

// 从源码中提取 import 的模块说明符
function extractImportSpecifiers(src) {
  const out = []
  const re = /import\s+(?:[^'"]*?\s+from\s+)?['"]([^'"]+)['"]/g
  let m
  while ((m = re.exec(src)) !== null) {
    out.push(m[1])
  }
  return out
}

// 校验一个 .vue 页面文件的模板与脚本存在
function checkVueFile(relPath) {
  const content = readText(relPath)
  if (content == null) {
    check(false, '页面文件存在: ' + relPath)
    return
  }
  check(true, '页面文件存在: ' + relPath)
  check(/<template>[\s\S]*<\/template>/.test(content), '包含 <template>: ' + relPath)
  check(/<script[^>]*>[\s\S]*<\/script>/.test(content), '包含 <script>: ' + relPath)
}

console.log('=== 1. 基础文件与 JSON 结构 ===')

const requiredFiles = [
  'main.js',
  'index.html',
  'App.vue',
  'manifest.json',
  'pages.json',
  'uni.scss',
  'config/index.js',
  'utils/demo-store.js'
]
for (const f of requiredFiles) {
  check(readText(f) != null, '必需文件存在: ' + f)
}

let pagesJson = null
let manifestJson = null
try {
  pagesJson = JSON.parse(readText('pages.json') || '')
  check(true, 'pages.json 为合法 JSON')
} catch (e) {
  check(false, 'pages.json 为合法 JSON: ' + e.message)
}
try {
  manifestJson = JSON.parse(readText('manifest.json') || '')
  check(true, 'manifest.json 为合法 JSON')
} catch (e) {
  check(false, 'manifest.json 为合法 JSON: ' + e.message)
}

console.log('=== 2. manifest 配置 ===')
if (manifestJson) {
  check(manifestJson.vueVersion === '3', "manifest.json vueVersion 为 '3'")
  const mp = manifestJson['mp-weixin']
  check(!!mp && (!mp.appid || /^wx[0-9a-f]{16}$/i.test(mp.appid)), '微信 AppID 留空或符合格式（不校验账号归属）')
}

console.log('=== 3. pages 路由 ===')
const pageSet = new Set()
if (pagesJson && Array.isArray(pagesJson.pages)) {
  check(pagesJson.pages.length === 13, '页面总数为 13（实际 ' + pagesJson.pages.length + '）')
  for (const p of pagesJson.pages) {
    const path = p.path
    if (pageSet.has(path)) {
      check(false, '页面路径唯一: ' + path)
    } else {
      pageSet.add(path)
      check(true, '页面路径唯一: ' + path)
    }
    checkVueFile(path + '.vue')
  }
  check(pagesJson.pages[0] && pagesJson.pages[0].path === 'pages/overview/overview', '首页为 pages/overview/overview')
} else {
  check(false, 'pages.json.pages 为数组')
}

console.log('=== 4. tabBar 有效性 ===')
const tabBar = pagesJson && pagesJson.tabBar
if (tabBar && Array.isArray(tabBar.list)) {
  check(tabBar.list.length >= 2 && tabBar.list.length <= 5, 'tabBar 项数量在 2~5（实际 ' + tabBar.list.length + '）')
  for (const item of tabBar.list) {
    check(!!item.pagePath, 'tabBar 项 pagePath 非空')
    check(!!item.text, 'tabBar 项 text 非空: ' + item.text)
    check(pageSet.has(item.pagePath), 'tabBar 页存在于 pages: ' + item.pagePath)
    checkVueFile(item.pagePath + '.vue')
  }
} else {
  check(false, 'tabBar.list 为数组')
}

console.log('=== 5. 组件存在性 ===')
const components = ['PageHeader', 'MetricCard', 'EmptyState', 'StatusTag', 'BarTrend']
for (const c of components) {
  check(existsSync(join(miniappRoot, 'components', c + '.vue')), '组件存在: components/' + c + '.vue')
}

console.log('=== 6. 相对导入可解析 ===')
const vueFiles = walk(join(miniappRoot, 'pages'), ['.vue']).concat(walk(join(miniappRoot, 'components'), ['.vue']))
const jsFiles = walk(miniappRoot, ['.js'])
const allCodeFiles = vueFiles.concat(jsFiles)
let importCount = 0
for (const rel of allCodeFiles) {
  const abs = join(miniappRoot, rel)
  const raw = readFileSync(abs, 'utf8')
  const src = rel.endsWith('.vue') ? extractScriptBlocks(raw) : raw
  const specifiers = extractImportSpecifiers(src)
  for (const spec of specifiers) {
    if (!spec.startsWith('.')) continue // 跳过 vue 等内置/包导入
    importCount += 1
    let target = resolve(dirname(abs), spec)
    let ok = existsSync(target)
    if (!ok && !/\.(js|vue|json)$/.test(spec)) {
      ok = existsSync(target + '.js') || existsSync(target + '.vue') || existsSync(target + '.json')
    }
    check(ok, '可解析导入: ' + rel + ' -> ' + spec)
  }
}
check(importCount > 0, '检测到相对导入（共 ' + importCount + ' 处）')

console.log('=== 7. 导航目标合法 ===')
const forbiddenNav = []
for (const rel of vueFiles) {
  const raw = readText(rel)
  const navRe = /uni\.(navigateTo|switchTab)\s*\(\s*\{\s*url:\s*['"]([^'"]+)['"]/g
  let m
  while ((m = navRe.exec(raw)) !== null) {
    const url = m[2].replace(/^\//, '')
    if (!pageSet.has(url)) {
      forbiddenNav.push(rel + ' -> ' + url)
    }
    const isTab = (tabBar?.list || []).some((item) => item.pagePath === url)
    if ((m[1] === 'switchTab') !== isTab) {
      forbiddenNav.push(rel + ' 使用错误的导航方式 -> ' + url)
    }
  }
  // 工作台使用数据驱动的入口，同样检查这些字面量路由。
  const entryRe = /url:\s*['"]\/([^'"]+)['"]/g
  while ((m = entryRe.exec(raw)) !== null) {
    if (!pageSet.has(m[1])) forbiddenNav.push(rel + ' -> ' + m[1])
  }
}
check(forbiddenNav.length === 0, '所有导航目标均在 pages 中' + (forbiddenNav.length ? '（缺失: ' + forbiddenNav.join('; ') + '）' : ''))

console.log('=== 8. 禁用依赖与请求边界 ===')
const forbiddenTokens = ['window.', 'document.', 'localStorage', 'axios', 'element-plus', 'XMLHttpRequest', 'fetch(', 'wx.request', 'console.log']
const allSourceFiles = allCodeFiles.concat(['main.js', 'App.vue'])
const violations = []
for (const rel of allSourceFiles) {
  const raw = readText(rel)
  if (raw == null) continue
  if (raw.includes('uni.request') && rel !== 'api/request.js') violations.push(rel + ' 在专用请求模块外调用 uni.request')
  for (const token of forbiddenTokens) {
    if (raw.includes(token)) {
      violations.push(rel + ' 含 ' + token)
    }
  }
}
check(violations.length === 0, '无禁用依赖且联网仅通过专用模块' + (violations.length ? '（' + violations.join('; ') + '）' : ''))

console.log('=== 9. 定时器卸载检查 ===')
const timerFiles = []
for (const rel of vueFiles) {
  const raw = readText(rel)
  if (raw.includes('setInterval')) {
    const hasClear = raw.includes('clearInterval')
    timerFiles.push(rel + (hasClear ? '（已清理）' : '（未清理）'))
    check(hasClear, 'setInterval 存在清理逻辑: ' + rel)
  }
}
if (timerFiles.length === 0) console.log('  PASS  无 setInterval 使用')

console.log('')
console.log('=== 汇总 ===')
console.log('失败项: ' + failures)
if (failures > 0) {
  console.log('未通过项:')
  for (const e of errors) console.log('  - ' + e)
  process.exit(1)
} else {
  console.log('全部校验通过（本脚本为静态结构校验，不代表微信实际编译）')
}
