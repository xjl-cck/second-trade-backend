package org.example.secondhandtradingplatform.product.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

@Data
public class ProductPageCache {

    private long current;
    private long size;
    private long total;
    private long logicalExpireAt;
    private List<ProductResponse> records;

    public static ProductPageCache from(Page<ProductResponse> page, long logicalExpireAt) {
        ProductPageCache cache = new ProductPageCache();
        cache.current = page.getCurrent();
        cache.size = page.getSize();
        cache.total = page.getTotal();
        cache.logicalExpireAt = logicalExpireAt;
        cache.records = page.getRecords();
        return cache;
    }

    public boolean isLogicalExpired() {
        return logicalExpireAt <= System.currentTimeMillis();
    }

    public Page<ProductResponse> toPage() {
        Page<ProductResponse> page = Page.of(current, size, total);
        page.setRecords(records == null ? List.of() : records);
        return page;
    }
}
