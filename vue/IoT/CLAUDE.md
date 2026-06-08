# 智慧农业 IoT 系统 — Vue 前端

Vue 3 前端应用，提供传感器数据可视化、设备管理、实时监控、AI 助手和用户认证界面。暗色主题，适配桌面和大屏。

## 工作流

- **每次完成任务后必须 git commit**，commit message 使用中文描述

## 技术栈

| 组件 | 版本/说明 |
|------|-----------|
| Vue | 3.x (Composition API + `<script setup>`) |
| 构建 | Vite 5 |
| UI 库 | Element Plus (暗色主题覆盖) |
| 图表 | ECharts 5 |
| 路由 | Vue Router 4 (hash 模式) |
| HTTP | axios (拦截器自动注入 JWT) |
| WebSocket | 原生 WebSocket (useWebSocket composable) |
| 二维码 | qrcode (canvas 渲染) |
| 状态 | 无 Pinia/Vuex，模块级 reactive + localStorage |

## 启动

```bash
cd vue/IoT && npm install && npm run dev
# → http://localhost:5173
```

## 环境变量

```
# .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080

# .env.production (留空使用同源地址)
```

---

## 一、路由表 (13 条)

| 路径 | 组件 | meta.title | layout | 说明 |
|------|------|------------|--------|------|
| `/` | — | — | — | 重定向到 `/dashboard` |
| `/login` | Login.vue | 登录 | `blank` | 全屏登录页，含 Google OAuth 按钮 |
| `/register` | Register.vue | 注册 | `blank` | 全屏注册页 |
| `/oauth-callback` | OAuthCallback.vue | 登录中... | `blank` | Google OAuth 回调处理页 |
| `/dashboard` | Dashboard.vue | 仪表盘 | default | 首页，系统概览统计 |
| `/monitor` | Monitor.vue | 实时监控 | default | WebSocket 实时数据 + 设备控制 |
| `/devices` | DeviceList.vue | 设备管理 | default | 设备卡片网格 |
| `/history` | History.vue | 历史数据 | default | 时间范围查询 + CSV 导出 |
| `/alarm` | Alarm.vue | 报警管理 | default | 报警规则 CRUD + 记录查看 |
| `/automation` | Automation.vue | 自动化规则 | default | 条件→动作规则 (前端 mock) |
| `/chat` | Chat.vue | AI 助手 | default | DeepSeek AI 对话 |
| `/pay` | Pay.vue | 支付测试 | default | 支付宝沙箱扫码支付 |
| `/screen` | Screen.vue | 数据大屏 | `blank` | 全屏实时仪表盘 |

**路由守卫** (`router.beforeEach`):
- 无 token + 非认证页 → 跳转 `/login`
- 有 token + 认证页 → 跳转 `/dashboard`
- 认证页: `/login`, `/register`, `/oauth-callback`

---

## 二、views/ — 页面组件 (11 个)

### 认证模块

| 文件 | 路由 | 功能 |
|------|------|------|
| `Login.vue` | `/login` | 用户名密码登录表单 + Google OAuth 登录按钮。错误提示支持表单验证和 OAuth 回调错误 (route.query.oauth_error)。登录成功存储 token/username 到 localStorage，支持 redirect 参数 |
| `Register.vue` | `/register` | 注册表单 (用户名 + 密码 + 确认密码)。校验: 密码≥6位，两次输入一致。成功自动登录跳转 Dashboard |
| `OAuthCallback.vue` | `/oauth-callback` | Google 登录回调处理。从 route.query 提取 token/username → 存入 localStorage → 跳转 Dashboard。无 token 时跳转 Login。含调试 console.log |

### 数据展示模块

| 文件 | 路由 | 功能 |
|------|------|------|
| `Dashboard.vue` | `/dashboard` | 系统仪表盘。加载 `/esp/history` → 计算统计指标 → 渲染: 4 个 GaugeCard (平均温/湿度/水位/信号) + 3 个状态卡片 (设备数/联动状态/消息总数) + TempHumChart 趋势图 + StatusPie 饼图 |
| `Monitor.vue` | `/monitor` | 实时监控页。初始加载历史数据 → 建立 WebSocket 连接 (useWebSocket) → 实时追加数据点 (上限 50) → 渲染: 4 个 GaugeCard (实时值) + 2 个状态卡片 + TempHumChart + ControlPanel (启停按钮)。显示 WebSocket 连接状态 (在线/离线/连接中) |
| `Screen.vue` | `/screen` | 全屏数据大屏。无侧栏无顶栏 (layout:blank)。实时时钟 (每秒更新) + 设备统计 + StatusPie + 联动/信号状态 + 大号 TempHumChart + 4 个大号指标。WebSocket 实时更新 (上限 60 点)。关闭按钮返回 Dashboard |

