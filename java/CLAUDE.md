# 智慧农业 IoT 系统 — Java 后端

Spring Boot 2.7.18 后端服务，负责 MQTT 消息处理、数据持久化、WebSocket 推送、REST API、用户认证和 AI 对话代理。

## 工作流

- **每次完成任务后必须 git commit**，commit message 使用中文描述
- **每个方法前必须写注释**，说明该方法的功能

## 技术栈

| 组件 | 版本/说明 |
|------|-----------|
| Spring Boot | 2.7.18 |
| Java | 8 (无 Map.of / var / record，用 HashMap 替代) |
| ORM | JPA (Hibernate) + MySQL 8 |
| 缓存 | Redis (Jedis 连接池) |
| MQTT | Eclipse Paho 客户端 → broker.emqx.io:1883 |
| WebSocket | Spring WebSocket → `/ws/sensor` |
| 安全 | Spring Security 5.7 + JWT (jjwt 0.9.1) + OAuth2 Client |
| 密码加密 | BCryptPasswordEncoder |
| 支付 | 支付宝沙箱 (alipay-sdk-java) |
| AI API | DeepSeek API (OpenAI 兼容格式) |

## 启动

```bash
cd java && ./mvnw spring-boot:run
# 或 IDE 中运行 IoTSystemApplication.main()
```

## 包结构 `com.ruoyi.iotsystem`

```
IoTSystemApplication.java         # Spring Boot 入口，main()
├── config/                       # 配置 & 基础设施 (12 文件)
├── controller/                   # REST 控制器 (6 文件)
├── service/                      # 业务逻辑 (5 文件)
├── entity/                       # JPA 实体 (4 文件)
├── repository/                   # 数据访问 (4 文件)
└── dto/                          # 数据传输对象 (5 文件)
```

---

## 一、config/ — 配置与基础设施

### 核心安全链

| 文件 | 说明 |
|------|------|
| `SecurityConfig.java` | Spring Security 主配置。STATELESS → IF_REQUIRED (OAuth2 需要 Session)。CSRF 关闭。CORS 全放行。公开: `/api/auth/**`, `/login/oauth2/**`, `/oauth2/**`, OPTIONS。其他全部需认证。401 返回 JSON。注册 OAuth2 登录 + JWT 过滤器 |
| `AppConfig.java` | 独立的 PasswordEncoder + AuthenticationManager Bean。与 SecurityConfig 分离以打破循环依赖 |
| `JwtAuthenticationFilter.java` | OncePerRequestFilter，提取 `Bearer <token>` → 解析 username → loadUserByUsername → 设置 SecurityContext。异常静默放行 |
| `JwtUtil.java` | JWT 工具。HS256 签名，Base64 解码密钥，24h 过期。方法: generateToken / extractUsername / validateToken |
| `CustomOAuth2UserService.java` | 继承 DefaultOAuth2UserService。Google 回调后提取 email/name/sub/picture，首次自动注册，已有则更新头像邮箱。将数据库 username 注入 customAttributes |
| `OAuth2SuccessHandler.java` | OAuth 登录成功 → 生成 JWT → 302 重定向到前端 `/#/oauth-callback?token=xxx&username=xxx` |
| `OAuth2FailureHandler.java` | OAuth 登录失败 → 记录日志 → 302 重定向到前端 `/#/login?oauth_error=错误信息` |

### 通信基础设施

| 文件 | 说明 |
|------|------|
| `MqttConfig.java` | MQTT 客户端 Bean。连接 `tcp://broker.emqx.io:1883`，clientId=`java_client_fixed` |
| `WebSocketConfig.java` | 实现 WebSocketConfigurer，注册 `/ws/sensor` 端点，允许所有来源 |
| `SensorWebSocketHandler.java` | TextWebSocketHandler，CopyOnWriteArrayList 维护连接池。broadcast(String) 向所有客户端推送 |
| `RedisConfig.java` | RedisTemplate<String, String> Bean，StringRedisSerializer 序列化 |

### 全局基础设施

| 文件 | 说明 |
|------|------|
| `GlobalExceptionHandler.java` | @RestControllerAdvice 全局异常处理器。统一捕获 BadCredentialsException → 401、RuntimeException → 400、AlipayApiException → 500、Exception → 500，全部包装为 ApiResponse 格式返回。Controller 层不再需要手动 try-catch |

---

## 二、controller/ — REST API

### 认证相关

