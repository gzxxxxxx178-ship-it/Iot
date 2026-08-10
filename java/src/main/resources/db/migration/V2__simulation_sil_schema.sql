-- V2: Simulation Software-in-the-Loop schema
-- Adds four tables for MATLAB simulation telemetry, rules, alarms, and commands.
-- All tables include owner_username for per-user data isolation.

CREATE TABLE IF NOT EXISTS simulation_telemetry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sample_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    occurred_at DATETIME NOT NULL,
    growth_stage VARCHAR(255),
    scenario_code VARCHAR(255),
    source_type VARCHAR(32) NOT NULL DEFAULT 'SIMULATION',
    water_level_mm DOUBLE,
    flow_rate_l_min DOUBLE,
    ec_ms_cm DOUBLE,
    soil_moisture_pct DOUBLE,
    rainfall_mm DOUBLE,
    pump_on BOOLEAN,
    irrigation_valve_open BOOLEAN,
    fertilizer_pump_on BOOLEAN,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sim_tel_owner_sample (owner_username, sample_id),
    KEY idx_sim_tel_owner_device_time (owner_username, device_id, occurred_at)
);

CREATE TABLE IF NOT EXISTS simulation_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    metric VARCHAR(32) NOT NULL,
    comparison_operator VARCHAR(8) NOT NULL,
    threshold DOUBLE NOT NULL,
    recovery_threshold DOUBLE NOT NULL,
    debounce_count INT NOT NULL DEFAULT 1,
    severity VARCHAR(16) NOT NULL DEFAULT 'WARN',
    action VARCHAR(32) NOT NULL DEFAULT 'NOTIFY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sim_rules_owner_device (owner_username, device_id),
    KEY idx_sim_rules_owner_enabled (owner_username, enabled)
);

CREATE TABLE IF NOT EXISTS simulation_alarms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    metric VARCHAR(32) NOT NULL,
    comparison_operator VARCHAR(8) NOT NULL,
    threshold DOUBLE NOT NULL,
    recovery_threshold DOUBLE NOT NULL,
    actual_value DOUBLE NOT NULL,
    severity VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    message VARCHAR(500),
    triggered_at DATETIME NOT NULL,
    acknowledged_at DATETIME,
    resolved_at DATETIME,
    resolution_value DOUBLE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sim_alarms_owner_device_status (owner_username, device_id, status),
    KEY idx_sim_alarms_rule_device (rule_id, device_id)
);

CREATE TABLE IF NOT EXISTS simulation_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alarm_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    message VARCHAR(500),
    feedback_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sim_cmds_owner_device_status (owner_username, device_id, status)
);
