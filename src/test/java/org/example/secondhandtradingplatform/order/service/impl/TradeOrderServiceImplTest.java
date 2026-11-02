package org.example.secondhandtradingplatform.order.service.impl;

import org.example.secondhandtradingplatform.address.service.ShippingAddressService;
import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.message.OrderEventPublisher;
import org.example.secondhandtradingplatform.order.returnrequest.service.ReturnRequestService;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeOrderServiceImplTest {

    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final String IDEMPOTENCY_KEY = "key-1";

    private TradeOrderMapper tradeOrderMapper;
    private ProductMapper productMapper;
    private SysUserMapper sysUserMapper;
    private RedisCacheService redisCacheService;
    private ShippingAddressService shippingAddressService;
    private ReturnRequestService returnRequestService;
    private TradeOrderServiceImpl tradeOrderService;

    @BeforeEach
    void setUp() {
        tradeOrderMapper = mock(TradeOrderMapper.class);
        productMapper = mock(ProductMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        redisCacheService = mock(RedisCacheService.class);
        shippingAddressService = mock(ShippingAddressService.class);
        returnRequestService = mock(ReturnRequestService.class);
        OrderEventPublisher orderEventPublisher = mock(OrderEventPublisher.class);
        tradeOrderService = new TradeOrderServiceImpl(
                tradeOrderMapper,
                productMapper,
                sysUserMapper,
                redisCacheService,
                orderEventPublisher,
                shippingAddressService,
                returnRequestService
        );
        UserContext.setUserId(BUYER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("同一幂等号、相同参数重放时返回首次创建的订单")
    void replayWithSameParametersReturnsOriginalOrder() {
        TradeOrder existingOrder = existingOrder(100L, 10L, 1, null);
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingOrder);

        OrderResponse response = tradeOrderService.create(request(10L, 1, null));

        assertEquals(100L, response.getId());
        assertEquals("SH100", response.getOrderNo());
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
        verifyNoInteractions(redisCacheService);
    }

    @Test
    @DisplayName("同一幂等号、换成商品 B 时明确拒绝，不返回商品 A 的订单")
    void sameKeyWithDifferentProductIsRejected() {
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingOrder(100L, 10L, 1, null));

        BizException exception = assertThrows(BizException.class,
                () -> tradeOrderService.create(request(11L, 1, null)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("SH100"));
        assertTrue(exception.getMessage().contains("商品 10 变为 11"));
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
        // 冲突在抢锁之前就被拦下，不会白白占用商品锁
        verifyNoInteractions(redisCacheService);
    }

    @Test
    @DisplayName("同一幂等号、数量不同时明确拒绝")
    void sameKeyWithDifferentQuantityIsRejected() {
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingOrder(100L, 10L, 1, null));

        BizException exception = assertThrows(BizException.class,
                () -> tradeOrderService.create(request(10L, 3, null)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("数量 1 变为 3"));
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
    }

    @Test
    @DisplayName("同一幂等号、收货地址不同时明确拒绝")
    void sameKeyWithDifferentAddressIsRejected() {
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingOrder(100L, 10L, 1, 5L));

        BizException exception = assertThrows(BizException.class,
                () -> tradeOrderService.create(request(10L, 1, 6L)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("收货地址 5 变为 6"));
        verify(tradeOrderMapper, never()).insert(any(TradeOrder.class));
    }

    @Test
    @DisplayName("并发下唯一约束兜底：参数一致则返回已提交的订单，不报错")
    void duplicateKeyRaceWithSameParametersReturnsCommittedOrder() {
        stubSuccessfulCreatePath(11L);

        TradeOrder concurrentOrder = existingOrder(200L, 11L, 1, null);
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKeyForUpdate(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(concurrentOrder);

        OrderResponse response = tradeOrderService.create(request(11L, 1, null));

        assertEquals(200L, response.getId());
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    @DisplayName("并发下唯一约束兜底：参数不一致则拒绝，不再返回别人的订单")
    void duplicateKeyRaceWithDifferentParametersIsRejected() {
        stubSuccessfulCreatePath(11L);

        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKeyForUpdate(BUYER_ID, IDEMPOTENCY_KEY))
                .thenReturn(existingOrder(200L, 10L, 1, null));

        BizException exception = assertThrows(BizException.class,
                () -> tradeOrderService.create(request(11L, 1, null)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("商品 10 变为 11"));
        verify(productMapper, never()).updateById(any(Product.class));
    }

    private void stubSuccessfulCreatePath(Long productId) {
        Product product = onSaleProduct(productId);
        when(tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(BUYER_ID, IDEMPOTENCY_KEY)).thenReturn(null);
        when(redisCacheService.tryLock(anyString(), any(Duration.class))).thenReturn("lock-token");
        when(productMapper.selectByIdForUpdate(productId)).thenReturn(product);
        doThrow(new DuplicateKeyException("uk_trade_order_buyer_idempotency"))
                .when(tradeOrderMapper).insert(any(TradeOrder.class));
        when(productMapper.selectById(anyLong())).thenReturn(product);
        when(sysUserMapper.selectById(anyLong())).thenReturn(null);
        when(returnRequestService.findByOrderId(anyLong())).thenReturn(null);
    }

    private Product onSaleProduct(Long productId) {
        Product product = new Product();
        product.setId(productId);
        product.setSellerId(SELLER_ID);
        product.setTitle("测试商品" + productId);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus(2);
        product.setDeliveryMethod(1);
        product.setLocation("图书馆门口");
        return product;
    }

    private TradeOrder existingOrder(Long orderId, Long productId, Integer quantity, Long addressId) {
        TradeOrder order = new TradeOrder();
        order.setId(orderId);
        order.setOrderNo("SH" + orderId);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setAddressId(addressId);
        order.setIdempotencyKey(IDEMPOTENCY_KEY);
        order.setAmount(new BigDecimal("10.00"));
        order.setStatus(0);
        return order;
    }

    private OrderCreateRequest request(Long productId, Integer quantity, Long addressId) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        request.setAddressId(addressId);
        request.setIdempotencyKey(IDEMPOTENCY_KEY);
        return request;
    }
}
