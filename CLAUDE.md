# 智慧农业 IoT 系统

## 项目概述

前后端分离的智能农业物联网系统。ESP8266 设备通过私有 MQTT TLS Broker 上报传感器数据，后端接收并持久化，通过鉴权 WebSocket 实时推送至前端。详细架构见 [CLAUDE.project.md](CLAUDE.project.md)。

## 子项目

| 项目 | 目录 | 技术栈 |
|------|------|--------|
| Java 后端 | [java/](java/) | Spring Boot 2.7 + MQTT + WebSocket + JPA + MySQL + Redis |
| Vue 前端 | [vue/IoT/](vue/IoT/) | Vue 3 + Element Plus + ECharts + WebSocket + axios |
| Arduino 固件 | [arduino/](arduino/) | ESP8266 + DHT11 + 水位传感器 + PubSubClient + TLS |

## 工作规范

- **每次完成任务后必须 git commit**，commit message 使用中文描述
- **后端每个方法前必须写注释**，说明该方法的功能
- **前端每个函数/API/composable 前必须写注释**，说明其功能

## 本地开发

```bash
# 1. 启动后端 (需要 MySQL + Redis)
cd java && ./mvnw spring-boot:run        # → http://localhost:8080

# 2. 启动前端 (热更新)
cd vue/IoT && npm install && npm run dev  # → http://localhost:5173

# 3. Arduino 上传
# 复制 secrets.example.h 为 secrets.h，填写 Wi-Fi 与设备 MQTT 凭据后上传到 ESP8266
```

## 生产部署

**前端**: Cloudflare Pages，连接 GitHub 仓库自动构建部署。
**后端**: VPS，nginx 提供 HTTP + HTTPS (Let's Encrypt) 双端口。

```bash
# 更新后端 (手动部署)
cd java && ./mvnw clean package -DskipTests
scp target/IoTSystem-0.0.1-SNAPSHOT.jar root@<VPS_IP>:/opt/iot/
ssh root@<VPS_IP> "systemctl restart iot"

# 更新前端 (推送 GitHub → Cloudflare Pages 自动构建)
git add . && git commit -m "描述改动" && git push origin main
```

**前端构建** (Cloudflare Pages 自动执行):
- 构建命令: `cd vue/IoT && npm install && npm run build`
- 输出目录: `vue/IoT/dist`
