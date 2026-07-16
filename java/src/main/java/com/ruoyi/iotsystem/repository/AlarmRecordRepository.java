package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.AlarmRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlarmRecordRepository extends JpaRepository<AlarmRecordEntity, Long> {

    // 查询最近100条报警记录
    List<AlarmRecordEntity> findTop100ByOrderByCreatedAtDesc();

    // 按时间范围倒序查询报警记录
    List<AlarmRecordEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end);

    // 查询指定规则和设备最近一次报警记录
    Optional<AlarmRecordEntity> findFirstByRuleIdAndDeviceIdOrderByCreatedAtDesc(
            Long ruleId,
            String deviceId);

    // 按用户查询最近报警记录
    List<AlarmRecordEntity> findTop100ByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    // 按用户和时间范围查询报警记录
    List<AlarmRecordEntity> findByOwnerUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(
            String ownerUsername, LocalDateTime start, LocalDateTime end);

    // 按用户查询指定规则和设备最近一次报警
    Optional<AlarmRecordEntity> findFirstByOwnerUsernameAndRuleIdAndDeviceIdOrderByCreatedAtDesc(
            String ownerUsername, Long ruleId, String deviceId);
}
