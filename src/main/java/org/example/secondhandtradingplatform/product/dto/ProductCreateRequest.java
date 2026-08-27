package org.example.secondhandtradingplatform.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductCreateRequest {

    @NotNull(message = "不能为空")
    private Long campusId;

    @NotNull(message = "不能为空")
    private Long categoryId;

    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "不能超过128个字符")
    private String title;

    @NotNull(message = "不能为空")
    @DecimalMin(value = "0.01", message = "必须大于0")
    private BigDecimal price;

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
