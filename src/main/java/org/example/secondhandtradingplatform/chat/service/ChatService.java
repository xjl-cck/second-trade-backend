package org.example.secondhandtradingplatform.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatReplayResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionCreateRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionResponse;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;

public interface ChatService {

    ChatSessionResponse createOrGetSession(ChatSessionCreateRequest request);

    Page<ChatSessionResponse> listSessions(Integer pageNo, Integer pageSize);

    Page<ChatMessageResponse> listMessages(Long sessionId, Integer pageNo, Integer pageSize);

    void markRead(Long sessionId);

    ChatMessageResponse send(Long userId, ChatMessageSendRequest request);

    ChatMessage getMessage(Long messageId);

    /**
     * 连接建立时的未读补发，最多返回 {@code maxMessages} 条（取最近的），按时间正序返回。
     */
    ChatReplayResponse replayUnreadMessages(Long userId, int maxMessages);
}
