package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.ChatRequest;
import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.entity.ChatMessageEntity;
import com.ruoyi.iotsystem.repository.ChatMessageRepository;
import com.ruoyi.iotsystem.service.DeepSeekClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

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

    private final DeepSeekClient deepSeekClient;

    // 注入具有超时和上游响应校验的DeepSeek客户端
    public ChatController(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    @Operation(summary = "发送消息", description = "发送对话消息到 DeepSeek AI，保存双方消息记录，返回 AI 回复")
    @PostMapping("/api/chat")
    public ApiResponse<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        String username = SecurityContextUtils.requireUsername();
        validateMessages(request);
        Map<String, String> message = deepSeekClient.requestCompletion(apiKey, apiUrl, request.getMessages());

        String sessionId = request.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            List<Map<String, String>> msgs = request.getMessages();
            if (!msgs.isEmpty()) {
                Map<String, String> lastMsg = msgs.get(msgs.size() - 1);
                if ("user".equals(lastMsg.get("role"))) {
                    saveMessage(username, sessionId, "user", lastMsg.get("content"));
                }
            }
            saveMessage(username, sessionId, message.get("role"), message.get("content"));
        }
        return ApiResponse.success(message);
    }

    @Operation(summary = "加载聊天历史", description = "按会话 ID 加载全部历史消息（单个会话数据量小，不做分页）")
    @GetMapping("/api/chat/history")
    public ApiResponse<List<ChatMessageEntity>> history(
            @Parameter(description = "会话 ID") @RequestParam String sessionId) {
        String username = SecurityContextUtils.requireUsername();
        List<ChatMessageEntity> messages =
                chatMessageRepository.findByUsernameAndSessionIdOrderByCreatedTimeAsc(username, sessionId);
        return ApiResponse.success(messages);
    }

    @Operation(summary = "清除聊天历史", description = "删除指定会话的所有聊天记录")
    @DeleteMapping("/api/chat/history")
    public ApiResponse<Map<String, Object>> clearHistory(
            @Parameter(description = "会话 ID") @RequestParam String sessionId) {
        String username = SecurityContextUtils.requireUsername();
        chatMessageRepository.deleteByUsernameAndSessionId(username, sessionId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ApiResponse.success(resp);
    }

    // 校验聊天请求规模和消息字段，限制外部AI调用成本与持久化膨胀
    private void validateMessages(ChatRequest request) {
        if (request.getSessionId().length() > 64 || request.getMessages().size() > 50) {
            throw new IllegalArgumentException("会话ID最多64个字符，消息最多50条");
        }
        for (Map<String, String> message : request.getMessages()) {
            String role = message == null ? null : message.get("role");
            String content = message == null ? null : message.get("content");
            if (!("system".equals(role) || "user".equals(role) || "assistant".equals(role))) {
                throw new IllegalArgumentException("消息角色无效");
            }
            if (content == null || content.trim().isEmpty() || content.length() > 8000) {
                throw new IllegalArgumentException("消息内容不能为空且不得超过8000个字符");
            }
        }
    }

    // 保存带用户归属的聊天消息
    private void saveMessage(String username, String sessionId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setUsername(username);
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
}
