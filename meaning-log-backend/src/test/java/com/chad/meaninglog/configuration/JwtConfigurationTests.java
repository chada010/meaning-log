package com.chad.meaninglog.configuration;

import com.chad.meaninglog.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {

    private static final String VALID_JWT_SECRET = "Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=";

    @TempDir
    Path configDirectory;

    @Test
    void localProfileLoadsTheLocalJwtSecret() throws IOException {
        try (ConfigurableApplicationContext context = runWithProfile("local")) {
            assertThat(context.getBean("configuredSecret", String.class)).isEqualTo("local-profile-secret");
        }
    }

    @Test
    void nonLocalProfileDoesNotLoadTheLocalJwtSecret() throws IOException {
        try (ConfigurableApplicationContext context = runWithProfile("prod")) {
            assertThat(context.getBean("configuredSecret", String.class)).isEmpty();
        }
    }

    @Test
    void defaultConfigurationRequiresJwtSecretFromTheEnvironment() throws IOException {
        Properties properties = loadProperties(Path.of("src/main/resources/application.properties"));
        assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET:}");
    }

    @Test
    void applicationFailsAtStartupWhenJwtSecretIsBlank() throws IOException {
        assertThatThrownBy(() -> runJwtServiceWithSecret(""))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("JWT secret must decode to at least 32 bytes");
    }

    @Test
    void applicationFailsAtStartupWhenJwtSecretIsNotBase64Encoded() {
        assertThatThrownBy(() -> runJwtServiceWithSecret("!".repeat(32)))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("JWT secret must be Base64 encoded");
    }

    @Test
    void applicationFailsAtStartupWhenJwtSecretDecodesToFewerThanThirtyTwoBytes() {
        assertThatThrownBy(() -> runJwtServiceWithSecret(Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("JWT secret must decode to at least 32 bytes");
    }

    @Test
    void applicationFailsAtStartupWhenJwtSecretContainsOnlyRepeatedBytes() {
        assertThatThrownBy(() -> runJwtServiceWithSecret(Base64.getEncoder().encodeToString(new byte[32])))
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("JWT secret must not contain only repeated bytes");
    }

    @Test
    void localJwtSecretSampleIsBase64EncodedAndLongEnough() throws IOException {
        Properties properties = loadProperties(Path.of("application-local.properties.example"));

        assertThat(Base64.getDecoder().decode(properties.getProperty("jwt.secret"))).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void applicationStartsWhenJwtSecretIsBase64EncodedAndStrongEnough() throws IOException {
        try (ConfigurableApplicationContext context = runJwtServiceWithSecret(VALID_JWT_SECRET)) {
            assertThat(context.getBean(JwtService.class)).isNotNull();
        }
    }

    private ConfigurableApplicationContext runWithProfile(String profile) throws IOException {
        Path localConfig = configDirectory.resolve("application-local.properties");
        Files.writeString(
                localConfig,
                Files.readString(Path.of("application-local.properties.example")) + "\ntest.jwt.secret=local-profile-secret\n"
        );
        Path applicationConfig = configDirectory.resolve("application.properties");
        Files.writeString(applicationConfig, """
                spring.config.import=optional:%s
                test.jwt.secret=${TEST_JWT_SECRET:}
                """.formatted(localConfig.toUri()));

        SpringApplication application = new SpringApplication(JwtPropertyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(
                "--spring.config.location=optional:" + applicationConfig.toUri(),
                "--spring.profiles.active=" + profile
        );
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (var inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private ConfigurableApplicationContext runJwtServiceWithSecret(String secret) throws IOException {
        Path applicationConfig = configDirectory.resolve("jwt-application.properties");
        Files.writeString(applicationConfig, "jwt.secret=" + secret + "\njwt.expiration-ms=86400000\n");

        SpringApplication application = new SpringApplication(JwtServiceConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(
                "--spring.config.location=" + applicationConfig.toUri(),
                "--jwt.secret=" + secret
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class JwtPropertyConfiguration {

        @Bean
        String configuredSecret(@Value("${test.jwt.secret}") String secret) {
            return secret;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JwtServiceConfiguration {

        @Bean
        JwtService jwtService(@Value("${jwt.secret}") String secret,
                              @Value("${jwt.expiration-ms}") long expirationMs) {
            return new JwtService(secret, expirationMs);
        }
    }
}
