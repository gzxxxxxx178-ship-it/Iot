# 小程序本地开发说明（miniapp）

## 概述

`miniapp/` 是一个可通过 HBuilderX 导入的**传统 uni-app Vue3 工程**（非 uni-app x），目标是先搭建完整功能的小程序页面，**暂不接真实后端、不上线**。所有数据均为明确标注的演示数据，不发起任何网络请求。

## 工具与本机路径

| 工具 | 本机路径 |
|------|----------|
| HBuilderX | `/Volumes/out/Applications/HBuilderX.app` |
| 微信开发者工具 | `/Volumes/out/Applications/wechatwebdevtools.app` |

> 若微信开发者工具安装路径不同，以实际安装位置为准。

## 本地编译步骤

1. 打开 HBuilderX（`/Volumes/out/Applications/HBuilderX.app`）。
2. 选择「文件 → 导入 → 从本地目录导入」，选择本项目 `miniapp/` 目录。
3. 在项目根 `manifest.json` 中确认 `vueVersion` 为 `3`，微信 `appid` 保持为空。
4. 菜单「运行 → 运行到小程序模拟器 → 微信开发者工具」，选择微信开发者工具路径。
5. 微信 AppID 为空时，HBuilderX 可以完成编译，但开发者工具自动打开可能报“不存在此 AppID”。手动导入 `miniapp/unpackage/dist/dev/mp-weixin/`，使用工具提供的「测试号」；取得自己的 AppID 后填写 `manifest.json` 的 `mp-weixin.appid`。
6. 也可选择「运行 → 运行到浏览器」检查页面。本工程已提供 Vue 3 所需的 `index.html`；浏览器通过不等同于微信模拟器或真机通过。
7. 若首次编译提示缺少 Sass 编译器，按 HBuilderX 官方插件提示安装 `compile-dart-sass` 后重新运行。本机此次已完成安装。

## 无 AppID 时的测试模式限制

- `manifest.json` 与 `mp-weixin.appid` 均为空，**未伪造正式 AppID**。
- 测试号可用能力以当前工具和账号为准；本机直接打开游客 AppID 失败，获取测试号时发生 TLS 网络连接错误，尚未完成微信模拟器运行验收。
- 本工程未使用微信登录、云开发等平台能力；目前可用 HBuilderX 的 Web 预览检查页面和本地演示交互。

## 演示边界（重要）

- 运行模式固定为 `demo`（见 `miniapp/config/index.js`），不请求任何真实 API。
- 登录页**不保存密码、不宣称真实认证**，仅提供「进入演示」。
- 设备控制、报警确认、自动化规则增删、仿真运行、研究运行等**只修改本地演示状态**（见 `miniapp/utils/demo-store.js`），不向真实设备下发命令。
- 支付演示为**模拟结果，无真实扣款**。
- AI 助手为**本地固定回复**，不调用任何 LLM。
- 研究示例运行指标为**演示占位值**，不复制冻结论文结论，不声称田间实测或生产验证。
- 数据导出明确标注为**未接入**。

## 待接入功能

- 真实登录认证（JWT/OAuth）与用户体系。
- 真实传感器数据读取（MQTT 历史/实时）。
- 真实设备控制命令下发。
- 报警规则 CRUD 与后端持久化。
- 自动化规则后端持久化与执行。
- 真实 AI 对话（DeepSeek/RAG）。
- 小程序支付渠道与资质确认；当前支付宝沙箱流程需单独适配，不能作为微信支付接入结果。
- 数据导出（CSV）。
- MATLAB 仿真与研究实验结果对接。

## 配置域名需后续确认

- 接入真实后端前，需要在微信公众平台后台配置**服务器域名**（request 合法域名、socket 合法域名、uploadFile/downloadFile 合法域名）。
- 当前 `config/index.js` 中 `apiBaseUrl` 与 `wsBaseUrl` 均为空，待域名备案与微信白名单确认后再填写。
- 小程序要求 HTTPS/WSS，后端 VPS 需具备有效证书。

## 验证说明

- 静态结构校验脚本：`node miniapp/scripts/verify.mjs`（Node 内置模块，仅校验 JSON/路由/资源/导航/禁用依赖）。
- 该脚本**不代表微信实际编译**；实际编译与 UI 验证由 Codex 在 HBuilderX / 微信开发者工具中完成。
- **未进行真机验证**，不声称真机运行通过。

### 本轮验收（2026-10-13）

- HBuilderX 5.07：微信小程序编译成功，Web 本地预览编译成功。
- 13 个页面入口在 Web 预览可达；320px/390px 检查及浏览器交互验证完成，错误日志为空。
- 已验证设备启动确认、报警确认后跨页数量同步、规则新增、历史分页、固定问答、模拟订单、仿真退出再进入完成、研究问答、重置和演示登录返回。
- 微信模拟器运行未通过验收：空 AppID 的自动打开失败；本机工具获取测试号时发生 TLS 网络错误。需恢复测试号获取或填写本人真实 AppID 后再运行。无需提供 AppSecret 来检查页面。
- 概览验收截图保存在本地 `miniapp/unpackage/qa/overview.jpg`，不纳入 Git。
