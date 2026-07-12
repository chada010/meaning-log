package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.RegisterRequest;
import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.service.AuthService;
import com.chad.meaninglog.service.ClientAddressResolver;
import com.chad.meaninglog.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTests {

    private static final String SOURCE_ADDRESS = "198.51.100.10";
    private static final String CLIENT_ADDRESS = "203.0.113.8";

    @Test
    void registerForwardsTheServerObservedSourceAddress() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = controller(authService);
        RegisterRequest request = new RegisterRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress();

        controller.register(request, servletRequest);

        verify(authService).register(request, SOURCE_ADDRESS);
    }

    @Test
    void resetPasswordForwardsTheServerObservedSourceAddress() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = controller(authService);
        ResetPasswordRequest request = new ResetPasswordRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress();

        controller.resetPassword(request, servletRequest);

        verify(authService).resetPassword(request, SOURCE_ADDRESS);
    }

    @Test
    void registerUsesForwardedClientAddressOnlyFromConfiguredTrustedProxy() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = controller(authService);
        RegisterRequest request = new RegisterRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress();
        servletRequest.addHeader("X-Forwarded-For", CLIENT_ADDRESS + ", 10.0.0.2");

        controller.register(request, servletRequest);

        verify(authService).register(request, CLIENT_ADDRESS);
    }

    @Test
    void resetPasswordIgnoresForwardedAddressFromUntrustedPeer() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = controller(authService);
        ResetPasswordRequest request = new ResetPasswordRequest();
        MockHttpServletRequest servletRequest = requestWithSourceAddress("198.51.101.10");
        servletRequest.addHeader("X-Forwarded-For", CLIENT_ADDRESS);

        controller.resetPassword(request, servletRequest);

        verify(authService).resetPassword(request, "198.51.101.10");
    }

    private AuthController controller(AuthService authService) {
        return new AuthController(
                authService,
                mock(EmailVerificationService.class),
                new ClientAddressResolver("198.51.100.0/24,10.0.0.0/8")
        );
    }

    private MockHttpServletRequest requestWithSourceAddress() {
        return requestWithSourceAddress(SOURCE_ADDRESS);
    }

    private MockHttpServletRequest requestWithSourceAddress(String sourceAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(sourceAddress);
        return request;
    }
}