**AuthController.java** — `@RestController` `/api/auth`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户名密码登录。Body: `{username, password}` → 返回 `ApiResponse<{token, username}>`。密码错误由全局异常处理器返回 401 |
| `/api/auth/register` | POST | 用户注册。Body: `{username, password}` → 返回 `ApiResponse<{token, username}>`。用户名重复抛 RuntimeException → 全局异常处理器返回 400 |
| `/api/auth/me` | GET | 获取当前用户信息。返回 `ApiResponse<{username, createdAt}>`。未登录返回 401 |

### 传感器数据

**EspController.java** — `@RestController` `/esp`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/esp/test` | GET | 健康检查，返回当前时间 |
| `/esp/sensor/data` | POST | 接收传感器数据 (JSON body → EspEntity) |
| `/esp/history` | GET | 最近 20 条传感器记录 |
| `/esp/history/range` | GET | 按时间范围查询。参数: `start`, `end` (ISO 格式) |
| `/esp/devices` | GET | 所有设备的汇总信息（每个设备的最新读数） |

### 设备控制

**DeviceControlController.java** — `@RestController` `/api/device`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/device/control` | POST | 发送启停命令。Body: `{command: "start" | "stop"}` → MQTT 发布到 `agri/device001/control` |

### AI 助手

**ChatController.java** — `@RestController`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 发送消息。Body: `{sessionId, messages: [{role, content}]}` → 调用 DeepSeek API → 保存双方消息 → 返回 AI 回复 |
| `/api/chat/history` | GET | 按 sessionId 加载聊天历史 |
| `/api/chat/history` | DELETE | 清除指定 session 的聊天记录 |

### Redis 管理

**RedisTestController.java** — `@RestController` `/redis`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/redis/set` | POST | 设置键值 (参数: key, value) |
| `/redis/setWithExpire` | POST | 设置键值 + 过期秒数 |
| `/redis/get` | GET | 获取键值 |
| `/redis/delete` | DELETE | 删除键 |
| `/redis/exists` | GET | 检查键是否存在 |

### 支付

**AlipayController.java** — `@RestController` `/api/alipay`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/alipay/create` | POST | 创建支付订单。Body: `{amount, subject}` → 返回 `{outTradeNo, qrCode}`。需登录 |
| `/api/alipay/query` | GET | 查询订单状态。参数: `outTradeNo` → 返回 `{status, amount, tradeNo}`。需登录 |
| `/api/alipay/notify` | POST | 支付宝异步回调。验签后更新订单。无需认证 |

---

## 三、service/ — 业务逻辑

| 文件 | 说明 |
|------|------|
| `MqttMessageService.java` | **MQTT 核心服务**。实现 MqttCallback，订阅 `agri/device001/data`。支持 JSON 和纯文本（正则提取）两种格式。数据解析后 → JPA 保存 → WebSocket 广播。publish() 方法发布控制指令 |
| `EspService.java` | 传感器数据服务。saveData / getRecentData / processDataAndGenerateResponse |
| `UserService.java` | 用户服务，实现 UserDetailsService。register: 验重→BCrypt 加密→保存→生成 JWT。login: AuthenticationManager 认证→生成 JWT。loadUserByUsername: OAuth 用户密码 null 时用空串兜底 |
| `RedisService.java` | Redis 字符串 KV 操作: set / get / delete / exists + 过期时间支持 |
| `AlipayService.java` | 支付宝支付服务。createOrder: 生成订单号→保存DB→调用 alipay.trade.precreate→返回二维码。queryOrder: 查支付宝→更新DB。handleNotify: 验签→更新订单。用 @PostConstruct 初始化 AlipayClient |

---

## 四、entity/ — 数据表

| 实体 | 表名 | 字段 |
|------|------|------|
| `EspEntity.java` | `esp_data` | id, deviceId, temperature, humidity, water, linkage, sendCount, rssi, timestamp, serverReceivedTime |
| `UserEntity.java` | `users` | id, username (唯一), password (可空), email (可空), avatar (可空), provider (LOCAL/GOOGLE), providerId (可空), createdAt |
| `ChatMessageEntity.java` | `chat_messages` | id, sessionId, role (user/assistant), content (TEXT), createdTime |
| `PaymentOrderEntity.java` | `payment_orders` | id, outTradeNo (唯一), username, amount (BigDecimal), subject, status (PENDING/SUCCESS/CLOSED), tradeNo (可空), createdAt, paidAt (可空) |

