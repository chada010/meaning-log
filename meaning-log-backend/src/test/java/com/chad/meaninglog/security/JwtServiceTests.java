package com.chad.meaninglog.security;

import com.chad.meaninglog.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {

    private static final long EXPIRATION_MS = 60_000;
    private static final long LEGACY_IAT_EPOCH_SECONDS = 1_704_067_200L;
    private static final long LEGACY_EXP_EPOCH_SECONDS = 4_102_444_800L;
    private static final String LEGACY_PAYLOAD = "{\"sub\":\"alice@example.com\",\"tokenVersion\":3,"
            + "\"iat\":1704067200,\"exp\":4102444800}";

    @Test
    void generatedTokenCanBeParsedAndValidated() {
        UserAccount user = user();
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
        assertThat(jwtService.hasValidSignature(token)).isTrue();
        assertThat(jwtService.isValid(token, user)).isTrue();
    }

    @Test
    void generatedTokenContainsAllExpectedClaims() {
        UserAccount user = user();
        String secret = encodedSecret(1);
        String token = jwtService(secret, EXPIRATION_MS).generateToken(user);

        Jws<Claims> parsedToken = parse(token, secret);
        Claims claims = parsedToken.getPayload();

        assertThat(parsedToken.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.keySet())
                .containsExactlyInAnyOrder("sub", "userId", "username", "tokenVersion", "iat", "exp");
        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(user.getId());
        assertThat(claims.get("username")).isEqualTo(user.getUsername());
        assertThat(((Number) claims.get("tokenVersion")).intValue()).isEqualTo(user.getTokenVersion());
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(EXPIRATION_MS);
    }

    @Test
    void tamperedTokenIsRejected() {
        UserAccount user = user();
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + encode("{\"sub\":\"attacker@example.com\"}") + "." + parts[2];

        assertThat(jwtService.hasValidSignature(tamperedToken)).isFalse();
        assertThat(jwtService.isValid(tamperedToken, user)).isFalse();
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);

        assertThat(jwtService.hasValidSignature("not-a-jwt")).isFalse();
        assertThat(jwtService.extractEmail("not-a-jwt")).isNull();
        assertThat(jwtService.isValid("not-a-jwt", user())).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        UserAccount user = user();
        JwtService jwtService = jwtService(encodedSecret(1), -1_000);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token, user)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        UserAccount user = user();
        String token = jwtService(encodedSecret(1), EXPIRATION_MS).generateToken(user);
        JwtService anotherJwtService = jwtService(encodedSecret(33), EXPIRATION_MS);

        assertThat(anotherJwtService.hasValidSignature(token)).isFalse();
        assertThat(anotherJwtService.isValid(token, user)).isFalse();
    }

    @Test
    void tokenVersionChangeInvalidatesAnExistingToken() {
        UserAccount user = user();
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        String token = jwtService.generateToken(user);

        user.setTokenVersion(user.getTokenVersion() + 1);

        assertThat(jwtService.isValid(token, user)).isFalse();
    }

    @Test
    void tokenWithoutExpirationIsRejected() {
        String secret = encodedSecret(1);
        UserAccount user = user();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("tokenVersion", user.getTokenVersion())
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService(secret, EXPIRATION_MS).isValid(token, user)).isFalse();
    }

    @Test
    void tokenFromLegacyManualHs256ImplementationRemainsValid() {
        String secret = encodedSecret(1);
        String token = legacyHs256Token(secret, LEGACY_PAYLOAD);
        JwtService jwtService = jwtService(secret, EXPIRATION_MS);

        Claims claims = parse(token, secret).getPayload();
        JwtService.VerifiedToken verifiedToken = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("alice@example.com");
        assertThat(((Number) claims.get("tokenVersion")).intValue()).isEqualTo(3);
        assertThat(claims.getIssuedAt().toInstant().getEpochSecond()).isEqualTo(LEGACY_IAT_EPOCH_SECONDS);
        assertThat(claims.getExpiration().toInstant().getEpochSecond()).isEqualTo(LEGACY_EXP_EPOCH_SECONDS);
        assertThat(verifiedToken).isEqualTo(new JwtService.VerifiedToken("alice@example.com", 3));
        assertThat(jwtService.isValid(token, user())).isTrue();
    }

    @Test
    void tokenSignedWithAnotherAlgorithmIsRejected() {
        String secret = encodedSecret(1, 48);
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        Instant now = Instant.now();
        UserAccount user = user();
        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(EXPIRATION_MS)))
                .signWith(key, Jwts.SIG.HS384)
                .compact();
        JwtService jwtService = jwtService(secret, EXPIRATION_MS);

        assertThat(jwtService.parseToken(token)).isNull();
        assertThat(jwtService.isValid(token, user)).isFalse();
    }

    private JwtService jwtService(String secret, long expirationMs) {
        return new JwtService(secret, expirationMs);
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        user.setTokenVersion(3);
        return user;
    }

    private Jws<Claims> parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodedSecret(int firstByte) {
        return encodedSecret(firstByte, 32);
    }

    private String encodedSecret(int firstByte, int length) {
        byte[] secret = new byte[length];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) (firstByte + index);
        }
        return Base64.getEncoder().encodeToString(secret);
    }

    private String legacyHs256Token(String secret, String payload) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String signingInput = header + "." + encode(payload);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (GeneralSecurityException ex) {
            throw new AssertionError("Failed to create legacy JWT fixture", ex);
        }
    }
}
