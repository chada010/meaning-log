package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.chad.meaninglog.time.BusinessTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskReaperTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-15T11:00:00Z"), BusinessTime.ZONE_ID);

    @Test
    void reapStaleRunningUsesConfiguredCutoffAndNotifiesCompletedTask() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskDeliveryService deliveryService = mock(AiTaskDeliveryService.class);
        AiTaskNotifier notifier = mock(AiTaskNotifier.class);
        AiTaskReaper reaper = new AiTaskReaper(repository, deliveryService, notifier, FIXED_CLOCK);
        ReflectionTestUtils.setField(reaper, "runningTimeoutSeconds", 600L);
        ReflectionTestUtils.setField(reaper, "reaperBatchSize", 10);
        AiTask task = task(42L);
        when(repository.findStaleRunning(any(LocalDateTime.class), eq(10))).thenReturn(List.of(task));
        when(repository.failStaleRunning(eq(42L), any(LocalDateTime.class),
                eq("running timeout"), any(LocalDateTime.class))).thenReturn(1);

        reaper.reapStaleRunning();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findStaleRunning(cutoffCaptor.capture(), eq(10));

        assertThat(cutoffCaptor.getValue())
                .isEqualTo(LocalDateTime.now(FIXED_CLOCK).minusSeconds(600));
        verify(notifier).publishDone(42L);
    }

    @Test
    void staleRunningStateChangeDoesNotPublishDuplicateNotification() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskDeliveryService deliveryService = mock(AiTaskDeliveryService.class);
        AiTaskNotifier notifier = mock(AiTaskNotifier.class);
        AiTaskReaper reaper = new AiTaskReaper(repository, deliveryService, notifier, FIXED_CLOCK);
        ReflectionTestUtils.setField(reaper, "runningTimeoutSeconds", 300L);
        ReflectionTestUtils.setField(reaper, "reaperBatchSize", 10);
        AiTask task = task(43L);
        when(repository.findStaleRunning(any(LocalDateTime.class), eq(10))).thenReturn(List.of(task));
        when(repository.failStaleRunning(eq(43L), any(LocalDateTime.class),
                eq("running timeout"), any(LocalDateTime.class))).thenReturn(0);

        reaper.reapStaleRunning();

        verify(notifier, never()).publishDone(any());
    }

    @Test
    void recoverStalePendingUsesConfiguredBatchLimit() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskDeliveryService deliveryService = mock(AiTaskDeliveryService.class);
        AiTaskNotifier notifier = mock(AiTaskNotifier.class);
        AiTaskReaper reaper = new AiTaskReaper(repository, deliveryService, notifier, FIXED_CLOCK);
        ReflectionTestUtils.setField(reaper, "pendingStaleSeconds", 60L);
        ReflectionTestUtils.setField(reaper, "deliveryBatchSize", 2);
        AiTask first = task(1L);
        AiTask second = task(2L);
        when(repository.findPendingForPublish(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of(first, second));
        when(deliveryService.republishIfDue(any(AiTask.class), any(LocalDateTime.class)))
                .thenReturn(true);

        reaper.recoverStalePending();

        verify(repository).findPendingForPublish(any(LocalDateTime.class), eq(2));
        verify(deliveryService).republishIfDue(eq(first), any(LocalDateTime.class));
        verify(deliveryService).republishIfDue(eq(second), any(LocalDateTime.class));
    }

    private static AiTask task(Long id) {
        AiTask task = new AiTask();
        task.setId(id);
        return task;
    }
}
