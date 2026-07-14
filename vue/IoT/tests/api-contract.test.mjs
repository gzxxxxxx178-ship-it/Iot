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
