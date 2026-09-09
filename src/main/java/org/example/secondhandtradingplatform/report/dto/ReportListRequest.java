package org.example.secondhandtradingplatform.report.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ReportListRequest {

    private Integer status;

    private String targetType;

    @Min(value = 1, message = "不能小于1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "不能小于1")
    private Integer pageSize = 10;
}
