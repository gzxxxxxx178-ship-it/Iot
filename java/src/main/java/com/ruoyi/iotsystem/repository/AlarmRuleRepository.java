package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.AlarmRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlarmRuleRepository extends JpaRepository<AlarmRuleEntity, Long> {

    // 按主键顺序查询全部启用规则
    List<AlarmRuleEntity> findByEnabledTrueOrderByIdAsc();

    // 按主键倒序查询全部规则
    List<AlarmRuleEntity> findAllByOrderByIdDesc();
}
