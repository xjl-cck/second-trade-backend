package org.example.secondhandtradingplatform.order.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCloseRequestedEvent {

    private Long orderId;
}
