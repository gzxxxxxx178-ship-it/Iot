import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import vm from 'node:vm'
import test from 'node:test'
import axios from 'axios'

const root = resolve(import.meta.dirname, '..')
const repositoryRoot = resolve(root, '../..')

// 在独立上下文加载真实ES模块；仅替换网络/组件生命周期依赖，不改写被测源码。
async function load(entry, { env = {}, globals = {}, modules = {} } = {}) {
  const context = vm.createContext({ URL, Request, Response,
    window: { location: { origin: 'https://work.example' } }, ...globals })
  const cache = new Map()
  // 缓存模块实例，按实际相对导入递归链接。
  function moduleAt(path) {
    if (cache.has(path)) return cache.get(path)
    let module
    if (Object.hasOwn(modules, path)) {
      const values = modules[path]
      module = new vm.SyntheticModule(Object.keys(values), function () {
        for (const [key, value] of Object.entries(values)) this.setExport(key, value)
      }, { context, identifier: path })
    } else {
      module = new vm.SourceTextModule(readFileSync(path, 'utf8'), { context,
        identifier: path, initializeImportMeta(meta) { meta.env = env } })
    }
    cache.set(path, module)
    return module
  }
  const module = moduleAt(entry)
  await module.link((specifier, parent) => moduleAt(specifier.startsWith('.')
    ? resolve(dirname(parent.identifier), `${specifier}.js`) : specifier))
  await module.evaluate()
  return module.namespace
}

const cases = [
  ['默认开发', { DEV: true }, 'http://localhost:8080', 'ws://localhost:8080'],
  ['空白开发配置', { DEV: true, VITE_API_BASE_URL: ' ', VITE_WS_BASE_URL: '' },
    'http://localhost:8080', 'ws://localhost:8080'],
  ['生产同源', {}, 'https://work.example', 'wss://work.example'],
  ['独立API及尾斜杠', { VITE_API_BASE_URL: ' https://api.example/// ' },
    'https://api.example', 'wss://api.example'],
  ['相对代理前缀', { VITE_API_BASE_URL: '/gateway/' },
    'https://work.example/gateway', 'wss://work.example/gateway'],
  ['显式WS前缀', { VITE_API_BASE_URL: 'https://api.example/', VITE_WS_BASE_URL: 'wss://live.example/edge/' },
    'https://api.example', 'wss://live.example/edge'],
  ['显式HTTP实时地址', { VITE_WS_BASE_URL: 'https://live.example/' },
    'https://work.example', 'wss://live.example'],
  ['相对WS地址', { VITE_WS_BASE_URL: '/realtime/' },
    'https://work.example', 'wss://work.example/realtime'],
]

for (const [name, env, api, ws] of cases) {
  test(`${name}：真实请求层领取票据后连接对应WebSocket地址`, async () => {
    const requests = [], sockets = [], timers = [], cleanups = []
    let ticketCount = 0
    const ticket = 'a+b /?=&中'
    const modules = {
      axios: { default: { create(config) {
        // 保留真实axios序列化和响应拦截器，仅用离线adapter代替HTTP传输。
        return axios.create({ ...config, adapter: async (request) => {
          requests.push({ url: axios.getUri(request), method: request.method,
            credentials: request.withCredentials })
          return { status: 200, headers: {}, config: request,
            data: { code: 200, data: { ticket: ticket + ++ticketCount } } }
        } })
      } } },
      vue: { ref: (value) => ({ value }), onUnmounted: (fn) => cleanups.push(fn) },
      [resolve(root, 'src/utils/message.js')]: { ElMessage: { error: assert.fail } },
      [resolve(root, 'src/utils/auth.js')]: { removeToken() {}, removeUsername() {} },
    }
    const globals = {
      WebSocket: class {
        constructor(url) { this.url = url; sockets.push(this) }
        close() { this.onclose?.() }
      },
      setTimeout(fn, delay) { const timer = { fn, delay }; timers.push(timer); return timer },
      clearTimeout() {},
    }
    const namespace = await load(resolve(root, 'src/composables/useWebSocket.js'), { env, modules, globals })
    const messages = []
    const connection = namespace.useWebSocket((message) => messages.push(message))
    // 等待真实Promise链：请求拦截器、adapter、响应解包、composable连接。
    await new Promise(setImmediate)
    assert.deepEqual(requests, [{ url: `${api}/api/ws/ticket`, method: 'post', credentials: true }])
    assert.equal(sockets[0].url, `${ws}/ws/sensor?ticket=${encodeURIComponent(ticket + '1')}`)
    sockets[0].onopen()
    assert.equal(connection.status.value, 'online')
    sockets[0].onmessage({ data: '{"temperature":22}' })
    assert.equal(messages[0].temperature, 22)
    sockets[0].onclose()
    assert.equal(timers[0].delay, 5000)
    timers[0].fn()
    await new Promise(setImmediate)
    assert.equal(requests.length, 2)
    assert.equal(sockets[1].url, `${ws}/ws/sensor?ticket=${encodeURIComponent(ticket + '2')}`)
    cleanups[0]()
    assert.equal(timers.length, 1, '卸载后不再安排重连')
  })
}

