package org.example.secondhandtradingplatform.order.returnrequest.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.order.returnrequest.entity.ReturnRequest;

import java.time.LocalDateTime;

@Data
public class ReturnRequestResponse {

    private Long id;
    private Long orderId;
    private String reason;
    private String description;
    private Integer status;
    private String statusText;
    private String sellerRemark;
    private LocalDateTime approvedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;

    public static ReturnRequestResponse from(ReturnRequest request) {
        ReturnRequestResponse response = new ReturnRequestResponse();
        response.setId(request.getId());
        response.setOrderId(request.getOrderId());
        response.setReason(request.getReason());
        response.setDescription(request.getDescription());
        response.setStatus(request.getStatus());
        response.setStatusText(statusText(request.getStatus()));
        response.setSellerRemark(request.getSellerRemark());
        response.setApprovedTime(request.getApprovedTime());
        response.setCompletedTime(request.getCompletedTime());
        response.setCreateTime(request.getCreateTime());
        return response;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待卖家审核";
            case 1 -> "退货中";
            case 2 -> "已驳回";
            case 3 -> "退款完成";
            default -> "未知";
        };
    }
}
