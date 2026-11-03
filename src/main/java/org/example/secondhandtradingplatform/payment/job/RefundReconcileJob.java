package org.example.secondhandtradingplatform.payment.job;

import org.example.secondhandtradingplatform.payment.service.AlipayRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 退款对账补偿。
 *
 * <p>退款是先落库再调支付宝的：如果调用超时/网络抖动，本地记的是失败或待退款，
 * 但支付宝那边可能已经退成功了。这个任务定期用 alipay.trade.fastpay.refund.query 核对，
 * 避免「钱退了、订单还显示没退」。</p>
 *
 * <p>只处理最近 24 小时、且 2 分钟内没被处理过的记录，天然形成退避，不会把老数据反复打到支付宝。</p>
 */
@Component
public class RefundReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(RefundReconcileJob.class);

    private final AlipayRefundService alipayRefundService;
    private final boolean enabled;
    private final int batchSize;

    public RefundReconcileJob(AlipayRefundService alipayRefundService,
                              @Value("${app.payment.refund-reconcile-enabled:true}") boolean enabled,
                              @Value("${app.payment.refund-reconcile-batch-size:50}") int batchSize) {
        this.alipayRefundService = alipayRefundService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.payment.refund-reconcile-interval-ms:300000}",
            initialDelayString = "${app.payment.refund-reconcile-initial-delay-ms:60000}"
    )
    public void reconcile() {
        if (!enabled) {
            return;
        }
        try {
            alipayRefundService.reconcilePending(batchSize);
        } catch (Exception e) {
            log.warn("退款对账任务执行失败", e);
        }
    }
}
