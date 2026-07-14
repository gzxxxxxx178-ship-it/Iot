package com.ruoyi.iotsystem.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {

    // 创建使用TLS Broker和唯一客户端ID的MQTT客户端
    @Bean(destroyMethod = "close")
    public MqttClient mqttClient(MqttProperties properties) throws MqttException {
        validateSecureConfiguration(properties);
        return new MqttClient(
                properties.getBroker(),
                buildUniqueClientId(properties.getClientIdPrefix()),
                new MemoryPersistence());
    }

    // 创建包含账号认证、保活和后端遗嘱消息的连接参数
    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties properties) {
        validateSecureConfiguration(properties);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.getUsername());
        options.setPassword(properties.getPassword().toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setSocketFactory(buildTrustedSocketFactory());
        options.setWill(
                properties.getTopicRoot() + "/backend/status",
                "offline".getBytes(),
                1,
                true);
        return options;
    }

    // 从应用内置的公开IoT根证书创建仅信任项目Broker的TLS套接字工厂
    private javax.net.ssl.SSLSocketFactory buildTrustedSocketFactory() {
        try (InputStream inputStream = new ClassPathResource("iot-mqtt-ca.crt").getInputStream()) {
            Certificate certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(inputStream);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("iot-mqtt-ca", certificate);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (Exception exception) {
            throw new IllegalStateException("加载MQTT根证书失败", exception);
        }
    }

    // 校验生产连接必须使用TLS且配置独立账号密码
    private void validateSecureConfiguration(MqttProperties properties) {
        if (properties.getBroker() == null || !properties.getBroker().startsWith("ssl://")) {
            throw new IllegalStateException("MQTT Broker必须使用ssl:// TLS连接");
        }
        if (isBlank(properties.getUsername()) || isBlank(properties.getPassword())) {
            throw new IllegalStateException("MQTT账号和密码不能为空");
        }
    }

    // 组合前缀、主机名和随机片段生成并行实例不冲突的客户端ID
    private String buildUniqueClientId(String prefix) {
        String hostname = "host";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // 主机名不可用时保留安全默认值。
        }
        return prefix + "-" + hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // 判断配置文本是否为空
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
