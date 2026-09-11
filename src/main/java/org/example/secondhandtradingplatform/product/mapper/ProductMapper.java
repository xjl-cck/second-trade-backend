package org.example.secondhandtradingplatform.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.product.dto.BrowseCountIncrement;
import org.example.secondhandtradingplatform.product.entity.Product;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("select * from product where id = #{productId} and is_deleted = 0 for update")
    Product selectByIdForUpdate(@Param("productId") Long productId);

    @Update("""
            <script>
            update product
            set browse_count = browse_count + case id
            <foreach collection="increments" item="item">
                when #{item.productId} then #{item.delta}
            </foreach>
            end
            where id in
            <foreach collection="increments" item="item" open="(" separator="," close=")">
                #{item.productId}
            </foreach>
            and is_deleted = 0
            </script>
            """)
    int batchIncrementBrowseCount(@Param("increments") List<BrowseCountIncrement> increments);

    @Update("update product set favorite_count = case when favorite_count + #{delta} < 0 then 0 else favorite_count + #{delta} end where id = #{productId}")
    int adjustFavoriteCount(@Param("productId") Long productId, @Param("delta") int delta);
}
