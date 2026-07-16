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

    // 查询指定用户的全部设备档案
    List<DeviceEntity> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    // 查询指定用户的未归档设备档案
    List<DeviceEntity> findByOwnerUsernameAndLifecycleStatusOrderByCreatedAtDesc(
            String ownerUsername, String lifecycleStatus);

    // 查询尚未完成归属迁移的历史设备
    List<DeviceEntity> findByOwnerUsernameIsNull();

    // 按不可变设备ID查询档案
    Optional<DeviceEntity> findByDeviceId(String deviceId);

    // 按设备ID和所属用户查询设备
    Optional<DeviceEntity> findByDeviceIdAndOwnerUsername(String deviceId, String ownerUsername);

    // 判断设备ID是否已经注册
    boolean existsByDeviceId(String deviceId);
}
