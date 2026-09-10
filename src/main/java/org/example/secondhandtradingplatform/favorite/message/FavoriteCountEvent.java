package org.example.secondhandtradingplatform.favorite.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class FavoriteCountEvent implements Serializable {

    private Long productId;
    private int delta;
}