### 设备管理模块

| 文件 | 路由 | 功能 |
|------|------|------|
| `DeviceList.vue` | `/devices` | 设备列表。调用 `/esp/devices` → 渲染 DeviceCard 网格。显示设备数量和空状态提示 |
| `History.vue` | `/history` | 历史数据查询。日期时间范围选择器 (默认最近 24h) → 调用 `/esp/history/range` → TempHumChart + 详细数据表格 (设备ID/温度/湿度/水位/RSSI/联动/发送次数/时间)。支持 CSV 导出 (sensor-data-时间戳.csv) |

### 智能管理模块

| 文件 | 路由 | 功能 |
|------|------|------|
| `Alarm.vue` | `/alarm` | 报警规则 CRUD。添加/删除规则弹窗 (选择指标: 温度/湿度/水位，运算符: 大于/小于/等于，阈值，启用开关)。报警记录表格 (设备ID/消息/时间) |
| `Automation.vue` | `/automation` | 自动化规则引擎。条件→动作规则 (当 指标 运算符 阈值 → 则 启停/通知)。前端 mock 数据，无后端持久化。支持启用/禁用开关和删除 |
| `Chat.vue` | `/chat` | AI 农业助手对话。加载历史 (`/api/chat/history`) → 输入消息 → 发送 (`/api/chat`，带 sessionId + 完整对话历史) → 流式渲染 AI 回复。消息气泡 (用户右/助手左) + 加载动画 (dot-pulse) + 自动滚动 + 清除历史功能。sessionId 从 utils/session 获取 |
| `Pay.vue` | `/pay` | 支付宝沙箱支付测试。金额输入 + 商品描述 → 调用 `/api/alipay/create` → qrcode 库渲染二维码到 canvas → 3 秒轮询 `/api/alipay/query` → 支付成功显示 el-result。支持多次支付，未登录自动跳转 |

---

## 三、components/ — 可复用组件 (8 个)

### 布局组件

| 文件 | 说明 |
|------|------|
| `common/AppLayout.vue` | 主布局壳。左侧 SideMenu (240px) + 右侧 (TopBar 56px + router-view + fade 过渡动画)。layout='blank' 的页面不包裹此组件 |
| `common/SideMenu.vue` | 垂直导航菜单。9 个菜单项 (仪表盘/实时监控/设备管理/历史数据/报警管理/自动化规则/AI助手/支付测试/数据大屏)。顶部 Logo "智慧农业"。根据 route.path 高亮激活项，点击 router.push |
| `common/TopBar.vue` | 顶部栏。面包屑 (从 route.matched 计算) + 当前日期 (中文格式) + 用户名显示 (绿色高亮，从 localStorage 读取) + 退出登录按钮 (清除 token/username → 跳转 /login) |

### 图表组件

| 文件 | Props | 说明 |
|------|-------|------|
| `charts/GaugeCard.vue` | label, value, unit, color | 指标卡片。彩色顶部色条 + 标签 + 数值 + 单位。用于 Dashboard/Monitor/Screen |
| `charts/TempHumChart.vue` | timeLabels, tempSeries, humSeries, height | 温湿度双线 ECharts 图。绿色温度线 + 蓝色湿度线，渐变填充，阴影发光。deep watch 响应数据变化 |
| `charts/StatusPie.vue` | data (Array<{value,name,itemStyle}>) | 设备状态环形饼图 (donut)。内径 55%，外径 80%。底部图例。deep watch 响应数据 |

### 设备组件

| 文件 | Props / Emits | 说明 |
|------|---------------|------|
| `device/DeviceCard.vue` | props: device | 设备卡片。显示状态指示灯 (绿/红) + 设备名/ID + 温度/湿度/水位/信号 + 联动标签 + 最后在线时间 |
| `device/ControlPanel.vue` | emits: command-sent | 设备控制面板。"启动"/"停止" 按钮 → 调用 controlDevice API → 显示结果 → emit 命令。加载状态显示 |

---

## 四、api/ — HTTP 接口层 (7 个)

