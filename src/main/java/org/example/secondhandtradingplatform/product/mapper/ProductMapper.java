package org.example.secondhandtradingplatform.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.product.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("select * from product where id = #{productId} and is_deleted = 0 for update")
    Product selectByIdForUpdate(@Param("productId") Long productId);

    @Update("update product set browse_count = browse_count + 1 where id = #{productId} and is_deleted = 0")
    int incrementBrowseCount(@Param("productId") Long productId);

    @Update("update product set favorite_count = case when favorite_count + #{delta} < 0 then 0 else favorite_count + #{delta} end where id = #{productId}")
    int adjustFavoriteCount(@Param("productId") Long productId, @Param("delta") int delta);
}
