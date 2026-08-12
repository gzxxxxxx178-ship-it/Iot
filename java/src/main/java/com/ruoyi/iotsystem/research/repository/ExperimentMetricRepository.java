package com.ruoyi.iotsystem.research.repository;

import com.ruoyi.iotsystem.research.entity.ExperimentMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 研究实验指标数据访问层。
 */
public interface ExperimentMetricRepository extends JpaRepository<ExperimentMetricEntity, Long> {

    /**
     * 按运行ID查找所有指标
     */
    List<ExperimentMetricEntity> findByRunId(Long runId);
}
