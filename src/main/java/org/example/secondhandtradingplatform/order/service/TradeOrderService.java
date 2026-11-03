package org.example.secondhandtradingplatform.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.order.dto.OrderCancelRequest;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderQueryRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeOrderService {

    OrderResponse create(OrderCreateRequest request);

    OrderResponse pay(Long orderId);

    /**
     * 第三方支付成功后推进订单：待确认 -> 已成交，并把商品标记为已售出。
     * 重复调用是幂等的，订单已成交时直接返回当前订单。
     */
    OrderResponse paySuccess(String orderNo, java.math.BigDecimal paidAmount);

    /**
     * 查询下单时间早于 deadline、仍处于待确认的订单 ID，供超时关单任务批量处理。
     */
    List<Long> findTimeoutOrderIds(LocalDateTime deadline, int limit);

    /**
     * 关闭单笔超时未支付订单：先关支付宝交易，再回滚库存并把订单置为已取消。
     * 订单状态已变化或还没到超时时间时返回 false。
     */
    boolean closeTimeoutOrder(Long orderId, LocalDateTime deadline);

    OrderResponse confirm(Long orderId);

    OrderResponse finish(Long orderId);

    OrderResponse cancel(Long orderId, OrderCancelRequest request);

    OrderResponse getDetail(Long orderId);

    Page<OrderResponse> page(OrderQueryRequest request);
}
