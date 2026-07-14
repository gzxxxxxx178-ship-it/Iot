package com.ruoyi.iotsystem.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttConfigTest {

    // 验证TLS连接参数包含认证、遗嘱和手动重连策略
    @Test
    void mqttConnectOptions_安全配置_应生成预期参数() {
        MqttProperties properties = secureProperties();
        MqttConnectOptions options = new MqttConfig().mqttConnectOptions(properties);

        assertEquals("iot_backend", options.getUserName());
        assertTrue(options.isCleanSession());
        assertFalse(options.isAutomaticReconnect());
        assertEquals("agri/backend/status", options.getWillDestination());
    }

    // 验证并行后端实例使用不同客户端ID避免互踢
    @Test
    void mqttClient_连续创建_应使用唯一客户端Id() throws Exception {
        MqttConfig config = new MqttConfig();
        MqttClient first = config.mqttClient(secureProperties());
        MqttClient second = config.mqttClient(secureProperties());
        try {
            assertTrue(first.getClientId().startsWith("iot-backend-"));
            assertTrue(second.getClientId().startsWith("iot-backend-"));
            assertNotEquals(first.getClientId(), second.getClientId());
        } finally {
            first.close();
            second.close();
        }
    }

    // 验证明文Broker配置会在客户端创建前被拒绝
    @Test
    void mqttClient_明文Broker_应拒绝启动() {
        MqttProperties properties = secureProperties();
        properties.setBroker("tcp://localhost:1883");

        assertThrows(IllegalStateException.class, () -> new MqttConfig().mqttClient(properties));
    }

    // 创建测试使用的安全MQTT配置
    private MqttProperties secureProperties() {
        MqttProperties properties = new MqttProperties();
        properties.setBroker("ssl://localhost:8883");
        properties.setUsername("iot_backend");
        properties.setPassword("test-password");
        return properties;
    }
}
