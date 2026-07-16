package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.EspEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EspRepository extends JpaRepository<EspEntity, Long> {

    // 最近20条（首页快速加载）
    List<EspEntity> findTop20ByOrderByServerReceivedTimeDesc();

    // 查询指定用户最近20条数据
    List<EspEntity> findTop20ByOwnerUsernameOrderByServerReceivedTimeDesc(String ownerUsername);

    // 按用户和时间范围分页查询
    Page<EspEntity> findByOwnerUsernameAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            String ownerUsername, LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 按用户、设备和时间范围分页查询
    Page<EspEntity> findByOwnerUsernameAndDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            String ownerUsername, String deviceId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 按用户和设备分页查询全部历史
    Page<EspEntity> findByOwnerUsernameAndDeviceIdOrderByServerReceivedTimeDesc(
            String ownerUsername, String deviceId, Pageable pageable);

    // 按用户分页查询全部历史
    Page<EspEntity> findAllByOwnerUsernameOrderByServerReceivedTimeDesc(
            String ownerUsername, Pageable pageable);

    // 按时间范围分页查询
    Page<EspEntity> findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 按设备和时间范围使用组合索引分页查询
    Page<EspEntity> findByDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            String deviceId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 按设备分页查询全部历史数据
    Page<EspEntity> findByDeviceIdOrderByServerReceivedTimeDesc(String deviceId, Pageable pageable);

    // 全量分页查询（不需要时间范围时使用）
    Page<EspEntity> findAllByOrderByServerReceivedTimeDesc(Pageable pageable);

    // 保留原有的非分页查询（向后兼容）
    List<EspEntity> findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT DISTINCT e.deviceId FROM EspEntity e")
    List<String> findDistinctDeviceIds();

    // 查询指定用户拥有的设备ID
    @Query("SELECT DISTINCT e.deviceId FROM EspEntity e WHERE e.ownerUsername = :ownerUsername")
    List<String> findDistinctDeviceIdsByOwnerUsername(@Param("ownerUsername") String ownerUsername);

    @Query("SELECT e FROM EspEntity e WHERE e.deviceId = ?1 ORDER BY e.serverReceivedTime DESC")
    List<EspEntity> findLatestByDeviceId(String deviceId);

    // 按服务端接收时间查询指定设备的最新一条数据
    Optional<EspEntity> findFirstByDeviceIdOrderByServerReceivedTimeDesc(String deviceId);

    // 查询指定用户设备最近一条数据
    Optional<EspEntity> findFirstByOwnerUsernameAndDeviceIdOrderByServerReceivedTimeDesc(
            String ownerUsername, String deviceId);

    // 将升级前没有归属的历史数据归属到设备所有者
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EspEntity e SET e.ownerUsername = :ownerUsername "
            + "WHERE e.deviceId = :deviceId AND e.ownerUsername IS NULL")
    int assignOwnerToUnownedData(@Param("deviceId") String deviceId,
            @Param("ownerUsername") String ownerUsername);

    // 通过单条批量语句删除保留期之前的传感器历史数据
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EspEntity e WHERE e.serverReceivedTime < :cutoff")
    int deleteExpiredData(@Param("cutoff") LocalDateTime cutoff);
}
