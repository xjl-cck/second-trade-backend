package org.example.secondhandtradingplatform.chat.service.impl;

import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatReplayResponse;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;
import org.example.secondhandtradingplatform.chat.mapper.ChatMessageMapper;
import org.example.secondhandtradingplatform.chat.mapper.ChatSessionMapper;
import org.example.secondhandtradingplatform.chat.message.ChatMessagePublisher;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    private ChatMessageMapper chatMessageMapper;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatMessageMapper = mock(ChatMessageMapper.class);
        chatService = new ChatServiceImpl(
                mock(ChatSessionMapper.class),
                chatMessageMapper,
                mock(ProductMapper.class),
                mock(SysUserMapper.class),
                mock(ChatMessagePublisher.class));
    }

    @Test
    @DisplayName("未读超过上限时只补发最近的一批，并标记 truncated")
    void replayKeepsOnlyMostRecentMessagesWhenTooMany() {
        // mapper 按 send_time desc, id desc 返回 5 条，limit 取 4
        when(chatMessageMapper.selectList(any())).thenReturn(descending(5));

        ChatReplayResponse replay = chatService.replayUnreadMessages(1L, 4);

        assertTrue(replay.truncated());
        assertEquals(List.of(2L, 3L, 4L, 5L), ids(replay.messages()));
    }

    @Test
    @DisplayName("未读没有超上限时全部补发，truncated=false")
    void replayReturnsAllMessagesWhenUnderLimit() {
        when(chatMessageMapper.selectList(any())).thenReturn(descending(3));

        ChatReplayResponse replay = chatService.replayUnreadMessages(1L, 10);

        assertFalse(replay.truncated());
        assertEquals(List.of(1L, 2L, 3L), ids(replay.messages()));
    }

    @Test
    @DisplayName("上限配置过大时按硬上限截断，避免配置写错推全库")
    void replayCapsConfiguredLimit() {
        when(chatMessageMapper.selectList(any())).thenReturn(descending(3));

        ChatReplayResponse replay = chatService.replayUnreadMessages(1L, 100000);

        assertFalse(replay.truncated());
        assertEquals(3, replay.messages().size());
    }

    private List<ChatMessage> descending(int count) {
        List<ChatMessage> rows = new ArrayList<>();
        for (int i = count; i >= 1; i--) {
            ChatMessage message = new ChatMessage();
            message.setId((long) i);
            message.setSessionId(9L);
            message.setSenderId(2L);
            message.setReceiverId(1L);
            message.setMsgType(0);
            message.setContent("m" + i);
            message.setReadStatus(0);
            message.setSendTime(LocalDateTime.of(2026, 11, 15, 10, i));
            rows.add(message);
        }
        return rows;
    }

    private List<Long> ids(List<ChatMessageResponse> messages) {
        return messages.stream().map(ChatMessageResponse::getId).toList();
    }
}
