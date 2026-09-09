package org.example.secondhandtradingplatform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminProductAuditRequest {

    @NotNull(message = "审核状态不能为空")
    @Min(value = 2, message = "审核状态只能为2或3")
    @Max(value = 3, message = "审核状态只能为2或3")
    private Integer status;

    @Size(max = 255, message = "审核原因不能超过255个字符")
    private String auditReason;
}
