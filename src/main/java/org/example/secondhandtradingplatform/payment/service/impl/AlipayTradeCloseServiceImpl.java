package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRecordMapper;
import org.example.secondhandtradingplatform.payment.service.AlipayTradeCloseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AlipayTradeCloseServiceImpl implements AlipayTradeCloseService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTradeCloseServiceImpl.class);

    /** 交易不存在：说明用户根本没打开收银台，本地关单即可 */
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";
    /** 重试关单时支付宝侧已经关闭，按幂等成功处理 */
    private static final String TRADE_ALREADY_CLOSED = "ACQ.TRADE_HAS_CLOSE";
    private static final int PAY_PENDING = 0;
    private static final int PAY_CLOSED = 2;

    private final AlipayProperties properties;
    private final ObjectProvider<AlipayClient> alipayClientProvider;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ObjectMapper objectMapper;

    public AlipayTradeCloseServiceImpl(AlipayProperties properties,
                                       ObjectProvider<AlipayClient> alipayClientProvider,
                                       PaymentRecordMapper paymentRecordMapper,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.alipayClientProvider = alipayClientProvider;
        this.paymentRecordMapper = paymentRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean closePendingTrade(String orderNo) {
        PaymentRecord pending = paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .eq(PaymentRecord::getStatus, PAY_PENDING)
                .orderByDesc(PaymentRecord::getId)
                .last("limit 1"));
        if (pending == null) {
            return true;
        }
        if (!closeQuietly(pending.getOutTradeNo())) {
            return false;
        }
        pending.setStatus(PAY_CLOSED);
        pending.setLastError(null);
        paymentRecordMapper.updateById(pending);
        return true;
    }

    private boolean closeQuietly(String outTradeNo) {
        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (!properties.isEnabled() || client == null || !StringUtils.hasText(outTradeNo)) {
            return true;
        }
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", outTradeNo);

        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizContent(toJson(bizContent));
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (response.isSuccess()) {
                log.info("支付宝交易已关闭，outTradeNo={}", outTradeNo);
                return true;
            }
            if (TRADE_NOT_EXIST.equals(response.getSubCode())
                    || TRADE_ALREADY_CLOSED.equals(response.getSubCode())) {
                log.debug("支付宝交易不存在或已经关闭，无需重复关闭，outTradeNo={}", outTradeNo);
                return true;
            }
            log.warn("关闭支付宝交易失败，outTradeNo={}，subCode={}，subMsg={}",
                    outTradeNo, response.getSubCode(), response.getSubMsg());
            return false;
        } catch (AlipayApiException e) {
            log.warn("调用支付宝关单接口异常，outTradeNo={}", outTradeNo, e);
            return false;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("构建支付宝关单请求报文失败", e);
        }
    }
}
