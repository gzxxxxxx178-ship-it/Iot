package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Service
public class DeviceCommandService {
    private final DeviceCommandRepository repository;
    private final MqttMessageService mqttMessageService;
    private final long timeoutSeconds;
    public DeviceCommandService(DeviceCommandRepository repository, @Lazy MqttMessageService mqttMessageService,
            @Value("${device.command.timeout-seconds:60}") long timeoutSeconds) {
        if (timeoutSeconds < 5 || timeoutSeconds > 3600) throw new IllegalArgumentException("设备命令超时时间必须在5到3600秒之间");
        this.repository = repository; this.mqttMessageService = mqttMessageService; this.timeoutSeconds = timeoutSeconds;
    }
    // 创建可审计命令，再发布带唯一标识的MQTT载荷
    @Transactional
    public DeviceCommandEntity issue(String deviceId, String ownerUsername, String command) {
        DeviceCommandEntity entity = repository.save(new DeviceCommandEntity(UUID.randomUUID().toString(), deviceId, ownerUsername, command));
        try {
            mqttMessageService.publishControl(deviceId, command, entity.getCommandId());
            entity.setStatus("DISPATCHED"); entity.setMessage("已发布，等待设备确认");
        } catch (RuntimeException exception) {
            entity.setStatus("FAILED"); entity.setMessage("MQTT发布失败");
        }
        return repository.save(entity);
    }
    // 查询当前用户最近命令，供控制界面观察设备确认结果
    public List<DeviceCommandEntity> getRecentCommands(String ownerUsername) {
        return repository.findTop100ByOwnerUsernameOrderByCreatedAtDesc(ownerUsername);
    }
    // 校验设备与命令匹配后记录一次确认，重复ACK保持幂等
    @Transactional
    public void acknowledge(String deviceId, String commandId, String status) {
        repository.findByCommandId(commandId).ifPresent(entity -> {
            if (!deviceId.equals(entity.getDeviceId()) || !"DISPATCHED".equals(entity.getStatus())) return;
            entity.setStatus("ACKNOWLEDGED".equals(status) ? "ACKNOWLEDGED" : "REJECTED");
            entity.setAcknowledgedAt(LocalDateTime.now());
            entity.setMessage("ACKNOWLEDGED".equals(status) ? "设备已确认" : "设备拒绝执行");
            repository.save(entity);
        });
    }

    // 周期性关闭未确认的已下发命令，避免把设备失联误显示为处理中
    @Scheduled(fixedDelayString = "${device.command.timeout-scan-interval-ms:10000}")
    @Transactional
    public void expireUnacknowledgedCommands() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        for (DeviceCommandEntity entity : repository.findByStatusAndCreatedAtBefore("DISPATCHED", deadline)) {
            entity.setStatus("TIMED_OUT");
            entity.setMessage("设备未在" + timeoutSeconds + "秒内确认");
            repository.save(entity);
        }
    }
}
