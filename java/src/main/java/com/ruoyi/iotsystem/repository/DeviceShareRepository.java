package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.DeviceShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceShareRepository extends JpaRepository<DeviceShareEntity, Long> {
    // 查询授权给指定账号的全部共享关系
    List<DeviceShareEntity> findByGranteeUsername(String granteeUsername);

    // 查询指定设备的共享名单
    List<DeviceShareEntity> findByDeviceIdOrderByCreatedAtAsc(String deviceId);

    // 查询账号是否有指定设备的共享权限
    boolean existsByDeviceIdAndGranteeUsername(String deviceId, String granteeUsername);

    // 按设备和账号删除共享关系
    void deleteByDeviceIdAndGranteeUsername(String deviceId, String granteeUsername);
}
