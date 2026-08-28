package org.example.secondhandtradingplatform.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.review.dto.ReviewCreateRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewFollowUpRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewListRequest;
import org.example.secondhandtradingplatform.review.dto.ReviewResponse;
import org.example.secondhandtradingplatform.review.service.OrderReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "评价模块", description = "交易完成后的评价、评分和追评")
public class OrderReviewController {

    private final OrderReviewService orderReviewService;

    public OrderReviewController(OrderReviewService orderReviewService) {
        this.orderReviewService = orderReviewService;
    }

    @PostMapping("/orders/{orderId}")
    @Operation(summary = "交易后评价", description = "订单完成后，买家或卖家评价交易对方")
    public Result<ReviewResponse> create(@PathVariable Long orderId,
                                         @Valid @RequestBody ReviewCreateRequest request) {
        return Result.success(orderReviewService.create(orderId, request));
    }

    @PostMapping("/{reviewId}/follow-up")
    @Operation(summary = "追评", description = "对自己的首评追加一次评价，可追加评分和内容")
    public Result<ReviewResponse> followUp(@PathVariable Long reviewId,
                                           @Valid @RequestBody ReviewFollowUpRequest request) {
        return Result.success(orderReviewService.followUp(reviewId, request));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "查询商品评价", description = "分页查询商品的首评和追评")
    public Result<Page<ReviewResponse>> listByProduct(@PathVariable Long productId,
                                                      @Valid ReviewListRequest request) {
        return Result.success(orderReviewService.listByProduct(productId, request));
    }
}
