package com.ruoyi.iotsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.config.SensorWebSocketHandler;
import com.ruoyi.iotsystem.entity.EspEntity;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MqttMessageService implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttMessageService.class);
    private static final String TOPIC = "agri/device001/data";

    private final MqttClient mqttClient;
    private final EspService espService;
    private final ObjectMapper objectMapper;
    private final SensorWebSocketHandler sensorWebSocketHandler;

    public MqttMessageService(MqttClient mqttClient,
                              EspService espService,
                              SensorWebSocketHandler sensorWebSocketHandler,
                              ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.espService = espService;
        this.sensorWebSocketHandler = sensorWebSocketHandler;
        this.objectMapper = objectMapper;

        this.mqttClient.setCallback(this);
        try {
            this.mqttClient.subscribe(TOPIC);
            logger.info("Subscribed to topic: {}", TOPIC);
        } catch (Exception e) {
            logger.error("Failed to subscribe to topic: {}", TOPIC, e);
        }
    }

    // MQTT连接断开回调
    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT connection lost: {}", cause.getMessage());
        // TODO: add reconnect logic if needed
    }

    // 接收MQTT消息，解析JSON或纯文本传感器数据，保存并WebSocket广播
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        logger.info("=====================================");
        logger.info("New MQTT message received!");
        logger.info("Topic: {}", topic);
        logger.info("Message: {}", payload);
        logger.info("QoS: {}", message.getQos());

        parseAndSaveData(payload);

        logger.info("=====================================");
    }

    private void parseAndSaveData(String payload) {
        if (tryParseAsJson(payload)) {
            return;
        }
        if (tryParseAsPlainText(payload)) {
            return;
        }
        logger.warn("Failed to parse MQTT message in any known format. Message: {}", payload);
    }

    private boolean tryParseAsJson(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            String deviceId = jsonNode.has("device")
                    ? jsonNode.get("device").asText()
                    : (jsonNode.has("deviceId") ? jsonNode.get("deviceId").asText() : "unknown");
            Double temperature = jsonNode.has("temperature") ? jsonNode.get("temperature").asDouble() : null;
            Double humidity = jsonNode.has("humidity") ? jsonNode.get("humidity").asDouble() : null;
            Long timestamp = jsonNode.has("timestamp")
                    ? jsonNode.get("timestamp").asLong()
                    : System.currentTimeMillis();

            Double water = jsonNode.has("water") ? jsonNode.get("water").asDouble() : null;
            Boolean linkage = jsonNode.has("linkage") ? jsonNode.get("linkage").asBoolean() : null;
            Integer sendCount = jsonNode.has("sendCount") ? jsonNode.get("sendCount").asInt() : null;
            Integer rssi = jsonNode.has("rssi") ? jsonNode.get("rssi").asInt() : null;

            EspEntity espEntity = new EspEntity(
                    deviceId, temperature, humidity, timestamp, water, linkage, sendCount, rssi);
            espService.saveData(espEntity);

            sensorWebSocketHandler.broadcast(objectMapper.writeValueAsString(espEntity));

            logger.info("Data saved to database from JSON: {}", espEntity);
            return true;
        } catch (Exception e) {
            logger.debug("Message is not in JSON format");
            return false;
        }
    }

    private boolean tryParseAsPlainText(String payload) {
        try {
            String deviceId = "unknown";
            Double temperature = null;
            Double humidity = null;
            Long timestamp = System.currentTimeMillis();

            Pattern devicePattern = Pattern.compile("Received data from device: (.+)");
            Matcher deviceMatcher = devicePattern.matcher(payload);
            if (deviceMatcher.find()) {
                deviceId = deviceMatcher.group(1).trim();
            }

            Pattern tempPattern = Pattern.compile("Temperature: ([0-9.]+)");
            Matcher tempMatcher = tempPattern.matcher(payload);
            if (tempMatcher.find()) {
                temperature = Double.parseDouble(tempMatcher.group(1));
            }

            Pattern humidityPattern = Pattern.compile("Humidity: ([0-9.]+)");
            Matcher humidityMatcher = humidityPattern.matcher(payload);
            if (humidityMatcher.find()) {
                humidity = Double.parseDouble(humidityMatcher.group(1));
            }

            Pattern timestampPattern = Pattern.compile("Timestamp: ([0-9]+)");
            Matcher timestampMatcher = timestampPattern.matcher(payload);
            if (timestampMatcher.find()) {
                timestamp = Long.parseLong(timestampMatcher.group(1));
            }

            EspEntity espEntity = new EspEntity(deviceId, temperature, humidity, timestamp);
            espService.saveData(espEntity);

            sensorWebSocketHandler.broadcast(objectMapper.writeValueAsString(espEntity));

            logger.info("Data saved to database from plain text: {}", espEntity);
            return true;
        } catch (Exception e) {
            logger.error("Failed to parse MQTT message as plain text: {}", e.getMessage(), e);
            return false;
        }
    }

    // MQTT消息送达完成回调
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No-op for subscriber
    }

    // 向指定MQTT主题发布消息（设备控制指令）
    public void publish(String topic, String content) {
        try {
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);
            logger.info("Published message to topic {}: {}", topic, content);
        } catch (Exception e) {
            logger.error("Failed to publish message to topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to publish MQTT message", e);
        }
    }
}
