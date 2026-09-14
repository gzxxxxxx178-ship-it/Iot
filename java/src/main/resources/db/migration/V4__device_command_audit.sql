CREATE TABLE IF NOT EXISTS device_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    command_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100),
    command VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    acknowledged_at DATETIME,
    message VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_command_id (command_id),
    KEY idx_device_command_owner_created (owner_username, created_at)
);
