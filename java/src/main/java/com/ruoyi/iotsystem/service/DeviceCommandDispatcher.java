package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
public class DeviceCommandDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(DeviceCommandDispatcher.class);
    private final DeviceCommandRepository repository;
    private final MqttMessageService mqttMessageService;
    private final DeviceService deviceService;
    private final TransactionTemplate transaction;
    private final TransactionTemplate withoutTransaction;
    private final long timeoutSeconds;

    // 为抢占和状态更新分别建立新事务，MQTT调用始终位于数据库事务之外。
    public DeviceCommandDispatcher(DeviceCommandRepository repository, @Lazy MqttMessageService mqttMessageService,
            DeviceService deviceService, PlatformTransactionManager transactionManager,
            @Value("${device.command.timeout-seconds:60}") long timeoutSeconds) {
        this.repository = repository;
        this.mqttMessageService = mqttMessageService;
        this.deviceService = deviceService;
        this.timeoutSeconds = timeoutSeconds;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.withoutTransaction = new TransactionTemplate(transactionManager);
        this.withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    // 提交后立即派发，异常由持久化待办或超时扫描收口，不反向伪报事务失败。
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommandQueued(DeviceCommandService.CommandQueued event) {
        try {
            dispatch(event.getCommandId());
        } catch (RuntimeException exception) {
            logger.error("Command dispatch interrupted: commandId={}", event.getCommandId(), exception);
        }
    }

    // 恢复提交后尚未抢占的命令，已开始派发的命令不自动重发以避免重复动作。
    @Scheduled(fixedDelayString = "${device.command.dispatch-interval-ms:1000}",
            initialDelayString = "${device.command.dispatch-interval-ms:1000}")
    public void recoverPendingCommands() {
        for (DeviceCommandEntity command : repository
                .findTop100ByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        "PENDING", LocalDateTime.now().minusSeconds(timeoutSeconds))) {
            onCommandQueued(new DeviceCommandService.CommandQueued(command.getCommandId()));
        }
    }

    // 抢占并提交可见状态后发布，快速ACK可以先于publish返回并拥有终态。
    public void dispatch(String commandId) {
        withoutTransaction.execute(status -> {
            dispatchWithoutTransaction(commandId);
            return null;
        });
    }

    // 暂停原事务上下文后执行派发，避免提交回调残留的持久化资源影响状态更新。
    private void dispatchWithoutTransaction(String commandId) {
        DeviceCommandEntity command = transaction.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            if (repository.claimPending(commandId, now, now.minusSeconds(timeoutSeconds)) != 1) {
                return null;
            }
            return repository.findByCommandId(commandId).orElse(null);
        });
        if (command == null) {
            return;
        }
        try {
            deviceService.assertControllable(command.getDeviceId(), command.getOwnerUsername());
            mqttMessageService.publishControl(command.getDeviceId(), command.getCommand(), commandId);
        } catch (RuntimeException exception) {
            transaction.execute(status -> repository.completeDispatch(commandId, "FAILED", "命令校验或MQTT发布失败"));
            logger.warn("Command dispatch failed: commandId={}", commandId, exception);
            return;
        }
        transaction.execute(status -> repository.completeDispatch(commandId, "DISPATCHED", "已发布，等待设备确认"));
    }
}
