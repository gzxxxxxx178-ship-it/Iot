package com.ruoyi.iotsystem.entity;

import java.time.LocalDateTime;
import javax.persistence.*;

@Entity
@Table(name = "esp_data")
public class EspEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    private Double temperature;
    private Double humidity;

    private Long timestamp;

    private Double water;
    private Boolean linkage;
    private Integer sendCount;
    private Integer rssi;

    @Column(name = "server_received_time")
    private LocalDateTime serverReceivedTime;

    // Constructors
    public EspEntity() {
    }

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
    }

    // Legacy constructor for backward compatibility if needed, or update call sites
    public EspEntity(String deviceId, Double temperature, Double humidity, Long timestamp) {
        this(deviceId, temperature, humidity, timestamp, null, null, null, null);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getWater() {
        return water;
    }

    public void setWater(Double water) {
        this.water = water;
    }

    public Boolean getLinkage() {
        return linkage;
    }

    public void setLinkage(Boolean linkage) {
        this.linkage = linkage;
    }

    public Integer getSendCount() {
        return sendCount;
    }

    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }

    public LocalDateTime getServerReceivedTime() {
        return serverReceivedTime;
    }

    public void setServerReceivedTime(LocalDateTime serverReceivedTime) {
        this.serverReceivedTime = serverReceivedTime;
    }

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
                '}';
    }
}