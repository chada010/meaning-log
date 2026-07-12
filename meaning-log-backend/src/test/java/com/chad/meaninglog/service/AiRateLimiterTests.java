package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimiterTests {

    @Test
    void firstRequestSetsWindowExpiration() {
        StringRedisTemplate redisTemplate = mockRedisTemplateReturning("ai:rate-limit:user:42", 1L);
        AiRateLimiter limiter = limiter(redisTemplate);

        limiter.check(user());

        verify(redisTemplate).expire("ai:rate-limit:user:42", Duration.ofSeconds(60));
    }

    @Test
    void sixthRequestInWindowIsRejected() {
        AiRateLimiter limiter = limiter(mockRedisTemplateReturning("ai:rate-limit:user:42", 6L));

        assertThatThrownBy(() -> limiter.check(user()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @Test
    void trialRequestsUseIndependentTrialLimitAndKey() {
        String trialKey = "ai:rate-limit:trial:203.0.113.7";
        StringRedisTemplate redisTemplate = mockRedisTemplateReturning(trialKey, 4L);
        AiRateLimiter limiter = limiter(redisTemplate);

        assertThatThrownBy(() -> limiter.checkTrial("203.0.113.7"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate mockRedisTemplateReturning(String key, Long count) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(count);
        return redisTemplate;
    }

    private AiRateLimiter limiter(StringRedisTemplate redisTemplate) {
        AiRateLimiter limiter = new AiRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "maxRequests", 5);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60L);
        ReflectionTestUtils.setField(limiter, "trialMaxRequests", 3);
        ReflectionTestUtils.setField(limiter, "trialWindowSeconds", 300L);
        return limiter;
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(42L);
        return user;
    }
}
