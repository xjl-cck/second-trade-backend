package org.example.secondhandtradingplatform.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.favorite.entity.Favorite;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
