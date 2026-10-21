package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DeviceCreateRequest;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.repository.DeviceShareRepository;
import com.ruoyi.iotsystem.repository.UserRepository;
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
    private final DeviceShareRepository deviceShareRepository;
    private final UserRepository userRepository;

    // 注入设备档案、传感器读数和在线超时配置
    public DeviceService(DeviceRepository deviceRepository, EspRepository espRepository,
            @Value("${dashboard.device-online-timeout-seconds:30}") long onlineTimeoutSeconds,
            DeviceShareRepository deviceShareRepository, UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.espRepository = espRepository;
        this.onlineTimeoutSeconds = onlineTimeoutSeconds;
        this.deviceShareRepository = deviceShareRepository;
        this.userRepository = userRepository;
    }

    // 查询设备档案并附加最新读数与实时在线状态
    @Transactional
    public List<DeviceResponse> listDevices(boolean includeArchived) {
        return listDevices(includeArchived, null);
    }

    // 查询指定用户设备并保留无主遥测设备的自动发现
    @Transactional
    public List<DeviceResponse> listDevices(boolean includeArchived, String ownerUsername) {
        synchronizeLegacyDevices();
        List<DeviceEntity> devices;
        if (ownerUsername == null) {
            devices = includeArchived
                    ? deviceRepository.findAllByOrderByCreatedAtDesc()
                    : deviceRepository.findByLifecycleStatusOrderByCreatedAtDesc(ACTIVE);
        } else {
            devices = visibleDevices(ownerUsername);
            if (!includeArchived) {
                devices.removeIf(device -> !ACTIVE.equals(device.getLifecycleStatus()));
            }
        }
        List<DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(toResponse(device, ownerUsername));
        }
        return responses;
    }

    // 查询指定设备详情
    public DeviceResponse getDevice(String deviceId) {
        return getDevice(deviceId, null);
    }

    // 查询当前用户可访问的设备详情
    public DeviceResponse getDevice(String deviceId, String ownerUsername) {
        return toResponse(findAccessibleDevice(deviceId, ownerUsername), ownerUsername);
    }

    // 校验设备ID唯一性并创建设备档案
    public DeviceResponse createDevice(DeviceCreateRequest request) {
        return createDevice(request, null);
    }

    // 创建绑定当前用户的设备档案
    public DeviceResponse createDevice(DeviceCreateRequest request, String ownerUsername) {
        String deviceId = normalizeDeviceId(request.getDeviceId());
        if (deviceRepository.existsByDeviceId(deviceId)) {
            throw new BusinessException("设备ID已存在");
        }
        DeviceEntity device = new DeviceEntity(
                deviceId,
                normalizeRequired(request.getDeviceName(), "设备名称不能为空"),
                normalizeType(request.getDeviceType()),
                normalizeOptional(request.getLocation()),
                normalizeOptional(request.getDescription()),
                request.getEnabled() == null ? Boolean.TRUE : request.getEnabled(), ownerUsername);
        return toResponse(deviceRepository.save(device), ownerUsername);
    }

    // 更新设备可编辑档案但保持设备ID不变
    public DeviceResponse updateDevice(String deviceId, DeviceUpdateRequest request) {
        return updateDevice(deviceId, request, null);
    }

    // 更新当前用户所属设备档案
    public DeviceResponse updateDevice(String deviceId, DeviceUpdateRequest request, String ownerUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        if (ARCHIVED.equals(device.getLifecycleStatus())) {
            throw new BusinessException("归档设备需先恢复后才能编辑");
        }
        device.setDeviceName(normalizeRequired(request.getDeviceName(), "设备名称不能为空"));
        device.setDeviceType(normalizeType(request.getDeviceType()));
        device.setLocation(normalizeOptional(request.getLocation()));
        device.setDescription(normalizeOptional(request.getDescription()));
        device.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        device.setUpdatedAt(LocalDateTime.now());
        return toResponse(deviceRepository.save(device), ownerUsername);
    }

    // 软删除设备档案并禁止继续控制和接收入库
    public void archiveDevice(String deviceId) {
        archiveDevice(deviceId, null);
    }

    // 归档当前用户所属设备并保留历史数据
    public void archiveDevice(String deviceId, String ownerUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        device.setLifecycleStatus(ARCHIVED);
        device.setEnabled(Boolean.FALSE);
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(device);
    }

    // 恢复归档设备并重新启用
    public DeviceResponse restoreDevice(String deviceId) {
        return restoreDevice(deviceId, null);
    }

    // 恢复当前用户所属设备档案
    public DeviceResponse restoreDevice(String deviceId, String ownerUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        device.setLifecycleStatus(ACTIVE);
        device.setEnabled(Boolean.TRUE);
        device.setUpdatedAt(LocalDateTime.now());
        return toResponse(deviceRepository.save(device), ownerUsername);
    }

    // 在发送远程控制前校验设备处于可操作状态
    public void assertControllable(String deviceId) {
        assertControllable(deviceId, null);
    }

    // 校验当前用户是否拥有可控制的设备
    public void assertControllable(String deviceId, String ownerUsername) {
        DeviceEntity device = findAccessibleDevice(deviceId, ownerUsername);
        if (!ACTIVE.equals(device.getLifecycleStatus()) || !Boolean.TRUE.equals(device.getEnabled())) {
            throw new BusinessException("设备已停用或归档，不能发送控制指令");
        }
    }

    // 校验自动化规则目标属于当前用户，并按规则启用状态确认设备生命周期与启用状态
    @Transactional
    public void assertAutomationTarget(String deviceId, String ownerUsername,
            boolean requireActive, boolean requireEnabled) {
        DeviceEntity device = findDevice(deviceId, ownerUsername);
        if (requireActive && !ACTIVE.equals(device.getLifecycleStatus())) {
            throw new BusinessException("自动化规则目标设备已归档");
        }
        if (requireEnabled && !Boolean.TRUE.equals(device.getEnabled())) {
            throw new BusinessException("启用自动化规则要求目标设备处于启用状态");
        }
    }

    // 接收可信上报时自动登记未知设备并拒绝停用或归档设备
    public void ensureTelemetryAllowed(String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        DeviceEntity device = deviceRepository.findByDeviceId(normalized)
                .orElseGet(() -> createDiscoveredDevice(normalized));
        if (!ACTIVE.equals(device.getLifecycleStatus()) || !Boolean.TRUE.equals(device.getEnabled())) {
            throw new BusinessException("设备已停用或归档，拒绝接收上报数据");
        }
    }

    // 返回设备所属用户，用于传感器规则执行和历史数据范围控制
    public String getOwnerUsername(String deviceId) {
        return findDevice(deviceId).getOwnerUsername();
    }

    // 查询当前设备所有者创建的共享账号名单
    @Transactional(readOnly = true)
    public List<String> listShares(String deviceId, String ownerUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        return deviceShareRepository.findByDeviceIdOrderByCreatedAtAsc(device.getDeviceId()).stream()
                .map(share -> share.getGranteeUsername()).collect(java.util.stream.Collectors.toList());
    }

    // 由设备所有者授权一个已存在账号查看、读取历史和控制设备
    @Transactional
    public List<String> addShare(String deviceId, String ownerUsername, String granteeUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        String grantee = granteeUsername == null ? "" : granteeUsername.trim();
        if (grantee.isEmpty() || grantee.length() > 100) {
            throw new BusinessException("共享账号名无效");
        }
        if (ownerUsername.equals(grantee)) {
            throw new BusinessException("设备所有者无需共享给自己");
        }
        if (userRepository == null || !userRepository.existsByUsername(grantee)) {
            throw new BusinessException("该账号不存在");
        }
        if (!deviceShareRepository.existsByDeviceIdAndGranteeUsername(device.getDeviceId(), grantee)) {
            deviceShareRepository.save(new com.ruoyi.iotsystem.entity.DeviceShareEntity(device.getDeviceId(), grantee));
        }
        return listShares(device.getDeviceId(), ownerUsername);
    }

    // 由设备所有者撤销指定账号的设备共享权限
    @Transactional
    public List<String> removeShare(String deviceId, String ownerUsername, String granteeUsername) {
        DeviceEntity device = findOwnedDevice(deviceId, ownerUsername);
        deviceShareRepository.deleteByDeviceIdAndGranteeUsername(device.getDeviceId(), granteeUsername);
        return listShares(device.getDeviceId(), ownerUsername);
    }

    // 校验当前账号拥有设备或受邀共享权限
    public void assertCanAccess(String deviceId, String username) {
        findAccessibleDevice(deviceId, username);
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
        return findDevice(deviceId, null);
    }

    // 查询设备并强制校验所属用户
    private DeviceEntity findDevice(String deviceId, String ownerUsername) {
        DeviceEntity device = deviceRepository.findByDeviceId(normalizeDeviceId(deviceId))
                .orElseThrow(() -> new BusinessException("设备不存在"));
        if (ownerUsername != null && !ownerUsername.equals(device.getOwnerUsername())) {
            throw new SecurityException("无权访问该设备");
        }
        return device;
    }

    // 查询账号可访问的设备并阻止访问无主设备或越权访问
    private DeviceEntity findAccessibleDevice(String deviceId, String username) {
        DeviceEntity device = deviceRepository.findByDeviceId(normalizeDeviceId(deviceId))
                .orElseThrow(() -> new BusinessException("设备不存在"));
        if (device.getOwnerUsername() == null || device.getOwnerUsername().trim().isEmpty()) {
            throw new SecurityException("无权访问该设备");
        }
        if (username != null && !username.equals(device.getOwnerUsername())
                && (deviceShareRepository == null
                    || !deviceShareRepository.existsByDeviceIdAndGranteeUsername(device.getDeviceId(), username))) {
            throw new SecurityException("无权访问该设备");
        }
        return device;
    }

    // 查询设备并强制要求当前账号为所有者
    private DeviceEntity findOwnedDevice(String deviceId, String username) {
        DeviceEntity device = deviceRepository.findByDeviceId(normalizeDeviceId(deviceId))
                .orElseThrow(() -> new BusinessException("设备不存在"));
        if (username != null && !username.equals(device.getOwnerUsername())) {
            throw new SecurityException("仅设备所有者可以管理设备档案和共享权限");
        }
        return device;
    }

    // 合并当前账号拥有和获授权的设备并按创建时间倒序返回
    private List<DeviceEntity> visibleDevices(String username) {
        List<DeviceEntity> devices = new ArrayList<>(deviceRepository.findByOwnerUsernameOrderByCreatedAtDesc(username));
        if (deviceShareRepository != null) {
            List<String> sharedIds = deviceShareRepository.findByGranteeUsername(username).stream()
                    .map(share -> share.getDeviceId()).collect(java.util.stream.Collectors.toList());
            if (!sharedIds.isEmpty()) {
                for (DeviceEntity device : deviceRepository.findByDeviceIdIn(sharedIds)) {
                    if (device.getOwnerUsername() == null || device.getOwnerUsername().trim().isEmpty()) {
                        continue;
                    }
                    if (devices.stream().noneMatch(existing -> existing.getDeviceId().equals(device.getDeviceId()))) {
                        devices.add(device);
                    }
                }
            }
        }
        devices.sort((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()));
        return devices;
    }

    // 组合档案和最新传感器读数形成响应对象
    private DeviceResponse toResponse(DeviceEntity device) {
        return toResponse(device, null);
    }

    // 组合设备状态并标记当前账号是否为所有者
    private DeviceResponse toResponse(DeviceEntity device, String username) {
        Optional<EspEntity> latest = espRepository
                .findFirstByDeviceIdOrderByServerReceivedTimeDesc(device.getDeviceId());
        boolean online = latest.isPresent() && isOnline(device, latest.get());
        return new DeviceResponse(device, latest.orElse(null), online,
                username != null && username.equals(device.getOwnerUsername()));
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
            throw new BusinessException("设备ID格式无效");
        }
        return normalized;
    }

    // 规范化必填文本
    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(errorMessage);
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
