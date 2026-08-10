package com.ruoyi.iotsystem.simulation.repository;

import com.ruoyi.iotsystem.simulation.entity.SimulationTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 仿真遥测数据仓库，按用户查询并支持幂等。
 */
@Repository
public interface SimulationTelemetryRepository extends JpaRepository<SimulationTelemetryEntity, Long> {

    // 按当前用户和样本ID幂等查询
    Optional<SimulationTelemetryEntity> findByOwnerUsernameAndSampleId(String ownerUsername, String sampleId);

    // 按当前用户和设备查询最新一条遥测
    Optional<SimulationTelemetryEntity> findFirstByOwnerUsernameAndDeviceIdOrderByOccurredAtDesc(
            String ownerUsername, String deviceId);

    // 按当前用户和设备分页查询历史遥测
    List<SimulationTelemetryEntity> findByOwnerUsernameAndDeviceIdOrderByOccurredAtDesc(
            String ownerUsername, String deviceId);
}
