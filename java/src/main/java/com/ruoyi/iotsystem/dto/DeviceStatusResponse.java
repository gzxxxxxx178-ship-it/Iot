package com.ruoyi.iotsystem.dto;

public class DeviceStatusResponse {

    private final String name;
    private final int value;

    // 创建设备状态分布项
    public DeviceStatusResponse(String name, int value) {
        this.name = name;
        this.value = value;
    }

    // 获取状态名称
    public String getName() {
        return name;
    }

    // 获取状态对应的设备数量
    public int getValue() {
        return value;
    }
}
