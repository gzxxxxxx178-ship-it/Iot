package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecutionEntity, Long> {

    // 查询最近一百条自动化执行记录
    List<AutomationExecutionEntity> findTop100ByOrderByCreatedAtDesc();

    // 按用户查询最近自动化执行记录
    List<AutomationExecutionEntity> findTop100ByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
}
