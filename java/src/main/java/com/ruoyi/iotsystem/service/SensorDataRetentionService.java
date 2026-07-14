package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.repository.EspRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SensorDataRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(SensorDataRetentionService.class);

    private final EspRepository espRepository;
    private final int retentionDays;
    private final boolean retentionEnabled;

    // 注入历史仓库和可配置的数据保留策略
    public SensorDataRetentionService(
            EspRepository espRepository,
            @Value("${sensor.history.retention-days:365}") int retentionDays,
            @Value("${sensor.history.retention-enabled:true}") boolean retentionEnabled) {
        if (retentionDays < 30) {
            throw new IllegalArgumentException("传感器历史保留周期不能少于30天");
        }
        this.espRepository = espRepository;
        this.retentionDays = retentionDays;
        this.retentionEnabled = retentionEnabled;
    }

    // 每日清理超过保留周期的历史记录并记录删除数量
    @Scheduled(cron = "${sensor.history.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public long cleanupExpiredData() {
        if (!retentionEnabled) {
            return 0L;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deleted = espRepository.deleteExpiredData(cutoff);
        logger.info("Sensor history retention cleanup deleted {} rows before {}", deleted, cutoff);
        return deleted;
    }
}
