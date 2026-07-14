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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "认证", description = "注册、登录、密码重置、验证码等公开接口，以及获取当前用户信息")
@CrossOrigin(origins = {LOCAL_FRONTEND_ORIGIN, LOOPBACK_FRONTEND_ORIGIN})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final ClientAddressResolver clientAddressResolver;

    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送 6 位验证码，60 秒内不可重复发送")
    @PostMapping("/send-code")
    public void sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.getEmail(), clientAddressResolver.resolve(servletRequest));
    }

    @Operation(summary = "注册账号", description = "校验邮箱验证码后创建新用户并返回 JWT")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return authService.register(request, clientAddressResolver.resolve(servletRequest));
    }

    @Operation(summary = "登录", description = "identifier 支持邮箱或用户名；成功返回 JWT")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, clientAddressResolver.resolve(servletRequest));
    }

    @Operation(summary = "重置密码", description = "通过邮箱验证码重置密码，重置后原 JWT 立即失效")
    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        authService.resetPassword(request, clientAddressResolver.resolve(servletRequest));
    }

    @Operation(summary = "获取当前用户信息", description = "需要携带有效 JWT")
    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal UserAccount user) {
        return authService.createAuthResponse(user);
    }
}
