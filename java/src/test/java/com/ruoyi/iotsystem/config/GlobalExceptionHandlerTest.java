package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    // 验证可预期业务错误保留400业务码与HTTP状态
    @Test
    void businessException_应映射为400() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApiResponse<?> response = handler.handleBusinessException(new BusinessException("设备不存在"));

        assertEquals(400, response.getCode());
        assertEquals("设备不存在", response.getMessage());
        assertResponseStatus("handleBusinessException", BusinessException.class, HttpStatus.BAD_REQUEST);
    }

    // 验证未知运行时异常只经过兜底处理并映射为500
    @Test
    void unexpectedRuntimeException_应映射为500() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApiResponse<?> response = handler.handleException(new RuntimeException("数据库连接失败"));

        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertResponseStatus("handleException", Exception.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 验证程序化参数校验错误不会被错误归类为服务端故障
    @Test
    void illegalArgumentException_应映射为400() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApiResponse<?> response = handler.handleIllegalArgument(new IllegalArgumentException("参数无效"));

        assertEquals(400, response.getCode());
        assertEquals("参数无效", response.getMessage());
        assertResponseStatus("handleIllegalArgument", IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
    }

    // 验证外部服务错误使用网关失败状态且不暴露其根因
    @Test
    void externalServiceException_应映射为502() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApiResponse<?> response = handler.handleExternalServiceException(
                new ExternalServiceException("AI 服务暂时不可用，请稍后重试",
                        new RuntimeException("upstream details")));

        assertEquals(502, response.getCode());
        assertEquals("AI 服务暂时不可用，请稍后重试", response.getMessage());
        assertResponseStatus("handleExternalServiceException", ExternalServiceException.class, HttpStatus.BAD_GATEWAY);
    }

    // 读取异常处理方法注解，验证Spring MVC实际写出的HTTP状态
    private void assertResponseStatus(String methodName, Class<?> parameterType, HttpStatus expected)
            throws NoSuchMethodException {
        Method method = GlobalExceptionHandler.class.getMethod(methodName, parameterType);
        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        assertEquals(expected, status.value());
    }
}