**UserEntity 两个构造函数:**
- `(username, password)` → 本地注册，provider="LOCAL"
- `(username, email, avatar, provider, providerId)` → OAuth 自动注册，password=null

---

## 五、repository/ — 数据访问

| 文件 | 自定义方法 |
|------|------------|
| `EspRepository.java` | findTop20ByOrderByServerReceivedTimeDesc / findByServerReceivedTimeBetween / findDistinctDeviceIds / findLatestByDeviceId |
| `UserRepository.java` | findByUsername / existsByUsername / findByProviderAndProviderId |
| `ChatMessageRepository.java` | findBySessionIdOrderByCreatedTimeAsc / deleteBySessionId |
| `PaymentOrderRepository.java` | findByOutTradeNo |

---

## 六、dto/ — 数据传输对象

| 文件 | 字段 |
|------|------|
| `ApiResponse.java` | 统一响应体。`{code, message, data}` 泛型包装。静态工厂: success(data) / fail(code, msg) / error(msg)。所有 Controller 统一返回此格式 |
| `AuthResponse.java` | token, username |
| `LoginRequest.java` | username, password |
| `RegisterRequest.java` | username, password |
| `ChatRequest.java` | sessionId, messages (List<Map<String,String>>) |

### 统一响应格式

所有 API 返回 `ApiResponse<T>` 结构，HTTP 状态码统一为 200，通过 `code` 字段区分业务状态：

| code | 含义 |
|------|------|
| 200 | 成功，data 含业务数据 |
| 400 | 业务错误（参数校验失败、用户名重复等） |
| 401 | 认证失败（用户名密码错误、Token 过期） |
| 500 | 服务器内部错误 |

前端 axios 响应拦截器自动解包 `data` 字段，因此 API 函数中的 `.then(r => r.data)` 得到的是解包后的业务数据，无需关心 `code`/`message` 包装。

---

## 七、配置管理 — 多环境 Profile

本项目使用 Spring Boot Profile 隔离敏感信息，解决「本地能调试 + git 不泄露密钥」的矛盾。

### 配置文件分层

| 文件 | 提交 git? | 内容 |
|------|-----------|------|
| `application.properties` | ✅ 提交 | 公开配置 + 占位符。`spring.profiles.active=dev` |
| `application-dev.properties` | ❌ gitignore | 本地开发真实密钥（数据库密码、JWT、API Key 等） |
| `application-prod.properties` | ❌ gitignore | VPS 生产环境真实密钥（在 VPS `/opt/iot/` 目录） |

### 工作原理

```
本地启动: mvn spring-boot:run
  → spring.profiles.active=dev
  → 加载 application.properties + application-dev.properties
  → 占位符被 application-dev.properties 覆盖

VPS 启动: java -jar app.jar --spring.profiles.active=prod
  → 加载 application.properties + application-prod.properties
  → 占位符被 application-prod.properties 覆盖
```

### 添加新密钥的步骤

1. `application.properties` → 写占位符：`my.secret=占位符-由profile覆盖`
2. `application-dev.properties` → 写本地真实值
3. VPS `/opt/iot/application-prod.properties` → 写生产真实值

### application.properties 参考

```properties
spring.profiles.active=dev               # 默认激活开发环境
spring.datasource.password=占位符-由profile覆盖
jwt.secret=占位符-由profile覆盖
deepseek.api.key=占位符-由profile覆盖
# ... 其他敏感值同理
```

## 八、数据流

### 传感器数据流
```
ESP32/ESP8266 → MQTT(agri/device001/data) → MqttMessageService.messageArrived()
  → 解析 JSON/纯文本 → EspService.saveData() → MySQL(esp_data)
  → SensorWebSocketHandler.broadcast() → Vue 前端实时更新
```

### 设备控制流
```
前端 ControlPanel → POST /api/device/control
  → DeviceControlController → MqttMessageService.publish()
  → MQTT(agri/device001/control) → ESP32/ESP8266
```

### 认证流 (本地)
```
Login.vue → POST /api/auth/login → UserService.login()
  → AuthenticationManager.authenticate() → 生成 JWT
  → 前端存储 token → axios 拦截器自动附加 Bearer token
  → JwtAuthenticationFilter 每次请求验证
```

### 认证流 (Google OAuth2)
```
Login.vue "Google登录" → /oauth2/authorization/google → Google 授权页
  → 回调 /login/oauth2/code/google → CustomOAuth2UserService (首次自动注册)
  → OAuth2SuccessHandler (生成 JWT) → 302 → 前端 /#/oauth-callback
  → OAuthCallback.vue 存储 token → 跳转 Dashboard
```