test('配置中非法协议、凭据、查询串和片段立即拒绝', async () => {
  const { resolveBackendUrls } = await load(resolve(root, 'src/utils/backendUrls.js'))
  for (const value of ['javascript:alert(1)', 'ftp://example.com', 'https://u:p@api.example',
    'https://api.example?token=secret', '/gateway#part']) {
    for (const key of ['VITE_API_BASE_URL', 'VITE_WS_BASE_URL']) {
      assert.throws(() => resolveBackendUrls({ [key]: value }, 'https://work.example'))
    }
  }
  assert.throws(() => resolveBackendUrls({ VITE_API_BASE_URL: 'wss://api.example' }, 'https://work.example'))
})

test('普通HTTP站点使用ws，开发覆盖保留IPv6主机和端口', async () => {
  const { resolveBackendUrls } = await load(resolve(root, 'src/utils/backendUrls.js'))
  assert.equal(resolveBackendUrls({}, 'http://127.0.0.1:5173').webSocketBaseUrl, 'ws://127.0.0.1:5173')
  assert.equal(resolveBackendUrls({ VITE_API_BASE_URL: 'http://[::1]:8081/' },
    'http://localhost:5173').webSocketBaseUrl, 'ws://[::1]:8081')
})

for (const route of ['api', 'ws', 'oauth2', 'login/oauth2']) {
  test(`Pages ${route}真实入口使用环境上游并保留请求和响应`, async () => {
    let forwarded
    const upgrade = route === 'ws'
    const response = new Response('upstream', { status: 302,
      headers: { location: 'https://accounts.example/login', 'set-cookie': 'session=new; HttpOnly' } })
    const { onRequest } = await load(resolve(repositoryRoot, `functions/${route}/[[path]].js`), {
      globals: { fetch: async (request) => { forwarded = request; return response } },
    })
    const headers = { cookie: 'session=old', origin: 'https://work.example', authorization: 'Bearer local-test' }
    if (upgrade) headers.upgrade = 'websocket'
    const request = new Request(`https://work.example/${route}/entry?ticket=one%2Btwo`, {
      method: upgrade ? 'GET' : 'POST', headers, ...(upgrade ? {} : { body: 'test-body' }),
    })
    const result = await onRequest({ request, env: { IOT_ORIGIN: 'https://backend.example:8443/' } })
    assert.equal(forwarded.url, `https://backend.example:8443/${route}/entry?ticket=one%2Btwo`)
    assert.equal(forwarded.method, request.method)
    for (const [key, value] of Object.entries(headers)) assert.equal(forwarded.headers.get(key), value)
    if (!upgrade) assert.equal(await forwarded.text(), 'test-body')
    assert.equal(forwarded.redirect, 'manual')
    assert.equal(result, response, '原样返回上游响应，不重建升级/重定向响应')
    assert.equal(result.headers.get('set-cookie'), 'session=new; HttpOnly')
  })
}

test('Pages未配置上游保持兼容，非法配置在外发前返回503', async () => {
  const forwarded = []
  const { proxyIotRequest } = await load(resolve(repositoryRoot, 'functions/_lib/iot-proxy.js'), {
    globals: { fetch: async (request) => { forwarded.push(request); return new Response('ok') } },
  })
  await proxyIotRequest(new Request('https://work.example/api/devices'))
  assert.equal(forwarded[0].url, 'https://node.61153652.xyz:8443/api/devices')
  for (const origin of ['http://backend.example', 'not a url', 'https://u:p@backend.example',
    'https://backend.example/prefix', 'https://backend.example/?query=1', 'https://backend.example/#fragment']) {
    const response = await proxyIotRequest(new Request('https://work.example/api/devices'), { IOT_ORIGIN: origin })
    assert.equal(response.status, 503)
  }
  assert.equal(forwarded.length, 1)
})
