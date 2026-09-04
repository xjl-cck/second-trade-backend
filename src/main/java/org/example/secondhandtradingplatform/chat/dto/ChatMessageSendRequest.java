package org.example.secondhandtradingplatform.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageSendRequest {

    @NotNull(message = "会话不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000个字符")
    private String content;

    @NotNull(message = "消息类型不能为空")
    @Min(value = 0, message = "消息类型只能为0、1、2")
    @Max(value = 2, message = "消息类型只能为0、1、2")
    private Integer msgType;
}