### AI 对话流
```
Chat.vue → POST /api/chat {sessionId, messages}
  → ChatController → DeepSeek API → 保存双方消息到 chat_messages
  → 返回 AI 回复 → 前端渲染
```

### 支付流 (支付宝沙箱)
```
Pay.vue → POST /api/alipay/create {amount, subject}
  → AlipayService.createOrder() → 生成订单号 → 保存 payment_orders
  → alipay.trade.precreate → 返回 qrCode → 前端展示二维码
  → 用户扫码支付 → 前端轮询 GET /api/alipay/query
  → AlipayService.queryOrder() → alipay.trade.query → 更新订单状态 SUCCESS
  → 支付宝异步 POST /api/alipay/notify → 验签 → 更新订单
```

## 九、注意事项

- **Java 8**: 不能用 `Map.of()` → 用 `new HashMap<>()` + `put()`
- **Spring Boot 2.6+**: 默认禁止循环依赖。PasswordEncoder 和 AuthenticationManager 放到独立的 AppConfig.java
- **@Lazy**: UserService 注入 AuthenticationManager 用 @Lazy 打破循环
- **SessionManagement**: 从 STATELESS 改为 IF_REQUIRED，否则 OAuth2 回调时 Session 中的 state 丢失
- **User 构造函数**: OAuth 用户 password=null，loadUserByUsername 中需兜底传空串 ""
- **API Key 安全**: application.properties 含 DeepSeek/Google 密钥，不要提交到公开仓库
- **data-dir**: JPA ddl-auto=update，首次启动会自动创建 users / chat_messages / payment_orders 表，esp_data 表需已存在
- **支付宝沙箱**: 开发用沙箱环境，需在 openhome.alipay.com 获取 APPID/私钥/公钥。本地无法接收异步回调时，前端轮询替代
- **MQTT 数据接收**: 后端订阅 `agri/device001/data`，支持 `device` 和 `deviceId` 两种 JSON 字段名
- **统一异常处理**: Controller 层不再需要 try-catch。业务异常抛 RuntimeException (自动返回 400)，认证异常抛 BadCredentialsException (自动返回 401)，`@Valid` 校验失败抛 MethodArgumentNotValidException (自动返回 400+具体字段错误)。所有异常由 GlobalExceptionHandler 统一转换为 ApiResponse 格式
- **ApiResponse 格式**: 所有接口统一返回 `{code, message, data}`。前端 axios 拦截器自动解包 data 字段，401 时清除登录态并跳转登录页
- **参数校验 (@Valid)**: 后端 DTO + Entity 使用 Bean Validation 注解（`@NotBlank`/`@NotNull`/`@Size`），Controller 用 `@Valid` 触发。前端 Element Plus el-form 配合 `rules` 做客户端校验。校验失败后端返回具体字段错误信息，前端在对应字段下方显示红色提示

## 十、生产部署

后端部署在 VPS，前端部署在 Cloudflare Pages。

**服务器配置**: Ubuntu / 1GB RAM / systemd 自启 / Nginx 反向代理 / TiDB Cloud

**nginx 代理**: HTTP 和 HTTPS 双端口，反向代理 `/api/`、`/esp/`、`/oauth2/`、`/login/oauth2/`、`/ws/` 到 Java:8080。HTTPS 证书由 Let's Encrypt 提供，certbot 自动 renew。

**配置文件**: `/opt/iot/application-prod.properties` (通过 `--spring.profiles.active=prod` 激活)

**管理命令**:
```bash
systemctl status iot       # 查看状态
systemctl restart iot      # 重启
journalctl -u iot -f       # 查看日志
```

**重新部署**:
```bash
# 本地构建
./mvnw clean package -DskipTests
# 上传
scp target/IoTSystem-0.0.1-SNAPSHOT.jar root@<VPS_IP>:/opt/iot/
# 重启
ssh root@<VPS_IP> "systemctl restart iot"
```

**生产环境变量** (`application-prod.properties`):
- `spring.datasource.url`: TiDB Cloud 连接
- `app.oauth2.redirect-uri`: Cloudflare Pages 地址（OAuth 成功后跳回前端）
- `spring.security.oauth2.client.registration.google.redirect-uri`: nip.io 域名的 `/login/oauth2/code/google`（Google 回调走 HTTP，需要和 Google Console 注册的一致）
