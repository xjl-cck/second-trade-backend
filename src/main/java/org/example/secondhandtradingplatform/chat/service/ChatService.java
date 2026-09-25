package org.example.secondhandtradingplatform.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionCreateRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionResponse;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;

import java.util.List;

public interface ChatService {

    ChatSessionResponse createOrGetSession(ChatSessionCreateRequest request);

    Page<ChatSessionResponse> listSessions(Integer pageNo, Integer pageSize);

    Page<ChatMessageResponse> listMessages(Long sessionId, Integer pageNo, Integer pageSize);

    void markRead(Long sessionId);

    ChatMessageResponse send(Long userId, ChatMessageSendRequest request);

    ChatMessage getMessage(Long messageId);

    List<ChatMessageResponse> listUnreadMessages(Long userId);
}
