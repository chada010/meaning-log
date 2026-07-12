package com.chad.meaninglog.security;

import com.chad.meaninglog.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    @Test
    void invalidSignatureDoesNotQueryTheUserRepository() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer unsigned-token");
        when(jwtService.hasValidSignature("unsigned-token")).thenReturn(false);

        new JwtAuthenticationFilter(jwtService, userAccountRepository, mock(ObjectMapper.class))
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        verify(jwtService).hasValidSignature("unsigned-token");
        verifyNoInteractions(userAccountRepository);
    }
}
