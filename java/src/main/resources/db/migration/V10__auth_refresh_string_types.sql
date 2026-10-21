-- V6使用CHAR，而JPA String映射要求VARCHAR；增量修正，保留已执行版本的校验和。
ALTER TABLE auth_refresh_sessions
    MODIFY COLUMN family_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN token_hash VARCHAR(64) NOT NULL,
    MODIFY COLUMN replaced_by_hash VARCHAR(64) NULL;
