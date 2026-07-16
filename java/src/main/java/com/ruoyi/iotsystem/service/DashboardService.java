package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Service
public class DashboardService {

    private final DeviceService deviceService;

    // 注入统一设备档案与在线状态服务
    public DashboardService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // 统计设备总数、在线数及在线设备最新温湿度平均值
    public DashboardStatsResponse getStats() {
        return getStats(null);
    }

    // 统计指定用户设备总数、在线数及最新温湿度平均值
    public DashboardStatsResponse getStats(String ownerUsername) {
        List<DeviceResponse> devices = ownerUsername == null
                ? deviceService.listDevices(false)
                : deviceService.listDevices(false, ownerUsername);
        List<DeviceResponse> onlineDevices = new ArrayList<>();

        for (DeviceResponse device : devices) {
            if ("online".equals(device.getStatus())) {
                onlineDevices.add(device);
            }
        }

        return new DashboardStatsResponse(
                devices.size(),
                onlineDevices.size(),
                calculateAverage(onlineDevices, DeviceResponse::getTemperature),
                calculateAverage(onlineDevices, DeviceResponse::getHumidity));
    }

    // 获取在线和离线设备数量分布
    public List<DeviceStatusResponse> getDeviceStatusDistribution() {
        return getDeviceStatusDistribution(null);
    }

    // 获取指定用户在线和离线设备数量分布
    public List<DeviceStatusResponse> getDeviceStatusDistribution(String ownerUsername) {
        DashboardStatsResponse stats = getStats(ownerUsername);
        return Arrays.asList(
                new DeviceStatusResponse("在线", stats.getOnlineCount()),
                new DeviceStatusResponse("离线", stats.getDeviceCount() - stats.getOnlineCount()));
    }

    // 计算有效传感器字段的算术平均值并保留一位小数
    private Double calculateAverage(
            List<DeviceResponse> devices,
            Function<DeviceResponse, Double> valueExtractor) {
        double sum = 0.0;
        int count = 0;
        for (DeviceResponse device : devices) {
            if (Boolean.FALSE.equals(device.getQualityValid())) {
                continue;
            }
            Double value = valueExtractor.apply(device);
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
