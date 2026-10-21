ALTER TABLE device_commands ADD COLUMN dispatched_at DATETIME NULL;
CREATE INDEX idx_device_command_status_created ON device_commands (status, created_at);
CREATE INDEX idx_device_command_status_dispatched ON device_commands (status, dispatched_at);
