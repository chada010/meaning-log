package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.CommunityRedisRepair;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface CommunityRedisRepairRepository extends BaseMapper<CommunityRedisRepair> {

    default List<CommunityRedisRepair> findPending(int limit) {
        return selectList(new LambdaQueryWrapper<CommunityRedisRepair>()
                .orderByAsc(CommunityRedisRepair::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Update("UPDATE community_redis_repairs "
            + "SET attempt_count = attempt_count + 1, last_error = #{error}, updated_at = #{now} "
            + "WHERE id = #{id}")
    int recordFailure(@Param("id") Long id,
                      @Param("error") String error,
                      @Param("now") LocalDateTime now);
}
