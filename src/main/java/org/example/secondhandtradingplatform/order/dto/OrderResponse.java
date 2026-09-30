package org.example.secondhandtradingplatform.order.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderResponse {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private String buyerNickname;
    private Long sellerId;
    private String sellerNickname;
    private Long productId;
    private Integer quantity;
    private String productTitle;
    private String productCoverImage;
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal amount;
    private Integer status;
    private String statusText;
    private String closeReason;
    private LocalDateTime dealTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createTime;
    private ReturnRequestResponse returnRequest;

    public static OrderResponse from(TradeOrder order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setBuyerId(order.getBuyerId());
        response.setSellerId(order.getSellerId());
        response.setProductId(order.getProductId());
        response.setQuantity(order.getQuantity() == null ? 1 : order.getQuantity());
        response.setAddressId(order.getAddressId());
        response.setReceiverName(order.getReceiverName());
        response.setReceiverPhone(order.getReceiverPhone());
        response.setReceiverAddress(order.getReceiverAddress());
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus());
        response.setStatusText(statusText(order.getStatus()));
        response.setCloseReason(order.getCloseReason());
        response.setDealTime(order.getDealTime());
        response.setFinishedTime(order.getFinishedTime());
        response.setCreateTime(order.getCreateTime());
        return response;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待确认";
            case 1 -> "已成交";
            case 2 -> "已取消";
            case 3 -> "已完成";
            default -> "未知";
        };
    }
}
