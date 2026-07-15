package com.chad.meaninglog.service.community.job;

import com.chad.meaninglog.service.community.CommunityReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconcileJob {

    private final CommunityReconcileService reconcileService;

    @Scheduled(cron = "${community.reconcile.cron:17 3 * * * *}")
    public void reconcile() {
        try {
            CommunityReconcileService.ReconcileResult result = reconcileService.reconcileRecent();
            log.info("社区对账完成: 检查 {}, DB 修正 {}, Redis 重建 {}",
                    result.checked(), result.corrected(), result.rebuilt());
        } catch (DataAccessException exception) {
            log.warn("社区对账依赖暂不可用, 本轮保留数据等待重试", exception);
        }
    }
}
