package com.ruoyi.iotsystem.dto;

import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import java.time.LocalDateTime;

public class AutomationExecutionView {
    private final AutomationExecutionEntity execution;
    private final String commandId;
    private final DeviceCommandEntity command;

    // 组合触发快照与已校验的当前命令状态，查询不修改任何持久化字段。
    public AutomationExecutionView(AutomationExecutionEntity execution, String commandId, DeviceCommandEntity command) {
        this.execution = execution;
        this.commandId = commandId;
        this.command = command;
    }

    // 获取执行记录主键。
    public Long getId() { return execution.getId(); }
    // 获取来源规则。
    public Long getRuleId() { return execution.getRuleId(); }
    // 获取设备标识。
    public String getDeviceId() { return execution.getDeviceId(); }
    // 获取发起账号。
    public String getOwnerUsername() { return execution.getOwnerUsername(); }
    // 获取动作。
    public String getAction() { return execution.getAction(); }
    // 获取触发时读数。
    public Double getActualValue() { return execution.getActualValue(); }
    // 获取触发时间。
    public LocalDateTime getCreatedAt() { return execution.getCreatedAt(); }
    // 获取关联的命令标识。
    public String getCommandId() { return commandId; }
    // 优先返回真实命令状态，历史未关联记录保留原始快照。
    public String getStatus() { return command == null ? execution.getStatus() : command.getStatus(); }
    // 优先返回当前命令的结果说明。
    public String getMessage() { return command == null ? execution.getMessage() : command.getMessage(); }
    // 提供原始状态用于区分历史快照与当前结果。
    public String getInitialStatus() { return execution.getStatus(); }
    // 明确当前状态是否来自已校验的命令记录。
    public boolean isCommandLinked() { return command != null; }
    // 获取设备确认时间，尚未确认时为空。
    public LocalDateTime getAcknowledgedAt() { return command == null ? null : command.getAcknowledgedAt(); }
}
