package com.chad.meaninglog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String SOURCE_ATTEMPT_KEY_PREFIX = "auth:login:attempts:source:";
    private static final String PRINCIPAL_SOURCE_ATTEMPT_KEY_PREFIX = "auth:login:attempts:principal-source:";
    private static final DefaultRedisScript<Long> RESERVE_ATTEMPT_SCRIPT = new DefaultRedisScript<>(
            "for index = 1, #KEYS do\n"
                    + "  if tonumber(redis.call('get', KEYS[index]) or '0') >= tonumber(ARGV[index + 1]) then\n"
                    + "    return 0\n"
                    + "  end\n"
                    + "end\n"
                    + "for index = 1, #KEYS do\n"
                    + "  if redis.call('incr', KEYS[index]) == 1 then\n"
                    + "    redis.call('expire', KEYS[index], ARGV[1])\n"
                    + "  end\n"
                    + "end\n"
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${auth.login.attempt-window-seconds:900}")
    private long windowSeconds;

    @Value("${auth.login.max-attempts-per-source:20}")
    private int maxAttemptsPerSource;

    @Value("${auth.login.max-attempts-per-principal-source:5}")
    private int maxAttemptsPerPrincipalSource;

    public void reserveAttempt(String principal, String sourceAddress) {
        Long reserved = redisTemplate.execute(
                RESERVE_ATTEMPT_SCRIPT,
                attemptKeys(principal, sourceAddress),
                String.valueOf(windowSeconds),
                String.valueOf(maxAttemptsPerSource),
                String.valueOf(maxAttemptsPerPrincipalSource)
        );
        if (!Long.valueOf(1).equals(reserved)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试次数过多，请稍后再试");
        }
    }

    public void clearFailures(String principal, String sourceAddress) {
        redisTemplate.delete(attemptKeys(principal, sourceAddress));
    }

    private List<String> attemptKeys(String principal, String sourceAddress) {
        return List.of(
                SOURCE_ATTEMPT_KEY_PREFIX + sourceAddress,
                PRINCIPAL_SOURCE_ATTEMPT_KEY_PREFIX + principal + ":" + sourceAddress
        );
    }
}
