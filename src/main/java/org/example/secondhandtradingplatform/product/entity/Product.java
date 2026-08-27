package org.example.secondhandtradingplatform.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sellerId;
    private Long campusId;
    private Long categoryId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String description;
    private String coverImage;
    private Integer exchangeType;
    private Integer deliveryMethod;
    private String location;
    private Integer status;
    private Integer browseCount;
    private Integer favoriteCount;
    private String contactPhone;
    private String auditReason;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
