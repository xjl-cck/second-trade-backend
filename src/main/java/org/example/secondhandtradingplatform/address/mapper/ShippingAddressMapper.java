package org.example.secondhandtradingplatform.address.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.example.secondhandtradingplatform.address.entity.ShippingAddress;

@Mapper
public interface ShippingAddressMapper extends BaseMapper<ShippingAddress> {

    @Update("update shipping_address set is_default = 0 where user_id = #{userId} and is_deleted = 0")
    void clearDefaultByUserId(Long userId);
}
