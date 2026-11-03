package org.example.secondhandtradingplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;

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
}
