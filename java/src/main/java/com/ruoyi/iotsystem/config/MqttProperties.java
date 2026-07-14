package com.ruoyi.iotsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private String broker;
    private String username;
    private String password;
    private String topicRoot = "agri";
    private String clientIdPrefix = "iot-backend";
    private int connectionTimeoutSeconds = 10;
    private int keepAliveSeconds = 30;
    private int initialReconnectDelaySeconds = 1;
    private int maxReconnectDelaySeconds = 60;

    // 获取MQTT Broker地址
    public String getBroker() { return broker; }

    // 设置MQTT Broker地址
    public void setBroker(String broker) { this.broker = broker; }

    // 获取后端MQTT账号
    public String getUsername() { return username; }

    // 设置后端MQTT账号
    public void setUsername(String username) { this.username = username; }

    // 获取后端MQTT密码
    public String getPassword() { return password; }

    // 设置后端MQTT密码
    public void setPassword(String password) { this.password = password; }

    // 获取Topic根路径
    public String getTopicRoot() { return topicRoot; }

    // 设置Topic根路径
    public void setTopicRoot(String topicRoot) { this.topicRoot = topicRoot; }

    // 获取客户端ID前缀
    public String getClientIdPrefix() { return clientIdPrefix; }

    // 设置客户端ID前缀
    public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }

    // 获取连接超时秒数
    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }

    // 设置连接超时秒数
    public void setConnectionTimeoutSeconds(int value) { this.connectionTimeoutSeconds = value; }

    // 获取保活秒数
    public int getKeepAliveSeconds() { return keepAliveSeconds; }

    // 设置保活秒数
    public void setKeepAliveSeconds(int value) { this.keepAliveSeconds = value; }

    // 获取首次重连延迟秒数
    public int getInitialReconnectDelaySeconds() { return initialReconnectDelaySeconds; }

    // 设置首次重连延迟秒数
    public void setInitialReconnectDelaySeconds(int value) { this.initialReconnectDelaySeconds = value; }

    // 获取最大重连延迟秒数
    public int getMaxReconnectDelaySeconds() { return maxReconnectDelaySeconds; }

    // 设置最大重连延迟秒数
    public void setMaxReconnectDelaySeconds(int value) { this.maxReconnectDelaySeconds = value; }
}
