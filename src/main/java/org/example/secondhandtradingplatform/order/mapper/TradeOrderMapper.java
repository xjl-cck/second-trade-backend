package org.example.secondhandtradingplatform.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}
