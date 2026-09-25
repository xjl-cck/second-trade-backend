package org.example.secondhandtradingplatform.system.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SystemNotificationEvent implements Serializable {

    private Long recipientId;
    private String title;
    private String content;
    private String relatedType;
    private Long relatedId;
    private LocalDateTime eventTime;
}
