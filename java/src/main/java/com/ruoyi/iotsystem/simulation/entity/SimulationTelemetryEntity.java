package com.ruoyi.iotsystem.simulation.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * MATLAB仿真遥测数据实体，按当前登录用户隔离。
 */
@Entity
@Table(name = "simulation_telemetry", indexes = {
        @Index(name = "idx_sim_tel_owner_device_time", columnList = "owner_username,device_id,occurred_at")
})
public class SimulationTelemetryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false, length = 64)
    private String sampleId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "growth_stage")
    private String growthStage;

    @Column(name = "scenario_code")
    private String scenarioCode;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "water_level_mm")
    private Double waterLevelMm;

    @Column(name = "flow_rate_l_min")
    private Double flowRateLMin;

    @Column(name = "ec_ms_cm")
    private Double ecMsCm;

    @Column(name = "soil_moisture_pct")
    private Double soilMoisturePct;

    @Column(name = "rainfall_mm")
    private Double rainfallMm;

    @Column(name = "pump_on")
    private Boolean pumpOn;

    @Column(name = "irrigation_valve_open")
    private Boolean irrigationValveOpen;

    @Column(name = "fertilizer_pump_on")
    private Boolean fertilizerPumpOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 供JPA使用的空构造函数
    public SimulationTelemetryEntity() {
    }

    // 创建带全部遥测参数的仿真数据实体
    public SimulationTelemetryEntity(String sampleId, String deviceId, String ownerUsername,
            LocalDateTime occurredAt, String growthStage, String scenarioCode,
            Double waterLevelMm, Double flowRateLMin, Double ecMsCm,
            Double soilMoisturePct, Double rainfallMm,
            Boolean pumpOn, Boolean irrigationValveOpen, Boolean fertilizerPumpOn) {
        this.sampleId = sampleId;
        this.deviceId = deviceId;
        this.ownerUsername = ownerUsername;
        this.occurredAt = occurredAt;
        this.growthStage = growthStage;
        this.scenarioCode = scenarioCode;
        this.sourceType = "SIMULATION";
        this.waterLevelMm = waterLevelMm;
        this.flowRateLMin = flowRateLMin;
        this.ecMsCm = ecMsCm;
        this.soilMoisturePct = soilMoisturePct;
        this.rainfallMm = rainfallMm;
        this.pumpOn = pumpOn;
        this.irrigationValveOpen = irrigationValveOpen;
        this.fertilizerPumpOn = fertilizerPumpOn;
        this.createdAt = LocalDateTime.now();
    }

    // 获取主键
    public Long getId() { return id; }
    // 设置主键
    public void setId(Long id) { this.id = id; }
    // 获取样本幂等ID
    public String getSampleId() { return sampleId; }
    // 设置样本幂等ID
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    // 获取设备标识
    public String getDeviceId() { return deviceId; }
    // 设置设备标识
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    // 获取归属用户名
    public String getOwnerUsername() { return ownerUsername; }
    // 设置归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    // 获取采样时间
    public LocalDateTime getOccurredAt() { return occurredAt; }
    // 设置采样时间
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    // 获取生长阶段
    public String getGrowthStage() { return growthStage; }
    // 设置生长阶段
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }
    // 获取场景编码
    public String getScenarioCode() { return scenarioCode; }
    // 设置场景编码
    public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }
    // 获取数据来源类型，始终为SIMULATION
    public String getSourceType() { return sourceType; }
    // 设置数据来源类型
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    // 获取水位(mm)
    public Double getWaterLevelMm() { return waterLevelMm; }
    // 设置水位(mm)
    public void setWaterLevelMm(Double waterLevelMm) { this.waterLevelMm = waterLevelMm; }
    // 获取流量(L/min)
    public Double getFlowRateLMin() { return flowRateLMin; }
    // 设置流量(L/min)
    public void setFlowRateLMin(Double flowRateLMin) { this.flowRateLMin = flowRateLMin; }
    // 获取电导率(mS/cm)
    public Double getEcMsCm() { return ecMsCm; }
    // 设置电导率(mS/cm)
    public void setEcMsCm(Double ecMsCm) { this.ecMsCm = ecMsCm; }
    // 获取土壤含水率(%)
    public Double getSoilMoisturePct() { return soilMoisturePct; }
    // 设置土壤含水率(%)
    public void setSoilMoisturePct(Double soilMoisturePct) { this.soilMoisturePct = soilMoisturePct; }
    // 获取降雨量(mm)
    public Double getRainfallMm() { return rainfallMm; }
    // 设置降雨量(mm)
    public void setRainfallMm(Double rainfallMm) { this.rainfallMm = rainfallMm; }
    // 获取水泵开关状态
    public Boolean getPumpOn() { return pumpOn; }
    // 设置水泵开关状态
    public void setPumpOn(Boolean pumpOn) { this.pumpOn = pumpOn; }
    // 获取灌溉阀开关状态
    public Boolean getIrrigationValveOpen() { return irrigationValveOpen; }
    // 设置灌溉阀开关状态
    public void setIrrigationValveOpen(Boolean irrigationValveOpen) { this.irrigationValveOpen = irrigationValveOpen; }
    // 获取施肥泵开关状态
    public Boolean getFertilizerPumpOn() { return fertilizerPumpOn; }
    // 设置施肥泵开关状态
    public void setFertilizerPumpOn(Boolean fertilizerPumpOn) { this.fertilizerPumpOn = fertilizerPumpOn; }
    // 获取入库时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置入库时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
