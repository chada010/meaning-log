package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AiRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${ai.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${ai.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public void check(UserAccount user) {
        String key = "ai:rate-limit:user:" + user.getId();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > maxRequests) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI requests are too frequent. Please try again later."
                );
            }
        } catch (RedisConnectionFailureException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务暂时不可用，日志仍可正常记录",
                    ex
            );
        }
    }
}
