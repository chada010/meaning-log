package com.chad.meaninglog.controller;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiTaskService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatRequestValidationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiRateLimiter aiRateLimiter;
    private AiTaskService aiTaskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiRateLimiter = mock(AiRateLimiter.class);
        aiTaskService = mock(AiTaskService.class);
        MeaningLogAiController controller = new MeaningLogAiController(
                mock(MeaningLogService.class),
                mock(XiaojiChatService.class),
                aiRateLimiter,
                aiTaskService
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsMessageLongerThanSixHundredCharactersBeforeAiTaskCreation() throws Exception {
        mockMvc.perform(post("/api/logs/1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMessage("a".repeat(601))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("对话内容不能超过600个字符"));

        verifyNoInteractions(aiRateLimiter, aiTaskService);
    }

    @Test
    void acceptsMessageWithExactlySixHundredCharacters() throws Exception {
        AiTask task = new AiTask();
        task.setId(9L);
        task.setTaskType(AiTaskType.LOG_REFINE);
        task.setStatus(AiTaskStatus.PENDING);
        when(aiTaskService.create(any(), eq(AiTaskType.LOG_REFINE), any())).thenReturn(task);

        mockMvc.perform(post("/api/logs/1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMessage("a".repeat(600))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value(9));

        verify(aiRateLimiter).check(any());
        verify(aiTaskService).create(any(), eq(AiTaskType.LOG_REFINE), any());
    }

    private String jsonMessage(String message) throws Exception {
        return objectMapper.writeValueAsString(Map.of("message", message));
    }
}
