package org.example.secondhandtradingplatform.chat.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.chat.entity.ChatSession;

import java.time.LocalDateTime;

@Data
public class ChatSessionResponse {

    private Long id;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private String productTitle;
    private String productCoverImage;
    private Long otherUserId;
    private String otherNickname;
    private String otherAvatar;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private LocalDateTime updateTime;

    public static ChatSessionResponse from(ChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setBuyerId(session.getBuyerId());
        response.setSellerId(session.getSellerId());
        response.setProductId(session.getProductId());
        response.setLastMessage(session.getLastMessage());
        response.setLastMessageTime(session.getLastMessageTime());
        response.setUnreadCount(session.getUnreadCount());
        response.setUpdateTime(session.getUpdateTime());
        return response;
    }
}
