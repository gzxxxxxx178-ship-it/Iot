package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.ChatRequest;
import com.ruoyi.iotsystem.entity.ChatMessageEntity;
import com.ruoyi.iotsystem.repository.ChatMessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "AI 助手", description = "DeepSeek AI 对话、聊天历史管理")
@RestController
public class ChatController {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Operation(summary = "发送消息", description = "发送对话消息到 DeepSeek AI，保存双方消息记录，返回 AI 回复")
    @PostMapping("/api/chat")
    public ApiResponse<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", request.getMessages());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        Map<String, Object> result = response.getBody();
        if (result != null && result.containsKey("choices")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> choices =
                    (java.util.List<Map<String, Object>>) result.get("choices");
            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, String> message = (Map<String, String>) choice.get("message");

            String sessionId = request.getSessionId();
            if (sessionId != null && !sessionId.isEmpty()) {
                List<Map<String, String>> msgs = request.getMessages();
                if (!msgs.isEmpty()) {
                    Map<String, String> lastMsg = msgs.get(msgs.size() - 1);
                    if ("user".equals(lastMsg.get("role"))) {
                        saveMessage(sessionId, "user", lastMsg.get("content"));
                    }
                }
                saveMessage(sessionId, message.get("role"), message.get("content"));
            }

            Map<String, String> resp = new HashMap<>();
            resp.put("role", message.get("role"));
            resp.put("content", message.get("content"));
            return ApiResponse.success(resp);
        }

        throw new RuntimeException("DeepSeek API 响应异常");
    }

    @Operation(summary = "加载聊天历史", description = "按会话 ID 加载全部历史消息（单个会话数据量小，不做分页）")
    @GetMapping("/api/chat/history")
    public ApiResponse<List<ChatMessageEntity>> history(
            @Parameter(description = "会话 ID") @RequestParam String sessionId) {
        List<ChatMessageEntity> messages =
                chatMessageRepository.findBySessionIdOrderByCreatedTimeAsc(sessionId);
        return ApiResponse.success(messages);
    }

    @Operation(summary = "清除聊天历史", description = "删除指定会话的所有聊天记录")
    @DeleteMapping("/api/chat/history")
    public ApiResponse<Map<String, Object>> clearHistory(
            @Parameter(description = "会话 ID") @RequestParam String sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ApiResponse.success(resp);
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
