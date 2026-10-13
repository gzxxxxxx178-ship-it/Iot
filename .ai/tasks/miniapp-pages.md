# MINI-01：HBuilderX 小程序页面搭建

- **状态**：`DONE`
- **任务编号**：`MINI-01`

## 目标与边界

用户要求使用本机 HBuilderX，先搭建完整功能的小程序页面，暂不接真实后端，不上线。根目录 git status 干净。新增 miniapp/ 为 HBuilderX 可导入的传统 uni-app Vue3 工程（非 uni-app x），无需 npm 安装，所有数据为明确标注的演示数据。Codex 负责实际编译和 UI 验证。

## 允许修改

- miniapp/** （禁止 unpackage 编译产物纳入源文件）
- docs/miniapp-local-development.md

## 禁止

不修改现有 java/vue/arduino/matlab、根配置、任务书、CLAUDE.md、AGENTS.md。不读凭据、不联网、不执行 Git 写入，不发送真实设备控制或连接后端。

## 页面要求

1. App.vue/main.js/pages.json/manifest.json/uni.scss，vueVersion=3；微信 AppID 留空并文档说明，不伪造正式 AppID。
2. 五个 tabBar：概览、设备、数据、工作台、我的。额外：账号登录、报警管理、自动化规则、AI 助手、支付演示、数据看板（现有大屏的手机布局）、仿真验证、研究工作台，共13页。每项原Web业务入口可达。
3. 安静、精致、信息密度适中的工作台；浅灰背景、白色卡片、墨绿主色，辅助蓝/琥珀状态色；不使用渐变、emoji、大段功能说明、外部字体/CDN；390px与窄屏无溢出；顶部标题/日期或设备上下文，清楚的状态标记。使用原生 tabBar，可用本地PNG图标，也可先只文字。
4. 演示状态所有页可见；数据明确为示例，禁用真实 API，登录不保存密码、不宣称真实认证。可使用“进入演示”按钮；个人页支持重置演示数据。
5. 不只是空壳：概览指标与短趋势（简单view柱状或轻量原生画法无需图表依赖）；设备筛选、详情、控制确认后只修改演示状态；历史时间筛选、分页/加载更多、导出明确未接入；报警筛选和确认；自动化规则开关/新增/删除确认；AI/RAG示例问答明确本地固定回复；支付模拟结果明确无真实扣款；仿真运行/重置并反馈状态；研究示例运行指标且不复制冻结论文结论。复杂表单可用modal或内嵌表单，不需要无限扩张。
6. 使用 components/ 共享 PageHeader/MetricCard/EmptyState 等适当组件，utils/demo-store.js 集中演示状态，config 模块说明模式和未来后端地址但不发请求。避免所有页共用同一个泛化模板；每页有业务结构。
7. 每个函数、生命周期回调、事件处理函数、composable前紧邻中文功能注释；不依赖Element Plus、浏览器DOM、window/document/localStorage或axios。导入路径兼容 HBuilderX，不使用未配置别名。全部导航用uni.switchTab/navigateTo；正确卸载定时器。
8. docs/miniapp-local-development.md：HBuilderX导入 miniapp、编译到微信开发者工具、两个工具真实路径(/Volumes/out/Applications/HBuilderX.app 与 wechatwebdevtools.app)、无AppID时工具可用的测试模式限制、演示边界、待接入功能、配置域名需后续确认。不能声称真机验证通过。
9. 合理的验证脚本 miniapp/scripts/verify.mjs（Node内置）：JSON结构、路由文件与资源存在、tabBar有效、页面script用本机已存在vue编译器解析也可由Codex做。不要伪装为实际微信编译。

## 验证

- node miniapp/scripts/verify.mjs
- git diff --check

完成后报告真实结果，Codex再独立审查与编译。不要声称已完成生产功能迁移。

## Codex 收口

worker 达到 40 轮上限后退出；页面已产出，Codex 直接完成最小修正和独立验证。
微信小程序编译、Web 编译、路由结构检查、浏览器演示交互与窄屏检查通过。
微信工具获取测试号遇到网络错误，模拟器和真机运行未验证；真实业务迁移不在本轮完成范围。
