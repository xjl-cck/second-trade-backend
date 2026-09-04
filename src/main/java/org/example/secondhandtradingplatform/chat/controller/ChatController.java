package org.example.secondhandtradingplatform.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionCreateRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionResponse;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.example.secondhandtradingplatform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "聊天模块", description = "买卖双方私聊会话和消息")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    @Operation(summary = "创建或获取聊天会话")
    public Result<ChatSessionResponse> createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        return Result.success(chatService.createOrGetSession(request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "聊天会话列表")
    public Result<Page<ChatSessionResponse>> listSessions(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(chatService.listSessions(pageNo, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "聊天历史消息")
    public Result<Page<ChatMessageResponse>> listMessages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(chatService.listMessages(sessionId, pageNo, pageSize));
    }

    @PutMapping("/sessions/{sessionId}/read")
    @Operation(summary = "标记会话已读")
    public Result<Void> markRead(@PathVariable Long sessionId) {
        chatService.markRead(sessionId);
        return Result.success();
    }
}
