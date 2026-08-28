package org.example.secondhandtradingplatform.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_review")
public class OrderReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long reviewerId;
    private Long revieweeId;
    private Integer rating;
    private String content;
    private Integer reviewType;
    private Long parentId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
