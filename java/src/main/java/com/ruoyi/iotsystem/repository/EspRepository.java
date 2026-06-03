package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.EspEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EspRepository extends JpaRepository<EspEntity, Long> {
    List<EspEntity> findTop20ByOrderByServerReceivedTimeDesc();

    List<EspEntity> findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT DISTINCT e.deviceId FROM EspEntity e")
    List<String> findDistinctDeviceIds();

    @Query("SELECT e FROM EspEntity e WHERE e.deviceId = ?1 ORDER BY e.serverReceivedTime DESC")
    List<EspEntity> findLatestByDeviceId(String deviceId);
}
