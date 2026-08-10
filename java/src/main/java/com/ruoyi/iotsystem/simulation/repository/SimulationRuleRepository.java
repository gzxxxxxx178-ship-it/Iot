package com.ruoyi.iotsystem.simulation.repository;

import com.ruoyi.iotsystem.simulation.entity.SimulationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 仿真规则仓库，按用户查询。
 */
@Repository
public interface SimulationRuleRepository extends JpaRepository<SimulationRuleEntity, Long> {

    // 按当前用户按主键倒序查询全部规则
    List<SimulationRuleEntity> findByOwnerUsernameOrderByIdDesc(String ownerUsername);

    // 按当前用户按主键升序查询全部启用规则
    List<SimulationRuleEntity> findByEnabledTrueAndOwnerUsernameOrderByIdAsc(String ownerUsername);
}
