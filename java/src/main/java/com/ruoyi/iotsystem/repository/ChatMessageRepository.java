package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedTimeAsc(String sessionId);

    @Modifying
    @Transactional
    void deleteBySessionId(String sessionId);
}
