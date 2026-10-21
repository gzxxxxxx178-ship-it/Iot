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
PENDING → DISPATCHING → DISPATCHED
              │              │
              └──────┬───────┘
                     ├→ ACKNOWLEDGED
                     ├→ REJECTED
                     └→ TIMED_OUT
              └→ FAILED
PENDING → TIMED_OUT
```

- `PENDING`：命令意图已保存；接口返回表示受理。外层业务事务提交后才允许派发，回滚不会发布。
- `DISPATCHING`：通过数据库条件更新抢占派发权并提交；MQTT 发布在数据库事务外执行。此时已允许设备回执，因此快速 ACK 不会因记录未提交而丢失。
- `DISPATCHED`：MQTT 发布已返回，尚未收到设备确认。发布结果只更新仍处于 `DISPATCHING` 的记录，不覆盖快速 ACK。
- `FAILED`：派发前权限/设备状态校验失败，或 MQTT 发布抛出异常。传输异常不能证明设备没有收到消息，需核查设备状态后再决定是否创建新命令。
- `TIMED_OUT`：等待派发超过有效期，或派发开始后未在 `device.command.timeout-seconds`（默认 60 秒）内取得终态。派发超时以 `dispatched_at` 为起点，兼容旧记录时回退到创建时间；超时只表示执行结果未确认。
- ACK 必须与审计记录的设备 ID 和 `commandId` 同时匹配，且记录处于 `DISPATCHING` 或 `DISPATCHED`。ACK、发布失败和超时通过带状态条件的更新竞争，先取得的终态保留；重复或迟到 ACK 不改变终态。

## 中断恢复与投递边界

- 每隔 `device.command.dispatch-interval-ms`（默认 1000 毫秒）扫描最多 100 条仍在有效期内的 `PENDING`，恢复提交后尚未开始派发的命令；超期待办关闭为 `TIMED_OUT`，避免迟到启动。
- 多个派发器竞争同一待办时，仅成功抢占的一方允许发布。派发时重新检查发起账号权限及设备启停状态。
- 已抢占的 `DISPATCHING` 在进程中断后不自动重发，因为无法判断中断发生在发布前还是发布后；未取得回执时由超时扫描关闭。此策略可能留下未执行的命令，避免服务端主动重复设备动作。
- MQTT QoS 1 本身允许重复投递；服务端抢占机制不等于设备动作恰好执行一次。固件仍需按 `commandId` 去重，尚待 REV-07 验收。

## 发布前检查

1. 执行后端完整测试，确认 V4、V8 Flyway 迁移随 JAR 发布；V8 为命令表增加派发时间及状态扫描索引，必须在新代码启动前完成迁移。本轮 H2 测试不代替目标 MySQL/TiDB 迁移验收。
2. 编译并烧录 ESP8266 固件；旧固件仅能接受文本命令，不能确认 JSON 命令。
3. 使用控制页面发送 `status`，确认命令表从 `DISPATCHED` 转为 `ACKNOWLEDGED`。
4. 断开设备或阻止状态回执，确认命令在超时后变为 `TIMED_OUT`。
