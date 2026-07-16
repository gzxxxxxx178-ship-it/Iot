package com.ruoyi.iotsystem.entity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import javax.persistence.*;

@Entity
@Table(name = "esp_data", indexes = {
        @Index(name = "idx_esp_device_received", columnList = "device_id,server_received_time"),
        @Index(name = "idx_esp_received_quality", columnList = "server_received_time,quality_valid")
})
public class EspEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "设备ID不能为空")
    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @NotNull(message = "温度不能为空")
    private Double temperature;

    @NotNull(message = "湿度不能为空")
    private Double humidity;

    @NotNull(message = "时间戳不能为空")
    private Long timestamp;

    private Double water;
    private Boolean linkage;
    private Integer sendCount;
    private Integer rssi;

    @Column(name = "server_received_time")
    private LocalDateTime serverReceivedTime;

    @Column(name = "quality_valid")
    private Boolean qualityValid;

    @Column(name = "quality_issues", length = 500)
    private String qualityIssues;

    // 创建供JPA反射使用的空实体
    public EspEntity() {
    }

    // 创建包含完整设备采集字段的传感器实体
    public EspEntity(String deviceId, Double temperature, Double humidity, Long timestamp,
            Double water, Boolean linkage, Integer sendCount, Integer rssi) {
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
        this.water = water;
        this.linkage = linkage;
        this.sendCount = sendCount;
        this.rssi = rssi;
        this.serverReceivedTime = LocalDateTime.now();
        this.qualityValid = Boolean.TRUE;
    }

    // 创建兼容旧调用方的基础传感器实体
    public EspEntity(String deviceId, Double temperature, Double humidity, Long timestamp) {
        this(deviceId, temperature, humidity, timestamp, null, null, null, null);
    }

    // 获取数据库主键
    public Long getId() {
        return id;
    }

    // 设置数据库主键
    public void setId(Long id) {
        this.id = id;
    }

    // 获取设备ID
    public String getDeviceId() {
        return deviceId;
    }

    // 设置设备ID
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    // 获取传感器记录所属用户
    public String getOwnerUsername() {
        return ownerUsername;
    }

    // 设置传感器记录所属用户
    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    // 获取温度读数
    public Double getTemperature() {
        return temperature;
    }

    // 设置温度读数
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    // 获取湿度读数
    public Double getHumidity() {
        return humidity;
    }

    // 设置湿度读数
    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    // 获取设备侧时间戳
    public Long getTimestamp() {
        return timestamp;
    }

    // 设置设备侧时间戳
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    // 获取水位ADC读数
    public Double getWater() {
        return water;
    }

    // 设置水位ADC读数
    public void setWater(Double water) {
        this.water = water;
    }

    // 获取联动状态
    public Boolean getLinkage() {
        return linkage;
    }

    // 设置联动状态
    public void setLinkage(Boolean linkage) {
        this.linkage = linkage;
    }

    // 获取设备累计发送次数
    public Integer getSendCount() {
        return sendCount;
    }

    // 设置设备累计发送次数
    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    // 获取Wi-Fi信号强度
    public Integer getRssi() {
        return rssi;
    }

    // 设置Wi-Fi信号强度
    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }

    // 获取服务端接收时间
    public LocalDateTime getServerReceivedTime() {
        return serverReceivedTime;
    }

    // 设置服务端接收时间
    public void setServerReceivedTime(LocalDateTime serverReceivedTime) {
        this.serverReceivedTime = serverReceivedTime;
    }

    // 获取数据质量有效状态，空值表示尚未评估的历史数据
    public Boolean getQualityValid() {
        return qualityValid;
    }

    // 设置数据质量有效状态
    public void setQualityValid(Boolean qualityValid) {
        this.qualityValid = qualityValid;
    }

    // 获取数据质量问题代码
    public String getQualityIssues() {
        return qualityIssues;
    }

    // 设置数据质量问题代码
    public void setQualityIssues(String qualityIssues) {
        this.qualityIssues = qualityIssues;
    }

    // 输出传感器实体的调试文本
    @Override
    public String toString() {
        return "EspEntity{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", water=" + water +
                ", linkage=" + linkage +
                ", sendCount=" + sendCount +
                ", rssi=" + rssi +
                ", timestamp=" + timestamp +
                ", serverReceivedTime=" + serverReceivedTime +
                ", qualityValid=" + qualityValid +
                ", qualityIssues='" + qualityIssues + '\'' +
                '}';
    }
}
