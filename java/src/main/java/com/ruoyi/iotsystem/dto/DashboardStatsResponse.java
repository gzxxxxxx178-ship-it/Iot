package com.ruoyi.iotsystem.dto;

public class DashboardStatsResponse {

    private final int deviceCount;
    private final int onlineCount;
    private final Double avgTemp;
    private final Double avgHum;

    // 创建仪表盘统计响应
    public DashboardStatsResponse(int deviceCount, int onlineCount, Double avgTemp, Double avgHum) {
        this.deviceCount = deviceCount;
        this.onlineCount = onlineCount;
        this.avgTemp = avgTemp;
        this.avgHum = avgHum;
    }

    // 获取设备总数
    public int getDeviceCount() {
        return deviceCount;
    }

    // 获取在线设备数
    public int getOnlineCount() {
        return onlineCount;
    }

    // 获取在线设备最新温度平均值
    public Double getAvgTemp() {
        return avgTemp;
    }

    // 获取在线设备最新湿度平均值
    public Double getAvgHum() {
        return avgHum;
    }
}
