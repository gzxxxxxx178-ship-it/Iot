#!/usr/bin/env bash
set -euo pipefail

# 本地执行：构建、上传、按 systemd 当前配置切换版本，并在健康检查失败时自动回滚。
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_DIR="$ROOT_DIR/java"
VPS_HOST="${VPS_HOST:?请设置 VPS_HOST，例如 api.example.com}"
VPS_USER="${VPS_USER:-root}"
VPS_APP_DIR="${VPS_APP_DIR:-/opt/iot}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
HEALTHCHECK_TIMEOUT_SECONDS="${HEALTHCHECK_TIMEOUT_SECONDS:-90}"
if [[ ! "$KEEP_RELEASES" =~ ^[1-9][0-9]*$ ]]; then
    echo "KEEP_RELEASES 必须是正整数" >&2
    exit 1
fi
if [[ ! "$HEALTHCHECK_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
    echo "HEALTHCHECK_TIMEOUT_SECONDS 必须是正整数" >&2
    exit 1
fi

cd "$JAVA_DIR"
./mvnw -q clean package -DskipTests

ARTIFACT="target/IoTSystem-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$ARTIFACT" ]]; then
    echo "构建产物不存在: $ARTIFACT" >&2
    exit 1
fi

COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
VERSION="$(date +%Y%m%d%H%M%S)-${COMMIT}"
REMOTE_STAGING="${VPS_APP_DIR}/.deploy-${VERSION}.jar"
ARTIFACT_BYTES="$(wc -c < "$ARTIFACT" | tr -d '[:space:]')"

ssh "${VPS_USER}@${VPS_HOST}" bash -s -- "$VPS_APP_DIR" "$ARTIFACT_BYTES" <<'REMOTE_PREPARE'
set -euo pipefail
APP_DIR="$1"
ARTIFACT_BYTES="$2"
mkdir -p "$APP_DIR"
AVAILABLE_KB="$(df -Pk "$APP_DIR" | awk 'NR == 2 {print $4}')"
REQUIRED_KB="$(((ARTIFACT_BYTES + 32 * 1024 * 1024 + 1023) / 1024))"
if (( AVAILABLE_KB < REQUIRED_KB )); then
    echo "磁盘空间不足：需要至少 $((REQUIRED_KB / 1024)) MiB，可用 $((AVAILABLE_KB / 1024)) MiB" >&2
    exit 1
fi
REMOTE_PREPARE

scp "$ARTIFACT" "${VPS_USER}@${VPS_HOST}:${REMOTE_STAGING}"

ssh "${VPS_USER}@${VPS_HOST}" bash -s -- \
    "$VPS_APP_DIR" "$REMOTE_STAGING" "$VERSION" "$KEEP_RELEASES" "$HEALTHCHECK_TIMEOUT_SECONDS" <<'REMOTE_SCRIPT'
set -euo pipefail
APP_DIR="$1"
STAGING="$2"
VERSION="$3"
KEEP_RELEASES="$4"
HEALTHCHECK_TIMEOUT_SECONDS="$5"
CURRENT="$APP_DIR/releases/current"
SERVICE_USER="$(systemctl show iot -p User --value)"
SERVICE_USER="${SERVICE_USER:-root}"
SERVICE_GROUP="$(id -gn "$SERVICE_USER")"
EXEC_START="$(systemctl show iot -p ExecStart --value)"
if [[ ! "$EXEC_START" =~ -jar[[:space:]]+([^[:space:];]+) ]]; then
    echo "无法从 iot systemd 配置识别 JAR 路径：$EXEC_START" >&2
    rm -f "$STAGING"
    exit 1
fi
JAR_TARGET="${BASH_REMATCH[1]}"

if [[ "$JAR_TARGET" == "$CURRENT" ]]; then
    DEPLOY_MODE="releases"
    RELEASE="$APP_DIR/releases/IoTSystem-${VERSION}.jar"
    PREVIOUS_TARGET=""
    if [[ -L "$CURRENT" ]]; then
        PREVIOUS_TARGET="$(readlink -f "$CURRENT" || true)"
    fi
    install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" "$APP_DIR/releases"
    chown "$SERVICE_USER:$SERVICE_GROUP" "$STAGING"
    mv -f "$STAGING" "$RELEASE"
    LINK_TMP="$APP_DIR/releases/.current-${VERSION}"
    ln -s "$RELEASE" "$LINK_TMP"
    mv -Tf "$LINK_TMP" "$CURRENT"
elif [[ "$JAR_TARGET" == "$APP_DIR/"*.jar ]]; then
    DEPLOY_MODE="fixed"
    RELEASE="$JAR_TARGET"
    BACKUP="${JAR_TARGET}.backup-${VERSION}"
    if [[ ! -f "$RELEASE" ]]; then
        echo "systemd 配置的当前 JAR 不存在：$RELEASE" >&2
        rm -f "$STAGING"
        exit 1
    fi
    # 同目录硬链接作为回滚副本，不额外占用一个 JAR 的磁盘空间。
    ln "$RELEASE" "$BACKUP"
    chown "$SERVICE_USER:$SERVICE_GROUP" "$STAGING"
    mv -f "$STAGING" "$RELEASE"
else
    echo "不支持的 systemd JAR 路径：$JAR_TARGET" >&2
    rm -f "$STAGING"
    exit 1
fi

healthcheck() {
    if [[ -x "$APP_DIR/healthcheck.sh" ]]; then
        HEALTHCHECK_TIMEOUT_SECONDS="$HEALTHCHECK_TIMEOUT_SECONDS" \
            "$APP_DIR/healthcheck.sh" "http://127.0.0.1:8080"
        return
    fi

    local deadline=$((SECONDS + HEALTHCHECK_TIMEOUT_SECONDS))
    while (( SECONDS < deadline )); do
        if curl --silent --show-error --max-time 5 \
            "http://127.0.0.1:8080/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

rollback() {
    echo "$1，开始回滚" >&2
    if [[ "$DEPLOY_MODE" == "releases" ]]; then
        if [[ -n "$PREVIOUS_TARGET" && -f "$PREVIOUS_TARGET" ]]; then
            LINK_TMP="$APP_DIR/releases/.rollback-${VERSION}"
            ln -s "$PREVIOUS_TARGET" "$LINK_TMP"
            mv -Tf "$LINK_TMP" "$CURRENT"
        else
            rm -f "$CURRENT"
        fi
    elif [[ -f "$BACKUP" ]]; then
        mv -f "$BACKUP" "$RELEASE"
    fi
    systemctl restart iot || true
    healthcheck || true
}

if ! systemctl restart iot; then
    rollback "新版本重启失败"
    exit 1
fi

if ! healthcheck; then
    rollback "新版本健康检查失败"
    exit 1
fi

if [[ "$DEPLOY_MODE" == "releases" ]]; then
    find "$APP_DIR/releases" -maxdepth 1 -type f -name 'IoTSystem-*.jar' -printf '%T@ %p\n' \
        | sort -nr | tail -n +$((KEEP_RELEASES + 1)) | cut -d' ' -f2- \
        | xargs -r rm -f
    echo "部署成功：$(readlink -f "$CURRENT")"
else
    echo "部署成功：${RELEASE}；回滚副本：${BACKUP}"
fi
REMOTE_SCRIPT
