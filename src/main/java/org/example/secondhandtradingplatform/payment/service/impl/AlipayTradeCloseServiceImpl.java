package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
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

    private final AlipayProperties properties;
    private final ObjectProvider<AlipayClient> alipayClientProvider;
    private final ObjectMapper objectMapper;

    public AlipayTradeCloseServiceImpl(AlipayProperties properties,
                                       ObjectProvider<AlipayClient> alipayClientProvider,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.alipayClientProvider = alipayClientProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean closeQuietly(String outTradeNo) {
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
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                log.debug("支付宝侧不存在该交易，无需关闭，outTradeNo={}", outTradeNo);
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
