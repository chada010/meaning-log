package com.chad.meaninglog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigurationValidatorTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfigurationValidator.class);

    @Test
    void startsWithValidMailConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=test-smtp-password",
                        "mail.from=noreply@example.com"
                )
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void failsWhenMailPasswordIsBlank() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=",
                        "mail.from=noreply@example.com"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MAIL_PASSWORD must contain a non-placeholder SMTP credential"));
    }

    @Test
    void failsWhenMailPasswordUsesExampleValue() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=your-resend-api-key",
                        "mail.from=noreply@example.com"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MAIL_PASSWORD must contain a non-placeholder SMTP credential"));
    }

    @Test
    void failsWhenMailFromIsBlank() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=test-smtp-password",
                        "mail.from="
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MAIL_FROM must contain a sender address"));
    }

    @Test
    void failsWhenMailFromUsesExampleDomain() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=test-smtp-password",
                        "mail.from=noreply@your-verified-domain.example"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MAIL_FROM must not use the example domain"));
    }

    @Test
    void failsWhenMailFromUsesBareExampleDomain() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=test-smtp-password",
                        "mail.from=noreply@example"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("MAIL_FROM must not use the example domain"));
    }

    @Test
    void failsWhenMailFromIsInvalid() {
        contextRunner
                .withPropertyValues(
                        "spring.mail.password=test-smtp-password",
                        "mail.from=invalid-address"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("MAIL_FROM must contain a valid sender address"));
    }
}
