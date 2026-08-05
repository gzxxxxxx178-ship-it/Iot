# 智慧农业 IoT 系统

## 项目概述

前后端分离的智能农业物联网系统。ESP32 设备通过 MQTT 上报传感器数据，后端接收并持久化，通过 WebSocket 实时推送至前端。详细架构见 [Codex.project.md](Codex.project.md)。

## 子项目

| 项目 | 目录 | 技术栈 |
|------|------|--------|
| Java 后端 | [java/](java/) | Spring Boot 2.7 + MQTT + WebSocket + JPA + MySQL + Redis |
| Vue 前端 | [vue/IoT/](vue/IoT/) | Vue 3 + Element Plus + ECharts + WebSocket + axios |
| Arduino 固件 | [arduino/](arduino/) | ESP32 + DHT11 + 水位传感器 + PubSubClient |

## 工作规范

- **每次任务经 Codex 审查和验证通过后必须 git commit**，commit message 使用中文描述
- **后端每个方法前必须写注释**，说明该方法的功能
- **前端每个函数/API/composable 前必须写注释**，说明其功能

## Codex 与 Claude 协作模式

- Codex 是项目主负责人：理解需求、设计目标和流程、编写验收标准、拆分任务、审查代码、独立验证、更新台账并创建最终提交。
- Claude Code 是实现 worker：根据 `.ai/tasks/current.md` 完成具体编码和定向测试，不自行改变需求、架构目标或验收标准。
- 代码任务默认通过 `scripts/claude_worker.sh` 交给 Claude；咨询、审查、诊断、任务设计、生产部署和最终验收仍由 Codex 负责。
- Claude 不执行 `git add`、`git commit`、`git push`、部署、SSH、数据库写操作、凭据读取或硬件上传；这些边界由 worker 权限配置和守卫脚本共同限制。
- Claude 完成后，Codex 必须检查完整 `git diff`，复核运行测试并完成正确性、简洁性、安全性审查；发现问题时退回 Claude 修订或由 Codex 做最小修正。
- 未通过 Codex 验收的 Claude 输出不得标记完成、提交或部署。Claude 不可用、任务涉及敏感凭据/生产环境，或委派成本高于实现成本时，Codex 可以直接完成并说明原因。
- 同一时间只允许一个实现者修改工作区。运行受管 worker 时，不得让手动打开的 Claude 会话同时修改相同文件。
- 详细流程见 [docs/CODEX_CLAUDE_WORKFLOW.md](docs/CODEX_CLAUDE_WORKFLOW.md)。

## Git 仓库边界

- 根目录 `/Volumes/out/Projects/DS-workplace` 是唯一 Git 仓库，远端为 GitHub `origin`。
- `java/` 和 `vue/IoT/` 仅作为子目录，不在其中执行 `git add`、`git commit` 或 `git push`。
- 所有状态检查、提交和部署前核对均从根目录执行：`git status`、`git diff`、`git add`、`git commit`。
- 前端推送由用户手动执行；Codex 只创建本地提交，不自动推送，Claude 不参与提交。

## 本地开发

```bash
# 1. 启动后端 (需要 MySQL + Redis)
cd java && ./mvnw spring-boot:run        # → http://localhost:8080

# 2. 启动前端 (热更新)
cd vue/IoT && npm install && npm run dev  # → http://localhost:5173

# 3. Arduino 上传
# 用 Arduino IDE 打开 arduino/MQTT/MQTT.ino → 修改WiFi配置 → 上传到ESP32
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
