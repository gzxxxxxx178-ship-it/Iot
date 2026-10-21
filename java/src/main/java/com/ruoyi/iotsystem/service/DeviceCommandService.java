package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final long timeoutSeconds;
    // 注入命令仓库和事务事件发布器，校验命令有效时限。
    public DeviceCommandService(DeviceCommandRepository repository, ApplicationEventPublisher eventPublisher,
            @Value("${device.command.timeout-seconds:60}") long timeoutSeconds) {
        if (timeoutSeconds < 5 || timeoutSeconds > 3600) throw new IllegalArgumentException("设备命令超时时间必须在5到3600秒之间");
        this.repository = repository; this.eventPublisher = eventPublisher; this.timeoutSeconds = timeoutSeconds;
    }
    // 仅保存待派发意图，MQTT发布必须等待当前或外层业务事务成功提交。
    @Transactional
    public DeviceCommandEntity issue(String deviceId, String ownerUsername, String command) {
        if (!("start".equals(command) || "stop".equals(command)
                || "read".equals(command) || "status".equals(command))) {
            throw new IllegalArgumentException("不支持的设备控制指令");
        }
        DeviceCommandEntity entity = new DeviceCommandEntity(UUID.randomUUID().toString(), deviceId, ownerUsername, command);
        entity.setMessage("命令已受理，等待事务提交后派发");
        entity = repository.save(entity);
        eventPublisher.publishEvent(new CommandQueued(entity.getCommandId()));
        return entity;
    }
    // 查询当前用户最近命令，供控制界面观察设备确认结果
    public List<DeviceCommandEntity> getRecentCommands(String ownerUsername) {
        return repository.findTop100ByOwnerUsernameOrderByCreatedAtDesc(ownerUsername);
    }
    // 接受派发中或等待回执状态的确认，重复或迟到回执不改变既有终态。
    @Transactional
    public void acknowledge(String deviceId, String commandId, String status) {
        if (!("ACKNOWLEDGED".equals(status) || "REJECTED".equals(status))) {
            return;
        }
        repository.acknowledge(deviceId, commandId, status, LocalDateTime.now(),
                "ACKNOWLEDGED".equals(status) ? "设备已确认" : "设备拒绝执行");
    }

    // 周期性关闭过期命令，通过条件更新与并发回执争用同一终态。
    @Scheduled(fixedDelayString = "${device.command.timeout-scan-interval-ms:10000}",
            initialDelayString = "${device.command.timeout-scan-interval-ms:10000}")
    @Transactional
    public void expireUnacknowledgedCommands() {
        repository.expireUnfinished(LocalDateTime.now().minusSeconds(timeoutSeconds),
                "命令已超过" + timeoutSeconds + "秒有效时限；设备执行结果需核查");
    }

    // 表示意图已写入当前事务，监听器只在成功提交后派发。
    public static final class CommandQueued {
        private final String commandId;

        // 创建不携带托管实体的事务事件。
        public CommandQueued(String commandId) {
            this.commandId = commandId;
        }

        // 获取待派发命令的不可变标识。
        public String getCommandId() {
            return commandId;
        }
    }
}
