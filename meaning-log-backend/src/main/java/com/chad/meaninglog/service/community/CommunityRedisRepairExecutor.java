package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.CommunityRedisRepair;
import com.chad.meaninglog.repository.CommunityRedisRepairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityRedisRepairExecutor {

    private static final int ERROR_MAX_LENGTH = 500;

    private final CommunityRedisRepairRepository repairRepository;
    private final CommunityRedisRebuilder rebuilder;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processById(Long repairId) {
        CommunityRedisRepair repair = repairRepository.selectById(repairId);
        if (repair == null) {
            return;
        }
        try {
            rebuilder.rebuild(repair);
            repairRepository.deleteById(repair.getId());
        } catch (DataAccessException exception) {
            keepForRetry(repair, exception, false);
        } catch (RuntimeException exception) {
            keepForRetry(repair, exception, true);
        }
    }

    private void keepForRetry(CommunityRedisRepair repair,
                              RuntimeException exception,
                              boolean unexpected) {
        String message = errorMessage(exception);
        if (unexpected) {
            log.error("社区 Redis repair 执行异常, repairId={}, type={}, aggregateId={}",
                    repair.getId(), repair.getRepairType(), repair.getAggregateId(), exception);
        } else {
            log.warn("社区 Redis repair 暂未完成, repairId={}, type={}, aggregateId={}, error={}",
                    repair.getId(), repair.getRepairType(), repair.getAggregateId(), message);
        }
        try {
            repairRepository.recordFailure(repair.getId(), message, LocalDateTime.now(businessClock));
        } catch (DataAccessException recordFailure) {
            log.error("记录社区 Redis repair 失败状态时数据库不可用, repairId={}",
                    repair.getId(), recordFailure);
        }
    }

    private String errorMessage(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        return value.length() <= ERROR_MAX_LENGTH
                ? value
                : value.substring(0, ERROR_MAX_LENGTH);
    }
}
