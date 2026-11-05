package org.example.secondhandtradingplatform.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.product.dto.BrowseCountIncrement;
import org.example.secondhandtradingplatform.product.entity.Product;

import java.time.LocalDateTime;
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

    @Update("update product set favorite_count = #{count} where id = #{productId} and is_deleted = 0")
    int syncFavoriteCount(@Param("productId") Long productId, @Param("count") long count);

    /**
     * 按 (update_time, id) 游标增量拉取未被逻辑删除的商品，供 ES 索引定时对账使用。
     * 直接用 SQL 绕开 {@code @TableLogic}，因为这里要按 update_time 走范围扫描而不是按主键查。
     */
    @Select("""
            select * from product
            where is_deleted = 0
              and update_time >= #{since}
              and update_time < #{until}
              and (update_time > #{afterTime} or (update_time = #{afterTime} and id > #{afterId}))
            order by update_time, id
            limit #{limit}
            """)
    List<Product> selectChangedAfter(@Param("since") LocalDateTime since,
                                     @Param("until") LocalDateTime until,
                                     @Param("afterTime") LocalDateTime afterTime,
                                     @Param("afterId") Long afterId,
                                     @Param("limit") int limit);

    /**
     * 同上，但只取已被逻辑删除的商品，用于把 ES 里残留的文档删掉。
     */
    @Select("""
            select id, update_time from product
            where is_deleted = 1
              and update_time >= #{since}
              and update_time < #{until}
              and (update_time > #{afterTime} or (update_time = #{afterTime} and id > #{afterId}))
            order by update_time, id
            limit #{limit}
            """)
    List<Product> selectDeletedAfter(@Param("since") LocalDateTime since,
                                     @Param("until") LocalDateTime until,
                                     @Param("afterTime") LocalDateTime afterTime,
                                     @Param("afterId") Long afterId,
                                     @Param("limit") int limit);
}
