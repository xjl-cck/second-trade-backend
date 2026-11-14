package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRecordMapper;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AlipayTradeCloseServiceImplTest {

    @Test
    void closesLatestPendingOutTradeNoInsteadOfOrderNo() throws Exception {
        AlipayProperties properties = mock(AlipayProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        AlipayClient client = mock(AlipayClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AlipayClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        PaymentRecordMapper paymentRecordMapper = mock(PaymentRecordMapper.class);

        PaymentRecord pending = new PaymentRecord();
        pending.setId(2L);
        pending.setOrderNo("SH100");
        pending.setOutTradeNo("SH100R123");
        pending.setStatus(0);
        when(paymentRecordMapper.selectOne(any(Wrapper.class))).thenReturn(pending);

        AlipayTradeCloseResponse response = mock(AlipayTradeCloseResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(response);

        AlipayTradeCloseServiceImpl service = new AlipayTradeCloseServiceImpl(
                properties, clientProvider, paymentRecordMapper, new ObjectMapper());

        AlipayTradeCloseResult result = service.closePendingTrade("SH100");
        assertEquals(AlipayTradeCloseResult.Status.CLOSED, result.status());
        assertEquals("SH100R123", result.outTradeNo());

        ArgumentCaptor<AlipayTradeCloseRequest> requestCaptor =
                ArgumentCaptor.forClass(AlipayTradeCloseRequest.class);
        verify(client).execute(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getBizContent().contains("SH100R123"));
        assertEquals(2, pending.getStatus());
        verify(paymentRecordMapper).updateById(pending);
    }

    @Test
    void reportsPaidWhenAlipayRejectsCloseBecauseTradeSucceeded() throws Exception {
        AlipayProperties properties = mock(AlipayProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        AlipayClient client = mock(AlipayClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AlipayClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        PaymentRecordMapper paymentRecordMapper = mock(PaymentRecordMapper.class);

        PaymentRecord pending = new PaymentRecord();
        pending.setId(2L);
        pending.setOrderNo("SH100");
        pending.setOutTradeNo("SH100R123");
        pending.setStatus(0);
        when(paymentRecordMapper.selectOne(any(Wrapper.class))).thenReturn(pending);

        AlipayTradeCloseResponse response = mock(AlipayTradeCloseResponse.class);
        when(response.isSuccess()).thenReturn(false);
        when(response.getSubCode()).thenReturn("ACQ.TRADE_HAS_SUCCESS");
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(response);

        AlipayTradeCloseServiceImpl service = new AlipayTradeCloseServiceImpl(
                properties, clientProvider, paymentRecordMapper, new ObjectMapper());

        AlipayTradeCloseResult result = service.closePendingTrade("SH100");

        assertEquals(AlipayTradeCloseResult.Status.PAID, result.status());
        assertEquals("SH100R123", result.outTradeNo());
        verify(paymentRecordMapper, never()).updateById(pending);
    }
}
