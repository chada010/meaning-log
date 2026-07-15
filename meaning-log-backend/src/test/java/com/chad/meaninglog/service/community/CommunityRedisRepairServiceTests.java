package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.CommunityRedisRepair;
import com.chad.meaninglog.repository.CommunityRedisRepairRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityRedisRepairServiceTests {

    private CommunityRedisRepairRepository repository;
    private CommunityRedisRepairExecutor executor;
    private CommunityRedisRepairService service;
    private CommunityRedisRepair repair;

    @BeforeEach
    void setUp() {
        repository = mock(CommunityRedisRepairRepository.class);
        executor = mock(CommunityRedisRepairExecutor.class);
        service = new CommunityRedisRepairService(repository, executor);
        repair = new CommunityRedisRepair();
        repair.setId(1L);
        repair.setRepairType(CommunityRedisRepair.Type.POST_STATE.name());
        repair.setAggregateId(100L);
        when(repository.insert(any(CommunityRedisRepair.class))).thenAnswer(invocation -> {
            CommunityRedisRepair inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rollbackNeverTouchesRedis() {
        beginTransactionSynchronization();

        service.enqueuePostState(100L, 10L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(executor, never()).processById(any());
    }

    @Test
    void commitHandsRepairToRequiresNewExecutor() {
        beginTransactionSynchronization();

        service.enqueuePostState(100L, 10L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(executor).processById(1L);
    }

    @Test
    void pendingRowsAreProcessedById() {
        when(repository.findPending(10)).thenReturn(List.of(repair));

        service.processPending(10);

        verify(executor).processById(1L);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
