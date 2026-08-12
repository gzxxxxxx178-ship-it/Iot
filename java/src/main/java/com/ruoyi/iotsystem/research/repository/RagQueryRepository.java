package com.ruoyi.iotsystem.research.repository;

import com.ruoyi.iotsystem.research.entity.RagQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * RAG问答记录数据访问层。
 */
public interface RagQueryRepository extends JpaRepository<RagQueryEntity, Long> {

    /**
     * 按owner查找最近的RAG问答记录，按创建时间倒序
     */
    List<RagQueryEntity> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
}
