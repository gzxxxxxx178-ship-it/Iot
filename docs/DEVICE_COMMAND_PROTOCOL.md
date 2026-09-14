# 设备控制确认协议

该协议用于区分服务端已发布 MQTT 与设备已接收并接受控制指令。适用于人工控制和自动化规则；仿真命令不使用此主题。

## Topic 与权限

- 服务端发布：`agri/<deviceId>/control`
- 设备回执：`agri/<deviceId>/status`
- 后端账号需对上述控制主题有写权限、状态主题有读权限；设备账号只能订阅自身控制主题并发布自身状态主题。

## 服务端指令

```json
{"commandId":"e1f0...","command":"start"}
```

`commandId` 为 UUID，单次命令唯一；`command` 仅允许 `start`、`stop`、`read`、`status`。控制消息使用 QoS 1 且不保留。

## 设备确认

```json
{"deviceId":"device001","commandId":"e1f0...","command":"start","status":"ACKNOWLEDGED"}
```

设备只应在已完成本地指令解析和接受后回传 `ACKNOWLEDGED`；无法接受时回传 `REJECTED`。普通在线状态消息不含 `commandId`，后端不会将其解释为命令确认。

## 状态机

```text
PENDING → DISPATCHED → ACKNOWLEDGED
                     ↘ REJECTED
                     ↘ TIMED_OUT
       ↘ FAILED
```

- `FAILED`：服务端无法发布 MQTT。
- `TIMED_OUT`：设备未在 `device.command.timeout-seconds`（默认 60 秒）内回执。
- ACK 必须与审计记录的设备 ID 和 `commandId` 同时匹配；重复 ACK 不改变终态。

## 发布前检查

1. 执行后端完整测试，确认 V4 Flyway 迁移随 JAR 发布。
2. 编译并烧录 ESP8266 固件；旧固件仅能接受文本命令，不能确认 JSON 命令。
3. 使用控制页面发送 `status`，确认命令表从 `DISPATCHED` 转为 `ACKNOWLEDGED`。
4. 断开设备或阻止状态回执，确认命令在超时后变为 `TIMED_OUT`。
