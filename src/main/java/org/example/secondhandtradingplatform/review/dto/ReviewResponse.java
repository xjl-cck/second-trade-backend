package org.example.secondhandtradingplatform.review.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.review.entity.OrderReview;

import java.time.LocalDateTime;

@Data
public class ReviewResponse {

    private Long id;
    private Long orderId;
    private Long reviewerId;
    private String reviewerNickname;
    private String reviewerAvatar;
    private Long revieweeId;
    private String revieweeNickname;
    private Integer rating;
    private String content;
    private Integer reviewType;
    private Long parentId;
    private LocalDateTime createTime;

    public static ReviewResponse from(OrderReview review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setOrderId(review.getOrderId());
        response.setReviewerId(review.getReviewerId());
        response.setRevieweeId(review.getRevieweeId());
        response.setRating(review.getRating());
        response.setContent(review.getContent());
        response.setReviewType(review.getReviewType());
        response.setParentId(review.getParentId());
        response.setCreateTime(review.getCreateTime());
        return response;
    }
}