| 文件 | 函数 | 请求 | 说明 |
|------|------|------|------|
| `request.js` | — | — | axios 实例 (baseURL + 10s 超时 + 拦截器) |
| `auth.js` | login(data) | POST `/api/auth/login` | 用户名密码登录 |
| | register(data) | POST `/api/auth/register` | 用户注册 |
| | getMe() | GET `/api/auth/me` | 获取当前用户 |
| `device.js` | getHistoryData() | GET `/esp/history` | 最近 20 条数据 |
| | getSensorHistory(params) | GET `/esp/history/range` | 时间范围查询 |
| | getDeviceList() | GET `/esp/devices` | 设备列表 |
| | controlDevice(command) | POST `/api/device/control` | 发送控制命令 |
| `dashboard.js` | getDashboardStats() | GET `/api/dashboard/stats` | 仪表盘统计 |
| | getDeviceStatusDistribution() | GET `/api/dashboard/device-status` | 设备状态分布 |
| `alarm.js` | getAlarmRules() | GET `/api/alarm/rules` | 报警规则列表 |
| | saveAlarmRule(data) | POST `/api/alarm/rules` | 保存规则 |
| | deleteAlarmRule(id) | DELETE `/api/alarm/rules/:id` | 删除规则 |
| | getAlarmRecords(params) | GET `/api/alarm/records` | 报警记录 |
| `chat.js` | sendMessage(messages) | POST `/api/chat` | 发送消息 (含 sessionId) |
| | getHistory() | GET `/api/chat/history` | 加载历史 |
| | clearHistory() | DELETE `/api/chat/history` | 清除历史 |
| `pay.js` | createOrder(data) | POST `/api/alipay/create` | 创建支付订单，返回二维码 |
| | queryOrder(outTradeNo) | GET `/api/alipay/query` | 查询订单支付状态 |

### axios 拦截器 (`request.js`)

- **请求拦截**: 读取 localStorage token → 注入 `Authorization: Bearer <token>` 头。console.log 调试日志
- **响应拦截**: 401 → 清除 token/username → 跳转 `/#/login`。其他错误 → ElMessage.error 提示

---

## 五、composables/ — 组合式函数 (2 个)

| 文件 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `useWebSocket.js` | onMessage 回调 | `{ status, disconnect }` | WebSocket 连接管理。自动连接 `/ws/sensor`，接收 JSON 调用 onMessage。断线 5s 自动重连 (上限 10 次)。status: 'connecting' / 'online' / 'offline'。onUnmounted 自动断开 |
| `useChart.js` | — | `{ chartRef, init, setOption, resize, dispose }` | ECharts 实例生命周期。init 创建实例 + window resize 监听。dispose 清理监听器。chartRef 绑定模板 ref |

---

## 六、utils/ — 工具函数 (3 个)

| 文件 | 导出函数 | 说明 |
|------|----------|------|
| `auth.js` | getToken / setToken / removeToken | Token 的 localStorage CRUD (key: `token`) |
| | getUsername / setUsername / removeUsername | 用户名的 localStorage CRUD (key: `username`) |
| `format.js` | formatTime(val) | 时间戳/数组 → `HH:mm:ss` |
| | formatDateTime(timestamp) | 时间戳 → `YYYY-MM-DD HH:mm:ss` |
| | formatDecimal(val, digits) | 数字四舍五入，null→`'--'` |
| `session.js` | getSessionId() | 生成/获取聊天 session ID (localStorage 持久化，格式: `Date.now().toString(36) + Math.random().toString(36)`) |

---

## 七、styles/ — 样式 (2 个)

| 文件 | 说明 |
|------|------|
| `variables.css` | CSS 自定义属性。暗色主题色板 (背景 3 层 + 文本 3 层 + 强调色 5 种: 绿/红/蓝/黄/紫) + 间距 + 圆角 + 布局尺寸 |
| `global.css` | 全局样式。引入 variables.css + Google Fonts "Outfit"。reset 样式，Element Plus 暗色覆盖 (el-card/el-table/el-button 等)，自定义滚动条，fade 过渡动画 |

---

## 八、数据流

### 实时监控数据流
```
ESP32/ESP8266 → MQTT → Java → WebSocket(/ws/sensor)
  → useWebSocket composable → onMessage 回调
  → 更新 ref (chartData, gaugeValues) → 模板响应式渲染
```

