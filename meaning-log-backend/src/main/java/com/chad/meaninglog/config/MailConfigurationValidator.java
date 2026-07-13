package com.chad.meaninglog.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class MailConfigurationValidator implements InitializingBean {

    private static final String EXAMPLE_PASSWORD = "your-resend-api-key";

    private final String password;
    private final String from;

    public MailConfigurationValidator(
            @Value("${spring.mail.password:}") String password,
            @Value("${mail.from:}") String from
    ) {
        this.password = password;
        this.from = from;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(password) || EXAMPLE_PASSWORD.equals(password.trim())) {
            throw new IllegalStateException("MAIL_PASSWORD must contain a non-placeholder SMTP credential");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("MAIL_FROM must contain a sender address");
        }

        String normalizedFrom = from.trim();
        InternetAddress address;
        try {
            address = new InternetAddress(normalizedFrom, true);
            address.validate();
        } catch (AddressException ex) {
            throw new IllegalStateException("MAIL_FROM must contain a valid sender address", ex);
        }
        if (address.getAddress().toLowerCase(Locale.ROOT).endsWith(".example")) {
            throw new IllegalStateException("MAIL_FROM must not use the example domain");
        }
    }
}
