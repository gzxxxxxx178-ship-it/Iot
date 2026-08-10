package com.ruoyi.iotsystem.simulation.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * MATLAB仿真遥测上传请求体。
 * sampleId作为当前用户内幂等键，至少包含一个有效数值指标。
 */
public class TelemetryRequest {

    @NotBlank(message = "sampleId不能为空")
    @Size(min = 1, max = 64, message = "sampleId长度必须在1到64之间")
    private String sampleId;

    @NotBlank(message = "deviceId不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "deviceId格式无效")
    private String deviceId;

    @NotNull(message = "occurredAt不能为空")
    private OffsetDateTime occurredAt;

    private String growthStage;

    private String scenarioCode;

    private Double waterLevelMm;

    private Double flowRateLMin;

    private Double ecMsCm;

    private Double soilMoisturePct;

    private Double rainfallMm;

    private Boolean pumpOn;

    private Boolean irrigationValveOpen;

    private Boolean fertilizerPumpOn;

    // 获取样本幂等ID
    public String getSampleId() { return sampleId; }
    // 设置样本幂等ID
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    // 获取设备ID
    public String getDeviceId() { return deviceId; }
    // 设置设备ID
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    // 获取采样时间
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    // 设置采样时间
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    // 获取生长阶段
    public String getGrowthStage() { return growthStage; }
    // 设置生长阶段
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }
    // 获取场景编码
    public String getScenarioCode() { return scenarioCode; }
    // 设置场景编码
    public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }
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
}
