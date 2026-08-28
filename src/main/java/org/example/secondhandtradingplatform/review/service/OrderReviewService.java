package org.example.secondhandtradingplatform.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.review.dto.ReviewCreateRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewFollowUpRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewListRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewResponse;

public interface OrderReviewService {

    ReviewResponse create(Long orderId, ReviewCreateRequest request);

    ReviewResponse followUp(Long reviewId, ReviewFollowUpRequest request);

    Page<ReviewResponse> listByProduct(Long productId, ReviewListRequest request);
}
