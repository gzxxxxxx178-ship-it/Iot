# ESP8266 采集固件

目标板为 `esp8266:esp8266:nodemcuv2`，当前引脚为 DHT11 GPIO5、水位 ADC A0。`water` 保持 0–1023 ADC 原始单位；当前代码不包含泵阀驱动，`linkage` 只是传感器条件标志。

## 固定依赖

| 依赖 | 验证版本 |
|---|---|
| Arduino CLI | 1.5.1 |
| ESP8266 Community | 3.1.2 |
| ArduinoJson | **6.21.5**，代码编译时检查精确版本 |
| PubSubClient | 2.8 |
| DHT sensor library | 1.4.7 |
| Adafruit Unified Sensor | 1.1.15 |

可通过 Arduino 库管理器安装对应版本，或使用以下命令准备环境；ESP8266 板卡索引为 `https://arduino.esp8266.com/stable/package_esp8266com_index.json`。

```bash
arduino-cli core install esp8266:esp8266@3.1.2
arduino-cli lib install 'ArduinoJson@6.21.5' 'PubSubClient@2.8' 'DHT sensor library@1.4.7' 'Adafruit Unified Sensor@1.1.15'
```

新增解析依赖来自 [ArduinoJson 6.21.5 官方发布](https://github.com/bblanchon/ArduinoJson/releases/tag/v6.21.5)。解析使用固定 `StaticJsonDocument<512>` 和最大两层嵌套；命令回执使用固定文档和 320 字节输出缓冲。处理器缓存共 16 条，宿主构建 `sizeof(CommandProtocol)=1488` 字节，具体目标布局以板卡编译为准。

## 命令与回执

标准载荷为 `{"commandId":"<唯一标识>","command":"status"}`，只允许这两个字段；JSON 动作区分大小写。`commandId` 为 1–64 个 ASCII 字母、数字、下划线或连字符，后端使用 UUID。最大载荷 384 字节。支持常规空白、字段顺序变化和合法字符串转义；重复键、尾随内容、嵌入 NUL、过深嵌套、错误根类型及非法 ID 不执行。

| 命令 | 当前实际效果 |
|---|---|
| `start` | 开启每 5 秒周期采样上报；已开启时不重置计时或累计发送次数 |
| `stop` | 停止周期上报，保持 MQTT 控制连接 |
| `read` | 单次发送有效读数，停止周期上报时仍可执行；距上次采样尝试不足 2 秒时复用有效样本 |
| `status` | 发布周期上报开关、采样质量和已有有效读数；无有效样本时读数为 null |

未知动作或合法 ID 携带无效动作字段返回 `REJECTED`，不执行。无法安全解析完整对象或标识时直接丢弃，后端以超时收口。传感器失败、遥测或状态写出失败也不能确认对应 `read/status` 成功。启动时没有有效样本；失败采样会撤销旧读数的有效状态，失败重试也遵守两秒间隔。

成功处理后回执包含 `deviceId`、`commandId`、`command`、`status`，其中 `status` 为 `ACKNOWLEDGED` 或 `REJECTED`，始终 `retain=false`。字段通过 JSON 库序列化，不拼接外部字符串。

- 在缓存窗口内，相同 ID 和动作只重发原结果；失败的原始动作也不自动重新执行。同 ID 不同动作先重发原结果、再返回冲突拒绝，不覆盖原结果；两者分别保留发送待办。
- 每个槽位每秒最多重试一轮，离线保留待办，重连后继续；32 位毫秒计时支持回绕。只有回执成功写出的槽位可被环形淘汰。16 条均待发送时，新增命令不执行，后端可能超时。
- 缓存仅在 RAM 中。旧记录被淘汰或设备重启后，相同 ID 可能再次执行，不能视为持久化的“恰好一次”。旧纯文本命令保留大小写兼容，无 ID、无审计回执，也无去重保证；后端标准控制使用 JSON。
- PubSubClient 2.8 的普通 publish 不提供发布 QoS 参数，返回 true 只证明本地写出；重试覆盖明确的发送失败，不能证明服务端已收到。依据为[官方版本实现](https://github.com/knolleary/pubsubclient/blob/v2.8/src/PubSubClient.cpp)。服务端超时及迟到 ACK 处理见[命令协议](../docs/DEVICE_COMMAND_PROTOCOL.md)。

当前服务端在线状态依据最近遥测时间；停止周期上报后，列表可能显示离线，这不等于 MQTT 控制连接已断开。人工恢复可使用网页设备列表的“开始上报”；小程序当前在线条件控制的使用限制仍需结合现场验证。

## 离线验收

宿主测试直接调用固件共用的 `CommandProtocol.h` 和 `SensorSampleState.h`，使用本地编译器及 ASan/UBSan，不连接 Broker 或真实设备：

```bash
node arduino/tests/run_tests.mjs /path/to/ArduinoJson-6.21.5
```

板卡编译使用独立临时 sketch 和示例凭据，避免读取被忽略的真实 `secrets.h`；在仓库根目录执行：

```bash
firmware_check_dir=$(mktemp -d)
mkdir "$firmware_check_dir/MQTT"
cp arduino/MQTT/MQTT.ino arduino/MQTT/CommandProtocol.h arduino/MQTT/SensorSampleState.h "$firmware_check_dir/MQTT/"
cp arduino/MQTT/secrets.example.h "$firmware_check_dir/MQTT/secrets.h"
arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 "$firmware_check_dir/MQTT"
```

若 ArduinoJson 放在独立目录，编译时增加 `--library /path/to/ArduinoJson-6.21.5`。临时产物仅用于编译验收，不烧录。真实使用的 Wi-Fi/MQTT 凭据仅写本机忽略的 `MQTT/secrets.h`，不得提交。

## 证书维护与现场验收

域名连接继续校验私有 CA，备用 IP 路径固定 Broker 叶子证书指纹。证书轮换前核对 SAN、有效期、签发 CA 和指纹；叶子证书更新会使旧备用指纹失效，CA 更新需要同步准备固件信任链。更新后先验证域名和备用路径，再经单独授权烧录，禁止以跳过证书校验替代轮换。NTP 校时仍为连接前置条件。

本轮不验证真实 TLS、传感器、硬件回执、断电去重或微信真机。后续台架先使用安全的 `status/read` 验证格式变化、重复 ID 和断网恢复；泵阀动作需独立驱动及物理反馈验收，不能用采集回执代替。
