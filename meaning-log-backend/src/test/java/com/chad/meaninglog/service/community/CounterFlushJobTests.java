package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.CommunityCounterBatchRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.job.CounterFlushJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterFlushJobTests {

    private CommunityRedisService redis;
    private CommunityRedisBatchService redisBatch;
    private PublicLogRepository publicLogRepository;
    private CommunityCounterBatchRepository counterBatchRepository;
    private CounterFlushJob job;

    @BeforeEach
    void setUp() {
        redis = mock(CommunityRedisService.class);
        redisBatch = mock(CommunityRedisBatchService.class);
        publicLogRepository = mock(PublicLogRepository.class);
        counterBatchRepository = mock(CommunityCounterBatchRepository.class);
        job = new CounterFlushJob(redis, redisBatch, publicLogRepository,
                counterBatchRepository,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void databaseFailureRequeuesAndNextRoundCanAck() {
        List<Long> ids = List.of(1L, 2L);
        when(redis.claimDirty(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of("1", "2"), List.of("1", "2"));
        when(publicLogRepository.selectByIds(ids)).thenReturn(posts(ids));
        when(redisBatch.batchGetCounts(ids)).thenReturn(counts(ids));
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .doReturn(2)
                .when(counterBatchRepository).updateViews(anyList());

        job.flush();
        job.flush();

        verify(redis).retryDirty(ids);
        verify(redis).ackDirty(ids);
        verify(counterBatchRepository, times(2)).updateViews(anyList());
    }

    @Test
    void oneHundredDirtyIdsUseBoundedBatchCallsInsteadOfNPlusOne() {
        List<Long> ids = LongStream.rangeClosed(1, 100).boxed().toList();
        when(redis.claimDirty(anyInt(), anyLong(), anyLong()))
                .thenReturn(ids.stream().map(String::valueOf).toList());
        when(publicLogRepository.selectByIds(ids)).thenReturn(posts(ids));
        when(redisBatch.batchGetCounts(ids)).thenReturn(counts(ids));

        job.flush();

        verify(publicLogRepository, times(1)).selectByIds(ids);
        verify(redisBatch, times(1)).batchGetCounts(ids);
        verify(counterBatchRepository, times(1)).updateViews(anyList());
        verify(redis, times(1)).ackDirty(ids);
    }

    private List<PublicLog> posts(List<Long> ids) {
        return ids.stream().map(id -> {
            PublicLog post = new PublicLog();
            post.setId(id);
            post.setViewCount(id);
            return post;
        }).toList();
    }

    private Map<Long, CommunityRedisBatchService.CommunityCounts> counts(List<Long> ids) {
        Map<Long, CommunityRedisBatchService.CommunityCounts> result = new LinkedHashMap<>();
        for (Long id : ids) {
            result.put(id, new CommunityRedisBatchService.CommunityCounts(0L, 0L, id + 1));
        }
        return result;
    }
}
