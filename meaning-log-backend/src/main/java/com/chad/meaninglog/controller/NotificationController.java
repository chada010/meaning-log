package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.community.NotificationResponse;
import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.NotificationRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.community.NotificationSseManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationSseManager sseManager;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserAccount user,
                                            @RequestParam(defaultValue = "false") boolean unreadOnly,
                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        int offset = (page - 1) * size;
        List<Notification> raw = notificationRepository.findByReceiverId(user.getId(), unreadOnly, offset, size);
        if (raw.isEmpty()) {
            return List.of();
        }
        Set<Long> actorIds = raw.stream().map(Notification::getActorId).collect(Collectors.toSet());
        Map<Long, UserAccount> actorMap = actorIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountRepository.selectBatchIds(actorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
        return raw.stream()
                .map(n -> NotificationResponse.from(n, actorMap.get(n.getActorId())))
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserAccount user) {
        return Map.of("count", notificationRepository.countUnread(user.getId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal UserAccount user, @PathVariable Long id) {
        int rows = notificationRepository.markAsRead(id, user.getId());
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在");
        }
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal UserAccount user) {
        notificationRepository.markAllAsRead(user.getId());
    }

    @GetMapping("/stream")
    public SseEmitter stream(@AuthenticationPrincipal UserAccount user) {
        return sseManager.register(user.getId());
    }
}
