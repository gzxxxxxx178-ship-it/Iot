# 后端可重复部署、健康检查与回滚

本目录只包含模板和脚本，不包含 VPS 密码、数据库密码、JWT 密钥、MQTT 凭据或私钥。脚本默认使用 SSH Key，不读取或保存密码。

## 首次 VPS 安装

在 Ubuntu VPS 上以 root 执行：

```bash
sudo bash install-vps.sh
sudoedit /opt/iot/iot.env
```

`iot.env` 使用 `KEY=value` 格式，权限应为 `0600`。生产必须显式使用 `prod` Profile；systemd 模板已经固定传入 `--spring.profiles.active=prod`。数据库结构由 Flyway 管理，JPA 只执行 `validate`。

安装 Nginx 后，将 `nginx/iot.conf.template` 中的 `${SERVER_NAME}` 替换为后端域名，执行 `nginx -t` 后再 reload。TLS 证书建议由 Certbot 管理。

## 发布与自动回滚

在本地仓库根目录执行：

```bash
VPS_HOST=api.example.com VPS_USER=root ./deploy/deploy-backend.sh
```

脚本会先检查服务器剩余空间，再构建并上传 JAR。它读取 `iot` systemd 单元实际配置的 `-jar` 路径，因此兼容两种布局：新安装使用 `/opt/iot/releases/current` 原子切换版本；仍使用 `/opt/iot/IoTSystem-0.0.1-SNAPSHOT.jar` 的旧服务会原子替换固定路径，并用同文件系统硬链接保留回滚副本。运行账户从 systemd 的 `User` 字段读取，未设置时按 systemd 默认的 root 处理。

重启后脚本优先调用服务器上的 `/opt/iot/healthcheck.sh`；旧服务器没有该脚本时，直接轮询 `http://127.0.0.1:8080/actuator/health`。检查失败会恢复前一版本并重启。版本目录模式默认保留最近 5 个版本，可用 `KEEP_RELEASES` 调整；固定路径模式保留每次成功部署前的 JAR 回滚副本。健康检查最长等待 90 秒，可用 `HEALTHCHECK_TIMEOUT_SECONDS` 调整。

发布前建议先运行：

```bash
cd java && ./mvnw -q -Dspring.profiles.active=test test
cd ../vue/IoT && npm run build && npm run test:api-contract
```

若本次发布包含设备控制功能，另按 [`设备控制确认协议`](../docs/DEVICE_COMMAND_PROTOCOL.md) 完成固件烧录与 ACK、超时两类现场复验；仅后端测试通过不构成设备执行成功的证明。

## 手工检查

```bash
systemctl status iot
journalctl -u iot -n 100 --no-pager
curl --fail https://api.example.com/actuator/health
nginx -t
systemctl reload nginx
```

健康端点只返回聚合状态，不显示数据库、Redis 或 MQTT 连接细节；组件故障应从 systemd 日志和应用日志定位。

## 日志保留上限

`systemd/99-iot-journald.conf` 将持久化日志限制为 200MB、运行时日志限制为 100MB，并保留至少 500MB 空闲磁盘空间、最多 14 天。`install-vps.sh` 会安装该配置并执行一次 `journalctl --vacuum-size=200M`。如 VPS 磁盘小于 10GB，不建议提高该上限。
