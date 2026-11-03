package org.example.secondhandtradingplatform.payment.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.payment.entity.PaymentRefund;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRefundResponse {

    private Long id;
    private Long returnRequestId;
    private String orderNo;
    private String outRequestNo;
    private String tradeNo;
    private BigDecimal amount;
    private BigDecimal refundFee;
    private String fundChange;
    private Integer status;
    private String statusText;
    private String error;
    private LocalDateTime refundTime;

    public static PaymentRefundResponse from(PaymentRefund refund) {
        PaymentRefundResponse response = new PaymentRefundResponse();
        response.setId(refund.getId());
        response.setReturnRequestId(refund.getReturnRequestId());
        response.setOrderNo(refund.getOrderNo());
        response.setOutRequestNo(refund.getOutRequestNo());
        response.setTradeNo(refund.getTradeNo());
        response.setAmount(refund.getAmount());
        response.setRefundFee(refund.getRefundFee());
        response.setFundChange(refund.getFundChange());
        response.setStatus(refund.getStatus());
        response.setStatusText(statusText(refund.getStatus()));
        response.setError(refund.getError());
        response.setRefundTime(refund.getRefundTime());
        return response;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待退款";
            case 1 -> "退款成功";
            case 2 -> "退款失败";
            default -> "未知";
        };
    }
}
