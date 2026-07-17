package com.chad.meaninglog.security;

import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    private static final long EXPIRATION_MS = 60_000;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticatesTheCurrentUser() throws Exception {
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        UserAccount user = user();
        String token = jwtService.generateToken(user);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        MockHttpServletRequest request = requestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter(jwtService, repository).doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        verify(repository).findByEmail(user.getEmail());
    }

    @ParameterizedTest(name = "{0} token returns 401")
    @MethodSource("invalidTokens")
    void invalidTokenReturnsUnauthorizedWithoutQueryingTheUser(String description, String token) throws Exception {
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter(jwtService, repository).doFilter(requestWithToken(token), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(filterChain.getRequest()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void changedTokenVersionReturnsUnauthorized() throws Exception {
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        UserAccount user = user();
        String token = jwtService.generateToken(user);
        user.setTokenVersion(user.getTokenVersion() + 1);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(jwtService, repository).doFilter(
                requestWithToken(token),
                response,
                new MockFilterChain()
        );

        assertThat(response.getStatus()).isEqualTo(401);
        verify(repository).findByEmail(user.getEmail());
    }

    private static Stream<Arguments> invalidTokens() {
        UserAccount user = user();
        JwtService jwtService = jwtService(encodedSecret(1), EXPIRATION_MS);
        String validToken = jwtService.generateToken(user);
        String expiredToken = jwtService(encodedSecret(1), -1_000).generateToken(user);
        String wrongKeyToken = jwtService(encodedSecret(33), EXPIRATION_MS).generateToken(user);
        return Stream.of(
                Arguments.of("malformed", "not-a-jwt"),
                Arguments.of("tampered", tamperSignature(validToken)),
                Arguments.of("expired", expiredToken),
                Arguments.of("wrong key", wrongKeyToken)
        );
    }

    private JwtAuthenticationFilter filter(JwtService jwtService, UserAccountRepository repository) {
        return new JwtAuthenticationFilter(jwtService, repository, new ObjectMapper());
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String tamperSignature(String token) {
        char finalCharacter = token.charAt(token.length() - 1);
        char replacement = finalCharacter == 'a' ? 'b' : 'a';
        return token.substring(0, token.length() - 1) + replacement;
    }

    private static JwtService jwtService(String secret, long expirationMs) {
        return new JwtService(secret, expirationMs);
    }

    private static UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        user.setTokenVersion(3);
        return user;
    }

    private static String encodedSecret(int firstByte) {
        byte[] secret = new byte[32];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) (firstByte + index);
        }
        return Base64.getEncoder().encodeToString(secret);
    }
}
