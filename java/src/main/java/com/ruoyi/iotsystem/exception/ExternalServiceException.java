package com.ruoyi.iotsystem.exception;

/**
 * 表示依赖的外部服务不可用或返回了无法处理的响应。
 */
public class ExternalServiceException extends RuntimeException {

    // 使用对客户端安全的错误消息和保留给日志的根因创建异常
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    // 使用对客户端安全的错误消息创建异常
    public ExternalServiceException(String message) {
        super(message);
    }
}
