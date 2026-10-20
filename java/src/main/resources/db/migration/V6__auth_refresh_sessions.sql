CREATE TABLE IF NOT EXISTS auth_refresh_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    username VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    replaced_by_hash CHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    KEY idx_auth_refresh_family (family_id),
    KEY idx_auth_refresh_expiry (expires_at)
);
