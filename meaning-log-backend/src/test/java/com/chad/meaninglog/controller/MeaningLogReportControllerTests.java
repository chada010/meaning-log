package com.chad.meaninglog.controller;

import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.AiTaskService;
import com.chad.meaninglog.service.MeaningLogAiWorkflowService;
import com.chad.meaninglog.service.MeaningLogImageService;
import com.chad.meaninglog.service.MeaningLogLifecycleService;
import com.chad.meaninglog.service.MeaningLogReportService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.MeaningLogSupportService;
import com.chad.meaninglog.service.XiaojiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeaningLogReportControllerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MeaningLogReportService reportService = new MeaningLogReportService(
                mock(com.chad.meaninglog.repository.MeaningLogRepository.class),
                mock(com.chad.meaninglog.repository.AiReportRepository.class),
                mock(MeaningLogSupportService.class),
                mock(AiService.class)
        );
        MeaningLogService meaningLogService = new MeaningLogService(
                mock(MeaningLogLifecycleService.class),
                mock(MeaningLogImageService.class),
                mock(MeaningLogAiWorkflowService.class),
                reportService,
                mock(MeaningLogSupportService.class)
        );
        MeaningLogReportController controller = new MeaningLogReportController(
                meaningLogService,
                mock(XiaojiChatService.class),
                mock(AiRateLimiter.class),
                mock(AiTaskService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void reportEndpointRejectsReversedDateRange() throws Exception {
        mockMvc.perform(post("/api/logs/ai/report")
                        .param("startDate", "2026-07-02")
                        .param("endDate", "2026-07-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate must not be after endDate"));
    }

    @Test
    void dailySummaryEndpointRejectsMissingDate() throws Exception {
        mockMvc.perform(post("/api/logs/ai/daily-summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date is required"));
    }

    @Test
    void dailySummaryEndpointRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/logs/ai/daily-summary")
                        .param("date", "2026-07-invalid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD format"));
    }

    @Test
    void reportEndpointRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/logs/ai/report")
                        .param("startDate", "2026-07-invalid")
                        .param("endDate", "2026-07-02")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate must use YYYY-MM-DD format"));
    }

    @Test
    void reportEndpointRejectsMissingStartDate() throws Exception {
        mockMvc.perform(post("/api/logs/ai/report")
                        .param("endDate", "2026-07-02")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate is required"));
    }
}
