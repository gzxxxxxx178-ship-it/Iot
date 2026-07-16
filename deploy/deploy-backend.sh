#!/usr/bin/env bash
set -euo pipefail

# 本地执行：构建、上传、原子切换版本，并在健康检查失败时自动回滚。
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_DIR="$ROOT_DIR/java"
VPS_HOST="${VPS_HOST:?请设置 VPS_HOST，例如 api.example.com}"
VPS_USER="${VPS_USER:-root}"
VPS_APP_DIR="${VPS_APP_DIR:-/opt/iot}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"

cd "$JAVA_DIR"
./mvnw -q clean package -DskipTests

ARTIFACT="target/IoTSystem-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$ARTIFACT" ]]; then
    echo "构建产物不存在: $ARTIFACT" >&2
    exit 1
fi

COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
VERSION="$(date +%Y%m%d%H%M%S)-${COMMIT}"
REMOTE_RELEASE="${VPS_APP_DIR}/releases/IoTSystem-${VERSION}.jar"

ssh "${VPS_USER}@${VPS_HOST}" "mkdir -p '${VPS_APP_DIR}/releases' && chown -R iot:iot '${VPS_APP_DIR}'"
scp "$ARTIFACT" "${VPS_USER}@${VPS_HOST}:${REMOTE_RELEASE}"

ssh "${VPS_USER}@${VPS_HOST}" bash -s -- "$VPS_APP_DIR" "$REMOTE_RELEASE" "$KEEP_RELEASES" <<'REMOTE_SCRIPT'
set -euo pipefail
APP_DIR="$1"
RELEASE="$2"
KEEP_RELEASES="$3"
CURRENT="$APP_DIR/releases/current"
PREVIOUS_TARGET=""
if [[ -L "$CURRENT" ]]; then
    PREVIOUS_TARGET="$(readlink -f "$CURRENT")"
fi

chown iot:iot "$RELEASE"
ln -sfn "$RELEASE" "$CURRENT"
systemctl restart iot

if ! /opt/iot/healthcheck.sh "http://127.0.0.1:8080"; then
    echo "新版本健康检查失败，开始回滚" >&2
    if [[ -n "$PREVIOUS_TARGET" && -f "$PREVIOUS_TARGET" ]]; then
        ln -sfn "$PREVIOUS_TARGET" "$CURRENT"
        systemctl restart iot
        /opt/iot/healthcheck.sh "http://127.0.0.1:8080" || true
    fi
    exit 1
fi

find "$APP_DIR/releases" -maxdepth 1 -type f -name 'IoTSystem-*.jar' -printf '%T@ %p\n' \
    | sort -nr | tail -n +$((KEEP_RELEASES + 1)) | cut -d' ' -f2- \
    | xargs -r rm -f
echo "部署成功: $(readlink -f "$CURRENT")"
REMOTE_SCRIPT
