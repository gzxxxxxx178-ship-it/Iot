package com.ruoyi.iotsystem.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    // MQTT Broker配置 - 根据Python配置设置
    private static final String BROKER = "tcp://broker.emqx.io:1883";
    private static final String CLIENT_ID = "java_client_fixed"; // 使用固定的客户端ID
    // private static final String TOPIC = "Demo"; // Topic subscription moved to
    // MqttMessageService

    // 创建MQTT客户端，连接公共EMQX Broker
    @Bean
    public MqttClient mqttClient() throws MqttException {
        System.out.println("Initializing MQTT client...");

        MqttClient client = new MqttClient(BROKER, CLIENT_ID);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);

        System.out.println("Connecting to MQTT broker: " + BROKER);
        client.connect(options);

        // 订阅逻辑已移动到 MqttMessageService
        // client.subscribe(TOPIC);

        System.out.println("MQTT client successfully connected!");

        return client;
    }
}