package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.EspEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EspRepository extends JpaRepository<EspEntity, Long> {

    // 最近20条（首页快速加载）
    List<EspEntity> findTop20ByOrderByServerReceivedTimeDesc();

    // 按时间范围分页查询
    Page<EspEntity> findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 全量分页查询（不需要时间范围时使用）
    Page<EspEntity> findAllByOrderByServerReceivedTimeDesc(Pageable pageable);

    // 保留原有的非分页查询（向后兼容）
    List<EspEntity> findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT DISTINCT e.deviceId FROM EspEntity e")
    List<String> findDistinctDeviceIds();

    @Query("SELECT e FROM EspEntity e WHERE e.deviceId = ?1 ORDER BY e.serverReceivedTime DESC")
    List<EspEntity> findLatestByDeviceId(String deviceId);

    // 按服务端接收时间查询指定设备的最新一条数据
    Optional<EspEntity> findFirstByDeviceIdOrderByServerReceivedTimeDesc(String deviceId);
}
