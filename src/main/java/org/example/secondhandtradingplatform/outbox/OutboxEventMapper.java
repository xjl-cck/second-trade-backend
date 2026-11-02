package org.example.secondhandtradingplatform.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("select * from outbox_event where status = 0 and next_attempt_at <= #{now} order by id limit #{limit}")
    List<OutboxEvent> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 抢占待投递事件：多实例同时扫描时只有一个实例能拿到（CAS 到“投递中”）。
     */
    @Update("update outbox_event set status = 1, attempts = attempts + 1 where id = #{id} and status = 0")
    int claim(@Param("id") Long id);

    @Update("update outbox_event set status = 2, last_error = null where id = #{id} and status = 1")
    int markSent(@Param("id") Long id);

    @Update("update outbox_event set status = 0, next_attempt_at = #{nextAttemptAt}, last_error = #{lastError} "
            + "where id = #{id} and status = 1")
    int markRetry(@Param("id") Long id,
                  @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                  @Param("lastError") String lastError);

    @Update("update outbox_event set status = 3, last_error = #{lastError} where id = #{id} and status = 1")
    int markFailed(@Param("id") Long id, @Param("lastError") String lastError);

    /**
     * 投递过程中实例宕机会把记录留在“投递中”，超时后放回待投递。
     */
    @Update("update outbox_event set status = 0 where status = 1 and update_time < #{deadline}")
    int recoverStuck(@Param("deadline") LocalDateTime deadline);
}
