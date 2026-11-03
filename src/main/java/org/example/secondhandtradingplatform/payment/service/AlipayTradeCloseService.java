package org.example.secondhandtradingplatform.payment.service;

/**
 * 关闭支付宝交易（用于超时未支付自动关单）。
 *
 * <p>单独成一个 Bean，避免 TradeOrderService 与 AlipayPaymentService 互相依赖成环。</p>
 */
public interface AlipayTradeCloseService {

    /**
     * 关闭指定商户订单号对应的支付宝交易。
     * 交易不存在、已关闭、或未启用支付宝时都视为「无需关闭」，返回 true。
     *
     * @return false 表示支付宝明确拒绝关闭，调用方应稍后重试
     */
    boolean closeQuietly(String outTradeNo);
}
