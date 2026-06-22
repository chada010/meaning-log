package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AuthResponse;
import com.chad.meaninglog.dto.LoginRequest;
import com.chad.meaninglog.dto.RegisterRequest;
import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.dto.SendCodeRequest;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AuthService;
import com.chad.meaninglog.service.EmailVerificationService;
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

    @PostMapping("/send-code")
    public void sendCode(@Valid @RequestBody SendCodeRequest request) {
        emailVerificationService.sendCode(request.getEmail());
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal UserAccount user) {
        return authService.createAuthResponse(user);
    }
}
