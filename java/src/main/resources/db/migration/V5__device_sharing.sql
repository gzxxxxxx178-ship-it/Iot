CREATE TABLE IF NOT EXISTS device_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    grantee_username VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_share_grantee (device_id, grantee_username),
    KEY idx_device_share_grantee (grantee_username, device_id)
);
