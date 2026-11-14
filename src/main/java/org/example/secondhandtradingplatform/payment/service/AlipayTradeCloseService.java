package org.example.secondhandtradingplatform.payment.service;

/**
 * 关闭支付宝交易（用于超时未支付自动关单）。
 *
 * <p>单独成一个 Bean，避免 TradeOrderService 与 AlipayPaymentService 互相依赖成环。</p>
 */
public interface AlipayTradeCloseService {

    /**
     * 关闭订单最新一笔待支付的支付宝交易。
     * 返回 CLOSED 时可以安全取消本地订单；PAID 时应同步支付结果；UNKNOWN 时保持关单中并重试。
     */
    AlipayTradeCloseResult closePendingTrade(String orderNo);
}
