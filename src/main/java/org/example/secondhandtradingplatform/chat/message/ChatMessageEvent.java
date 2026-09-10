package org.example.secondhandtradingplatform.chat.message;

import lombok.Data;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;

import java.io.Serializable;

@Data
public class ChatMessageEvent implements Serializable {

    private ChatMessageResponse message;
}
