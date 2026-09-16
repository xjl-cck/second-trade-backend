package org.example.secondhandtradingplatform.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.order.dto.OrderCancelRequest;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderQueryRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单模块", description = "下单、模拟支付、确认成交、取消和完成订单")
public class TradeOrderController {

    private final TradeOrderService tradeOrderService;

    public TradeOrderController(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    @PostMapping
    @Operation(summary = "下单", description = "买家选择自己的收货地址后对上架商品下单，生成待确认订单")
    public Result<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(tradeOrderService.create(request));
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "模拟支付", description = "买家模拟支付，将待确认订单推进为已成交，并把商品标记为已售出")
    public Result<OrderResponse> pay(@PathVariable Long orderId) {
        return Result.success(tradeOrderService.pay(orderId));
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "卖家确认成交", description = "卖家确认后订单变为已成交，并把商品标记为已售出")
    public Result<OrderResponse> confirm(@PathVariable Long orderId) {
        return Result.success(tradeOrderService.confirm(orderId));
    }

    @PostMapping("/{orderId}/finish")
    @Operation(summary = "确认完成", description = "买家或卖家确认交易完成，将已成交订单推进为已完成")
    public Result<OrderResponse> finish(@PathVariable Long orderId) {
        return Result.success(tradeOrderService.finish(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "取消订单", description = "买家或卖家取消待确认订单")
    public Result<OrderResponse> cancel(@PathVariable Long orderId,
                                        @Valid @RequestBody(required = false) OrderCancelRequest request) {
        return Result.success(tradeOrderService.cancel(orderId, request));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "查询当前登录用户参与的订单详情")
    public Result<OrderResponse> getDetail(@PathVariable Long orderId) {
        return Result.success(tradeOrderService.getDetail(orderId));
    }

    @GetMapping
    @Operation(summary = "订单列表", description = "查询当前登录用户的订单，role 可传 buyer 或 seller，status 可筛选订单状态")
    public Result<Page<OrderResponse>> page(@Valid OrderQueryRequest request) {
        return Result.success(tradeOrderService.page(request));
    }
}
