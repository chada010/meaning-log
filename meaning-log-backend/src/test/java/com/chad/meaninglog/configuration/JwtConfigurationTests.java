package com.chad.meaninglog.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTests {

    @Test
    void localJwtSecretIsOnlyActiveInTheLocalProfile() throws IOException {
        Properties localProperties = loadProperties(Path.of("application-local.properties.example"));

        assertThat(localProperties.getProperty("spring.config.activate.on-profile")).isEqualTo("local");
    }

    @Test
    void defaultConfigurationRequiresJwtSecretFromTheEnvironment() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);

            assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET}");
        }
    }

    private Properties loadProperties(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        }
    }
}
