package com.chad.meaninglog.service;

import com.chad.meaninglog.repository.AiTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskReaperTests {

    @Test
    void reapStaleRunningUsesConfiguredCutoff() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskReaper reaper = new AiTaskReaper(repository);
        ReflectionTestUtils.setField(reaper, "runningTimeoutSeconds", 600L);
        when(repository.reapStaleRunning(any(LocalDateTime.class), anyString())).thenReturn(2);

        LocalDateTime before = LocalDateTime.now().minusSeconds(600);
        reaper.reapStaleRunning();
        LocalDateTime after = LocalDateTime.now().minusSeconds(600);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).reapStaleRunning(cutoffCaptor.capture(), reasonCaptor.capture());

        assertThat(cutoffCaptor.getValue()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(reasonCaptor.getValue()).isEqualTo("running timeout");
    }

    @Test
    void noopWhenRepositoryReportsZeroAffected() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskReaper reaper = new AiTaskReaper(repository);
        ReflectionTestUtils.setField(reaper, "runningTimeoutSeconds", 300L);
        when(repository.reapStaleRunning(any(LocalDateTime.class), anyString())).thenReturn(0);

        reaper.reapStaleRunning();

        verify(repository).reapStaleRunning(any(LocalDateTime.class), anyString());
    }
}
