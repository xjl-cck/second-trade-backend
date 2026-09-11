package org.example.secondhandtradingplatform.product.dto;

import lombok.Data;

@Data
public class BrowseCountIncrement {

    private final Long productId;
    private final long delta;

    public BrowseCountIncrement(Long productId, long delta) {
        this.productId = productId;
        this.delta = delta;
    }
}
