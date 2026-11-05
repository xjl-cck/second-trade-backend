package org.example.secondhandtradingplatform.product.job;

import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.product.service.impl.ProductSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchSyncJobTest {

    private ProductSearchIndex searchIndex;
    private ProductMapper productMapper;
    private ProductSearchSyncJob job;

    @BeforeEach
    void setUp() {
        searchIndex = mock(ProductSearchIndex.class);
        productMapper = mock(ProductMapper.class);
        when(searchIndex.isAvailable()).thenReturn(true);
        when(searchIndex.index(any(Product.class))).thenReturn(true);
        when(searchIndex.delete(anyLong())).thenReturn(true);
        job = new ProductSearchSyncJob(searchIndex, productMapper, true, 600, 2);
    }

    @Test
    @DisplayName("窗口内的商品逐条写进 ES，并在不满一批时结束")
    void syncsChangedProducts() {
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        when(productMapper.selectChangedAfter(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(product(1L, time), product(2L, time)))
                .thenReturn(List.of());
        when(productMapper.selectDeletedAfter(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());

        job.sync();

        verify(searchIndex, times(2)).index(any(Product.class));
        verify(searchIndex, never()).delete(anyLong());
    }

    @Test
    @DisplayName("满批之后继续翻页，游标推进后遇到空批结束")
    void pagesUntilEmpty() {
        LocalDateTime first = LocalDateTime.now().minusMinutes(5);
        LocalDateTime second = LocalDateTime.now().minusMinutes(4);
        when(productMapper.selectChangedAfter(any(), any(), any(), anyLong(), eq(2)))
                .thenReturn(List.of(product(1L, first), product(2L, first)))
                .thenReturn(List.of(product(3L, second)));
        when(productMapper.selectDeletedAfter(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());

        job.sync();

        verify(searchIndex, times(3)).index(any(Product.class));
        verify(productMapper, times(2)).selectChangedAfter(any(), any(), any(), anyLong(), eq(2));
    }

    @Test
    @DisplayName("逻辑删除的商品从索引里删掉，404 之类的问题不打断整轮")
    void removesDeletedProducts() {
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        when(productMapper.selectChangedAfter(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(productMapper.selectDeletedAfter(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(product(9L, time)));

        job.sync();

        verify(searchIndex).delete(9L);
    }

    @Test
    @DisplayName("ES 不可用或开关关闭时直接跳过，不去查库")
    void skipsWhenDisabledOrUnavailable() {
        when(searchIndex.isAvailable()).thenReturn(false);
        job.sync();
        verify(productMapper, never()).selectChangedAfter(any(), any(), any(), anyLong(), anyInt());

        ProductSearchSyncJob disabled =
                new ProductSearchSyncJob(searchIndex, productMapper, false, 600, 2);
        assertDoesNotThrow(disabled::sync);
        verify(productMapper, never()).selectChangedAfter(any(), any(), any(), anyLong(), anyInt());
    }

    private Product product(Long id, LocalDateTime updateTime) {
        Product product = new Product();
        product.setId(id);
        product.setTitle("测试商品" + id);
        product.setUpdateTime(updateTime);
        return product;
    }
}
