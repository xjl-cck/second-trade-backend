package org.example.secondhandtradingplatform.review.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.review.dto.ReviewCreateRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewFollowUpRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewListRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewResponse;
import org.example.secondhandtradingplatform.review.entity.OrderReview;
import org.example.secondhandtradingplatform.review.mapper.OrderReviewMapper;
import org.example.secondhandtradingplatform.review.service.OrderReviewService;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderReviewServiceImpl implements OrderReviewService {

    private static final int ORDER_FINISHED = 3;
    private static final int REVIEW_INITIAL = 0;
    private static final int REVIEW_FOLLOW_UP = 1;
    private static final int REVIEW_VISIBLE = 1;

    private final OrderReviewMapper orderReviewMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final SysUserMapper sysUserMapper;

    public OrderReviewServiceImpl(OrderReviewMapper orderReviewMapper,
                                  TradeOrderMapper tradeOrderMapper,
                                  SysUserMapper sysUserMapper) {
        this.orderReviewMapper = orderReviewMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResponse create(Long orderId, ReviewCreateRequest request) {
        Long reviewerId = requireLoginUserId();
        TradeOrder order = getFinishedOrder(orderId);
        Long revieweeId = resolveReviewee(order, reviewerId);
        if (hasReview(orderId, reviewerId, REVIEW_INITIAL)) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该订单已经评价过了");
        }

        OrderReview review = new OrderReview();
        review.setOrderId(orderId);
        review.setReviewerId(reviewerId);
        review.setRevieweeId(revieweeId);
        review.setRating(request.getRating());
        review.setContent(trimToNull(request.getContent()));
        review.setReviewType(REVIEW_INITIAL);
        review.setParentId(null);
        review.setStatus(REVIEW_VISIBLE);
        review.setIsDeleted(0);
        orderReviewMapper.insert(review);
        return buildResponse(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResponse followUp(Long reviewId, ReviewFollowUpRequest request) {
        Long reviewerId = requireLoginUserId();
        OrderReview initialReview = orderReviewMapper.selectById(reviewId);
        if (initialReview == null || !Integer.valueOf(REVIEW_INITIAL).equals(initialReview.getReviewType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "首评不存在");
        }
        if (!reviewerId.equals(initialReview.getReviewerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能追评自己的评价");
        }
        getFinishedOrder(initialReview.getOrderId());
        if (hasReview(reviewId, reviewerId)) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该评价已经追评过了");
        }

        OrderReview followUp = new OrderReview();
        followUp.setOrderId(initialReview.getOrderId());
        followUp.setReviewerId(reviewerId);
        followUp.setRevieweeId(initialReview.getRevieweeId());
        followUp.setRating(request.getRating());
        followUp.setContent(trimToNull(request.getContent()));
        followUp.setReviewType(REVIEW_FOLLOW_UP);
        followUp.setParentId(reviewId);
        followUp.setStatus(REVIEW_VISIBLE);
        followUp.setIsDeleted(0);
        orderReviewMapper.insert(followUp);
        return buildResponse(followUp);
    }

    @Override
    public Page<ReviewResponse> listByProduct(Long productId, ReviewListRequest request) {
        if (request == null) {
            request = new ReviewListRequest();
        }
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        List<TradeOrder> orders = tradeOrderMapper.selectList(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getProductId, productId));
        List<Long> orderIds = orders.stream().map(TradeOrder::getId).toList();
        if (orderIds.isEmpty()) {
            Page<ReviewResponse> emptyPage = Page.of(pageNo, pageSize, 0);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        Page<OrderReview> reviewPage = orderReviewMapper.selectPage(Page.of(pageNo, pageSize),
                Wrappers.<OrderReview>lambdaQuery()
                        .in(OrderReview::getOrderId, orderIds)
                        .eq(OrderReview::getStatus, REVIEW_VISIBLE)
                        .orderByDesc(OrderReview::getCreateTime)
                        .orderByDesc(OrderReview::getId));
        List<ReviewResponse> records = new ArrayList<>();
        for (OrderReview review : reviewPage.getRecords()) {
            records.add(buildResponse(review));
        }
        Page<ReviewResponse> responsePage = Page.of(pageNo, pageSize, reviewPage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    private TradeOrder getFinishedOrder(Long orderId) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!Integer.valueOf(ORDER_FINISHED).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "交易完成后才能评价");
        }
        return order;
    }

    private Long resolveReviewee(TradeOrder order, Long reviewerId) {
        if (reviewerId.equals(order.getBuyerId())) {
            return order.getSellerId();
        }
        if (reviewerId.equals(order.getSellerId())) {
            return order.getBuyerId();
        }
        throw new BizException(ErrorCode.FORBIDDEN, "只能评价自己参与的订单");
    }

    private boolean hasReview(Long orderId, Long reviewerId, int reviewType) {
        Long count = orderReviewMapper.selectCount(Wrappers.<OrderReview>lambdaQuery()
                .eq(OrderReview::getOrderId, orderId)
                .eq(OrderReview::getReviewerId, reviewerId)
                .eq(OrderReview::getReviewType, reviewType));
        return count != null && count > 0;
    }

    private boolean hasReview(Long parentId, Long reviewerId) {
        Long count = orderReviewMapper.selectCount(Wrappers.<OrderReview>lambdaQuery()
                .eq(OrderReview::getParentId, parentId)
                .eq(OrderReview::getReviewerId, reviewerId)
                .eq(OrderReview::getReviewType, REVIEW_FOLLOW_UP));
        return count != null && count > 0;
    }

    private ReviewResponse buildResponse(OrderReview review) {
        ReviewResponse response = ReviewResponse.from(review);
        SysUser reviewer = sysUserMapper.selectById(review.getReviewerId());
        if (reviewer != null) {
            response.setReviewerNickname(reviewer.getNickname());
            response.setReviewerAvatar(reviewer.getAvatar());
        }
        SysUser reviewee = sysUserMapper.selectById(review.getRevieweeId());
        if (reviewee != null) {
            response.setRevieweeNickname(reviewee.getNickname());
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
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
