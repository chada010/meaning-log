package com.chad.meaninglog.security;

import com.chad.meaninglog.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(validateSecret(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        VerifiedToken verifiedToken = parseToken(token);
        return verifiedToken == null ? null : verifiedToken.email();
    }

    public boolean isValid(String token, UserAccount user) {
        return isValid(parseToken(token), user);
    }

    public boolean isValid(VerifiedToken token, UserAccount user) {
        return token != null
                && user != null
                && token.email().equals(user.getEmail())
                && token.tokenVersion() == user.getTokenVersion();
    }

    public boolean hasValidSignature(String token) {
        return parseToken(token) != null;
    }

    public VerifiedToken parseToken(String token) {
        try {
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(signedClaims.getHeader().getAlgorithm())) {
                return null;
            }
            Claims claims = signedClaims.getPayload();
            Object tokenVersion = claims.get("tokenVersion");
            if (claims.getSubject() == null
                    || claims.getExpiration() == null
                    || !(tokenVersion instanceof Number number)) {
                return null;
            }
            return new VerifiedToken(claims.getSubject(), number.intValue());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private byte[] validateSecret(String configuredSecret) {
        String normalizedSecret = configuredSecret == null ? "" : configuredSecret.trim();
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(normalizedSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT secret must be Base64 encoded");
        }
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must decode to at least 32 bytes");
        }
        if (containsOnlyRepeatedBytes(secretBytes)) {
            throw new IllegalArgumentException("JWT secret must not contain only repeated bytes");
        }
        return secretBytes;
    }

    private boolean containsOnlyRepeatedBytes(byte[] secretBytes) {
        byte firstByte = secretBytes[0];
        for (int index = 1; index < secretBytes.length; index++) {
            if (secretBytes[index] != firstByte) {
                return false;
            }
        }
        return true;
    }

    public record VerifiedToken(String email, int tokenVersion) {
    }
}
