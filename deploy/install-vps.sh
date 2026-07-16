#!/usr/bin/env bash
set -euo pipefail

# 在新 Ubuntu VPS 上以 root 执行。真实密钥仍需通过 /opt/iot/iot.env 手工注入。
if [[ "$(id -u)" -ne 0 ]]; then
    echo "请使用 root 执行此脚本" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="${APP_DIR:-/opt/iot}"

id iot >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin iot
install -d -o iot -g iot -m 0750 "$APP_DIR" "$APP_DIR/releases"
install -m 0750 -o root -g root "$SCRIPT_DIR/healthcheck.sh" "$APP_DIR/healthcheck.sh"
install -m 0644 -o root -g root "$SCRIPT_DIR/systemd/iot.service" /etc/systemd/system/iot.service
install -d -m 0755 /etc/systemd/journald.conf.d
install -m 0644 -o root -g root "$SCRIPT_DIR/systemd/99-iot-journald.conf" \
    /etc/systemd/journald.conf.d/99-iot-journald.conf
systemctl restart systemd-journald
journalctl --vacuum-size=200M >/dev/null

if [[ ! -f "$APP_DIR/iot.env" ]]; then
    install -m 0600 -o root -g root /dev/null "$APP_DIR/iot.env"
    echo "已创建 $APP_DIR/iot.env，请填入生产环境变量后再启动 iot 服务。" >&2
fi

systemctl daemon-reload
systemctl enable iot
echo "systemd 已安装。填写 $APP_DIR/iot.env 后，将首个 JAR 放入 $APP_DIR/releases 并创建 current 链接。"
