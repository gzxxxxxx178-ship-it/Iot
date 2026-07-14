# Mosquitto 生产配置

生产 Broker 使用 `38.47.98.235.nip.io:8883`、TLS 1.2+、账号密码认证和最小 Topic ACL。禁止开放匿名 1883 监听器。

## 文件位置

- `iot.conf` → `/etc/mosquitto/conf.d/iot.conf`
- `acl` → `/etc/mosquitto/auth/acl`
- `certs/iot-ca.crt` → 可公开分发的 IoT 根证书，后端和固件固定信任
- CA 私钥、Broker 私钥（不进入 Git）→ `/etc/mosquitto/pki/`
- 密码库（不进入 Git）→ `/etc/mosquitto/auth/passwd`

## 独立轮换凭据

```bash
# 后端账号
mosquitto_passwd /etc/mosquitto/auth/passwd iot_backend

# 单台设备账号
mosquitto_passwd /etc/mosquitto/auth/passwd device001

systemctl reload mosquitto
```

轮换后同步更新 `/opt/iot/application-prod.properties` 或设备本地 `secrets.h`。不得将真实密码或任何私钥写入本目录。

## 证书边界

MQTT 使用独立私有 CA，避免公网证书链变化造成 ESP8266 BearSSL 握手不兼容。当前 CA 有效期至 2036-07-11，Broker 证书有效期为签发日起 5 年。轮换 Broker 证书时必须保留相同 SAN；轮换 CA 时必须先更新后端和设备信任证书，再切换 Broker。

## 验证

```bash
mosquitto_sub -h 38.47.98.235.nip.io -p 8883 --cafile /etc/mosquitto/pki/ca.crt \
  -u '<账号>' -P '<密码>' -t 'agri/device001/data' -C 1
```
