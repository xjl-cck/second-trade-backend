package org.example.secondhandtradingplatform.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportCreateRequest {

    @NotBlank(message = "举报对象类型不能为空")
    @Pattern(regexp = "product|user|message", message = "举报对象类型只能为product、user、message")
    private String targetType;

    @NotNull(message = "举报对象不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 128, message = "举报原因不能超过128个字符")
    private String reason;

    @Size(max = 500, message = "举报详情不能超过500个字符")
    private String detail;

    @Size(max = 1000, message = "证据链接不能超过1000个字符")
    private String evidenceUrls;
}
