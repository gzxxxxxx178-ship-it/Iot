package com.ruoyi.iotsystem.exception;

/**
 * 表示可预期的请求业务错误，调用方可修正输入或资源状态后重试。
 */
public class BusinessException extends RuntimeException {

    // 使用可安全返回给客户端的业务错误消息创建异常
    public BusinessException(String message) {
        super(message);
    }
}
