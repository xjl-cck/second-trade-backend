package org.example.secondhandtradingplatform.payment.service;

import org.example.secondhandtradingplatform.payment.dto.PaymentCreateResponse;
import org.example.secondhandtradingplatform.payment.dto.PaymentStatusResponse;

import java.util.Map;

/**
 * 支付宝（电脑网站支付）接入。
 *
 * <p>流程：买家调用 {@link #createPagePay} 拿到收银台表单 -> 付款 ->
 * 支付宝异步通知 {@link #handleNotify}（权威结果）+ 同步跳回 return 做主动查询兜底。</p>
 */
public interface AlipayPaymentService {

    /**
     * 创建支付宝电脑网站支付订单，返回可直接跳转收银台的表单 HTML。
     */
    PaymentCreateResponse createPagePay(Long orderId);

    /**
     * 处理支付宝异步通知：验签、校验金额、幂等推进订单状态。
     * 失败抛异常，由 Controller 返回 failure 让支付宝重试。
     */
    void handleNotify(Map<String, String> params);

    /**
     * 主动查询支付宝交易并同步本地状态，用于前端轮询。
     */
    PaymentStatusResponse queryAndSync(String orderNo);

    /**
     * 按支付宝商户订单号同步状态，供 return 同步跳回使用（该回调没有登录态）。
     */
    PaymentStatusResponse syncByOutTradeNo(String outTradeNo);
}
