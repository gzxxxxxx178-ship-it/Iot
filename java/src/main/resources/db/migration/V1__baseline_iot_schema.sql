-- P4-03 baseline: create the current schema for new installations.
-- Existing installations are baselined at version 0 and receive the additive columns below.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    email VARCHAR(255),
    avatar VARCHAR(255),
    provider VARCHAR(255),
    provider_id VARCHAR(255),
    created_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
);

CREATE TABLE IF NOT EXISTS iot_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100),
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    location VARCHAR(150),
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iot_devices_device_id (device_id),
    KEY idx_device_owner_status (owner_username, lifecycle_status)
);

CREATE TABLE IF NOT EXISTS esp_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(255),
    owner_username VARCHAR(100),
    temperature DOUBLE,
    humidity DOUBLE,
    uptime_millis BIGINT,
    water DOUBLE,
    linkage BOOLEAN,
    send_count INT,
    rssi INT,
    server_received_time DATETIME,
    quality_valid BOOLEAN,
    quality_issues VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_esp_device_received (device_id, server_received_time),
    KEY idx_esp_received_quality (server_received_time, quality_valid),
    KEY idx_esp_owner_received (owner_username, server_received_time)
);

CREATE TABLE IF NOT EXISTS alarm_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric VARCHAR(255) NOT NULL,
    comparison_operator VARCHAR(255) NOT NULL,
    threshold DOUBLE NOT NULL,
    enabled BOOLEAN NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    owner_username VARCHAR(100),
    cooldown_seconds INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_alarm_rule_enabled (enabled),
    KEY idx_alarm_rule_owner (owner_username)
);

CREATE TABLE IF NOT EXISTS alarm_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    owner_username VARCHAR(100),
    metric VARCHAR(255) NOT NULL,
    comparison_operator VARCHAR(255) NOT NULL,
    threshold DOUBLE NOT NULL,
    actual_value DOUBLE NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_alarm_record_rule_device_time (rule_id, device_id, created_at),
    KEY idx_alarm_record_created_at (created_at),
    KEY idx_alarm_record_owner (owner_username, created_at)
);

CREATE TABLE IF NOT EXISTS automation_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100),
    metric VARCHAR(32) NOT NULL,
    comparison_operator VARCHAR(8) NOT NULL,
    threshold DOUBLE NOT NULL,
    action VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    debounce_count INT NOT NULL,
    cooldown_seconds INT NOT NULL,
    consecutive_matches INT NOT NULL,
    last_triggered_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_automation_rule_enabled_device (enabled, device_id),
    KEY idx_automation_rule_owner (owner_username)
);

CREATE TABLE IF NOT EXISTS automation_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100),
    action VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    actual_value DOUBLE,
    message VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_automation_execution_created (created_at),
    KEY idx_automation_execution_rule (rule_id, created_at),
    KEY idx_automation_execution_owner (owner_username, created_at)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    role VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_time DATETIME,
    PRIMARY KEY (id),
    KEY idx_chat_username_session (username, session_id, created_time)
);

CREATE TABLE IF NOT EXISTS payment_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    out_trade_no VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    trade_no VARCHAR(255),
    created_at DATETIME,
    paid_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_out_trade_no (out_trade_no),
    KEY idx_payment_username (username)
);

-- Additive compatibility for databases created before this baseline migration.
ALTER TABLE esp_data ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE esp_data ADD COLUMN IF NOT EXISTS uptime_millis BIGINT;
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE alarm_rules ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE alarm_records ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE automation_rules ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE automation_executions ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS username VARCHAR(100);
