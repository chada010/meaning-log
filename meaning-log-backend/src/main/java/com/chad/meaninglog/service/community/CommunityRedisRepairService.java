package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.CommunityRedisRepair;
import com.chad.meaninglog.repository.CommunityRedisRepairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityRedisRepairService {

    private final CommunityRedisRepairRepository repairRepository;
    private final CommunityRedisRepairExecutor repairExecutor;

    public void enqueuePostState(Long publicLogId, Long userId) {
        enqueue(CommunityRedisRepair.Type.POST_STATE, publicLogId, userId);
    }

    public void enqueuePostPublish(Long publicLogId) {
        enqueue(CommunityRedisRepair.Type.POST_PUBLISH, publicLogId, null);
    }

    public void enqueueFollowState(Long followerId, Long followeeId) {
        enqueue(CommunityRedisRepair.Type.FOLLOW_STATE, followerId, followeeId);
    }

    public int processPending(int limit) {
        List<CommunityRedisRepair> pending = repairRepository.findPending(limit);
        for (CommunityRedisRepair repair : pending) {
            dispatch(repair.getId());
        }
        return pending.size();
    }

    private void enqueue(CommunityRedisRepair.Type type, Long aggregateId, Long relatedId) {
        CommunityRedisRepair repair = new CommunityRedisRepair();
        repair.setRepairType(type.name());
        repair.setAggregateId(aggregateId);
        repair.setRelatedId(relatedId);
        repairRepository.insert(repair);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(repair.getId());
                }
            });
        } else {
            dispatch(repair.getId());
        }
    }

    private void dispatch(Long repairId) {
        try {
            repairExecutor.processById(repairId);
        } catch (DataAccessException exception) {
            log.warn("社区 Redis repair 暂无法启动新事务, repairId={} 将保留重试", repairId, exception);
        } catch (RuntimeException exception) {
            log.error("社区 Redis repair 调度异常, repairId={} 将保留重试", repairId, exception);
        }
    }
}
