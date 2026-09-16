package org.example.secondhandtradingplatform.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressSaveRequest {

    @NotBlank(message = "收件人不能为空")
    @Size(max = 32, message = "收件人不能超过32个字符")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "联系电话格式不正确")
    private String receiverPhone;

    @NotBlank(message = "省份不能为空")
    @Size(max = 32, message = "省份不能超过32个字符")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 32, message = "城市不能超过32个字符")
    private String city;

    @NotBlank(message = "区县不能为空")
    @Size(max = 32, message = "区县不能超过32个字符")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 128, message = "详细地址不能超过128个字符")
    private String detail;

    private Boolean isDefault;
}
