package com.chad.meaninglog.controller;

import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.MeaningLogAiWorkflowService;
import com.chad.meaninglog.service.MeaningLogImageService;
import com.chad.meaninglog.service.MeaningLogLifecycleService;
import com.chad.meaninglog.service.MeaningLogReportService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.MeaningLogSupportService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.web.SseEmitterSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                mock(AiService.class),
                mock(AiRateLimiter.class)
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
                mock(AiService.class),
                mock(SseEmitterSupport.class),
                new ObjectMapper()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void reportEndpointsRejectReversedDateRangeWithSameError() throws Exception {
        assertInvalidDateRange("/api/logs/ai/report?startDate=2026-07-02&endDate=2026-07-01");
        assertInvalidDateRange("/api/logs/ai/report/stream");
    }

    @Test
    void dailySummaryEndpointsRejectMissingDateWithSameError() throws Exception {
        assertMissingDailyDate("/api/logs/ai/daily-summary");
        assertMissingDailyDate("/api/logs/ai/daily-summary/stream");
        mockMvc.perform(post("/api/logs/ai/daily-summary/stream")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date is required"));
    }

    @Test
    void dailySummaryEndpointsRejectInvalidDateFormatWithSameError() throws Exception {
        assertInvalidDailyDate("/api/logs/ai/daily-summary?date=2026-07-invalid");
        assertInvalidDailyDate("/api/logs/ai/daily-summary/stream");
    }

    @Test
    void reportEndpointsRejectInvalidDateFormatWithSameError() throws Exception {
        assertInvalidDateFormat("/api/logs/ai/report?startDate=2026-07-invalid&endDate=2026-07-02");
        assertInvalidDateFormat("/api/logs/ai/report/stream");
    }

    @Test
    void reportEndpointsRejectMissingStartDateWithSameError() throws Exception {
        assertMissingStartDate("/api/logs/ai/report?endDate=2026-07-02");
        assertMissingStartDate("/api/logs/ai/report/stream");
    }

    private void assertInvalidDateRange(String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/stream")) {
            request.content("{\"startDate\":\"2026-07-02\",\"endDate\":\"2026-07-01\"}");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate must not be after endDate"));
    }

    private void assertInvalidDateFormat(String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/stream")) {
            request.content("{\"startDate\":\"2026-07-invalid\",\"endDate\":\"2026-07-02\"}");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate must use YYYY-MM-DD format"));
    }

    private void assertMissingDailyDate(String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/stream")) {
            request.content("{}");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date is required"));
    }

    private void assertInvalidDailyDate(String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/stream")) {
            request.content("{\"date\":\"2026-07-invalid\"}");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date must use YYYY-MM-DD format"));
    }

    private void assertMissingStartDate(String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/stream")) {
            request.content("{\"endDate\":\"2026-07-02\"}");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate is required"));
    }
}
