package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommandEntity, Long> {
    Optional<DeviceCommandEntity> findByCommandId(String commandId);
}
