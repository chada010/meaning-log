package com.chad.meaninglog.mq;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiTaskCreatedResponse;
import com.chad.meaninglog.dto.AiTaskResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=",
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com",
        "app.ai.api-key=test-key"
})
@AutoConfigureMockMvc
class AiTaskMqIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("meaning_log")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void wireInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MeaningLogRepository meaningLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpenAiClient openAiClient;

    @Test
    void logAnalyzeTaskFlowsThroughMqAndPersistsResult() throws Exception {
        UserAccount user = new UserAccount();
        user.setEmail("mq-integration@example.com");
        user.setUsername("mqIntegration");
        user.setPasswordHash("test-hash");
        userAccountRepository.save(user);

        MeaningLog log = new MeaningLog();
        log.setUser(user);
        log.setTitle("MQ integration");
        log.setContent("A journal entry for MQ verification.");
        log.setLogDate(LocalDate.of(2026, 7, 14));
        meaningLogRepository.save(log);

        LogAiResult aiResult = new LogAiResult("Integrated title", "Integrated summary", List.of("mq", "test"));
        when(openAiClient.analyzeLog(anyString(), anyString(), any(), anyString(), anyList()))
                .thenReturn(aiResult);

        String token = jwtService.generateToken(user);

        MvcResult createResult = mockMvc.perform(post("/api/logs/{id}/ai", log.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andReturn();

        AiTaskCreatedResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                AiTaskCreatedResponse.class);
        Long taskId = created.taskId();

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            MvcResult statusResult = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .get("/api/ai/tasks/{id}", taskId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            AiTaskResponse resp = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(),
                    AiTaskResponse.class);
            org.assertj.core.api.Assertions.assertThat(resp.status()).isEqualTo(AiTaskStatus.SUCCESS);
            org.assertj.core.api.Assertions.assertThat(resp.resultJson()).contains("Integrated title");
        });
    }
}
