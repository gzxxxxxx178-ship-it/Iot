package com.ruoyi.iotsystem.simulation.repository;

import com.ruoyi.iotsystem.simulation.entity.SimulationCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 仿真命令仓库，按用户、设备和状态查询待处理命令。
 */
@Repository
public interface SimulationCommandRepository extends JpaRepository<SimulationCommandEntity, Long> {

    // 按当前用户和设备查询待处理的命令
    List<SimulationCommandEntity> findByOwnerUsernameAndDeviceIdAndStatusOrderByCreatedAtDesc(
            String ownerUsername, String deviceId, String status);
}
