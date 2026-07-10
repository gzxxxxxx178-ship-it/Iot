package com.ruoyi.iotsystem.config;

import com.alipay.api.AlipayApiException;
import com.ruoyi.iotsystem.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 统一捕获 Controller 层抛出的异常并转换为 {@link ApiResponse} 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 登录认证失败 → 401
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleBadCredentials(BadCredentialsException e) {
        log.warn("登录认证失败: {}", e.getMessage());
        return ApiResponse.fail(401, "用户名或密码错误");
    }

    // @Valid 校验失败（@RequestBody 参数）→ 400，返回第一个校验错误
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return ApiResponse.fail(400, msg);
    }

    // 校验失败（@RequestParam / @PathVariable 参数）→ 400
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ApiResponse.fail(400, msg);
    }

    // 业务运行时异常 → 400（如用户名已存在等）
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.fail(400, e.getMessage());
    }

    // 支付宝 API 调用异常 → 500
    @ExceptionHandler(AlipayApiException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleAlipayApiException(AlipayApiException e) {
        log.error("支付宝接口异常: {}", e.getMessage(), e);
        return ApiResponse.error("支付宝接口异常: " + e.getMessage());
    }

    // 兜底：未预期的服务器内部错误 → 500
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return ApiResponse.error("服务器内部错误");
    }
}
