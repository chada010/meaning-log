package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AuthResponse;
import com.chad.meaninglog.dto.LoginRequest;
import com.chad.meaninglog.dto.RegisterRequest;
import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.security.JwtService;
import com.chad.meaninglog.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static com.chad.meaninglog.util.EmailNormalizer.normalize;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResponse register(RegisterRequest request, String sourceAddress) {
        String email = normalize(request.getEmail());

        emailVerificationService.verifyCode(email, request.getVerificationCode(), sourceAddress);

        if (userAccountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        if (userAccountRepository.existsByUsername(request.getUsername().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordHasher.hash(request.getPassword()));
        UserAccount savedUser = userAccountRepository.save(user);
        return createAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String sourceAddress) {
        String identifier = request.getIdentifier().trim();
        boolean isEmailIdentifier = identifier.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        String principal = isEmailIdentifier ? normalize(identifier) : identifier.toLowerCase(Locale.ROOT);
        loginAttemptService.reserveAttempt(principal, sourceAddress);

        UserAccount user;
        if (isEmailIdentifier) {
            user = userAccountRepository.findByEmail(principal)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));
        } else {
            user = userAccountRepository.findByUsername(identifier)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));
        }

        if (!passwordHasher.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }

        loginAttemptService.clearFailures(principal, sourceAddress);
        return createAuthResponse(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String sourceAddress) {
        String email = normalize(request.getEmail());
        emailVerificationService.verifyCode(email, request.getVerificationCode(), sourceAddress);

        UserAccount user = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email is not registered"));
        user.setPasswordHash(passwordHasher.hash(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userAccountRepository.save(user);
    }

    public AuthResponse createAuthResponse(UserAccount user) {
        return AuthResponse.from(user, jwtService.generateToken(user));
    }
}
