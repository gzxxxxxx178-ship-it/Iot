package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.ChatRequest;
import com.ruoyi.iotsystem.entity.ChatMessageEntity;
import com.ruoyi.iotsystem.repository.ChatMessageRepository;
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

@RestController
public class ChatController {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    // AI 对话：保存用户消息、调用 DeepSeek API、保存 AI 回复并返回
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

            // 保存用户消息和 AI 回复到数据库
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

    // 按会话 ID 加载聊天历史
    @GetMapping("/api/chat/history")
    public ApiResponse<List<ChatMessageEntity>> history(@RequestParam String sessionId) {
        List<ChatMessageEntity> messages =
                chatMessageRepository.findBySessionIdOrderByCreatedTimeAsc(sessionId);
        return ApiResponse.success(messages);
    }

    // 清除指定会话的聊天历史
    @DeleteMapping("/api/chat/history")
    public ApiResponse<Map<String, Object>> clearHistory(@RequestParam String sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ApiResponse.success(resp);
    }

    // 保存一条聊天消息到数据库
    private void saveMessage(String sessionId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
