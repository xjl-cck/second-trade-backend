package org.example.secondhandtradingplatform.order.returnrequest.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReturnRequestHandleRequest {

    @Size(max = 255, message = "处理说明不能超过255个字符")
    private String remark;
}
