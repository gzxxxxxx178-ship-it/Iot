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

- **你是 Codex 管理的实现 worker**：只执行 `.ai/tasks/current.md` 中明确列出的编码范围和验证要求
- **禁止执行 git add、commit、push、pull、merge、rebase、reset、checkout、switch、stash 或部署操作**；Codex 审查通过后负责最终提交
- 修改前检查工作区并保护已有改动；只编辑任务允许的路径，不擅自扩大范围、调整目标或修改协作规则
- 完成后运行任务指定的定向测试，自查完整差异，并用简洁报告说明修改文件、测试结果、风险和阻塞项
- 不读取或输出 `.env`、`application-dev.properties`、`secrets.h`、SSH、数据库、OAuth、JWT、MQTT 等真实凭据
- **后端每个方法前必须写注释**，说明该方法的功能
- **前端每个函数/API/composable 前必须写注释**，说明其功能

## Git 仓库边界

- 根目录是唯一 Git 仓库，`java/` 和 `vue/IoT/` 不再作为独立仓库使用。
- 所有 Git 操作从根目录执行；子目录内不执行提交或推送。
- Claude 不创建提交、不推送、不部署；Codex 验收后创建本地中文提交，前端上线仍由用户手动执行 `git push origin main`。

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

# 更新前端 (用户推送 GitHub → Cloudflare Pages 自动构建)
# 在根目录完成提交后，由用户执行：git push origin main
```

**前端构建** (Cloudflare Pages 自动执行):
- 构建命令: `cd vue/IoT && npm install && npm run build`
- 输出目录: `vue/IoT/dist`
