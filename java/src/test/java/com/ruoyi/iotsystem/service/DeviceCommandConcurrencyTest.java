package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.TestInfrastructureConfiguration;
import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "device.command.timeout-seconds=5",
        "device.command.dispatch-interval-ms=3600000",
        "device.command.timeout-scan-interval-ms=3600000"
})
@ActiveProfiles("test")
@Import(TestInfrastructureConfiguration.class)
class DeviceCommandConcurrencyTest {
    @Autowired private DeviceCommandService service;
    @Autowired private DeviceCommandDispatcher dispatcher;
    @Autowired private DeviceCommandRepository repository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private MqttMessageService mqtt;
    private TransactionTemplate transaction;
    private String deviceId;

    // 在隔离H2中创建设备，所有网络发布均使用替身。
    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        deviceId = "command-" + UUID.randomUUID();
        deviceRepository.save(new DeviceEntity(deviceId, deviceId, "ESP8266", null, null, true, "alice"));
    }

    // 清理本测试创建的命令与设备，不接触任何外部数据库。
    @AfterEach
    void cleanUp() {
        repository.deleteAll(repository.findTop100ByOwnerUsernameOrderByCreatedAtDesc("alice"));
        deviceRepository.findByDeviceId(deviceId).ifPresent(deviceRepository::delete);
    }

    // 模拟持久化后进程中断，直接写入待办而不发布事务事件。
    private DeviceCommandEntity seed(String state, boolean expired) {
        DeviceCommandEntity command = new DeviceCommandEntity(UUID.randomUUID().toString(), deviceId, "alice", "status");
        command.setStatus(state);
        command = repository.save(command);
        if (expired) {
            Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusSeconds(30));
            jdbc.update("UPDATE device_commands SET created_at = ?, dispatched_at = ? WHERE command_id = ?",
                    old, "PENDING".equals(state) ? null : old, command.getCommandId());
        }
        return command;
    }

    // 从新事务中读取持久化结果，避免一级缓存遮蔽并发更新。
    private DeviceCommandEntity reload(String commandId) {
        return repository.findByCommandId(commandId).orElseThrow(AssertionError::new);
    }

    // 验证外层事务提交前不发布，且提前伪造的回执不能确认未派发命令。
    @Test
    void publishesOnlyAfterBusinessTransactionCommits() {
        DeviceCommandEntity command = transaction.execute(status -> {
            DeviceCommandEntity pending = service.issue(deviceId, "alice", "status");
            verifyNoInteractions(mqtt);
            service.acknowledge(deviceId, pending.getCommandId(), "ACKNOWLEDGED");
            assertEquals("PENDING", reload(pending.getCommandId()).getStatus());
            return pending;
        });

        verify(mqtt).publishControl(deviceId, "status", command.getCommandId());
        assertEquals("DISPATCHED", reload(command.getCommandId()).getStatus());
        assertNotNull(reload(command.getCommandId()).getDispatchedAt());
    }

    // 验证外层业务回滚时命令意图和MQTT副作用均不存在。
    @Test
    void rollbackNeverPublishesOrLeavesACommand() {
        String commandId = transaction.execute(status -> {
            DeviceCommandEntity pending = service.issue(deviceId, "alice", "start");
            status.setRollbackOnly();
            return pending.getCommandId();
        });

        verifyNoInteractions(mqtt);
        assertFalse(repository.findByCommandId(commandId).isPresent());
    }

    // 模拟publish返回前的快速ACK，确认审计记录已经提交且终态不会被发布结果覆盖。
    @Test
    void fastAcknowledgementWinsBeforePublishReturns() {
        doAnswer(invocation -> {
            String commandId = invocation.getArgument(2);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("DISPATCHING", reload(commandId).getStatus());
            service.acknowledge(deviceId, commandId, "ACKNOWLEDGED");
            return null;
        }).when(mqtt).publishControl(eq(deviceId), eq("status"), anyString());

        String commandId = service.issue(deviceId, "alice", "status").getCommandId();
        DeviceCommandEntity acknowledged = reload(commandId);
        assertEquals("ACKNOWLEDGED", acknowledged.getStatus());
        assertNotNull(acknowledged.getAcknowledgedAt());
        service.acknowledge(deviceId, commandId, "REJECTED");
        assertEquals("ACKNOWLEDGED", reload(commandId).getStatus());
        assertEquals(acknowledged.getAcknowledgedAt(), reload(commandId).getAcknowledgedAt());
    }

    // 验证快速ACK之后出现的传输异常也不能把已确认命令改为失败。
    @Test
    void transportFailureCannotOverwriteAnAcknowledgement() {
        doAnswer(invocation -> {
            service.acknowledge(deviceId, invocation.getArgument(2), "ACKNOWLEDGED");
            throw new IllegalStateException("simulated transport failure after delivery");
        }).when(mqtt).publishControl(eq(deviceId), eq("status"), anyString());

        String commandId = service.issue(deviceId, "alice", "status").getCommandId();

        assertEquals("ACKNOWLEDGED", reload(commandId).getStatus());
    }

    // 验证发布失败有持久化终态，错误设备和非法状态回执均不能改变命令。
    @Test
    void publicationFailureAndInvalidAcknowledgementsRemainSafe() {
        doThrow(new IllegalStateException("simulated unavailable broker"))
                .when(mqtt).publishControl(eq(deviceId), eq("status"), anyString());
        String commandId = service.issue(deviceId, "alice", "status").getCommandId();
        assertEquals("FAILED", reload(commandId).getStatus());
        service.acknowledge(deviceId, commandId, "ACKNOWLEDGED");
        assertEquals("FAILED", reload(commandId).getStatus());

        DeviceCommandEntity pending = seed("DISPATCHED", false);
        service.acknowledge("other-device", pending.getCommandId(), "ACKNOWLEDGED");
        service.acknowledge(deviceId, pending.getCommandId(), "SUCCESS");
        assertEquals("DISPATCHED", reload(pending.getCommandId()).getStatus());
        service.acknowledge(deviceId, pending.getCommandId(), "REJECTED");
        assertEquals("REJECTED", reload(pending.getCommandId()).getStatus());
    }

    // 并发抢占同一持久化待办时只允许一次MQTT发布。
    @Test
    void concurrentDispatchersPublishOnce() throws Exception {
        String commandId = seed("PENDING", false).getCommandId();
        race(() -> dispatcher.dispatch(commandId), () -> dispatcher.dispatch(commandId));

        verify(mqtt, times(1)).publishControl(deviceId, "status", commandId);
        assertEquals("DISPATCHED", reload(commandId).getStatus());
    }

    // 同时执行超时与ACK时仅一个终态获胜，随后重复动作不得覆盖获胜状态。
    @Test
    void concurrentTimeoutAndAcknowledgementKeepOneTerminalState() throws Exception {
        String commandId = seed("DISPATCHED", true).getCommandId();
        race(() -> service.acknowledge(deviceId, commandId, "ACKNOWLEDGED"),
                service::expireUnacknowledgedCommands);

        String finalStatus = reload(commandId).getStatus();
        assertTrue("ACKNOWLEDGED".equals(finalStatus) || "TIMED_OUT".equals(finalStatus));
        service.expireUnacknowledgedCommands();
        service.acknowledge(deviceId, commandId, "REJECTED");
        assertEquals(finalStatus, reload(commandId).getStatus());
    }

    // 验证提交后丢失事件的待办可恢复，已过期或派发结果不明的命令不会自动重发。
    @Test
    void recoveryDispatchesOnlyFreshPendingCommands() {
        String fresh = seed("PENDING", false).getCommandId();
        String expired = seed("PENDING", true).getCommandId();
        String uncertain = seed("DISPATCHING", true).getCommandId();

        dispatcher.recoverPendingCommands();
        dispatcher.recoverPendingCommands();
        service.expireUnacknowledgedCommands();

        verify(mqtt, times(1)).publishControl(deviceId, "status", fresh);
        verify(mqtt, never()).publishControl(deviceId, "status", expired);
        verify(mqtt, never()).publishControl(deviceId, "status", uncertain);
        assertEquals("TIMED_OUT", reload(expired).getStatus());
        assertEquals("TIMED_OUT", reload(uncertain).getStatus());
    }

    // 验证命令入队后设备停用时重新校验权限，不能按旧快照发布。
    @Test
    void queuedCommandRechecksDeviceBeforePublishing() {
        String commandId = seed("PENDING", false).getCommandId();
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).get();
        device.setEnabled(false);
        deviceRepository.save(device);

        dispatcher.dispatch(commandId);

        verifyNoInteractions(mqtt);
        assertEquals("FAILED", reload(commandId).getStatus());
    }

    // 使用屏障同时启动两个独立线程并等待完成，避免依靠睡眠猜测竞态。
    private void race(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> left = executor.submit(() -> { await(start); first.run(); });
            Future<?> right = executor.submit(() -> { await(start); second.run(); });
            start.countDown();
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    // 等待并发起跑信号并将中断显式报告为测试失败。
    private void await(CountDownLatch start) {
        try {
            assertTrue(start.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
