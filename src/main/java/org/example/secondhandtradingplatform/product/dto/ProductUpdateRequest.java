package org.example.secondhandtradingplatform.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {

    private Long campusId;
    private Long categoryId;

    @Size(max = 128, message = "不能超过128个字符")
    private String title;

    @DecimalMin(value = "0.01", message = "必须大于0")
    private BigDecimal price;

    @Min(value = 1, message = "库存至少为1")
    private Integer stock;

    @DecimalMin(value = "0.01", message = "必须大于0")
    private BigDecimal originalPrice;

    @Size(max = 2000, message = "不能超过2000个字符")
    private String description;

    private String coverImage;
    private Integer exchangeType;
    private Integer deliveryMethod;

    @Size(max = 128, message = "不能超过128个字符")
    private String location;

    @Size(max = 20, message = "不能超过20个字符")
    private String contactPhone;
}
