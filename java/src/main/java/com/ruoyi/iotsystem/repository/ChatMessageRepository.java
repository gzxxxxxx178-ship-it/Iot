package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByUsernameAndSessionIdOrderByCreatedTimeAsc(String username, String sessionId);

    @Modifying
    @Transactional
    void deleteByUsernameAndSessionId(String username, String sessionId);
}
