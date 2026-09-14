package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话接口客户端，集中处理网络超时与上游响应校验。
 */
@Service
public class DeepSeekClient {

    private final RestTemplate restTemplate;

    // 按配置创建具备连接和读取超时的HTTP客户端
    @Autowired
    public DeepSeekClient(
            @Value("${deepseek.api.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${deepseek.api.read-timeout-ms:30000}") int readTimeoutMs) {
        this(createRestTemplate(connectTimeoutMs, readTimeoutMs));
    }

    // 为单元测试或受控调用注入HTTP客户端
    DeepSeekClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 向DeepSeek发送对话并提取经校验的助手回复
    public Map<String, String> requestCompletion(
            String apiKey, String apiUrl, List<Map<String, String>> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", messages);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl, new HttpEntity<>(body, headers), Map.class);
            return extractAssistantMessage(response.getBody());
        } catch (RestClientException exception) {
            throw new ExternalServiceException("AI 服务暂时不可用，请稍后重试", exception);
        }
    }

    // 创建超时受控的RestTemplate，防止上游网络故障长期占用请求线程
    private static RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    // 校验DeepSeek响应结构并提取第一条助手回复
    @SuppressWarnings("unchecked")
    private Map<String, String> extractAssistantMessage(Map response) {
        if (response == null || !(response.get("choices") instanceof List)) {
            throw new ExternalServiceException("AI 服务返回异常，请稍后重试");
        }
        List<?> choices = (List<?>) response.get("choices");
        if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
            throw new ExternalServiceException("AI 服务返回异常，请稍后重试");
        }
        Object messageValue = ((Map<?, ?>) choices.get(0)).get("message");
        if (!(messageValue instanceof Map)) {
            throw new ExternalServiceException("AI 服务返回异常，请稍后重试");
        }
        Map<?, ?> message = (Map<?, ?>) messageValue;
        Object role = message.get("role");
        Object content = message.get("content");
        if (!(role instanceof String) || !(content instanceof String)
                || ((String) content).trim().isEmpty()) {
            throw new ExternalServiceException("AI 服务返回异常，请稍后重试");
        }
        Map<String, String> result = new HashMap<>();
        result.put("role", (String) role);
        result.put("content", (String) content);
        return result;
    }
}
