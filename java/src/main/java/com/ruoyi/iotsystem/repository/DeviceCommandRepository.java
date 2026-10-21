package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommandEntity, Long> {
    // 根据不可变命令标识查询审计记录。
    Optional<DeviceCommandEntity> findByCommandId(String commandId);

    // 一次查询执行列表关联的命令，避免逐条查询。
    List<DeviceCommandEntity> findByCommandIdIn(Collection<String> commandIds);

    // 仅恢复仍在有效时限内的待派发命令，每批限制一百条。
    List<DeviceCommandEntity> findTop100ByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String status, LocalDateTime createdAt);

    // 查询当前账号最近一百条命令。
    List<DeviceCommandEntity> findTop100ByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    // 原子抢占尚未过期的待派发意图，提交后才允许接触MQTT。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceCommandEntity c SET c.status = 'DISPATCHING', c.dispatchedAt = :now, "
            + "c.message = '正在派发，尚未获设备确认' WHERE c.commandId = :commandId "
            + "AND c.status = 'PENDING' AND c.createdAt >= :deadline")
    int claimPending(@Param("commandId") String commandId, @Param("now") LocalDateTime now,
            @Param("deadline") LocalDateTime deadline);

    // 仅更新尚未收到终态的派发结果，避免覆盖快速ACK。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceCommandEntity c SET c.status = :status, c.message = :message "
            + "WHERE c.commandId = :commandId AND c.status = 'DISPATCHING'")
    int completeDispatch(@Param("commandId") String commandId, @Param("status") String status,
            @Param("message") String message);

    // 将匹配设备且尚未结束的派发命令原子转为设备回执终态。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceCommandEntity c SET c.status = :status, c.acknowledgedAt = :now, "
            + "c.message = :message WHERE c.commandId = :commandId AND c.deviceId = :deviceId "
            + "AND c.status IN ('DISPATCHING', 'DISPATCHED')")
    int acknowledge(@Param("deviceId") String deviceId, @Param("commandId") String commandId,
            @Param("status") String status, @Param("now") LocalDateTime now,
            @Param("message") String message);

    // 以条件更新关闭过期命令，绝不覆盖已确认或拒绝的状态。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceCommandEntity c SET c.status = 'TIMED_OUT', c.message = :message "
            + "WHERE (c.status = 'PENDING' AND c.createdAt < :deadline) "
            + "OR (c.status IN ('DISPATCHING', 'DISPATCHED') "
            + "AND COALESCE(c.dispatchedAt, c.createdAt) < :deadline)")
    int expireUnfinished(@Param("deadline") LocalDateTime deadline, @Param("message") String message);
}
