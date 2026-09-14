package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class DeepSeekClientTest {

    private DeepSeekClient client;
    private MockRestServiceServer server;

    // 使用本地HTTP替身验证客户端，不访问真实DeepSeek服务
    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        client = new DeepSeekClient(restTemplate);
        server = MockRestServiceServer.createServer(restTemplate);
    }

    // 验证合法上游响应被解析为助手角色与内容
    @Test
    void requestCompletion_合法响应_返回助手消息() {
        server.expect(once(), requestTo("https://deepseek.test/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"灌溉已完成\"}}]}",
                        MediaType.APPLICATION_JSON));

        Map<String, String> reply = client.requestCompletion(
                "test-key", "https://deepseek.test/v1/chat/completions", Collections.singletonList(userMessage()));

        assertEquals("assistant", reply.get("role"));
        assertEquals("灌溉已完成", reply.get("content"));
        server.verify();
    }

    // 验证上游HTTP失败转为不泄露细节的外部服务异常
    @Test
    void requestCompletion_上游失败_抛出外部服务异常() {
        server.expect(requestTo("https://deepseek.test/v1/chat/completions"))
                .andRespond(withServerError());

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> client.requestCompletion("test-key", "https://deepseek.test/v1/chat/completions",
                        Collections.singletonList(userMessage())));

        assertEquals("AI 服务暂时不可用，请稍后重试", exception.getMessage());
        server.verify();
    }

    // 验证缺少助手消息的上游响应不会触发类型转换或数组越界
    @Test
    void requestCompletion_响应结构错误_抛出外部服务异常() {
        server.expect(requestTo("https://deepseek.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> client.requestCompletion("test-key", "https://deepseek.test/v1/chat/completions",
                        Collections.singletonList(userMessage())));

        assertEquals("AI 服务返回异常，请稍后重试", exception.getMessage());
        server.verify();
    }

    // 构造最小有效用户消息
    private Map<String, String> userMessage() {
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "查询土壤湿度");
        return message;
    }
}
