package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.example.secondhandtradingplatform.payment.service.AlipayPaymentService;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseResult;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseConsumer {

    private static final int ORDER_CLOSING = 4;

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderService tradeOrderService;
    private final AlipayTradeCloseService alipayTradeCloseService;
    private final AlipayPaymentService alipayPaymentService;

    public OrderCloseConsumer(TradeOrderMapper tradeOrderMapper,
                              TradeOrderService tradeOrderService,
                              AlipayTradeCloseService alipayTradeCloseService,
                              AlipayPaymentService alipayPaymentService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.alipayTradeCloseService = alipayTradeCloseService;
        this.alipayPaymentService = alipayPaymentService;
    }

    @RabbitListener(queues = "${app.rabbitmq.order-close-queue}")
    public void consume(OrderCloseRequestedEvent event) {
        TradeOrder order = tradeOrderMapper.selectById(event.getOrderId());
        if (order == null || !Integer.valueOf(ORDER_CLOSING).equals(order.getStatus())) {
            return;
        }

        // 此处没有事务：支付宝 RPC 不持有订单行锁。
        AlipayTradeCloseResult result = alipayTradeCloseService.closePendingTrade(order.getOrderNo());
        switch (result.status()) {
            case CLOSED -> tradeOrderService.completeClosing(order.getId());
            case PAID -> alipayPaymentService.syncByOutTradeNo(result.outTradeNo());
            case UNKNOWN -> throw new IllegalStateException("支付宝关单结果未知，等待消息或定时任务重试");
        }
    }
}
