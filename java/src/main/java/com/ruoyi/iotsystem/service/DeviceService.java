package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DeviceCreateRequest;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DeviceService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final DeviceRepository deviceRepository;
    private final EspRepository espRepository;
    private final long onlineTimeoutSeconds;

    // 注入设备档案、传感器读数和在线超时配置
    public DeviceService(DeviceRepository deviceRepository, EspRepository espRepository,
            @Value("${dashboard.device-online-timeout-seconds:30}") long onlineTimeoutSeconds) {
        this.deviceRepository = deviceRepository;
        this.espRepository = espRepository;
        this.onlineTimeoutSeconds = onlineTimeoutSeconds;
    }

    // 查询设备档案并附加最新读数与实时在线状态
    @Transactional
    public List<DeviceResponse> listDevices(boolean includeArchived) {
        synchronizeLegacyDevices();
        List<DeviceEntity> devices = includeArchived
                ? deviceRepository.findAllByOrderByCreatedAtDesc()
                : deviceRepository.findByLifecycleStatusOrderByCreatedAtDesc(ACTIVE);
        List<DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(toResponse(device));
        }
        return responses;
    }

    // 查询指定设备详情
    public DeviceResponse getDevice(String deviceId) {
        return toResponse(findDevice(deviceId));
    }

    // 校验设备ID唯一性并创建设备档案
    public DeviceResponse createDevice(DeviceCreateRequest request) {
        String deviceId = normalizeDeviceId(request.getDeviceId());
        if (deviceRepository.existsByDeviceId(deviceId)) {
            throw new RuntimeException("设备ID已存在");
        }
        DeviceEntity device = new DeviceEntity(
                deviceId,
                normalizeRequired(request.getDeviceName(), "设备名称不能为空"),
                normalizeType(request.getDeviceType()),
                normalizeOptional(request.getLocation()),
                normalizeOptional(request.getDescription()),
                request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return toResponse(deviceRepository.save(device));
    }

    // 更新设备可编辑档案但保持设备ID不变
    public DeviceResponse updateDevice(String deviceId, DeviceUpdateRequest request) {
        DeviceEntity device = findDevice(deviceId);
        if (ARCHIVED.equals(device.getLifecycleStatus())) {
            throw new RuntimeException("归档设备需先恢复后才能编辑");
        }
        device.setDeviceName(normalizeRequired(request.getDeviceName(), "设备名称不能为空"));
        device.setDeviceType(normalizeType(request.getDeviceType()));
        device.setLocation(normalizeOptional(request.getLocation()));
        device.setDescription(normalizeOptional(request.getDescription()));
        device.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        device.setUpdatedAt(LocalDateTime.now());
        return toResponse(deviceRepository.save(device));
    }

    // 软删除设备档案并禁止继续控制和接收入库
    public void archiveDevice(String deviceId) {
        DeviceEntity device = findDevice(deviceId);
        device.setLifecycleStatus(ARCHIVED);
        device.setEnabled(Boolean.FALSE);
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(device);
    }

    // 恢复归档设备并重新启用
    public DeviceResponse restoreDevice(String deviceId) {
        DeviceEntity device = findDevice(deviceId);
        device.setLifecycleStatus(ACTIVE);
        device.setEnabled(Boolean.TRUE);
        device.setUpdatedAt(LocalDateTime.now());
        return toResponse(deviceRepository.save(device));
    }

    // 在发送远程控制前校验设备处于可操作状态
    public void assertControllable(String deviceId) {
        DeviceEntity device = findDevice(deviceId);
        if (!ACTIVE.equals(device.getLifecycleStatus()) || !Boolean.TRUE.equals(device.getEnabled())) {
            throw new RuntimeException("设备已停用或归档，不能发送控制指令");
        }
    }

    // 接收可信上报时自动登记未知设备并拒绝停用或归档设备
    public void ensureTelemetryAllowed(String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        DeviceEntity device = deviceRepository.findByDeviceId(normalized)
                .orElseGet(() -> createDiscoveredDevice(normalized));
        if (!ACTIVE.equals(device.getLifecycleStatus()) || !Boolean.TRUE.equals(device.getEnabled())) {
            throw new RuntimeException("设备已停用或归档，拒绝接收上报数据");
        }
    }

    // 将升级前已经存在历史读数的设备补录为可管理档案
    private void synchronizeLegacyDevices() {
        for (String deviceId : espRepository.findDistinctDeviceIds()) {
            if (!deviceRepository.existsByDeviceId(deviceId)) {
                createDiscoveredDevice(deviceId);
            }
        }
    }

    // 创建由可信历史或MQTT消息自动发现的设备档案
    private synchronized DeviceEntity createDiscoveredDevice(String deviceId) {
        Optional<DeviceEntity> existing = deviceRepository.findByDeviceId(deviceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return deviceRepository.save(new DeviceEntity(
                deviceId, deviceId, "ESP8266", null, "系统自动发现", Boolean.TRUE));
    }

    // 查询设备档案，不存在时返回明确业务错误
    private DeviceEntity findDevice(String deviceId) {
        return deviceRepository.findByDeviceId(normalizeDeviceId(deviceId))
                .orElseThrow(() -> new RuntimeException("设备不存在"));
    }

    // 组合档案和最新传感器读数形成响应对象
    private DeviceResponse toResponse(DeviceEntity device) {
        Optional<EspEntity> latest = espRepository
                .findFirstByDeviceIdOrderByServerReceivedTimeDesc(device.getDeviceId());
        boolean online = latest.isPresent() && isOnline(device, latest.get());
        return new DeviceResponse(device, latest.orElse(null), online);
    }

    // 根据生命周期、启用状态和最近上报时间判断设备是否在线
    private boolean isOnline(DeviceEntity device, EspEntity latest) {
        return ACTIVE.equals(device.getLifecycleStatus())
                && Boolean.TRUE.equals(device.getEnabled())
                && latest.getServerReceivedTime() != null
                && !latest.getServerReceivedTime().isBefore(
                        LocalDateTime.now().minusSeconds(onlineTimeoutSeconds));
    }

    // 校验设备ID并去除首尾空白
    private String normalizeDeviceId(String deviceId) {
        String normalized = normalizeRequired(deviceId, "设备ID不能为空");
        if (!DEVICE_ID_PATTERN.matcher(normalized).matches()) {
            throw new RuntimeException("设备ID格式无效");
        }
        return normalized;
    }

    // 规范化必填文本
    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(errorMessage);
        }
        return value.trim();
    }

    // 规范化设备类型并应用ESP8266默认值
    private String normalizeType(String value) {
        return value == null || value.trim().isEmpty() ? "ESP8266" : value.trim();
    }

    // 将空白可选文本转换为空值
    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
