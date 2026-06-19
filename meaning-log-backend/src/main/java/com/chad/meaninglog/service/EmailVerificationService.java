package com.chad.meaninglog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "email:verify:code:";
    private static final String COOLDOWN_KEY_PREFIX = "email:verify:cooldown:";

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${email.verification.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    @Value("${email.verification.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    /**
     * 生成并发送 6 位验证码；同一邮箱 60 秒内只能发一次。
     */
    public void sendCode(String email) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请稍后再试，发送太频繁");
        }

        String code = generateCode();
        sendEmail(email, code);

        // 邮件发送成功后再写入 Redis，避免发送失败时 cooldown 锁住用户
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, Duration.ofSeconds(codeTtlSeconds));
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
    }

    /**
     * 校验验证码；正确后立即删除，防止重复使用。
     */
    public void verifyCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新发送");
        }
        if (!stored.equals(code.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不正确");
        }
        redisTemplate.delete(CODE_KEY_PREFIX + email);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
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
