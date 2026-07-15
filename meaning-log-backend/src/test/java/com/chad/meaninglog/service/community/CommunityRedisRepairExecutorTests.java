package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.CommunityRedisRepair;
import com.chad.meaninglog.repository.CommunityRedisRepairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityRedisRepairExecutorTests {

    private CommunityRedisRepairRepository repository;
    private CommunityRedisRebuilder rebuilder;
    private CommunityRedisRepairExecutor executor;
    private CommunityRedisRepair repair;

    @BeforeEach
    void setUp() {
        repository = mock(CommunityRedisRepairRepository.class);
        rebuilder = mock(CommunityRedisRebuilder.class);
        executor = new CommunityRedisRepairExecutor(repository, rebuilder,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
        repair = new CommunityRedisRepair();
        repair.setId(1L);
        repair.setRepairType(CommunityRedisRepair.Type.POST_STATE.name());
        repair.setAggregateId(100L);
        when(repository.selectById(1L)).thenReturn(repair);
    }

    @Test
    void redisFailureKeepsQueueForRetry() {
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(rebuilder).rebuild(repair);

        executor.processById(1L);

        verify(repository).recordFailure(eq(1L), any(), any());
        verify(repository, never()).deleteById((Serializable) 1L);
    }

    @Test
    void successfulRetryAcknowledgesQueueRow() {
        doNothing().when(rebuilder).rebuild(repair);

        executor.processById(1L);

        verify(repository).deleteById((Serializable) 1L);
    }
}
