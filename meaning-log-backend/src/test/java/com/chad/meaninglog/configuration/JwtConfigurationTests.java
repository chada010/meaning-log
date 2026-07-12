package com.chad.meaninglog.configuration;

import com.chad.meaninglog.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {

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
                .hasRootCauseMessage("JWT secret must be at least 32 bytes");
    }

    @Test
    void applicationStartsWhenJwtSecretMeetsTheMinimumLength() throws IOException {
        try (ConfigurableApplicationContext context = runJwtServiceWithSecret("a".repeat(32))) {
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtService jwtService(ObjectMapper objectMapper, @Value("${jwt.secret}") String secret,
                              @Value("${jwt.expiration-ms}") long expirationMs) {
            return new JwtService(objectMapper, secret, expirationMs);
        }
    }
}
