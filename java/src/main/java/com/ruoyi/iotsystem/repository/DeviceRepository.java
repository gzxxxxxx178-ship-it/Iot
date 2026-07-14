package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    // 按创建时间倒序查询全部设备档案
    List<DeviceEntity> findAllByOrderByCreatedAtDesc();

    // 查询未归档设备档案
    List<DeviceEntity> findByLifecycleStatusOrderByCreatedAtDesc(String lifecycleStatus);

    // 按不可变设备ID查询档案
    Optional<DeviceEntity> findByDeviceId(String deviceId);

    // 判断设备ID是否已经注册
    boolean existsByDeviceId(String deviceId);
}
