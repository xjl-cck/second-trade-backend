package org.example.secondhandtradingplatform.product.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品变更事件：库存扣减/回补、状态变化等会直接影响商品详情的写操作。
 * <p>
 * 订单侧只负责发出事件（写 outbox），缓存失效由 {@link ProductCacheEvictConsumer} 统一处理，
 * 避免每个改库存/改状态的地方各自删缓存、漏一处就出现脏读。
 */
@Data
public class ProductChangedEvent {

    /** 变更类型：库存扣减 */
    public static final String REASON_STOCK_DEDUCTED = "stock-deducted";
    /** 变更类型：库存回补 */
    public static final String REASON_STOCK_RESTORED = "stock-restored";
    /** 变更类型：状态变化（如库存归零置为已售出） */
    public static final String REASON_STATUS_CHANGED = "status-changed";

    private Long productId;

    private String reason;

    private LocalDateTime eventTime;

    public static ProductChangedEvent of(Long productId, String reason) {
        ProductChangedEvent event = new ProductChangedEvent();
        event.setProductId(productId);
        event.setReason(reason);
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
