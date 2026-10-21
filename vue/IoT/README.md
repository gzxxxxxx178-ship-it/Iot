# 智慧农业网页工作台

Vue 3 网页，后端默认监听 `localhost:8080`。在仓库根目录执行：

```bash
npm --prefix vue/IoT ci
npm --prefix vue/IoT run dev
npm --prefix vue/IoT run test:api-contract
npm --prefix vue/IoT run build
```

## 后端连接配置

HTTP、Google 登录入口和 WebSocket 共用 `src/utils/backendUrls.js`。地址是后端根基址，程序会追加 `/api/...`、`/oauth2/...` 或 `/ws/sensor`，不要把这些路由重复写入基址。

| 场景 | VITE_API_BASE_URL | VITE_WS_BASE_URL | 实际连接 |
|---|---|---|---|
| 默认开发 | 留空 | 留空 | HTTP/OAuth 使用 `http://localhost:8080`，实时连接使用 `ws://localhost:8080` |
| 默认生产 | 留空 | 留空 | 当前站点同源，HTTPS 自动对应 WSS |
| 独立后端 | `https://api.example` | 留空 | HTTP/OAuth 使用该基址，实时连接使用 `wss://api.example` |
| 独立实时服务 | `https://api.example` | `wss://live.example` | 票据从 API 领取，连接独立实时服务 |
| 同源代理前缀 | `/gateway/` | 留空 | 当前站点 `/gateway/api/...` 和 `/gateway/ws/sensor` |

配置支持尾斜杠、相对根路径及显式端口；协议、凭据、查询参数和片段经过校验。WS 配置可使用 HTTP(S) 或 WS(S) 协议。不同域名的 Cookie、CORS、WebSocket 来源白名单及 OAuth 回调地址仍须由后端正确配置；地址解析不会绕过浏览器安全约束。

本地覆盖写入忽略的 `.env.development.local`。这些 `VITE_*` 值在构建时进入前端产物，不得放入密钥，修改后需重启开发服务或重新构建。

## Cloudflare Pages 同源代理

仓库根目录 `functions/` 负责 `/api`、`/oauth2`、`/login/oauth2` 和 `/ws`。生产前端默认留空两个 `VITE_*` 配置，浏览器只连接当前站点。

服务端环境变量 `IOT_ORIGIN` 可在 Pages 对应生产/预览环境分别设置，例如 `https://backend.example:8443`。读取方式遵循 [Pages Functions bindings](https://developers.cloudflare.com/pages/functions/bindings/) 的 `context.env`。它必须是无凭据、无路径前缀、无查询串或片段的 HTTPS origin；非法值返回 503，不发送请求。未设置时保留既有后端地址，避免现有部署因新增配置立即中断；新环境应显式设置。

代理保留方法、请求体、Cookie、查询参数和升级头，直接返回上游响应；重定向以 manual 模式交还浏览器处理。本地测试覆盖入口转发和响应透传，未替代 Cloudflare 线上升级、真实 Cookie/OAuth 和后端连通性验收。

## 本轮验证范围

地址行为测试通过真实请求模块、axios 拦截器、票据 API 与实时连接 composable 执行，离线 adapter 和 WebSocket 替身阻止真实网络访问。覆盖默认开发、生产同源、独立 API、显式 WS、相对路径、尾斜杠、票据编码、断线重新领票与卸载停止重连；Pages 四类入口覆盖方法、请求体、认证头、查询参数和响应透传。
