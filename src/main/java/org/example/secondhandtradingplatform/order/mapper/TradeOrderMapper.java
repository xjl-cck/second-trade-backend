package org.example.secondhandtradingplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;

import java.time.LocalDateTime;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Select("select * from trade_order where buyer_id = #{buyerId} and idempotency_key = #{idempotencyKey} and is_deleted = 0 limit 1")
    TradeOrder selectByBuyerIdAndIdempotencyKey(@Param("buyerId") Long buyerId,
                                                @Param("idempotencyKey") String idempotencyKey);

    @Select("select * from trade_order where buyer_id = #{buyerId} and idempotency_key = #{idempotencyKey} and is_deleted = 0 limit 1 for update")
    TradeOrder selectByBuyerIdAndIdempotencyKeyForUpdate(@Param("buyerId") Long buyerId,
                                                         @Param("idempotencyKey") String idempotencyKey);

    @Select("select * from trade_order where id = #{orderId} and is_deleted = 0 for update")
    TradeOrder selectByIdForUpdate(@Param("orderId") Long orderId);

    @Select("select * from trade_order where order_no = #{orderNo} and is_deleted = 0 limit 1")
    TradeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("select * from trade_order where order_no = #{orderNo} and is_deleted = 0 limit 1 for update")
    TradeOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Update("update trade_order set status = #{canceledStatus}, close_reason = #{closeReason} "
            + "where id = #{orderId} and status = #{expectedStatus} and is_deleted = 0")
    int cancelIfStatus(@Param("orderId") Long orderId,
                       @Param("expectedStatus") Integer expectedStatus,
                       @Param("canceledStatus") Integer canceledStatus,
                       @Param("closeReason") String closeReason);

    @Update("update trade_order set status = #{closingStatus}, update_time = current_timestamp "
            + "where id = #{orderId} and status = #{waitingStatus} and create_time < #{deadline} and is_deleted = 0")
    int markClosingIfExpired(@Param("orderId") Long orderId,
                             @Param("waitingStatus") Integer waitingStatus,
                             @Param("closingStatus") Integer closingStatus,
                             @Param("deadline") LocalDateTime deadline);

    @Update("update trade_order set update_time = current_timestamp "
            + "where id = #{orderId} and status = #{closingStatus} and update_time < #{staleBefore} and is_deleted = 0")
    int claimClosingIfStale(@Param("orderId") Long orderId,
                            @Param("closingStatus") Integer closingStatus,
                            @Param("staleBefore") LocalDateTime staleBefore);
}
