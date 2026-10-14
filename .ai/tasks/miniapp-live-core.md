# 小程序真实接口核心迁移

- **状态**：`DONE`
- **任务编号**：`MINI-04`
- **任务名称**：账号、设备、概览和历史接口接入

## 目标结果

保留13页演示，在“我的”配置后端地址并切换真实模式，用现有用户名密码登录；概览、设备和历史页可请求现有Java接口，设备人工控制展示真实命令状态。仅本地开发，不部署、不控制硬件验证。

## 已确认背景

- 工作区开始干净；Codex新增本任务和台账不属于worker业务改动。
- uni-app Vue3 HBuilderX工程，无package.json；使用uni.request，不使用axios/DOM/浏览器存储。当前所有页面读utils/demo-store.js。
- 后端所有接口返回{code:200,message,data}，HTTP错误和业务错误均检查。登录POST /api/auth/login {username,password} 返回{token,username}，Authorization: Bearer JWT已被后端支持。token仅内存，密码不存储。无需改Java。
- GET /api/dashboard/stats -> {deviceCount,onlineCount,avgTemp,avgHum}。GET /api/devices -> DeviceResponse[]；仔细读dto。water是原始ADC，不是cm；null不可显示0。
- GET /esp/history/page -> {content,page,size,totalElements,totalPages}，start/end是本地ISO LocalDateTime无Z，页码0起。EspEntity时间serverReceivedTime；温湿度字段temperature/humidity。
- POST /api/device/control {deviceId,command:'start'|'stop'} -> DeviceCommandEntity {commandId,deviceId,command,status,message,...}；GET /api/device/commands列表状态。实际service状态PENDING/DISPATCHED/ACKNOWLEDGED/REJECTED/FAILED/TIMED_OUT，HTTP成功不等于执行成功。
- 已有demo-store所有功能保持；未迁移的报警/自动化/AI/支付/仿真/研究/大屏继续标演示，不从真实账号状态伪装全功能已接入。

## 允许修改范围

- miniapp/config/、miniapp/api/、miniapp/utils/（不重写现有演示数据与功能）
- miniapp/composables/、miniapp/components/
- miniapp/pages/login/、mine/、overview/、devices/、data/
- miniapp/scripts/、miniapp/tests/
- docs/miniapp-local-development.md

## 禁止范围

- 不修改其他业务模块、Java、Web项目、manifest/AppID、Clash配置、AGENTS/CLAUDE/.claude/、任务书、台账、worker脚本。
- 不Git写入、不推送部署、不访问凭据、不读取application真实配置、不发真实网络/硬件请求。不运行HBuilder或原生UI，由Codex独立验收。

## 实现要求

1. 连接配置支持demo/live，默认demo，页面可编辑HTTP(S)后端地址（局域网/localhost开发用，排除URL凭据、查询/hash等危险地址），仅配置可用uni存储持久化。保存更换地址/模式、退出、401必须清空token/真实用户数据。未认证真实页提示登录；没有后端数据就空白/错误，不回退演示。
2. 专用uni.request Promise封装，固定超时，统一HTTP/业务/网络错误；Bearer仅发当前配置origin，auth接口不带token，忽略cookie会话。通过会话generation或等效机制防止旧账号/旧地址/已退出的异步响应写回（包括旧401清新token）。真实数据单独store，不共享演示state；所有函数/API/composable前中文功能注释。
3. 登录页提供真实登录和原演示入口；真实模式需先配地址。无需注册/OAuth本轮。密码提交后清空、加载禁重复提交、错误反馈。我的显示正确用户名/真实状态/配置保存/退出；演示重置仅影响demo。
4. PageHeader增加显式badge属性，默认演示；仅接入的页面传入真实模式badge，不让其他demo页误标真实。真实概览用后端stats显示在线数、总设备、均温湿，不显示演示报警规则/七日趋势，提示这些待接入。water各真实页必须标ADC，禁止cm。
5. 设备列表/详情真实映射、刷新/加载失败提示、离线禁控制、二次确认、pending禁止重复发送；控制回执列表显示状态，通过手动刷新获取新状态即可，无需WebSocket/轮询。本轮不做设备CRUD。不要把SENT当执行成功，也不要乐观改linkage。
6. 历史真实分页、设备筛选、24h/7d/30d、加载更多、刷新/筛选重置、固定查询截止时间、并发过期请求忽略，空/错误/重试友好。CSV仍明确未接入。演示原分页可用。
7. onShow刷新或按需刷新，无需WebSocket本轮。退出/换模式时页面本地详情/历史不得残留上一会话。接口失败不可留下旧账号数据。
8. 更新verify.mjs，放行仅专用api/request.js的uni.request；保留浏览器API禁用，不为通过静态检查删除规则。新建有价值的node内置测试（可vm.SourceTextModule+vue stub，node --experimental-vm-modules --test miniapp/tests/*.test.mjs）。验证Bearer、登录无token、错误与超时、401清会话、旧请求/旧401不能污染新账号、配置校验、DTO null与ADC/分页边界。测试用mock uni不发网络、不安装依赖。
9. 文档更新本轮功能、配置步骤、电脑localhost与真机LAN差别、开发者工具本地HTTP需要用户手动启用局部开发调试域名选项（不改manifest全局TLS/urlCheck、不涉及发布）。明确尚未完成模块。

## 验收与验证

- node miniapp/scripts/verify.mjs
- node --experimental-vm-modules --test miniapp/tests/*.test.mjs
- git diff --check
- Codex再独立HBuilderX编译和页面验收。报告真实执行测试，不把mock测试当Java端到端。

## Codex 执行记录

- 受管 worker 读取后持续未产出完整实现，停止后仅留下一个配置文件，未提供报告。Codex 重新实现配置并完成其余修改、完整审查和独立验证。
- 7项隔离平台契约测试、静态检查、17项后端定向测试通过；HBuilderX微信编译成功，微信模拟器连接页面可见。
- Web使用仅监听本机的临时HTTP替身验收登录成功/失败、设备空读数、控制确认及DISPATCHED回执、历史25条分页、退出清空；320px检查通过。临时服务与账号未写入业务配置或仓库。
- 本机Java后端未运行，无真实账号端到端和硬件ACK验收；本轮未控制真实设备、未部署。剩余模块见台账。
