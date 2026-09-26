package org.example.secondhandtradingplatform.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.address.entity.ShippingAddress;
import org.example.secondhandtradingplatform.address.service.ShippingAddressService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.order.dto.OrderCancelRequest;
import org.example.secondhandtradingplatform.order.dto.OrderCreateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderQueryRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.message.OrderEventPublisher;
import org.example.secondhandtradingplatform.order.message.OrderStatusEvent;
import org.example.secondhandtradingplatform.order.service.TradeOrderService;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TradeOrderServiceImpl implements TradeOrderService {

    private static final int PRODUCT_ON_SALE = 2;
    private static final int PRODUCT_SOLD = 4;
    private static final int ORDER_WAIT_CONFIRM = 0;
    private static final int ORDER_DEALED = 1;
    private static final int ORDER_CANCELED = 2;
    private static final int ORDER_FINISHED = 3;
    private static final Duration ORDER_LOCK_TTL = Duration.ofSeconds(10);
    private static final String ORDER_LOCK_PREFIX = "lock:order:product:";

    private final TradeOrderMapper tradeOrderMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisCacheService redisCacheService;
    private final OrderEventPublisher orderEventPublisher;
    private final ShippingAddressService shippingAddressService;

    public TradeOrderServiceImpl(TradeOrderMapper tradeOrderMapper,
                                 ProductMapper productMapper,
                                 SysUserMapper sysUserMapper,
                                 RedisCacheService redisCacheService,
                                 OrderEventPublisher orderEventPublisher,
                                 ShippingAddressService shippingAddressService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.productMapper = productMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisCacheService = redisCacheService;
        this.orderEventPublisher = orderEventPublisher;
        this.shippingAddressService = shippingAddressService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse create(OrderCreateRequest request) {
        Long buyerId = requireLoginUserId();
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        String idempotencyKey = request.getIdempotencyKey().trim();
        TradeOrder existingOrder = tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey);
        if (existingOrder != null) {
            return  buildResponse(existingOrder);
        }
        String lockKey = ORDER_LOCK_PREFIX + request.getProductId();
        String lockToken = redisCacheService.tryLock(lockKey, ORDER_LOCK_TTL);
        if (lockToken == null) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "订单正在处理中，请稍后重试");
        }
        try {
            Product product = productMapper.selectByIdForUpdate(request.getProductId());
            if (product == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
            }
            existingOrder = tradeOrderMapper.selectByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey);
            if (existingOrder != null) {
                return buildResponse(existingOrder);
            }
            if (!Integer.valueOf(PRODUCT_ON_SALE).equals(product.getStatus())) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "只能购买上架中的商品");
            }
            if (buyerId.equals(product.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "不能购买自己发布的商品");
            }
            if (product.getStock() == null || product.getStock() < quantity) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "该商品库存不足");
            }
            ShippingAddress address = shippingAddressService.getForCurrentUser(request.getAddressId());

            TradeOrder order = new TradeOrder();
            order.setOrderNo(generateOrderNo());
            order.setBuyerId(buyerId);
            order.setSellerId(product.getSellerId());
            order.setProductId(product.getId());
            order.setQuantity(quantity);
            order.setIdempotencyKey(idempotencyKey);
            order.setAddressId(address.getId());
            order.setReceiverName(address.getReceiverName());
            order.setReceiverPhone(address.getReceiverPhone());
            order.setReceiverAddress(formatAddress(address));
            order.setAmount(product.getPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
            order.setStatus(ORDER_WAIT_CONFIRM);
            order.setIsDeleted(0);
            tradeOrderMapper.insert(order);
            product.setStock(product.getStock() - quantity);
            productMapper.updateById(product);
            orderEventPublisher.publishAfterCommit(buildEvent(order, product, "订单已创建"));
            return buildResponse(order);
        } finally {
            redisCacheService.unlock(lockKey, lockToken);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse pay(Long orderId) {
        TradeOrder order = getOrderForCurrentUserForUpdate(orderId);
        Long userId = requireLoginUserId();
        if (!userId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有买家可以模拟支付");
        }
        return deal(order, "订单状态不是待确认，不能支付");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse confirm(Long orderId) {
        TradeOrder order = getOrderForCurrentUserForUpdate(orderId);
        Long userId = requireLoginUserId();
        if (!userId.equals(order.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有卖家可以确认成交");
        }
        return deal(order, "订单状态不是待确认，不能确认成交");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse finish(Long orderId) {
        TradeOrder order = getOrderForCurrentUserForUpdate(orderId);
        if (!Integer.valueOf(ORDER_DEALED).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "订单状态不是已成交，不能确认完成");
        }
        order.setStatus(ORDER_FINISHED);
        order.setFinishedTime(LocalDateTime.now());
        tradeOrderMapper.updateById(order);
        orderEventPublisher.publishAfterCommit(buildEvent(order, null, "订单已完成"));
        return buildResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancel(Long orderId, OrderCancelRequest request) {
        TradeOrder order = getOrderForCurrentUserForUpdate(orderId);
        if (!Integer.valueOf(ORDER_WAIT_CONFIRM).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "只有待确认订单可以取消");
        }
        order.setStatus(ORDER_CANCELED);
        order.setCloseReason(trimToNull(request == null ? null : request.getReason()));
        tradeOrderMapper.updateById(order);
        Product canceledProduct = productMapper.selectByIdForUpdate(order.getProductId());
        if (canceledProduct != null) {
            int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
            canceledProduct.setStock((canceledProduct.getStock() == null ? 0 : canceledProduct.getStock()) + quantity);
            if (Integer.valueOf(PRODUCT_SOLD).equals(canceledProduct.getStatus())) {
                canceledProduct.setStatus(PRODUCT_ON_SALE);
            }
            productMapper.updateById(canceledProduct);
        }
        orderEventPublisher.publishAfterCommit(buildEvent(order, null, "订单已取消"));
        return buildResponse(order);
    }

    @Override
    public OrderResponse getDetail(Long orderId) {
        return buildResponse(getOrderForCurrentUser(orderId));
    }

    @Override
    public Page<OrderResponse> page(OrderQueryRequest request) {
        Long userId = requireLoginUserId();
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());

        LambdaQueryWrapper<TradeOrder> query = Wrappers.lambdaQuery(TradeOrder.class)
                .eq(request.getStatus() != null, TradeOrder::getStatus, request.getStatus())
                .orderByDesc(TradeOrder::getCreateTime)
                .orderByDesc(TradeOrder::getId);
        if ("buyer".equalsIgnoreCase(request.getRole())) {
            query.eq(TradeOrder::getBuyerId, userId);
        } else if ("seller".equalsIgnoreCase(request.getRole())) {
            query.eq(TradeOrder::getSellerId, userId);
        } else {
            query.and(wrapper -> wrapper.eq(TradeOrder::getBuyerId, userId)
                    .or()
                    .eq(TradeOrder::getSellerId, userId));
        }

        Page<TradeOrder> orderPage = tradeOrderMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<OrderResponse> responsePage = Page.of(pageNo, pageSize, orderPage.getTotal());
        List<OrderResponse> records = orderPage.getRecords().stream()
                .map(this::buildResponse)
                .toList();
        responsePage.setRecords(records);
        return responsePage;
    }

    private OrderResponse deal(TradeOrder order, String errorMessage) {
        if (!Integer.valueOf(ORDER_WAIT_CONFIRM).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, errorMessage);
        }
        Product product = productMapper.selectByIdForUpdate(order.getProductId());
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!Integer.valueOf(PRODUCT_ON_SALE).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "商品不是上架状态，不能成交");
        }

        order.setStatus(ORDER_DEALED);
        order.setDealTime(LocalDateTime.now());
        tradeOrderMapper.updateById(order);

        if (product.getStock() == null || product.getStock() <= 0) {
            product.setStatus(PRODUCT_SOLD);
        }
        productMapper.updateById(product);
        orderEventPublisher.publishAfterCommit(buildEvent(order, product, "订单已成交"));
        return buildResponse(order);
    }

    private OrderStatusEvent buildEvent(TradeOrder order, Product product, String statusText) {
        OrderStatusEvent event = new OrderStatusEvent();
        event.setOrderId(order.getId());
        event.setOrderNo(order.getOrderNo());
        event.setProductId(order.getProductId());
        event.setProductTitle(product == null ? null : product.getTitle());
        event.setBuyerId(order.getBuyerId());
        event.setSellerId(order.getSellerId());
        event.setStatus(order.getStatus());
        event.setStatusText(statusText);
        event.setEventTime(LocalDateTime.now());
        return event;
    }

    private TradeOrder getOrderForCurrentUser(Long orderId) {
        Long userId = requireLoginUserId();
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        return verifyOrderOwner(order, userId);
    }

    private TradeOrder getOrderForCurrentUserForUpdate(Long orderId) {
        Long userId = requireLoginUserId();
        TradeOrder order = tradeOrderMapper.selectByIdForUpdate(orderId);
        return verifyOrderOwner(order, userId);
    }

    private TradeOrder verifyOrderOwner(TradeOrder order, Long userId) {
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的订单");
        }
        return order;
    }

    private OrderResponse buildResponse(TradeOrder order) {
        OrderResponse response = OrderResponse.from(order);
        Product product = productMapper.selectById(order.getProductId());
        if (product != null) {
            response.setProductTitle(product.getTitle());
            response.setProductCoverImage(product.getCoverImage());
        }
        SysUser buyer = sysUserMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            response.setBuyerNickname(buyer.getNickname());
        }
        SysUser seller = sysUserMapper.selectById(order.getSellerId());
        if (seller != null) {
            response.setSellerNickname(seller.getNickname());
        }
        return response;
    }

    private String generateOrderNo() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "SH" + timestamp + random;
    }

    private String formatAddress(ShippingAddress address) {
        return address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
