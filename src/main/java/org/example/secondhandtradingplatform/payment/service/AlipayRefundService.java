package org.example.secondhandtradingplatform.payment.service;

import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.returnrequest.entity.ReturnRequest;
import org.example.secondhandtradingplatform.payment.dto.PaymentRefundResponse;

import java.util.Optional;

/**
 * 支付宝退款。退款属于资金操作，失败不能假装成功，所以这里只返回结果、不抛业务异常，
 * 由调用方决定要不要回滚退货单状态。
 */
public interface AlipayRefundService {

    /**
     * 对退货单发起全额退款。
     *
     * @return empty 表示该订单不是支付宝支付（如模拟支付），无需线上退款；
     * 否则返回退款结果，status=1 才算成功
     */
    Optional<PaymentRefundResponse> refund(ReturnRequest returnRequest, TradeOrder order);

    /**
     * 主动查询支付宝退款结果并同步本地记录，用于补偿与对账。
     */
    PaymentRefundResponse queryAndSync(Long returnRequestId);

    /**
     * 定时对账：扫描「待退款 / 退款失败」的流水，回查支付宝把实际已退成功的补正。
     *
     * @return 本次确认退款成功的笔数
     */
    int reconcilePending(int limit);
}
