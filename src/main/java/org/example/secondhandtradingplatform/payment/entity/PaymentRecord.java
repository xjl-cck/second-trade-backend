package org.example.secondhandtradingplatform.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水。一次订单支付对应一条记录，out_trade_no 与订单号一致以保证幂等。
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private String outTradeNo;
    private String tradeNo;
    private String channel;
    private String payMethod;
    private BigDecimal amount;
    private Long buyerId;
    private Integer status;
    private LocalDateTime paidTime;
    private LocalDateTime notifyTime;
    private String lastError;
    private String rawNotify;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
