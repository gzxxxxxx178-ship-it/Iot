package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class DashboardService {

    private final EspRepository espRepository;
    private final long onlineTimeoutSeconds;

    // 注入传感器仓库和可配置的设备在线超时阈值
    public DashboardService(
            EspRepository espRepository,
            @Value("${dashboard.device-online-timeout-seconds:30}") long onlineTimeoutSeconds) {
        this.espRepository = espRepository;
        this.onlineTimeoutSeconds = onlineTimeoutSeconds;
    }

    // 统计设备总数、在线数及在线设备最新温湿度平均值
    public DashboardStatsResponse getStats() {
        List<String> deviceIds = espRepository.findDistinctDeviceIds();
        List<EspEntity> latestReadings = findLatestReadings(deviceIds);
        LocalDateTime onlineThreshold = LocalDateTime.now().minusSeconds(onlineTimeoutSeconds);
        List<EspEntity> onlineReadings = new ArrayList<>();

        for (EspEntity reading : latestReadings) {
            if (isOnline(reading, onlineThreshold)) {
                onlineReadings.add(reading);
            }
        }

        return new DashboardStatsResponse(
                deviceIds.size(),
                onlineReadings.size(),
                calculateAverage(onlineReadings, EspEntity::getTemperature),
                calculateAverage(onlineReadings, EspEntity::getHumidity));
    }

    // 获取在线和离线设备数量分布
    public List<DeviceStatusResponse> getDeviceStatusDistribution() {
        DashboardStatsResponse stats = getStats();
        return Arrays.asList(
                new DeviceStatusResponse("在线", stats.getOnlineCount()),
                new DeviceStatusResponse("离线", stats.getDeviceCount() - stats.getOnlineCount()));
    }

    // 查询每个设备最新的一条传感器读数
    private List<EspEntity> findLatestReadings(List<String> deviceIds) {
        List<EspEntity> latestReadings = new ArrayList<>();
        for (String deviceId : deviceIds) {
            Optional<EspEntity> latest = espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc(deviceId);
            if (latest.isPresent()) {
                latestReadings.add(latest.get());
            }
        }
        return latestReadings;
    }

    // 根据服务端最近接收时间判断设备是否在线
    private boolean isOnline(EspEntity reading, LocalDateTime onlineThreshold) {
        return reading.getServerReceivedTime() != null
                && !reading.getServerReceivedTime().isBefore(onlineThreshold);
    }

    // 计算有效传感器字段的算术平均值并保留一位小数
    private Double calculateAverage(List<EspEntity> readings, Function<EspEntity, Double> valueExtractor) {
        double sum = 0.0;
        int count = 0;
        for (EspEntity reading : readings) {
            if (Boolean.FALSE.equals(reading.getQualityValid())) {
                continue;
            }
            Double value = valueExtractor.apply(reading);
            if (value != null && Double.isFinite(value)) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return Math.round((sum / count) * 10.0) / 10.0;
    }
}
