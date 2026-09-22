package org.example.secondhandtradingplatform.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.secondhandtradingplatform.favorite.entity.Favorite;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    @Select("select * from favorite where user_id = #{userId} and product_id = #{productId} order by is_deleted asc, id desc limit 1")
    Favorite selectIncludingDeleted(@Param("userId") Long userId, @Param("productId") Long productId);

    @Update("update favorite set is_deleted = #{isDeleted} where id = #{id}")
    int updateDeleted(@Param("id") Long id, @Param("isDeleted") int isDeleted);
}
