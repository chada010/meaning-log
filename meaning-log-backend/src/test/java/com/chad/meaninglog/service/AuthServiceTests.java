package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.ResetPasswordRequest;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.security.JwtService;
import com.chad.meaninglog.util.PasswordHasher;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTests {

    @Test
    void resetPasswordRequiresSixDigitVerificationCode() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ResetPasswordRequest request = resetRequest("12345");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("verificationCode"));
    }

    @Test
    void resetPasswordVerifiesNormalizedEmailBeforeChangingPassword() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        EmailVerificationService verificationService = mock(EmailVerificationService.class);
        UserAccount user = new UserAccount();
        user.setPasswordHash("old-hash");
        user.setTokenVersion(3);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.hash("new-password")).thenReturn("new-hash");

        service(repository, passwordHasher, verificationService).resetPassword(resetRequest("123456"));

        InOrder inOrder = inOrder(verificationService, repository, passwordHasher);
        inOrder.verify(verificationService).verifyCode("alice@example.com", "123456");
        inOrder.verify(repository).findByEmail("alice@example.com");
        inOrder.verify(passwordHasher).hash("new-password");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(4);
    }

    @Test
    void resetPasswordDoesNotChangeAccountWhenVerificationFails() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        EmailVerificationService verificationService = mock(EmailVerificationService.class);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(verificationService).verifyCode("alice@example.com", "123456");

        assertThatThrownBy(() -> service(repository, passwordHasher, verificationService)
                .resetPassword(resetRequest("123456")))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository, passwordHasher);
    }

    private AuthService service(
            UserAccountRepository repository,
            PasswordHasher passwordHasher,
            EmailVerificationService verificationService
    ) {
        return new AuthService(repository, passwordHasher, mock(JwtService.class), verificationService);
    }

    private ResetPasswordRequest resetRequest(String verificationCode) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(" Alice@Example.COM ");
        request.setNewPassword("new-password");
        ReflectionTestUtils.setField(request, "verificationCode", verificationCode);
        return request;
    }
}
