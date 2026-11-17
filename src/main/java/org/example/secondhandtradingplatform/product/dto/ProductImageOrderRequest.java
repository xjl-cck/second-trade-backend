package org.example.secondhandtradingplatform.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductImageOrderRequest {

    /**
     * 按目标顺序排列的图片 ID，必须与该商品现有图片完全一致。
     */
    @NotEmpty(message = "图片顺序不能为空")
    @Size(max = 9, message = "最多只能有 9 张图片")
    private List<Long> imageIds;
}
