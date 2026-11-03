package org.example.secondhandtradingplatform.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.order.dto.OrderCancelRequest;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderQueryRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;

public interface TradeOrderService {

    OrderResponse create(OrderCreateRequest request);

    OrderResponse pay(Long orderId);

    /**
     * 第三方支付成功后推进订单：待确认 -> 已成交，并把商品标记为已售出。
     * 重复调用是幂等的，订单已成交时直接返回当前订单。
     */
    OrderResponse paySuccess(String orderNo, java.math.BigDecimal paidAmount);

    OrderResponse confirm(Long orderId);

    OrderResponse finish(Long orderId);

    OrderResponse cancel(Long orderId, OrderCancelRequest request);

    OrderResponse getDetail(Long orderId);

    Page<OrderResponse> page(OrderQueryRequest request);
}
