package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DeviceCommandService {
    private final DeviceCommandRepository repository;
    private final MqttMessageService mqttMessageService;
    public DeviceCommandService(DeviceCommandRepository repository, @Lazy MqttMessageService mqttMessageService) {
        this.repository = repository; this.mqttMessageService = mqttMessageService;
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
}
