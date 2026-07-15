package com.chad.meaninglog.controller;

import com.chad.meaninglog.service.MeaningLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeaningLogRequestValidationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeaningLogService meaningLogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        meaningLogService = mock(MeaningLogService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MeaningLogController(meaningLogService))
                .setValidator(validator)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsOversizedDataUrlBeforeServiceAndBase64Decode() throws Exception {
        assertInvalidImage(
                validImageWith("dataUrl", "a".repeat(2_900_001)),
                "图片内容过大"
        );
    }

    @Test
    void rejectsOversizedFileNameBeforeService() throws Exception {
        assertInvalidImage(
                validImageWith("fileName", "a".repeat(181)),
                "图片文件名不能超过180个字符"
        );
    }

    @Test
    void rejectsOversizedCaptionBeforeService() throws Exception {
        assertInvalidImage(
                validImageWith("caption", "a".repeat(161)),
                "图片说明不能超过160个字符"
        );
    }

    @Test
    void rejectsInvalidNestedImageBeforeService() throws Exception {
        assertInvalidImage(
                Map.of("fileName", "photo.jpg", "contentType", "image/jpeg"),
                "图片内容不能为空"
        );
    }

    private void assertInvalidImage(Map<String, String> image, String expectedMessage) throws Exception {
        Map<String, Object> request = Map.of(
                "content", "正文",
                "logDate", "2026-07-15",
                "images", List.of(image)
        );

        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage));

        verifyNoInteractions(meaningLogService);
    }

    private Map<String, String> validImageWith(String field, String value) {
        return Map.of(
                "fileName", field.equals("fileName") ? value : "photo.jpg",
                "caption", field.equals("caption") ? value : "说明",
                "contentType", "image/jpeg",
                "dataUrl", field.equals("dataUrl") ? value : "data:image/jpeg;base64,YQ=="
        );
    }
}