### 认证数据流
```
本地登录: Login.vue → POST /api/auth/login → 存 token → router.push('/dashboard')
Google登录: Login.vue → /oauth2/authorization/google → Google → 回调
  → OAuth2SuccessHandler 302 → /#/oauth-callback → OAuthCallback.vue 存 token → Dashboard

API 调用: axios interceptor → 读 token → 附加 Authorization 头 → 请求
401 处理: 清 token → 跳转 /login
```

### AI 对话数据流
```
Chat.vue → 输入消息 → POST /api/chat {sessionId, messages: [...历史, 新消息]}
  → Java → DeepSeek API → 保存到 MySQL(chat_messages)
  → 返回 AI 回复 → 追加到消息列表 → 自动滚动到底部
```

### 支付数据流
```
Pay.vue → POST /api/alipay/create {amount, subject}
  → 返回 {outTradeNo, qrCode} → qrcode 渲染到 canvas
  → 用户用支付宝沙箱 App 扫码 → 支付
  → 前端轮询 GET /api/alipay/query?outTradeNo=xxx
  → status=SUCCESS → 显示支付成功 (el-result) → 停止轮询
```

---

## 九、生产部署

前端部署在 **Cloudflare Pages**，后端部署在 **VPS**。

### Cloudflare Pages 配置

| 配置项 | 值 |
|--------|-----|
| 分支 | `main` |
| 构建命令 | `cd vue/IoT && npm install && npm run build` |
| 输出目录 | `vue/IoT/dist` |

推送 GitHub 自动触发构建部署，无需手动 scp。

### 环境变量

生产环境 `.env.production` 中设置 `VITE_API_BASE_URL` 和 `VITE_WS_BASE_URL` 指向后端 VPS 的 HTTPS 地址，注意使用 `wss://` 协议以支持 WebSocket。

- `request.js` 生产环境使用 `VITE_API_BASE_URL`（不为空），不再走同源
- `Login.vue` Google 登录从 `VITE_API_BASE_URL` 推导后端地址
- `useWebSocket.js` 使用 `VITE_WS_BASE_URL` 连接 WSS

### VPS 端口架构

VPS 上 nginx 监听 HTTP 和 HTTPS 双端口作为反向代理，Java 后端在本地 8080 端口，Let's Encrypt 提供 SSL 证书并自动续期。

### OAuth 流程（跨域）

```
Cloudflare Pages → VPS HTTPS /oauth2/authorization/google → Google 授权
  → Google 回调 VPS HTTP /login/oauth2/code/google
  → 后端 OAuth2SuccessHandler → 302 跳回 Cloudflare Pages
```

**已修复的坑**:
- nginx `Connection "upgrade"` 引号不能被转义，否则 WebSocket 握手失败
- nginx 必须代理 `/esp/` 路径，否则传感器数据接口返回 index.html
- OAuth 回调 redirect-uri 必须和 Google Console 一致（使用 nip.io 域名而不是裸 IP）
- Cloudflare Pages 强制 HTTPS，VPS 必须提供 SSL 否则浏览器阻止 mixed content
- Let's Encrypt 证书每 90 天自动 renew（certbot 已配置定时任务）

---

## 十、关键约定

- **导入路径**: `views/` → `../api/xxx`; `components/charts/` → `../../composables/xxx`; `components/device/` → `../../api/xxx`
- **图标**: 全局注册 Element Plus Icons，模板中直接用组件名。报警图标是 `Bell` 不是 `ClockBell`
- **暗色主题**: CSS 变量在 `variables.css`，Element Plus 覆盖在 `global.css`
- **路由**: hash 模式，后端无需 SPA fallback 配置
- **状态管理**: 不加 Pinia/Vuex，用 composables + localStorage + reactive ref 足够
- **WebSocket**: `useWebSocket(onMessage)` 自动管理生命周期，无需手动断开
- **API 错误**: axios 拦截器统一处理 401 跳转和其他错误提示，各页面不需要额外 try-catch

## 十、踩坑记录

- `@element-plus/icons-vue` 中报警图标是 `Bell`，不存在 `ClockBell`
- `views/` 到 `api/` 的路径是 `../api/xxx`，写 `../../` 会导致编译失败 (views 比 components 少一层嵌套)
- Vite 端口被占用时自动切到 5174，先确认端口再排查
- Vue Router hash 模式: 后端 OAuth2 回调重定向到 `/#/oauth-callback?token=...` 时，query 参数在 hash 后面，route.query 可正常解析
