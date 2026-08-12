package com.ruoyi.iotsystem.research.repository;

import com.ruoyi.iotsystem.research.entity.ExperimentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 研究实验运行数据访问层。
 */
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, Long> {

    /**
     * 按owner和runKey查找运行记录（幂等校验）
     */
    Optional<ExperimentRunEntity> findByOwnerUsernameAndRunKey(String ownerUsername, String runKey);

    /**
     * 按owner查找指定类型的最新运行记录，按executedAt倒序取第一条
     */
    Optional<ExperimentRunEntity> findFirstByOwnerUsernameAndExperimentTypeOrderByExecutedAtDesc(
            String ownerUsername, String experimentType);

    /**
     * 按owner查找所有运行记录，按executedAt倒序
     */
    List<ExperimentRunEntity> findByOwnerUsernameOrderByExecutedAtDesc(String ownerUsername);

    /**
     * 按owner和类型查找运行记录，按executedAt倒序，限制条数
     */
    List<ExperimentRunEntity> findByOwnerUsernameAndExperimentTypeOrderByExecutedAtDesc(
            String ownerUsername, String experimentType);
}
