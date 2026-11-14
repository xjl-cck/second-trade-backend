package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.example.secondhandtradingplatform.payment.service.AlipayPaymentService;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseResult;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCloseConsumerTest {

    private TradeOrderMapper tradeOrderMapper;
    private TradeOrderService tradeOrderService;
    private AlipayTradeCloseService alipayTradeCloseService;
    private AlipayPaymentService alipayPaymentService;
    private OrderCloseConsumer consumer;

    @BeforeEach
    void setUp() {
        tradeOrderMapper = mock(TradeOrderMapper.class);
        tradeOrderService = mock(TradeOrderService.class);
        alipayTradeCloseService = mock(AlipayTradeCloseService.class);
        alipayPaymentService = mock(AlipayPaymentService.class);
        consumer = new OrderCloseConsumer(
                tradeOrderMapper, tradeOrderService, alipayTradeCloseService, alipayPaymentService);
    }

    @Test
    void closedTradeCompletesLocalCancellation() {
        TradeOrder order = closingOrder();
        when(tradeOrderMapper.selectById(10L)).thenReturn(order);
        when(alipayTradeCloseService.closePendingTrade("SH10"))
                .thenReturn(AlipayTradeCloseResult.closed("SH10R1"));

        consumer.consume(new OrderCloseRequestedEvent(10L));

        verify(tradeOrderService).completeClosing(10L);
        verify(alipayPaymentService, never()).syncByOutTradeNo("SH10R1");
    }

    @Test
    void paidTradeSynchronizesPaymentInsteadOfCanceling() {
        TradeOrder order = closingOrder();
        when(tradeOrderMapper.selectById(10L)).thenReturn(order);
        when(alipayTradeCloseService.closePendingTrade("SH10"))
                .thenReturn(AlipayTradeCloseResult.paid("SH10R1"));

        consumer.consume(new OrderCloseRequestedEvent(10L));

        verify(alipayPaymentService).syncByOutTradeNo("SH10R1");
        verify(tradeOrderService, never()).completeClosing(10L);
    }

    @Test
    void unknownTradeKeepsClosingStateAndRetries() {
        TradeOrder order = closingOrder();
        when(tradeOrderMapper.selectById(10L)).thenReturn(order);
        when(alipayTradeCloseService.closePendingTrade("SH10"))
                .thenReturn(AlipayTradeCloseResult.unknown("SH10R1"));

        assertThrows(IllegalStateException.class,
                () -> consumer.consume(new OrderCloseRequestedEvent(10L)));

        verify(tradeOrderService, never()).completeClosing(10L);
        verify(alipayPaymentService, never()).syncByOutTradeNo("SH10R1");
    }

    private TradeOrder closingOrder() {
        TradeOrder order = new TradeOrder();
        order.setId(10L);
        order.setOrderNo("SH10");
        order.setStatus(4);
        return order;
    }
}
