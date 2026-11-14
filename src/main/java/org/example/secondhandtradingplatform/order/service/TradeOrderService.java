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

    /**
     * 第三方支付成功后推进订单：待确认 -> 已成交，并把商品标记为已售出。
     * 重复调用是幂等的，订单已成交时直接返回当前订单。
     */
    OrderResponse paySuccess(String orderNo, java.math.BigDecimal paidAmount);

    /**
     * 查询下单时间早于 deadline、仍处于待确认的订单 ID，供超时关单任务批量处理。
     */
    List<Long> findTimeoutOrderIds(LocalDateTime deadline, int limit);

    /** 抢占一笔超时订单并写入异步关单命令。 */
    boolean markClosing(Long orderId, LocalDateTime deadline);

    /** 支付宝确认关闭后，把关单中的订单置为已取消并恢复库存。 */
    boolean completeClosing(Long orderId);

    /** 查询长时间停留在关单中的订单，供补偿任务重新投递。 */
    List<Long> findStuckClosingOrderIds(LocalDateTime staleBefore, int limit);

    /** 抢占一笔卡住的关单并重新写入异步关单命令。 */
    boolean retryClosing(Long orderId, LocalDateTime staleBefore);

    OrderResponse confirm(Long orderId);

    OrderResponse finish(Long orderId);

    OrderResponse cancel(Long orderId, OrderCancelRequest request);

    OrderResponse getDetail(Long orderId);

    Page<OrderResponse> page(OrderQueryRequest request);
}
