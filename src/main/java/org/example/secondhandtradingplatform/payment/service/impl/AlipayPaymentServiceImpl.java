package org.example.secondhandtradingplatform.payment.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.dto.PaymentCreateResponse;
import org.example.secondhandtradingplatform.payment.dto.PaymentStatusResponse;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;
import org.example.secondhandtradingplatform.payment.mapper.PaymentRecordMapper;
import org.example.secondhandtradingplatform.payment.service.AlipayPaymentService;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class AlipayPaymentServiceImpl implements AlipayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentServiceImpl.class);

    private static final int ORDER_STATUS_WAIT_CONFIRM = 0;
    private static final int ORDER_STATUS_DEALED = 1;
    private static final int ORDER_STATUS_CANCELED = 2;
    private static final int ORDER_STATUS_FINISHED = 3;

    private static final int PAY_PENDING = 0;
    private static final int PAY_SUCCESS = 1;
    private static final int PAY_CLOSED = 2;
    /** 支付宝已付款但本地订单状态不允许成交（如已取消），需要人工退款 */
    private static final int PAY_EXCEPTION = 3;

    private static final String CHANNEL = "alipay";
    private static final String PAY_METHOD = "page";
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String TRADE_CLOSED = "TRADE_CLOSED";
    private static final Set<String> PAID_TRADE_STATUS = Set.of("TRADE_SUCCESS", "TRADE_FINISHED");

    private final AlipayProperties properties;
    private final ObjectProvider<AlipayClient> alipayClientProvider;
    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderService tradeOrderService;
    private final ProductMapper productMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ObjectMapper objectMapper;

    public AlipayPaymentServiceImpl(AlipayProperties properties,
                                    ObjectProvider<AlipayClient> alipayClientProvider,
                                    TradeOrderMapper tradeOrderMapper,
                                    TradeOrderService tradeOrderService,
                                    ProductMapper productMapper,
                                    PaymentRecordMapper paymentRecordMapper,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.alipayClientProvider = alipayClientProvider;
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.productMapper = productMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateResponse createPagePay(Long orderId) {
        AlipayClient client = requireClient();
        Long buyerId = requireLoginUserId();
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有买家可以支付");
        }
        if (!Integer.valueOf(ORDER_STATUS_WAIT_CONFIRM).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "订单状态不是待确认，不能支付");
        }

        PaymentRecord record = ensurePendingRecord(order, buyerId);

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(properties.getReturnUrl());
        request.setBizContent(buildPagePayBizContent(order, record));

        String formHtml;
        try {
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                log.warn("创建支付宝交易失败，outTradeNo={}，code={}，subMsg={}",
                        record.getOutTradeNo(), response.getCode(), response.getSubMsg());
                throw new BizException(ErrorCode.SYSTEM_ERROR, "创建支付宝交易失败：" + response.getSubMsg());
            }
            formHtml = response.getBody();
        } catch (AlipayApiException e) {
            log.error("调用支付宝下单接口异常，outTradeNo={}", record.getOutTradeNo(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "调用支付宝下单接口失败：" + e.getErrMsg());
        }

        PaymentCreateResponse result = new PaymentCreateResponse();
        result.setOrderNo(order.getOrderNo());
        result.setOutTradeNo(record.getOutTradeNo());
        result.setChannel(CHANNEL);
        result.setPayMethod(PAY_METHOD);
        result.setAmount(order.getAmount());
        result.setFormHtml(formHtml);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(Map<String, String> params) {
        requireClient();
        verifyNotify(params);

        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        if (!StringUtils.hasText(outTradeNo) || !StringUtils.hasText(tradeStatus)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付宝通知缺少交易信息");
        }
        PaymentRecord record = findByOutTradeNo(outTradeNo);
        if (record == null) {
            // 不是本系统发起的交易：记录日志后返回 success，避免支付宝无意义地重试一整天
            log.error("收到未知交易的支付宝异步通知，outTradeNo={}，params={}", outTradeNo, params);
            return;
        }
        if (PAID_TRADE_STATUS.contains(tradeStatus)) {
            applyPaid(record, params.get("trade_no"), parseAmount(params.get("total_amount")), toJsonQuietly(params));
        } else if (TRADE_CLOSED.equals(tradeStatus)) {
            markClosed(record, params.get("trade_no"), toJsonQuietly(params));
        } else {
            log.info("忽略非终态的支付宝通知，outTradeNo={}，tradeStatus={}", outTradeNo, tradeStatus);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusResponse queryAndSync(String orderNo) {
        Long userId = requireLoginUserId();
        TradeOrder order = tradeOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能查询自己的订单");
        }
        PaymentRecord record = latestRecord(orderNo);
        if (record != null && !Integer.valueOf(PAY_SUCCESS).equals(record.getStatus())) {
            syncFromAlipay(record);
        }
        return buildStatus(record, tradeOrderMapper.selectByOrderNo(orderNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusResponse syncByOutTradeNo(String outTradeNo) {
        PaymentRecord record = findByOutTradeNo(outTradeNo);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付记录不存在");
        }
        if (!Integer.valueOf(PAY_SUCCESS).equals(record.getStatus())) {
            syncFromAlipay(record);
        }
        return buildStatus(record, tradeOrderMapper.selectByOrderNo(record.getOrderNo()));
    }

    private void applyPaid(PaymentRecord record, String tradeNo, BigDecimal paidAmount, String rawNotify) {
        if (Integer.valueOf(PAY_SUCCESS).equals(record.getStatus())) {
            return;
        }
        TradeOrder order = tradeOrderMapper.selectByOrderNoForUpdate(record.getOrderNo());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (paidAmount == null || order.getAmount() == null || paidAmount.compareTo(order.getAmount()) != 0) {
            log.error("支付金额与订单金额不一致，outTradeNo={}，通知金额={}，订单金额={}",
                    record.getOutTradeNo(), paidAmount, order.getAmount());
            record.setStatus(PAY_EXCEPTION);
            record.setLastError("支付金额与订单金额不一致");
            record.setNotifyTime(LocalDateTime.now());
            record.setRawNotify(rawNotify);
            paymentRecordMapper.updateById(record);
            return;
        }
        if (Integer.valueOf(ORDER_STATUS_CANCELED).equals(order.getStatus())) {
            log.warn("订单已取消但收到支付成功通知，需要原路退款，orderNo={}，tradeNo={}", order.getOrderNo(), tradeNo);
            record.setStatus(PAY_EXCEPTION);
            record.setTradeNo(tradeNo);
            record.setLastError("订单已取消，支付款项需要原路退款");
            record.setNotifyTime(LocalDateTime.now());
            record.setRawNotify(rawNotify);
            paymentRecordMapper.updateById(record);
            return;
        }

        tradeOrderService.paySuccess(order.getOrderNo(), paidAmount);

        record.setStatus(PAY_SUCCESS);
        record.setTradeNo(tradeNo);
        record.setPaidTime(LocalDateTime.now());
        record.setNotifyTime(LocalDateTime.now());
        record.setRawNotify(rawNotify);
        record.setLastError(null);
        paymentRecordMapper.updateById(record);
        log.info("支付宝支付成功已入账，orderNo={}，tradeNo={}，amount={}", order.getOrderNo(), tradeNo, paidAmount);
    }

    private void markClosed(PaymentRecord record, String tradeNo, String rawNotify) {
        if (Integer.valueOf(PAY_SUCCESS).equals(record.getStatus())) {
            return;
        }
        record.setStatus(PAY_CLOSED);
        if (StringUtils.hasText(tradeNo)) {
            record.setTradeNo(tradeNo);
        }
        record.setNotifyTime(LocalDateTime.now());
        record.setRawNotify(rawNotify);
        paymentRecordMapper.updateById(record);
    }

    private void syncFromAlipay(PaymentRecord record) {
        AlipayClient client = requireClient();
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", record.getOutTradeNo());

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(toJson(bizContent));
        AlipayTradeQueryResponse response;
        try {
            response = client.execute(request);
        } catch (AlipayApiException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "查询支付宝交易失败：" + e.getErrMsg());
        }
        if (!response.isSuccess()) {
            log.debug("支付宝交易尚未成功，outTradeNo={}，code={}，subMsg={}",
                    record.getOutTradeNo(), response.getCode(), response.getSubMsg());
            return;
        }
        String tradeStatus = response.getTradeStatus();
        if (PAID_TRADE_STATUS.contains(tradeStatus)) {
            BigDecimal totalAmount = response.getTotalAmount() == null ? null : new BigDecimal(response.getTotalAmount());
            applyPaid(record, response.getTradeNo(), totalAmount, null);
        } else if (TRADE_CLOSED.equals(tradeStatus)) {
            markClosed(record, response.getTradeNo(), null);
        }
    }

    private void verifyNotify(Map<String, String> params) {
        if (!StringUtils.hasText(params.get("sign"))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付宝通知缺少签名");
        }
        String appId = params.get("app_id");
        if (!properties.getAppId().equals(appId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付宝通知的 app_id 不匹配");
        }
        boolean verified;
        try {
            verified = AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    properties.getCharset(),
                    properties.getSignType());
        } catch (AlipayApiException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "支付宝通知验签异常：" + e.getErrMsg());
        }
        if (!verified) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付宝通知验签失败");
        }
    }

    private PaymentRecord ensurePendingRecord(TradeOrder order, Long buyerId) {
        PaymentRecord paid = paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderNo, order.getOrderNo())
                .eq(PaymentRecord::getStatus, PAY_SUCCESS)
                .last("limit 1"));
        if (paid != null) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该订单已支付");
        }
        PaymentRecord pending = paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderNo, order.getOrderNo())
                .eq(PaymentRecord::getStatus, PAY_PENDING)
                .orderByDesc(PaymentRecord::getId)
                .last("limit 1"));
        if (pending != null) {
            return pending;
        }

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setOutTradeNo(nextOutTradeNo(order));
        record.setChannel(CHANNEL);
        record.setPayMethod(PAY_METHOD);
        record.setAmount(order.getAmount());
        record.setBuyerId(buyerId);
        record.setStatus(PAY_PENDING);
        try {
            paymentRecordMapper.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            PaymentRecord existing = findByOutTradeNo(record.getOutTradeNo());
            if (existing == null) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "创建支付流水失败，请重试");
            }
            return existing;
        }
    }

    /** 首次用订单号作为 out_trade_no；历史支付单已关闭时追加后缀，避免支付宝返回 TRADE_HAS_CLOSE */
    private String nextOutTradeNo(TradeOrder order) {
        PaymentRecord latest = latestRecord(order.getOrderNo());
        if (latest == null) {
            return order.getOrderNo();
        }
        return order.getOrderNo() + "R" + System.currentTimeMillis();
    }

    private String buildPagePayBizContent(TradeOrder order, PaymentRecord record) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", record.getOutTradeNo());
        bizContent.put("total_amount", formatAmount(order.getAmount()));
        bizContent.put("subject", buildSubject(order));
        bizContent.put("product_code", PRODUCT_CODE);
        bizContent.put("timeout_express", properties.getTimeoutExpress());
        return toJson(bizContent);
    }

    private String buildSubject(TradeOrder order) {
        Product product = productMapper.selectById(order.getProductId());
        String subject = StringUtils.hasText(product == null ? null : product.getTitle())
                ? properties.getSubjectPrefix() + product.getTitle()
                : properties.getSubjectPrefix() + order.getOrderNo();
        return subject.length() > 128 ? subject.substring(0, 128) : subject;
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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

    private PaymentStatusResponse buildStatus(PaymentRecord record, TradeOrder order) {
        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setOrderNo(order != null ? order.getOrderNo() : (record == null ? null : record.getOrderNo()));
        response.setPaid(record != null && Integer.valueOf(PAY_SUCCESS).equals(record.getStatus()));
        response.setTradeNo(record == null ? null : record.getTradeNo());
        response.setChannel(record == null ? CHANNEL : record.getChannel());
        response.setAmount(order != null ? order.getAmount() : (record == null ? null : record.getAmount()));
        response.setPaidTime(record == null ? null : record.getPaidTime());
        if (order != null) {
            response.setOrderStatus(order.getStatus());
            response.setOrderStatusText(statusText(order.getStatus()));
            response.setDealTime(order.getDealTime());
        }
        return response;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ORDER_STATUS_WAIT_CONFIRM -> "待确认";
            case ORDER_STATUS_DEALED -> "已成交";
            case ORDER_STATUS_CANCELED -> "已取消";
            case ORDER_STATUS_FINISHED -> "已完成";
            default -> "未知";
        };
    }

    private PaymentRecord findByOutTradeNo(String outTradeNo) {
        return paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOutTradeNo, outTradeNo)
                .last("limit 1"));
    }

    private PaymentRecord latestRecord(String orderNo) {
        return paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .orderByDesc(PaymentRecord::getId)
                .last("limit 1"));
    }

    private AlipayClient requireClient() {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.BUSINESS_ERROR,
                    "支付宝支付未启用：请配置 ALIPAY_ENABLED=true、ALIPAY_APP_ID、ALIPAY_APP_PRIVATE_KEY、ALIPAY_PUBLIC_KEY");
        }
        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (client == null) {
            throw new BizException(ErrorCode.BUSINESS_ERROR,
                    "支付宝客户端未初始化，请查看启动日志里「支付宝客户端初始化失败」的具体原因（通常是私钥文件或公钥没配对）");
        }
        return client;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "构建支付宝请求报文失败");
        }
    }

    private String toJsonQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
