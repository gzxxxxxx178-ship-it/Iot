CREATE TABLE IF NOT EXISTS wechat_miniapp_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    app_id VARCHAR(32) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_app_openid (app_id, open_id),
    UNIQUE KEY uk_wechat_app_username (app_id, username)
);
