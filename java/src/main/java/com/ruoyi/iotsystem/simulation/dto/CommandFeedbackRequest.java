package com.ruoyi.iotsystem.simulation.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * MATLAB仿真命令反馈请求体。
 * 仅允许SUCCESS/FAILED作为终态，message最长500字符。
 */
public class CommandFeedbackRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "SUCCESS|FAILED", message = "状态只能是SUCCESS或FAILED")
    private String status;

    @Size(max = 500, message = "反馈消息不能超过500个字符")
    private String message;

    // 获取反馈状态
    public String getStatus() { return status; }
    // 设置反馈状态
    public void setStatus(String status) { this.status = status; }
    // 获取反馈消息
    public String getMessage() { return message; }
    // 设置反馈消息
    public void setMessage(String message) { this.message = message; }
}
