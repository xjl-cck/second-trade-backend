package org.example.secondhandtradingplatform.review.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ReviewListRequest {

    @Min(value = 1, message = "不能小于1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "不能小于1")
    private Integer pageSize = 10;
}
