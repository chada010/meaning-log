package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${ai.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${ai.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${ai.trial-rate-limit.max-requests:3}")
    private int trialMaxRequests;

    @Value("${ai.trial-rate-limit.window-seconds:300}")
    private long trialWindowSeconds;

    public void check(UserAccount user) {
        check("ai:rate-limit:user:" + user.getId(), maxRequests, windowSeconds);
    }

    public void checkTrial(String ip) {
        check("ai:rate-limit:trial:" + ip, trialMaxRequests, trialWindowSeconds);
    }

    private void check(String key, int limit, long window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(window));
            }
            if (count != null && count > limit) {
                log.warn("AI rate limit hit: key={}, count={}, limit={}, window={}s", key, count, limit, window);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI requests are too frequent. Please try again later."
                );
            }
        } catch (RedisConnectionFailureException ex) {
            log.error("AI rate limiter Redis unavailable: key={}", key, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务暂时不可用，日志仍可正常记录",
                    ex
            );
        }
    }
}
