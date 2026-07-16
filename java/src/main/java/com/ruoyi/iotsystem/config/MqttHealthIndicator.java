package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.service.MqttMessageService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 生产环境将MQTT连接状态纳入Actuator健康检查
@Component
@Profile("prod")
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttMessageService mqttMessageService;

    // 注入MQTT消息服务
    public MqttHealthIndicator(MqttMessageService mqttMessageService) {
        this.mqttMessageService = mqttMessageService;
    }

    // 返回MQTT连接健康状态，不暴露Broker地址或凭据
    @Override
    public Health health() {
        if (mqttMessageService.isConnected()) {
            return Health.up().build();
        }
        return Health.down().withDetail("component", "mqtt").build();
    }
}
