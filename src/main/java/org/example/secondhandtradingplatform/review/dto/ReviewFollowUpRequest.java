package org.example.secondhandtradingplatform.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewFollowUpRequest {

    @Min(value = 1, message = "评分不能低于1")
    @Max(value = 5, message = "评分不能高于5")
    private Integer rating;

    @Size(max = 500, message = "追评内容不能超过500个字符")
    private String content;
}
