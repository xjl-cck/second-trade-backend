package org.example.secondhandtradingplatform.chat.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;

import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {

    private Long id;
    private Long sessionId;
    private Long senderId;
    private Long receiverId;
    private Integer msgType;
    private String content;
    private Integer readStatus;
    private LocalDateTime sendTime;

    public static ChatMessageResponse from(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setMsgType(message.getMsgType());
        response.setContent(message.getContent());
        response.setReadStatus(message.getReadStatus());
        response.setSendTime(message.getSendTime());
        return response;
    }
}
