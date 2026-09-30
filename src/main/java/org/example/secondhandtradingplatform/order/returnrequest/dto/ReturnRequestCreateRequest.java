package org.example.secondhandtradingplatform.order.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReturnRequestCreateRequest {

    @NotBlank(message = "退货原因不能为空")
    @Size(max = 255, message = "退货原因不能超过255个字符")
    private String reason;

    @Size(max = 1000, message = "退货说明不能超过1000个字符")
    private String description;
}
