package com.chad.meaninglog.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
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
        assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET}");
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

    @Configuration(proxyBeanMethods = false)
    static class JwtPropertyConfiguration {

        @Bean
        String configuredSecret(@Value("${test.jwt.secret}") String secret) {
            return secret;
        }
    }
}
