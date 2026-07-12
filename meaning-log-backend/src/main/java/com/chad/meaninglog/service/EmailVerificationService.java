package com.chad.meaninglog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static com.chad.meaninglog.util.EmailNormalizer.normalize;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "email:verify:code:";
    private static final String COOLDOWN_KEY_PREFIX = "email:verify:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX = "email:verify:attempts:";
    private static final String TOTAL_ATTEMPT_KEY_PREFIX = "email:verify:total-attempts:";
    private static final String SEND_SOURCE_ATTEMPT_KEY_PREFIX = "email:verify:send:source:";
    private static final String SEND_GLOBAL_ATTEMPT_KEY = "email:verify:send:global";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT = new DefaultRedisScript<>(
            "local stored = redis.call('get', KEYS[1])\n"
                    + "if not stored then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "local function attemptCount(key, storedCode)\n"
                    + "  local attemptState = redis.call('get', key)\n"
                    + "  if not attemptState then\n"
                    + "    return 0\n"
                    + "  end\n"
                    + "  local separator = string.find(attemptState, ':')\n"
                    + "  if separator and string.sub(attemptState, 1, separator - 1) == storedCode then\n"
                    + "    return tonumber(string.sub(attemptState, separator + 1)) or 0\n"
                    + "  end\n"
                    + "  return 0\n"
                    + "end\n"
                    + "if stored == ARGV[1] then\n"
                    + "  redis.call('del', KEYS[1])\n"
                    + "  redis.call('del', KEYS[2])\n"
                    + "  redis.call('del', KEYS[3])\n"
                    + "  return 1\n"
                    + "end\n"
                    + "local sourceAttempts = attemptCount(KEYS[2], stored)\n"
                    + "local totalAttempts = attemptCount(KEYS[3], stored)\n"
                    + "if sourceAttempts >= tonumber(ARGV[2]) or totalAttempts >= tonumber(ARGV[3]) then\n"
                    + "  return -1\n"
                    + "end\n"
                    + "local updatedSourceAttempts = sourceAttempts + 1\n"
                    + "local updatedTotalAttempts = totalAttempts + 1\n"
                    + "local ttl = redis.call('ttl', KEYS[1])\n"
                    + "if ttl > 0 then\n"
                    + "  redis.call('set', KEYS[2], stored .. ':' .. updatedSourceAttempts, 'EX', ttl)\n"
                    + "  redis.call('set', KEYS[3], stored .. ':' .. updatedTotalAttempts, 'EX', ttl)\n"
                    + "end\n"
                    + "if updatedSourceAttempts >= tonumber(ARGV[2]) or updatedTotalAttempts >= tonumber(ARGV[3]) then\n"
                    + "  return -1\n"
                    + "end\n"
                    + "return 0",
            Long.class
    );
    private static final DefaultRedisScript<Long> RELEASE_COOLDOWN_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n"
                    + "  return redis.call('del', KEYS[1])\n"
                    + "end\n"
                    + "return 0",
            Long.class
    );
    private static final DefaultRedisScript<Long> RESERVE_SEND_ATTEMPT_SCRIPT = new DefaultRedisScript<>(
            "local sourceAttempts = tonumber(redis.call('get', KEYS[1]) or '0')\n"
                    + "local globalAttempts = tonumber(redis.call('get', KEYS[2]) or '0')\n"
                    + "if sourceAttempts >= tonumber(ARGV[2]) or globalAttempts >= tonumber(ARGV[3]) then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "if redis.call('incr', KEYS[1]) == 1 then\n"
                    + "  redis.call('expire', KEYS[1], ARGV[1])\n"
                    + "end\n"
                    + "if redis.call('incr', KEYS[2]) == 1 then\n"
                    + "  redis.call('expire', KEYS[2], ARGV[1])\n"
                    + "end\n"
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${email.verification.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    @Value("${email.verification.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${email.verification.max-attempts:5}")
    private int maxVerificationAttempts;

    @Value("${email.verification.max-total-attempts:20}")
    private int maxTotalVerificationAttempts;

    @Value("${email.verification.send.max-attempts-per-source:5}")
    private int maxSendAttemptsPerSource;

    @Value("${email.verification.send.max-attempts-global:100}")
    private int maxSendAttemptsGlobal;

    @Value("${email.verification.send.window-seconds:60}")
    private long sendAttemptWindowSeconds;

    /**
     * 生成并发送 6 位验证码；同一邮箱 60 秒内只能发一次。
     */
    public void sendCode(String email, String sourceAddress) {
        String normalizedEmail = normalize(email);
        String cooldownKey = COOLDOWN_KEY_PREFIX + normalizedEmail;
        String cooldownToken = generateCooldownToken();
        Boolean cooldownReserved = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                cooldownToken,
                Duration.ofSeconds(cooldownSeconds)
        );
        if (!Boolean.TRUE.equals(cooldownReserved)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请稍后再试，发送太频繁");
        }

        try {
            reserveSendAttempt(sourceAddress);
            String code = generateCode();
            sendEmail(normalizedEmail, code);
            redisTemplate.opsForValue().set(CODE_KEY_PREFIX + normalizedEmail, code, Duration.ofSeconds(codeTtlSeconds));
        } catch (RuntimeException ex) {
            releaseCooldown(cooldownKey, cooldownToken);
            throw ex;
        }
    }

    /**
     * 校验验证码；正确后立即删除，防止重复使用。
     */
    public void verifyCode(String email, String code, String sourceAddress) {
        String normalizedEmail = normalize(email);
        Long consumed = redisTemplate.execute(
                CONSUME_CODE_SCRIPT,
                List.of(
                        CODE_KEY_PREFIX + normalizedEmail,
                        ATTEMPT_KEY_PREFIX + normalizedEmail + ":" + sourceAddress,
                        TOTAL_ATTEMPT_KEY_PREFIX + normalizedEmail
                ),
                code.trim(),
                String.valueOf(maxVerificationAttempts),
                String.valueOf(maxTotalVerificationAttempts)
        );
        if (!Long.valueOf(1).equals(consumed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码无效、已过期或尝试次数过多");
        }
    }

    private String generateCode() {
        int n = RANDOM.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private String generateCooldownToken() {
        byte[] token = new byte[16];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private void reserveSendAttempt(String sourceAddress) {
        Long reserved = redisTemplate.execute(
                RESERVE_SEND_ATTEMPT_SCRIPT,
                List.of(SEND_SOURCE_ATTEMPT_KEY_PREFIX + sourceAddress, SEND_GLOBAL_ATTEMPT_KEY),
                String.valueOf(sendAttemptWindowSeconds),
                String.valueOf(maxSendAttemptsPerSource),
                String.valueOf(maxSendAttemptsGlobal)
        );
        if (!Long.valueOf(1).equals(reserved)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请稍后再试，发送太频繁");
        }
    }

    private void releaseCooldown(String cooldownKey, String cooldownToken) {
        redisTemplate.execute(RELEASE_COOLDOWN_SCRIPT, List.of(cooldownKey), cooldownToken);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("【Meaning Log】邮箱验证码");
        message.setText("你的验证码是：" + code + "\n\n有效期 5 分钟，请勿泄露给他人。");
        mailSender.send(message);
    }
}
