package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AuthResponse;
import com.chad.meaninglog.dto.LoginRequest;
import com.chad.meaninglog.dto.RegisterRequest;
import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.dto.SendCodeRequest;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AuthService;
import com.chad.meaninglog.service.ClientAddressResolver;
import com.chad.meaninglog.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.chad.meaninglog.web.WebConstants.LOOPBACK_FRONTEND_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

@CrossOrigin(origins = {LOCAL_FRONTEND_ORIGIN, LOOPBACK_FRONTEND_ORIGIN})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final ClientAddressResolver clientAddressResolver;

    @PostMapping("/send-code")
    public void sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.getEmail(), clientAddressResolver.resolve(servletRequest));
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return authService.register(request, clientAddressResolver.resolve(servletRequest));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, clientAddressResolver.resolve(servletRequest));
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        authService.resetPassword(request, clientAddressResolver.resolve(servletRequest));
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal UserAccount user) {
        return authService.createAuthResponse(user);
    }
}
