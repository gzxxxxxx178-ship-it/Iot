package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRuleEntity, Long> {

    // 按主键顺序查询全部启用规则
    List<AutomationRuleEntity> findByEnabledTrueOrderByIdAsc();

    // 按主键倒序查询全部规则
    List<AutomationRuleEntity> findAllByOrderByIdDesc();

    // 按用户查询自动化规则
    List<AutomationRuleEntity> findByOwnerUsernameOrderByIdDesc(String ownerUsername);

    // 按用户查询启用的自动化规则
    List<AutomationRuleEntity> findByEnabledTrueAndOwnerUsernameOrderByIdAsc(String ownerUsername);
}
