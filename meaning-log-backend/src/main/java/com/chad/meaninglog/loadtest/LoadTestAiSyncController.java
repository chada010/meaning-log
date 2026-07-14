package com.chad.meaninglog.loadtest;

import com.chad.meaninglog.entity.UserAccount;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

/**
 * 压测 baseline：模拟 Track 2 引入 MQ 前的同步 AI 接口 —— HTTP 线程直接被 5s 上游 sleep 卡住。
 * 仅在 loadtest profile 下暴露，用于对比新版 202 异步接口的 QPS / 延迟。
 * 端点仍走 JWT 认证，保证与异步端点公平对比（只差"阻塞 vs 异步"这一个变量）。
 */
@Hidden
@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@Profile("loadtest")
@RestController
@RequestMapping("/api/loadtest")
@Slf4j
public class LoadTestAiSyncController {

    private static final long MOCK_UPSTREAM_DELAY_MS = 5_000L;

    @PostMapping(value = "/logs/{id}/ai-sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> generateSyncBaseline(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) throws InterruptedException {
        Thread.sleep(MOCK_UPSTREAM_DELAY_MS);
        return Map.of(
                "id", id,
                "title", "Loadtest",
                "summary", "Loadtest baseline (synchronous 5s upstream).",
                "tags", List.of("loadtest")
        );
    }
}
