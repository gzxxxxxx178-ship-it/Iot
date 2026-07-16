package com.ruoyi.iotsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.config.MqttProperties;
import com.ruoyi.iotsystem.config.SensorWebSocketHandler;
import com.ruoyi.iotsystem.entity.EspEntity;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MqttMessageService implements MqttCallbackExtended {

    private static final Logger logger = LoggerFactory.getLogger(MqttMessageService.class);
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final MqttClient mqttClient;
    private final MqttConnectOptions connectOptions;
    private final MqttProperties properties;
    private final EspService espService;
    private final ObjectMapper objectMapper;
    private final SensorWebSocketHandler sensorWebSocketHandler;
    private final ScheduledExecutorService reconnectExecutor;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private volatile int reconnectDelaySeconds;
    private volatile boolean shuttingDown;

    // 注入MQTT连接、传感器持久化和WebSocket广播依赖
    public MqttMessageService(
            MqttClient mqttClient,
            MqttConnectOptions connectOptions,
            MqttProperties properties,
            EspService espService,
            SensorWebSocketHandler sensorWebSocketHandler,
            ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.connectOptions = connectOptions;
        this.properties = properties;
        this.espService = espService;
        this.sensorWebSocketHandler = sensorWebSocketHandler;
        this.objectMapper = objectMapper;
        this.reconnectDelaySeconds = properties.getInitialReconnectDelaySeconds();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mqtt-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    // 注册回调并尝试首次连接，失败时进入指数退避重连
    @PostConstruct
    public void initialize() {
        mqttClient.setCallback(this);
        connectOrScheduleRetry();
    }

    // 停止重连线程并安全断开MQTT连接
    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        reconnectExecutor.shutdownNow();
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception exception) {
            logger.warn("MQTT shutdown failed: {}", exception.getMessage());
        }
    }

    // 连接成功后重置退避时间、重新订阅数据Topic并发布在线状态
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        reconnectScheduled.set(false);
        reconnectDelaySeconds = properties.getInitialReconnectDelaySeconds();
        try {
            String dataTopic = properties.getTopicRoot() + "/+/data";
            mqttClient.subscribe(dataTopic, 1);
            publishInternal(properties.getTopicRoot() + "/backend/status", "online", true);
            logger.info("MQTT {}connected and subscribed to {}", reconnect ? "re" : "", dataTopic);
        } catch (Exception exception) {
            logger.error("MQTT subscription recovery failed", exception);
            forceDisconnectAfterRecoveryFailure();
            scheduleReconnect();
        }
    }

    // 记录连接中断并安排指数退避重连
    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
        scheduleReconnect();
    }

    // 校验Topic设备身份后解析、保存并广播传感器消息
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        Optional<String> topicDeviceId = extractDeviceId(topic, "data");
        if (!topicDeviceId.isPresent()) {
            logger.warn("Rejected MQTT message from unauthorized topic structure: {}", topic);
            return;
        }
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        parseAndSaveData(topicDeviceId.get(), payload);
    }

    // MQTT消息送达完成回调无需额外处理
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No-op: delivery state is handled by the Paho client.
    }

    // 向指定设备的控制Topic发布经过白名单校验的指令
    public void publishControl(String deviceId, String command) {
        validateDeviceId(deviceId);
        if (!"start".equals(command) && !"stop".equals(command)
                && !"read".equals(command) && !"status".equals(command)) {
            throw new IllegalArgumentException("不支持的设备控制指令");
        }
        publishInternal(properties.getTopicRoot() + "/" + deviceId + "/control", command, false);
    }

    // 返回当前MQTT会话连接状态，供生产健康检查使用
    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    // 尝试连接Broker，失败时保持应用运行并安排重试
    private synchronized void connectOrScheduleRetry() {
        if (shuttingDown || mqttClient.isConnected()) {
            return;
        }
        try {
            mqttClient.connect(connectOptions);
        } catch (Exception exception) {
            logger.warn("MQTT connection failed, retrying in {} seconds: {}",
                    reconnectDelaySeconds, exception.getMessage());
            scheduleReconnect();
        }
    }

    // 按1、2、4秒递增到配置上限安排唯一重连任务
    private void scheduleReconnect() {
        if (shuttingDown || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int delay = reconnectDelaySeconds;
        reconnectDelaySeconds = Math.min(
                properties.getMaxReconnectDelaySeconds(),
                Math.max(delay + 1, delay * 2));
        reconnectExecutor.schedule(() -> {
            reconnectScheduled.set(false);
            connectOrScheduleRetry();
        }, delay, TimeUnit.SECONDS);
    }

    // 订阅恢复失败后强制断开，使下一次退避任务能够重新建立完整会话
    private void forceDisconnectAfterRecoveryFailure() {
        try {
            mqttClient.disconnectForcibly();
        } catch (Exception exception) {
            logger.warn("MQTT forced disconnect after recovery failure failed: {}", exception.getMessage());
        }
    }

    // 根据消息格式选择JSON或受身份约束的兼容纯文本解析
    private void parseAndSaveData(String expectedDeviceId, String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode != null && jsonNode.isObject()) {
                parseJsonAndSave(expectedDeviceId, jsonNode);
                return;
            }
        } catch (JsonProcessingException ignored) {
            // 非JSON消息继续尝试兼容纯文本协议。
        } catch (Exception exception) {
            logger.error("Failed to persist JSON MQTT message: {}", exception.getMessage());
            return;
        }
        if (!tryParsePlainText(expectedDeviceId, payload)) {
            logger.warn("Rejected MQTT payload for device {}", expectedDeviceId);
        }
    }

    // 校验JSON载荷设备ID与Topic一致后持久化并广播
    private void parseJsonAndSave(String expectedDeviceId, JsonNode jsonNode) throws Exception {
        JsonNode deviceNode = jsonNode.get("deviceId");
        if (deviceNode == null || !expectedDeviceId.equals(deviceNode.asText())) {
            logger.warn("Rejected MQTT identity mismatch: topic={}, payload={}",
                    expectedDeviceId, deviceNode == null ? "missing" : deviceNode.asText());
            return;
        }
        Double temperature = jsonNode.has("temperature") ? jsonNode.get("temperature").asDouble() : null;
        Double humidity = jsonNode.has("humidity") ? jsonNode.get("humidity").asDouble() : null;
        Long uptimeMillis = jsonNode.has("uptimeMillis")
                ? jsonNode.get("uptimeMillis").asLong()
                : (jsonNode.has("timestamp") ? jsonNode.get("timestamp").asLong() : 0L);
        Double water = jsonNode.has("water") ? jsonNode.get("water").asDouble() : null;
        Boolean linkage = jsonNode.has("linkage") ? jsonNode.get("linkage").asBoolean() : null;
        Integer sendCount = jsonNode.has("sendCount") ? jsonNode.get("sendCount").asInt() : null;
        Integer rssi = jsonNode.has("rssi") ? jsonNode.get("rssi").asInt() : null;

        EspEntity entity = new EspEntity(
                expectedDeviceId, temperature, humidity, uptimeMillis, water, linkage, sendCount, rssi);
        EspEntity saved = espService.saveData(entity);
        sensorWebSocketHandler.broadcast(objectMapper.writeValueAsString(saved));
    }

    // 兼容旧纯文本协议并拒绝Topic与载荷设备不一致的数据
    private boolean tryParsePlainText(String expectedDeviceId, String payload) {
        try {
            String deviceId = findGroup(payload, "Received data from device: ([A-Za-z0-9_-]+)");
            if (!expectedDeviceId.equals(deviceId)) {
                return false;
            }
            Double temperature = parseDouble(findGroup(payload, "Temperature: (-?[0-9.]+)"));
            Double humidity = parseDouble(findGroup(payload, "Humidity: ([0-9.]+)"));
            String timestampText = findGroup(payload, "Timestamp: ([0-9]+)");
            Long uptimeMillis = timestampText == null ? 0L : Long.parseLong(timestampText);
            EspEntity saved = espService.saveData(
                    new EspEntity(deviceId, temperature, humidity, uptimeMillis));
            sensorWebSocketHandler.broadcast(objectMapper.writeValueAsString(saved));
            return true;
        } catch (Exception exception) {
            logger.debug("Plain text MQTT parsing failed: {}", exception.getMessage());
            return false;
        }
    }

    // 从设备级Topic中提取并校验设备ID
    private Optional<String> extractDeviceId(String topic, String expectedSuffix) {
        if (topic == null) {
            return Optional.empty();
        }
        String[] segments = topic.split("/", -1);
        if (segments.length != 3
                || !properties.getTopicRoot().equals(segments[0])
                || !expectedSuffix.equals(segments[2])
                || !DEVICE_ID_PATTERN.matcher(segments[1]).matches()) {
            return Optional.empty();
        }
        return Optional.of(segments[1]);
    }

    // 校验设备ID能安全用于Topic路径
    private void validateDeviceId(String deviceId) {
        if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("设备ID格式无效");
        }
    }

    // 发布QoS 1消息并在断线时触发重连
    private void publishInternal(String topic, String content, boolean retained) {
        if (!mqttClient.isConnected()) {
            scheduleReconnect();
            throw new IllegalStateException("MQTT连接不可用");
        }
        try {
            MqttMessage message = new MqttMessage(content.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(retained);
            mqttClient.publish(topic, message);
        } catch (Exception exception) {
            logger.error("Failed to publish MQTT topic {}", topic, exception);
            throw new RuntimeException("MQTT消息发布失败", exception);
        }
    }

    // 从兼容文本载荷中提取首个正则分组
    private String findGroup(String payload, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(payload);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // 将可空文本转换为双精度数值
    private Double parseDouble(String value) {
        return value == null ? null : Double.parseDouble(value);
    }
}
