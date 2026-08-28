package org.example.secondhandtradingplatform.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.order.dto.OrderCancelRequest;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderQueryRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;

public interface TradeOrderService {

    OrderResponse create(OrderCreateRequest request);

    OrderResponse pay(Long orderId);

    OrderResponse confirm(Long orderId);

    OrderResponse finish(Long orderId);

    OrderResponse cancel(Long orderId, OrderCancelRequest request);

    OrderResponse getDetail(Long orderId);

    Page<OrderResponse> page(OrderQueryRequest request);
}
