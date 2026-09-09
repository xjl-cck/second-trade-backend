package org.example.secondhandtradingplatform.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportHandleRequest {

    @NotNull(message = "处理状态不能为空")
    @Min(value = 1, message = "处理状态只能为1、2、3")
    @Max(value = 3, message = "处理状态只能为1、2、3")
    private Integer status;

    @NotBlank(message = "处理结果不能为空")
    @Size(max = 500, message = "处理结果不能超过500个字符")
    private String handleResult;
}
