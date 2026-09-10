package org.example.secondhandtradingplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Select("select * from trade_order where id = #{orderId} and is_deleted = 0 for update")
    TradeOrder selectByIdForUpdate(@Param("orderId") Long orderId);
}
