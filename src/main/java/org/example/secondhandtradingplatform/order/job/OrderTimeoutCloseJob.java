package org.example.secondhandtradingplatform.order.job;

import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付自动关单。
 *
 * <p>为什么需要：支付宝侧的交易到 timeout_express 会自己关闭，但本地订单不会跟着变，
 * 库存也就一直占着（下单时已扣减）。所以需要本地定时扫描 + 主动调 alipay.trade.close。</p>
 */
@Component
public class OrderTimeoutCloseJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseJob.class);

    private final TradeOrderService tradeOrderService;
    private final boolean enabled;
    private final long timeoutMinutes;
    private final int batchSize;

    public OrderTimeoutCloseJob(TradeOrderService tradeOrderService,
                                @Value("${app.order.auto-close-enabled:true}") boolean enabled,
                                @Value("${app.order.timeout-minutes:30}") long timeoutMinutes,
                                @Value("${app.order.auto-close-batch-size:50}") int batchSize) {
        this.tradeOrderService = tradeOrderService;
        this.enabled = enabled;
        this.timeoutMinutes = timeoutMinutes;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.order.auto-close-interval-ms:60000}",
            initialDelayString = "${app.order.auto-close-initial-delay-ms:30000}"
    )
    public void closeTimeoutOrders() {
        if (!enabled || timeoutMinutes <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Long> orderIds;
        try {
            orderIds = tradeOrderService.findTimeoutOrderIds(deadline, batchSize);
        } catch (Exception e) {
            log.warn("扫描超时未支付订单失败", e);
            return;
        }
        if (orderIds.isEmpty()) {
            return;
        }
        int closed = 0;
        for (Long orderId : orderIds) {
            // 每笔订单独立事务：一笔失败不影响其它订单
            try {
                if (tradeOrderService.closeTimeoutOrder(orderId, deadline)) {
                    closed++;
                }
            } catch (Exception e) {
                log.warn("自动关闭超时订单失败，orderId={}，下一轮重试", orderId, e);
            }
        }
        if (closed > 0) {
            log.info("自动关闭超时未支付订单 {} 笔（本轮扫描 {} 笔）", closed, orderIds.size());
        }
    }
}
