package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.returnrequest.entity.ReturnRequest;
import org.example.secondhandtradingplatform.order.returnrequest.mapper.ReturnRequestMapper;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.dto.PaymentRefundResponse;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;
import org.example.secondhandtradingplatform.payment.entity.PaymentRefund;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRecordMapper;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRefundMapper;
import org.example.secondhandtradingplatform.payment.service.AlipayRefundService;
import org.example.secondhandtradingplatform.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AlipayRefundServiceImpl implements AlipayRefundService {

    private static final Logger log = LoggerFactory.getLogger(AlipayRefundServiceImpl.class);

    private static final int REFUND_PENDING = 0;
    private static final int REFUND_SUCCESS = 1;
    private static final int REFUND_FAILED = 2;

    private static final int PAY_SUCCESS = 1;
    private static final String REFUND_SUCCESS_STATUS = "REFUND_SUCCESS";

    private final AlipayProperties properties;
    private final ObjectProvider<AlipayClient> alipayClientProvider;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentRefundMapper paymentRefundMapper;
    private final ReturnRequestMapper returnRequestMapper;
    private final ObjectMapper objectMapper;

    public AlipayRefundServiceImpl(AlipayProperties properties,
                                   ObjectProvider<AlipayClient> alipayClientProvider,
                                   PaymentRecordMapper paymentRecordMapper,
                                   PaymentRefundMapper paymentRefundMapper,
                                   ReturnRequestMapper returnRequestMapper,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.alipayClientProvider = alipayClientProvider;
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentRefundMapper = paymentRefundMapper;
        this.returnRequestMapper = returnRequestMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 用独立事务：退款结果（成功或失败）必须先落库，再让调用方决定是否回滚退货单状态，
     * 否则失败信息会跟着外层事务一起回滚，事后没法排查。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Optional<PaymentRefundResponse> refund(ReturnRequest returnRequest, TradeOrder order) {
        PaymentRecord payment = findPaidRecord(order.getOrderNo());
        if (payment == null) {
            log.info("订单 {} 没有支付宝支付流水，跳过线上退款", order.getOrderNo());
            return Optional.empty();
        }

        PaymentRefund refund = findOrCreate(returnRequest, order, payment);
        if (Integer.valueOf(REFUND_SUCCESS).equals(refund.getStatus())) {
            return Optional.of(PaymentRefundResponse.from(refund));
        }

        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (!properties.isEnabled() || client == null) {
            return Optional.of(PaymentRefundResponse.from(fail(refund, "支付宝客户端不可用，无法发起退款")));
        }

        BigDecimal amount = order.getAmount();
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("refund_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        bizContent.put("out_request_no", refund.getOutRequestNo());
        bizContent.put("refund_reason", "二手交易平台退货退款");

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent(toJson(bizContent));

        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (response.isSuccess()) {
                refund.setStatus(REFUND_SUCCESS);
                refund.setTradeNo(response.getTradeNo() == null ? payment.getTradeNo() : response.getTradeNo());
                refund.setRefundFee(parseAmount(response.getRefundFee()));
                refund.setFundChange(response.getFundChange());
                refund.setError(null);
                refund.setRefundTime(LocalDateTime.now());
                paymentRefundMapper.updateById(refund);
                log.info("支付宝退款成功，orderNo={}，outRequestNo={}，refundFee={}，fundChange={}",
                        order.getOrderNo(), refund.getOutRequestNo(), response.getRefundFee(), response.getFundChange());
            } else {
                String message = StringUtils.hasText(response.getSubMsg()) ? response.getSubMsg() : response.getMsg();
                fail(refund, message);
                log.warn("支付宝退款失败，orderNo={}，code={}，subCode={}，subMsg={}",
                        order.getOrderNo(), response.getCode(), response.getSubCode(), response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            fail(refund, e.getErrMsg());
            log.error("调用支付宝退款接口异常，orderNo={}", order.getOrderNo(), e);
        }
        return Optional.of(PaymentRefundResponse.from(refund));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundResponse queryAndSync(Long returnRequestId) {
        ReturnRequest returnRequest = returnRequestMapper.selectById(returnRequestId);
        if (returnRequest == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退货申请不存在");
        }
        Long userId = requireLoginUserId();
        if (!userId.equals(returnRequest.getBuyerId()) && !userId.equals(returnRequest.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能查询自己参与的退货单");
        }
        PaymentRefund refund = findByReturnRequestId(returnRequestId);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该退货单还没有退款记录");
        }
        if (Integer.valueOf(REFUND_SUCCESS).equals(refund.getStatus())) {
            return PaymentRefundResponse.from(refund);
        }
        syncFromAlipay(refund);
        return PaymentRefundResponse.from(refund);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reconcilePending(int limit) {
        if (!properties.isEnabled()) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();
        java.util.List<PaymentRefund> pending = paymentRefundMapper.selectList(Wrappers.<PaymentRefund>lambdaQuery()
                .in(PaymentRefund::getStatus, REFUND_PENDING, REFUND_FAILED)
                .gt(PaymentRefund::getCreateTime, now.minusHours(24))
                .lt(PaymentRefund::getUpdateTime, now.minusMinutes(2))
                .orderByAsc(PaymentRefund::getId)
                .last("limit " + safeLimit));
        int synced = 0;
        for (PaymentRefund refund : pending) {
            try {
                if (syncFromAlipay(refund)) {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("退款对账失败，refundId={}，orderNo={}", refund.getId(), refund.getOrderNo(), e);
            }
        }
        if (synced > 0) {
            log.info("退款对账补偿成功 {} 笔（本轮扫描 {} 笔）", synced, pending.size());
        }
        return synced;
    }

    /**
     * 回查支付宝退款单，确认成功则把本地流水补正。
     *
     * @return 本次是否确认到「退款成功」
     */
    private boolean syncFromAlipay(PaymentRefund refund) {
        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (!properties.isEnabled() || client == null) {
            return false;
        }
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", refund.getOrderNo());
        bizContent.put("out_request_no", refund.getOutRequestNo());
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizContent(toJson(bizContent));
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(request);
            if (response.isSuccess() && REFUND_SUCCESS_STATUS.equals(response.getRefundStatus())) {
                refund.setStatus(REFUND_SUCCESS);
                refund.setRefundFee(parseAmount(response.getRefundAmount()));
                refund.setError(null);
                refund.setRefundTime(LocalDateTime.now());
                paymentRefundMapper.updateById(refund);
                log.info("支付宝退款查询确认成功，orderNo={}，outRequestNo={}", refund.getOrderNo(), refund.getOutRequestNo());
                return true;
            }
            return false;
        } catch (AlipayApiException e) {
            log.warn("查询支付宝退款结果失败，orderNo={}", refund.getOrderNo(), e);
            return false;
        }
    }

    private PaymentRefund findOrCreate(ReturnRequest returnRequest, TradeOrder order, PaymentRecord payment) {
        PaymentRefund existing = findByReturnRequestId(returnRequest.getId());
        if (existing != null) {
            return existing;
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setOutTradeNo(payment.getOutTradeNo());
        refund.setTradeNo(payment.getTradeNo());
        refund.setReturnRequestId(returnRequest.getId());
        refund.setOutRequestNo(buildOutRequestNo(returnRequest.getId()));
        refund.setAmount(order.getAmount());
        refund.setStatus(REFUND_PENDING);
        try {
            paymentRefundMapper.insert(refund);
            return refund;
        } catch (DuplicateKeyException e) {
            PaymentRefund concurrent = findByReturnRequestId(returnRequest.getId());
            if (concurrent == null) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "创建退款流水失败，请重试");
            }
            return concurrent;
        }
    }

    private PaymentRefund fail(PaymentRefund refund, String message) {
        refund.setStatus(REFUND_FAILED);
        refund.setError(truncate(message));
        paymentRefundMapper.updateById(refund);
        return refund;
    }

    private PaymentRefund findByReturnRequestId(Long returnRequestId) {
        return paymentRefundMapper.selectOne(Wrappers.<PaymentRefund>lambdaQuery()
                .eq(PaymentRefund::getReturnRequestId, returnRequestId)
                .last("limit 1"));
    }

    private PaymentRecord findPaidRecord(String orderNo) {
        return paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .eq(PaymentRecord::getStatus, PAY_SUCCESS)
                .orderByDesc(PaymentRecord::getId)
                .last("limit 1"));
    }

    private String buildOutRequestNo(Long returnRequestId) {
        return "RF" + returnRequestId;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private BigDecimal parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "构建支付宝退款请求报文失败");
        }
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
