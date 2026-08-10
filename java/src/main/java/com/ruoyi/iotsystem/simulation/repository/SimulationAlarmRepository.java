package com.ruoyi.iotsystem.simulation.repository;

import com.ruoyi.iotsystem.simulation.entity.SimulationAlarmEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 仿真报警仓库，按用户、设备和状态查询。
 */
@Repository
public interface SimulationAlarmRepository extends JpaRepository<SimulationAlarmEntity, Long> {

    // 按当前用户、设备和状态查询报警
    List<SimulationAlarmEntity> findByOwnerUsernameAndDeviceIdAndStatusOrderByTriggeredAtDesc(
            String ownerUsername, String deviceId, String status);

    // 按当前用户和设备查询全部报警
    List<SimulationAlarmEntity> findByOwnerUsernameAndDeviceIdOrderByTriggeredAtDesc(
            String ownerUsername, String deviceId);

    // 查询指定用户、规则和设备下未恢复的报警
    Optional<SimulationAlarmEntity> findFirstByOwnerUsernameAndRuleIdAndDeviceIdAndStatusNotOrderByTriggeredAtDesc(
            String ownerUsername, Long ruleId, String deviceId, String status);

    // 查询指定用户和设备下全部未恢复的报警
    List<SimulationAlarmEntity> findByOwnerUsernameAndDeviceIdAndStatusNot(
            String ownerUsername, String deviceId, String status);
}
