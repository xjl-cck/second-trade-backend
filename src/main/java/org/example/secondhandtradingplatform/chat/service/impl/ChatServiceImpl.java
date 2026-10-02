package org.example.secondhandtradingplatform.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionCreateRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatSessionResponse;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;
import org.example.secondhandtradingplatform.chat.entity.ChatSession;
import org.example.secondhandtradingplatform.chat.message.ChatMessagePublisher;
import org.example.secondhandtradingplatform.chat.mapper.ChatMessageMapper;
import org.example.secondhandtradingplatform.chat.mapper.ChatSessionMapper;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final int UNREAD = 0;
    private static final int READ = 1;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatMessagePublisher chatMessagePublisher;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           ProductMapper productMapper,
                           SysUserMapper sysUserMapper,
                           ChatMessagePublisher chatMessagePublisher) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.productMapper = productMapper;
        this.sysUserMapper = sysUserMapper;
        this.chatMessagePublisher = chatMessagePublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResponse createOrGetSession(ChatSessionCreateRequest request) {
        Long buyerId = requireLoginUserId();
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (buyerId.equals(product.getSellerId())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "不能和自己发布的商品创建会话");
        }

        ChatSession session = chatSessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getBuyerId, buyerId)
                .eq(ChatSession::getSellerId, product.getSellerId())
                .eq(ChatSession::getProductId, product.getId()));
        if (session == null) {
            session = new ChatSession();
            session.setBuyerId(buyerId);
            session.setSellerId(product.getSellerId());
            session.setProductId(product.getId());
            session.setUnreadCount(0);
            session.setIsDeleted(0);
            chatSessionMapper.insert(session);
        }
        return buildSessionResponse(session, buyerId);
    }

    @Override
    public Page<ChatSessionResponse> listSessions(Integer pageNo, Integer pageSize) {
        Long userId = requireLoginUserId();
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);
        Page<ChatSession> sessionPage = chatSessionMapper.selectPage(Page.of(normalizedPageNo, normalizedPageSize),
                Wrappers.<ChatSession>lambdaQuery()
                        .and(query -> query.eq(ChatSession::getBuyerId, userId)
                                .or()
                                .eq(ChatSession::getSellerId, userId))
                        .orderByDesc(ChatSession::getUpdateTime)
                        .orderByDesc(ChatSession::getId));
        Page<ChatSessionResponse> responsePage = Page.of(normalizedPageNo, normalizedPageSize, sessionPage.getTotal());
        responsePage.setRecords(sessionPage.getRecords().stream()
                .map(session -> buildSessionResponse(session, userId))
                .toList());
        return responsePage;
    }

    @Override
    public Page<ChatMessageResponse> listMessages(Long sessionId, Integer pageNo, Integer pageSize) {
        Long userId = requireLoginUserId();
        getParticipantSession(sessionId, userId);
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);
        Page<ChatMessage> messagePage = chatMessageMapper.selectPage(Page.of(normalizedPageNo, normalizedPageSize),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getSendTime)
                        .orderByDesc(ChatMessage::getId));
        Page<ChatMessageResponse> responsePage = Page.of(normalizedPageNo, normalizedPageSize, messagePage.getTotal());
        responsePage.setRecords(messagePage.getRecords().stream()
                .map(ChatMessageResponse::from)
                .toList());
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long sessionId) {
        Long userId = requireLoginUserId();
        getParticipantSession(sessionId, userId);
        chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                .set(ChatMessage::getReadStatus, READ)
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getReadStatus, UNREAD));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResponse send(Long userId, ChatMessageSendRequest request) {
        if (request.getSessionId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话不能为空");
        }
        if (request.getMsgType() == null || request.getMsgType() < 0 || request.getMsgType() > 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息类型只能为0、1、2");
        }
        ChatSession session = getParticipantSession(request.getSessionId(), userId);
        Long receiverId = userId.equals(session.getBuyerId()) ? session.getSellerId() : session.getBuyerId();
        String content = request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(userId);
        message.setReceiverId(receiverId);
        message.setMsgType(request.getMsgType());
        message.setContent(content);
        message.setReadStatus(UNREAD);
        message.setSendTime(LocalDateTime.now());
        message.setIsDeleted(0);
        chatMessageMapper.insert(message);

        session.setLastMessage(content);
        session.setLastMessageTime(message.getSendTime());
        session.setUnreadCount((session.getUnreadCount() == null ? 0 : session.getUnreadCount()) + 1);
        chatSessionMapper.updateById(session);
        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatMessagePublisher.publishAfterCommit(response);
        return response;
    }

    @Override
    public ChatMessage getMessage(Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        return message;
    }

    @Override
    public List<ChatMessageResponse> listUnreadMessages(Long userId) {
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getReceiverId, userId)
                        .eq(ChatMessage::getReadStatus, UNREAD)
                        .orderByAsc(ChatMessage::getSendTime)
                        .orderByAsc(ChatMessage::getId))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    private ChatSession getParticipantSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "聊天会话不存在");
        }
        if (!userId.equals(session.getBuyerId()) && !userId.equals(session.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该聊天会话");
        }
        return session;
    }

    private ChatSessionResponse buildSessionResponse(ChatSession session, Long userId) {
        ChatSessionResponse response = ChatSessionResponse.from(session);
        Long unreadCount = chatMessageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, session.getId())
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getReadStatus, UNREAD));
        response.setUnreadCount(unreadCount.intValue());
        Long otherUserId = userId.equals(session.getBuyerId()) ? session.getSellerId() : session.getBuyerId();
        response.setOtherUserId(otherUserId);
        SysUser otherUser = sysUserMapper.selectById(otherUserId);
        if (otherUser != null) {
            response.setOtherNickname(otherUser.getNickname());
            response.setOtherAvatar(otherUser.getAvatar());
        }
        Product product = productMapper.selectById(session.getProductId());
        if (product != null) {
            response.setProductTitle(product.getTitle());
            response.setProductCoverImage(product.getCoverImage());
        }
        return response;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
