# 小程序本地开发

## 当前状态（2026-10-16）

`miniapp/` 为HBuilderX传统uni-app Vue3工程。MINI-05已删除演示状态、预置设备、伪造历史、模拟订单、固定问答与虚构研究/仿真结果，只使用真实后端。

| 功能 | 当前状态 |
|---|---|
| 账号密码登录、退出 | 已接入，令牌仅内存保存 |
| 概览 | 后端在线设备最新温湿度均值、设备总数及在线数 |
| 设备列表、详情、人工控制、命令回执 | 已接入 |
| 历史查询 | 按设备、24小时/7天/30天筛选，服务端分页 |
| 报警管理 | 真实规则列表、创建、启停、删除及最近100条记录 |
| 自动化规则 | 真实规则增改删、启停和执行记录；启动/停止规则启用前二次确认，只能选择本人设备 |
| AI 助手 | 真实后端会话、历史加载、发送、失败重试和当前会话清除；会话ID按账号保存在本机 |
| 数据看板 | 后端设备状态分布、在线设备平均温湿度及各设备最新读数/最近上报时间 |
| 支付、仿真、研究工作台 | 保留页面入口，尚未迁移真实业务接口，不显示本地演示结果 |
| 注册、OAuth、WebSocket、CSV导出、设备档案编辑 | 待迁移 |

新增报警、自动化、AI与看板页面均使用统一真实请求模块。水位始终展示为传感器 ADC 原始值；看板的状态柱图表示设备数量，不代表传感器趋势。自动化启动/停止动作可能下发硬件命令，用户必须在小程序确认后才能保存启用规则。

## 启动本机后端

使用已有且被Git忽略的开发配置，不以测试Profile替代真实后端：

```bash
cd java
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

默认Profile未携带本机数据库密码，直接启动会认证失败。现有dev配置已启动成功，地址为 `http://localhost:8080`。数据库、Redis和MQTT由后端配置负责，小程序不保存这些凭据。`GET /actuator/health`返回UP不能证明MQTT已连接或设备已上报；设备接口未认证返回HTTP401。

## 编译与登录

1. HBuilderX路径：`/Volumes/out/Applications/HBuilderX.app`。导入 `miniapp/`，运行到微信开发者工具（`/Volumes/out/Applications/wechatwebdevtools.app`）。
2. 微信工具打开 **`miniapp/unpackage/dist/dev/mp-weixin/`** 编译目录，不是源码目录。微信测试AppID为 `wx072082beff11b3e7`，位于manifest的mp-weixin字段，与根级DCloud AppID不同。
3. 「我的」默认后端为 `http://localhost:8080`。如需更换，仅填写根地址，不含 `/api`、查询参数或账号密码。旧版演示配置自动迁移为真实模式。
4. 「账号登录」使用现有系统账号。密码提交后输入框清空，令牌仅内存保存，重启后重新登录；本地仅保存后端地址。
5. 概览和设备页进入时刷新，也可手动刷新。历史查询冻结一次分页的截止时间，筛选变化后重置。
6. 如开发工具拦截本机HTTP，由开发者在该项目「详情→本地设置」启用本地调试选项「不校验合法域名、web-view（业务域名）、TLS版本以及HTTPS证书」。仓库未关闭manifest域名校验，此选项不适用于发布。

模拟器可访问电脑localhost；真机的localhost指手机自身，需改电脑局域网地址。浏览器预览还受后端CORS限制。正式发布需单独完成合法HTTPS域名配置。

## 数据与控制语义

- 未登录、缺测或接口失败时不补入演示数据。切换地址、退出或认证失效清空真实数据，迟到响应不能恢复旧账号状态。
- 水位为原始ADC，不是厘米；缺测显示 `--`。历史使用服务端接收时间。
- 人工控制二次确认后操作真实设备。DISPATCHED仅表示已下发，ACKNOWLEDGED才表示设备确认；刷新读取回执，不乐观修改设备运行状态。
- 网络超时可能发生在后端已接收命令之后，先刷新回执再决定是否重试。退出只清除本机令牌，后端目前没有JWT服务端撤销能力。

## 联调证据与阻塞

- 2026-10-14本机dev后端启动成功，8080监听，健康接口UP。未认证设备查询HTTP401。
- 本机 `device001` 最新记录为2026-07-15 17:19:33，未证明本次通电产生新上报。未删除真实历史或修改用户密码。
- MQTT持续退避重试。TUN下域名原为Fake-IP；追加当前项目服务器直连/真实解析后已解析到真实IP，但TLS握手仍超时/提前关闭，未确认Broker连接成功。
- 用户已通过正常认证登录pogacar。原设备归属核对为gzxxxxxx178；用户明确授权仅本机迁移后，已将device001档案与2538条历史记录迁移到pogacar，页面刷新可见设备且正确显示离线。未绕过认证、修改其他设备归属或发送硬件控制命令。
- 归属迁移前备份保存在本机忽略目录 `.ai/runtime/device001-owner-before-20261014.json`，仅包含指定设备档案的原归属/更新时间及历史行ID/原归属，不含密码。远程数据库未修改。MQTT与新上报验证仍受阻。

微信TUN恢复见 [网络说明](clash-wechat-tun.md)。额外IoT补丁为 `scripts/clash_iot_direct.js`，当前自建订阅在原main和微信补丁之后追加；只处理项目服务器，不关闭TUN或证书校验。应用前脚本备份在本地忽略目录 `miniapp/unpackage/qa/clash-before-iot-script.js`，恢复该脚本并重新激活当前订阅可撤销IoT部分。DNS修复不代表MQTT已连通。

## 验证

### 本机测试账户（2026-10-15）

用户指定的LOCAL测试账户test已创建在本机开发库，BCrypt保存密码；登录、当前用户、设备和概览接口认证验证通过。账户当前没有设备，未自动转移pogacar的设备。账户密码不保存在仓库文档或业务源码中。

用户授权仅本地调试后，已在微信开发者工具当前项目本地设置启用不校验域名/HTTPS的调试例外，仅影响忽略目录内的project.private.config.json。源码manifest及生成project.config.json保持urlCheck=true。微信模拟器实际登录test成功，概览刷新及“我的”已连接状态验证通过，截图在miniapp/unpackage/qa/wechat-test-account.png。

当前本机开发配置曾缺少JWT签名密钥，导致密码认证通过后仍无法签发令牌。已将随机密钥保存在被Git忽略的application-dev.properties中并重启后端，未修改生产配置或放宽注册密码长度。

```bash
node miniapp/scripts/verify.mjs
node --experimental-vm-modules --test miniapp/tests/*.test.mjs
node --test scripts/tests/clash_wechat_direct.test.cjs scripts/tests/clash_iot_direct.test.cjs
```

9项小程序契约测试覆盖认证、旧会话隔离、错误、控制回执、历史分页、旧配置迁移及GET/DELETE查询参数编码；6项分流测试使用脱敏配置。新增页面通过小程序静态结构检查和 Vue 脚本/模板编译；最近新增页面尚未在 HBuilderX/微信开发者工具中完成逐页视觉验收，真机及硬件ACK也尚未验收。

MINI-04曾以临时HTTP替身验收登录、控制确认、25条分页、退出清空和320px布局，替身已停止，当前不提供演示入口。编译产物和截图位于忽略目录 `miniapp/unpackage/`。
