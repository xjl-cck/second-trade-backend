package org.example.secondhandtradingplatform.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.product.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("update product set favorite_count = case when favorite_count + #{delta} < 0 then 0 else favorite_count + #{delta} end where id = #{productId}")
    int adjustFavoriteCount(@Param("productId") Long productId, @Param("delta") int delta);
}
