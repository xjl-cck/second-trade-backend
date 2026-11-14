package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRecordMapper;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlipayPaymentServiceImplTest {

    @Test
    void refundsSuccessfulPaymentThatArrivesAfterOrderCancellation() throws Exception {
        AlipayProperties properties = mock(AlipayProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        AlipayClient client = mock(AlipayClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AlipayClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        PaymentRecordMapper paymentRecordMapper = mock(PaymentRecordMapper.class);

        AlipayTradeRefundResponse response = mock(AlipayTradeRefundResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);

        AlipayPaymentServiceImpl service = new AlipayPaymentServiceImpl(
                properties,
                clientProvider,
                mock(TradeOrderMapper.class),
                mock(TradeOrderService.class),
                mock(ProductMapper.class),
                paymentRecordMapper,
                new ObjectMapper()
        );
        PaymentRecord record = new PaymentRecord();
        record.setId(8L);
        record.setOrderNo("SH100");
        record.setOutTradeNo("SH100R123");
        record.setStatus(0);
        TradeOrder order = new TradeOrder();
        order.setOrderNo("SH100");
        order.setAmount(new BigDecimal("21.00"));

        service.refundCanceledPayment(record, order, "20261113001",
                new BigDecimal("21.00"), "{}");

        ArgumentCaptor<AlipayTradeRefundRequest> requestCaptor =
                ArgumentCaptor.forClass(AlipayTradeRefundRequest.class);
        verify(client).execute(requestCaptor.capture());
        String bizContent = requestCaptor.getValue().getBizContent();
        assertTrue(bizContent.contains("SH100R123"));
        assertTrue(bizContent.contains("LR8"));
        assertEquals(4, record.getStatus());
        verify(paymentRecordMapper).updateById(record);
    }
}
