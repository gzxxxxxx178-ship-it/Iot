package com.ruoyi.iotsystem;

import com.ruoyi.iotsystem.config.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 为Spring上下文测试提供不访问外部网络的基础设施替身。
 */
@TestConfiguration
@EnableConfigurationProperties(MqttProperties.class)
public class TestInfrastructureConfiguration {

    // 创建不会连接真实Broker的MQTT客户端替身
    @Bean(name = "mqttClient")
    public MqttClient testMqttClient() {
        return Mockito.mock(MqttClient.class);
    }

    // 创建供MQTT服务注入的测试连接参数
    @Bean(name = "mqttConnectOptions")
    public MqttConnectOptions testMqttConnectOptions() {
        return new MqttConnectOptions();
    }

    // 创建不会访问Redis服务器的Redis模板替身
    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> testRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }
}
