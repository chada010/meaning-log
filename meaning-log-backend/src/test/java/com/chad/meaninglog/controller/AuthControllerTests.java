package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.RegisterRequest;
import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.service.AuthService;
import com.chad.meaninglog.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTests {

    private static final String SOURCE_ADDRESS = "198.51.100.10";

    @Test
    void registerForwardsTheServerObservedSourceAddress() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService, mock(EmailVerificationService.class));
        RegisterRequest request = new RegisterRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress();

        controller.register(request, servletRequest);

        verify(authService).register(request, SOURCE_ADDRESS);
    }

    @Test
    void resetPasswordForwardsTheServerObservedSourceAddress() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService, mock(EmailVerificationService.class));
        ResetPasswordRequest request = new ResetPasswordRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress();

        controller.resetPassword(request, servletRequest);

        verify(authService).resetPassword(request, SOURCE_ADDRESS);
    }

    private MockHttpServletRequest requestWithSourceAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(SOURCE_ADDRESS);
        return request;
    }
}
