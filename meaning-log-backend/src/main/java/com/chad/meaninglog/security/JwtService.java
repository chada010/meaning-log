package com.chad.meaninglog.security;

import com.chad.meaninglog.entity.UserAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationMs;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserAccount user) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getEmail());
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("tokenVersion", user.getTokenVersion());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusMillis(expirationMs).getEpochSecond());

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        String signingInput = headerPart + "." + payloadPart;
        return signingInput + "." + sign(signingInput);
    }

    public String extractEmail(String token) {
        Map<String, Object> payload = readPayload(token);
        Object subject = payload.get("sub");
        return subject instanceof String email ? email : null;
    }

    public boolean isValid(String token, UserAccount user) {
        String email = extractEmail(token);
        return email != null
                && email.equals(user.getEmail())
                && hasCurrentTokenVersion(token, user)
                && !isExpired(token)
                && hasValidSignature(token);
    }

    private boolean hasCurrentTokenVersion(String token, UserAccount user) {
        Object tokenVersion = readPayload(token).get("tokenVersion");
        return tokenVersion instanceof Number number && number.intValue() == user.getTokenVersion();
    }

    private boolean isExpired(String token) {
        Map<String, Object> payload = readPayload(token);
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number)) {
            return true;
        }
        return Instant.now().getEpochSecond() >= number.longValue();
    }

    private boolean hasValidSignature(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        String expected = sign(parts[0] + "." + parts[1]);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> readPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Map.of();
            }
            byte[] payload = URL_DECODER.decode(parts[1]);
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create JWT", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }
}
