package com.chad.meaninglog.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证新库与已有 V4 库都能按顺序升级到最新版本。
 *
 * @author wwj
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationChainTests {

    private static final String LATEST_VERSION = "6";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("meaning_log")
            .withUsername("test")
            .withPassword("test")
            .withEnv("TZ", "Asia/Shanghai");

    @BeforeEach
    void resetSchema() {
        flyway().clean();
    }

    @Test
    void emptySchemaMigratesFromV1ToV6() {
        MigrateResult result = flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(6);
        assertThat(result.targetSchemaVersion).isEqualTo(LATEST_VERSION);
        assertCurrentVersion(LATEST_VERSION);
    }

    @Test
    void existingV4SchemaMigratesThroughV5AndV6() {
        MigrateResult initial = flyway("4").migrate();
        assertThat(initial.migrationsExecuted).isEqualTo(4);
        assertThat(initial.targetSchemaVersion).isEqualTo("4");

        MigrateResult upgrade = flyway().migrate();

        assertThat(upgrade.success).isTrue();
        assertThat(upgrade.migrationsExecuted).isEqualTo(2);
        assertThat(upgrade.targetSchemaVersion).isEqualTo(LATEST_VERSION);
        assertCurrentVersion(LATEST_VERSION);
    }

    private static Flyway flyway(String target) {
        return configuration().target(target).load();
    }

    private static Flyway flyway() {
        return configuration().load();
    }

    private static FluentConfiguration configuration() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
    }

    private static void assertCurrentVersion(String expected) {
        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo(expected);
    }
}
