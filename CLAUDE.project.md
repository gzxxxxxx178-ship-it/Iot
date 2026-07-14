# 智慧农业 IoT 系统 — 项目参考

> 每次会话自动加载的是 [CLAUDE.md](CLAUDE.md)，本文件仅在深入了解项目细节时需要查阅。

## 系统架构

```mermaid
graph TB
    subgraph Device["设备层 (ESP8266)"]
        ESP[ESP8266]
        DHT[DHT11 温湿度]
        WATER[水位传感器]
        DHT --- ESP
        WATER --- ESP
    end

    subgraph MQTT["MQTT 消息中间件"]
        BROKER[私有 Mosquitto TLS :8883<br/>设备级 Topic + ACL]
    end

    subgraph VPS["VPS (海外服务器)"]
        NGINX80[nginx :80 HTTP]
        NGINX8443[nginx :8443 HTTPS<br/>Let's Encrypt SSL]
        JAVA[Java Spring Boot :8080]
        NGINX80 --> JAVA
        NGINX8443 --> JAVA
    end

    subgraph DB["数据存储"]
        TIDB[(TiDB Cloud<br/>MySQL 8)]
        REDIS[(Redis<br/>缓存)]
    end

    subgraph Frontend["前端展示层"]
        CF[Cloudflare Pages<br/>Vue 3 + Element Plus]
    end

    subgraph External["外部服务"]
        GOOGLE[Google OAuth2]
        DEEPSEEK[DeepSeek AI API]
        ALIPAY[支付宝沙箱]
    end

    %% 数据上报流
    ESP -->|"MQTT publish<br/>温湿度/水位/RSSI"| BROKER
    BROKER -->|"订阅消费"| JAVA
    JAVA -->|"JPA 持久化"| TIDB
    JAVA -->|"缓存"| REDIS

    %% 实时推送流
    JAVA -->|"WebSocket 推送"| NGINX8443
    NGINX8443 -->|"wss://"| CF

    %% 前端 API 请求
    CF -->|"HTTPS API 请求"| NGINX8443
    NGINX8443 -->|"REST /api/* /esp/*"| JAVA

    %% 设备控制流
    CF -->|"POST /api/device/control"| JAVA
    JAVA -->|"MQTT publish start/stop"| BROKER
    BROKER -->|"订阅接收"| ESP

    %% 认证流
    CF -->|"OAuth2 登录"| JAVA
    JAVA <-->|"授权/回调"| GOOGLE

    %% AI 和支付
    JAVA <-->|"HTTP API"| DEEPSEEK
    JAVA <-->|"precreate/query/notify"| ALIPAY

    %% 样式
    classDef device fill:#1a1a2e,stroke:#e94560,color:#eee
    classDef mqtt fill:#16213e,stroke:#0f3460,color:#eee
    classDef vps fill:#0f3460,stroke:#533483,color:#eee
    classDef db fill:#1a1a2e,stroke:#00b4d8,color:#eee
    classDef frontend fill:#16213e,stroke:#f77f00,color:#eee
    classDef external fill:#1a1a2e,stroke:#2a9d8f,color:#eee

    class ESP,DHT,WATER device
    class BROKER mqtt
    class NGINX80,NGINX8443,JAVA vps
    class TIDB,REDIS db
    class CF frontend
    class GOOGLE,DEEPSEEK,ALIPAY external
```

### 部署拓扑

```
ESP8266 ──MQTT TLS──▶ 私有 Broker ──订阅──▶ Java ──JPA──▶ 云数据库
                                       │
            ┌──────────────────────────┤
            │                          │
      nginx (HTTP)              nginx (HTTPS)
       仅 API 代理                SSL 终止 + WSS
            │                          │
            └──────────┬───────────────┘
                       │
               Cloudflare Pages
                  (Vue 3 前端)
```

## 功能模块

| 模块 | 说明 | 涉及文件 |
|------|------|----------|
| 传感器数据采集 | ESP8266 通过设备级 MQTT TLS Topic 上报，后端校验设备身份 | `MqttMessageService`, `EspEntity`, `MQTT.ino` |
| 实时监控 | WebSocket 推送实时数据，前端图表动态更新 | `SensorWebSocketHandler`, `Monitor.vue` |
| 设备管理 | 设备列表、状态展示、远程启停控制 | `DeviceControlController`, `DeviceList.vue` |
| 历史数据 | 设备/时间组合查询、异常质量标记、保留策略和全量 CSV 导出 | `SensorHistoryService`, `SensorDataRetentionService`, `History.vue` |
| 数据大屏 | 全屏实时仪表盘，适合展示大屏 | `Screen.vue` |
| 报警管理 | 报警规则 CRUD + 报警记录查看 | `Alarm.vue` |
| 自动化规则 | 持久化条件引擎，支持防抖、冷却、停用和执行审计 | `AutomationService`, `Automation.vue` |
| AI 助手 | DeepSeek API 驱动的对话助手，历史记录持久化 | `ChatController`, `Chat.vue` |
| 用户认证 | 本地注册/登录 + Google OAuth2 + JWT | `AuthController`, `Login.vue`, `Register.vue` |
| 支付宝支付 | 沙箱环境支付测试：下单→扫码→回调确认 | `AlipayController`, `Pay.vue` |

## 前置依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK | 8+ | 后端运行环境 |
| MySQL | 8.0 / TiDB Cloud | 数据库 |
| Redis | 5+ | 缓存 |
| Node.js | 16+ | 前端构建 |
| Arduino IDE | 2.x | ESP8266 固件上传 |

## 子项目详细文档

- Java 后端: [java/CLAUDE.md](java/CLAUDE.md)
- Vue 前端: [vue/IoT/CLAUDE.md](vue/IoT/CLAUDE.md)
