package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EspService {

    private static final Logger logger = LoggerFactory.getLogger(EspService.class);

    private final EspRepository espRepository;
    private final AlarmService alarmService;
    private final AutomationService automationService;
    private final DeviceService deviceService;

    // 注入传感器数据仓库、设备档案、报警评估和自动化执行服务
    public EspService(EspRepository espRepository, AlarmService alarmService,
            AutomationService automationService, DeviceService deviceService) {
        this.espRepository = espRepository;
        this.alarmService = alarmService;
        this.automationService = automationService;
        this.deviceService = deviceService;
    }

    /**
     * 保存ESP设备数据到数据库
     * 
     * @param espEntity ESP设备数据实体
     * @return 保存后的实体对象
     */
    public EspEntity saveData(EspEntity espEntity) {
        deviceService.ensureTelemetryAllowed(espEntity.getDeviceId());
        if (espEntity.getOwnerUsername() == null) {
            espEntity.setOwnerUsername(deviceService.getOwnerUsername(espEntity.getDeviceId()));
        }
        if (espEntity.getServerReceivedTime() == null) {
            espEntity.setServerReceivedTime(LocalDateTime.now());
        }
        evaluateDataQuality(espEntity);
        logger.info(
                "Saving ESP data to database: DeviceId={}, Temperature={}, Humidity={}, Water={}, Linkage={}, SendCount={}, Rssi={}, Timestamp={}",
                espEntity.getDeviceId(), espEntity.getTemperature(), espEntity.getHumidity(),
                espEntity.getWater(), espEntity.getLinkage(), espEntity.getSendCount(), espEntity.getRssi(),
                espEntity.getTimestamp());

        EspEntity savedEntity = espRepository.save(espEntity);

        logger.info("Successfully saved ESP data with ID: {}", savedEntity.getId());

        if (!Boolean.FALSE.equals(savedEntity.getQualityValid())) {
            evaluateBusinessRules(savedEntity);
        } else {
            logger.warn("Skipped alarm and automation for invalid sensor data: device={}, issues={}",
                    savedEntity.getDeviceId(), savedEntity.getQualityIssues());
        }

        return savedEntity;
    }

    /**
     * 处理并保存ESP设备数据，返回格式化的响应字符串
     * 
     * @param espEntity ESP设备数据实体
     * @return 格式化的JSON响应字符串
     */
    public String processDataAndGenerateResponse(EspEntity espEntity) {
        try {
            // 保存数据到数据库
            EspEntity savedEntity = saveData(espEntity);

            // 返回成功响应
            return String.format(
                    "{\"status\":\"success\", \"message\":\"Data received and saved.\", \"id\":%d}",
                    savedEntity.getId());
        } catch (Exception e) {
            logger.error("Failed to save data to database", e);
            return String.format(
                    "{\"status\":\"error\", \"message\":\"Failed to save data: %s\"}",
                    e.getMessage());
        }
    }

    /**
     * 获取最近的20条数据
     * 
     * @return List<EspEntity>
     */
    public java.util.List<EspEntity> getRecentData() {
        return espRepository.findTop20ByOrderByServerReceivedTimeDesc();
    }

    // 获取指定用户最近的20条传感器数据
    public java.util.List<EspEntity> getRecentData(String ownerUsername) {
        return espRepository.findTop20ByOwnerUsernameOrderByServerReceivedTimeDesc(ownerUsername);
    }

    // 根据传感器物理边界标记异常数据但保留原始读数
    private void evaluateDataQuality(EspEntity reading) {
        List<String> issues = new ArrayList<>();
        if (!isFiniteInRange(reading.getTemperature(), 0.0, 50.0)) {
            issues.add("TEMPERATURE_OUT_OF_RANGE");
        }
        if (!isFiniteInRange(reading.getHumidity(), 0.0, 100.0)) {
            issues.add("HUMIDITY_OUT_OF_RANGE");
        }
        if (reading.getWater() != null && !isFiniteInRange(reading.getWater(), 0.0, 1023.0)) {
            issues.add("WATER_OUT_OF_RANGE");
        }
        if (reading.getRssi() != null && (reading.getRssi() < -120 || reading.getRssi() > 0)) {
            issues.add("RSSI_OUT_OF_RANGE");
        }
        if (reading.getSendCount() != null && reading.getSendCount() < 0) {
            issues.add("SEND_COUNT_NEGATIVE");
        }
        reading.setQualityValid(issues.isEmpty());
        reading.setQualityIssues(issues.isEmpty() ? null : String.join(";", issues));
    }

    // 执行只允许有效数据进入的报警与自动化规则
    private void evaluateBusinessRules(EspEntity savedEntity) {
        String ownerUsername = savedEntity.getOwnerUsername();
        try {
            if (ownerUsername == null) {
                alarmService.evaluate(savedEntity);
            } else {
                alarmService.evaluate(savedEntity, ownerUsername);
            }
        } catch (Exception e) {
            logger.warn("Alarm evaluation failed for device {}: {}", savedEntity.getDeviceId(), e.getMessage());
        }
        try {
            if (ownerUsername == null) {
                automationService.evaluate(savedEntity);
            } else {
                automationService.evaluate(savedEntity, ownerUsername);
            }
        } catch (Exception e) {
            logger.warn("Automation evaluation failed for device {}: {}",
                    savedEntity.getDeviceId(), e.getMessage());
        }
    }

    // 判断可空数值是否为有限值且处于闭区间内
    private boolean isFiniteInRange(Double value, double minimum, double maximum) {
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
