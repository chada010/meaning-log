package com.chad.meaninglog.service;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.AiChatMessageRepository;
import com.chad.meaninglog.repository.AiChatSessionRepository;
import com.chad.meaninglog.repository.AiReportRepository;
import com.chad.meaninglog.repository.LogImageRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class XiaojiChatSupportService {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final AiReportRepository aiReportRepository;
    private final LogImageRepository logImageRepository;

    @Transactional(readOnly = true)
    public AiChatSession getSession(UserAccount user, Long sessionId) {
        return sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    @Transactional(readOnly = true)
    public MeaningLog getMeaningLog(UserAccount user, Long logId) {
        return meaningLogRepository.findByIdAndUser(logId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
    }

    @Transactional(readOnly = true)
    public AiReport getAiReport(UserAccount user, Long reportId) {
        return aiReportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
    }

    @Transactional(readOnly = true)
    public List<AiChatMessage> findMessages(AiChatSession session) {
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    @Transactional(readOnly = true)
    public List<AiChatMessage> findRecentMessages(AiChatSession session) {
        return messageRepository.findTop16BySessionOrderByCreatedAtDesc(session);
    }

    @Transactional(readOnly = true)
    public List<com.chad.meaninglog.entity.LogImage> findLogImages(MeaningLog log) {
        return logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(log);
    }

    @Transactional
    public AiChatSession getOrCreateLogSession(UserAccount user, MeaningLog log) {
        return sessionRepository.findFirstByUserAndTypeAndMeaningLogOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.LOG,
                log
        ).orElseGet(() -> {
            AiChatSession session = new AiChatSession();
            session.setUser(user);
            session.setType(AiChatSession.Type.LOG);
            session.setMeaningLog(log);
            session.setTitle("日志：" + truncate(log.getTitle(), 40));
            return sessionRepository.save(session);
        });
    }

    @Transactional
    public AiChatSession getOrCreateReportSession(UserAccount user, AiReport report) {
        return sessionRepository.findFirstByUserAndTypeAndAiReportOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.REPORT,
                report
        ).orElseGet(() -> {
            AiChatSession session = new AiChatSession();
            session.setUser(user);
            session.setType(AiChatSession.Type.REPORT);
            session.setAiReport(report);
            session.setTitle("报告：" + truncate(report.getTitle(), 40));
            return sessionRepository.save(session);
        });
    }

    @Transactional
    public AiChatSession createGeneralSession(UserAccount user, String firstMessage) {
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setType(AiChatSession.Type.GENERAL);
        session.setTitle(truncate(firstMessage, 40));
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<OpenAiClient.ChatTurn> buildRecentTurns(AiChatSession session) {
        return findRecentMessages(session).stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                .map(message -> new OpenAiClient.ChatTurn(
                        message.getRole() == AiChatMessage.Role.USER ? "user" : "assistant",
                        message.getContent()
                ))
                .toList();
    }

    @Transactional
    public void saveMessage(AiChatSession session, AiChatMessage.Role role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public boolean lockLogForStreamReply(Long logId) {
        return meaningLogRepository.findByIdForUpdate(logId).isPresent();
    }

    @Transactional
    public void deleteLogChats(MeaningLog log) {
        messageRepository.deleteByMeaningLogId(log.getId());
        sessionRepository.deleteByMeaningLog(log);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "新的小记对话";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
