# 微信开发者工具与 Clash Verge TUN 兼容修复

## 本机结果（2026-10-14）

在 Clash Verge Rev 2.5.8 / Mihomo 1.19.32 中，为当前选中的「自建节点」订阅追加局部扩展脚本，保持 TUN 开启及微信开发者工具直连。原有订阅脚本和节点组调整逻辑已保留。

修复前，`mp.weixin.qq.com` 解析为 Fake-IP `198.18.1.145`；基础库下载请求出现 `SSL_ERROR_SYSCALL`。修复后，微信平台与基础库域名返回真实 IP，HTTPS 请求均返回 200，测试基础库下载完整（16,900,447 字节）。微信开发者工具重新编译后概览正常显示，运行日志显示基础库 3.17.4。观察到预加载及灰度库提示，未观察到阻止页面启动的错误。

这些结果支持当前订阅下的兼容修复，不能据此断言所有 TUN、Fake-IP 或代理节点均有同类问题。此前基础库核心脚本 HTTP 500 是故障表现；用户的 TUN 开关对照及本轮验证补充了网络触发因素。

## 改动范围

只为以下域名追加高优先级 DIRECT 规则、Fake-IP 过滤和直连 DoH 解析策略：

- `weixin.qq.com`
- `servicewechat.com`
- `wechat.com`

DoH 使用阿里云和 DNSPod 的 HTTPS 接口，保留证书校验。其余域名的 DNS 配置、路由顺序、节点和代理组保留原值。没有全局切换 DNS 模式、关闭 TUN 或关闭 TLS 校验。

可复用补丁：`scripts/clash_wechat_direct.js`。将其完整追加到现有订阅扩展脚本的末尾，先运行原 `main`，再追加微信处理。脚本应只追加一次；规则变换本身可重复执行。支持 `blacklist` 和 `rule` 模式，遇到 `whitelist` 会明确拒绝，避免改变原语义。

Clash Verge 2.5.5 之后扩展配置中的 DNS 字段可能整体替换，因此采用脚本逐项追加，避免覆盖原 DNS 策略。依据：[Clash Verge 扩展机制](https://www.clashverge.dev/guide/extend.html)、[Mihomo DNS 配置](https://wiki.metacubex.one/config/dns/)。

## 生效范围与回退

当前补丁仅作用于「自建节点」订阅，切换其他订阅不会自动继承。保留这种局部范围，便于回退与隔离其他订阅。

原脚本备份在本机 `miniapp/unpackage/qa/clash-original-profile-script.js`，不纳入 Git。恢复时进入 Clash Verge「订阅 → 自建节点右键 → 扩展脚本」，将内容替换为该备份并保存。也可仅删除追加部分，从「微信开发工具 TUN 兼容补丁」注释开始到文件末尾，保留原 `main`。

## 复验

1. 确认当前订阅为「自建节点」，TUN 开启；微信开发者工具选择「不使用任何代理」。
2. 检查微信域名返回真实 IP，并验证基础库 HTTPS 下载。
3. 在微信开发者工具点击「工具 → 编译」，确认概览和页面切换正常。
4. 本机截图：`miniapp/unpackage/qa/wechat-tun-fixed.png`（不纳入 Git）。
5. 补丁回归检查：`node --test scripts/tests/clash_wechat_direct.test.cjs`。使用脱敏配置，不读取真实节点、订阅链接或凭据。
