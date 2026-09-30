package org.example.secondhandtradingplatform.order.returnrequest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.secondhandtradingplatform.order.returnrequest.entity.ReturnRequest;

@Mapper
public interface ReturnRequestMapper extends BaseMapper<ReturnRequest> {

    @Select("select * from return_request where order_id = #{orderId} and is_deleted = 0 limit 1")
    ReturnRequest selectByOrderId(@Param("orderId") Long orderId);

    @Select("select * from return_request where id = #{id} and is_deleted = 0 for update")
    ReturnRequest selectByIdForUpdate(@Param("id") Long id);
}
