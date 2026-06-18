package com.chad.meaninglog.service;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.LogImage;
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
public class XiaojiChatService {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final AiReportRepository aiReportRepository;
    private final LogImageRepository logImageRepository;
    private final AiService aiService;
    private final AiRateLimiter aiRateLimiter;

    @Transactional(readOnly = true)
    public List<AiChatSessionResponse> findGeneralSessions(UserAccount user) {
        return sessionRepository.findByUserAndTypeOrderByUpdatedAtDesc(user, AiChatSession.Type.GENERAL)
                .stream()
                .map(AiChatSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiChatMessageResponse> findMessages(UserAccount user, Long sessionId) {
        AiChatSession session = getSession(user, sessionId);
        return messageRepository.findBySessionOrderByCreatedAtAsc(session)
                .stream()
                .map(AiChatMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiChatResponse findLogMessages(UserAccount user, Long logId) {
        MeaningLog log = meaningLogRepository.findByIdAndUser(logId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
        return sessionRepository.findFirstByUserAndTypeAndMeaningLogOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.LOG,
                log
        ).map(session -> new AiChatResponse(
                session.getId(),
                null,
                null,
                null,
                messageRepository.findBySessionOrderByCreatedAtAsc(session)
                        .stream()
                        .map(AiChatMessageResponse::from)
                        .toList()
        )).orElseGet(() -> new AiChatResponse(null, null, null, null, List.of()));
    }

    @Transactional(readOnly = true)
    public AiChatResponse findReportMessages(UserAccount user, Long reportId) {
        AiReport report = aiReportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
        return sessionRepository.findFirstByUserAndTypeAndAiReportOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.REPORT,
                report
        ).map(session -> new AiChatResponse(
                session.getId(),
                null,
                null,
                null,
                messageRepository.findBySessionOrderByCreatedAtAsc(session)
                        .stream()
                        .map(AiChatMessageResponse::from)
                        .toList()
        )).orElseGet(() -> new AiChatResponse(null, null, null, null, List.of()));
    }

    @Transactional
    public AiChatResponse chatWithLog(UserAccount user, Long logId, String userMessage) {
        aiRateLimiter.check(user);
        MeaningLog log = meaningLogRepository.findByIdAndUser(logId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
        AiChatSession session = getOrCreateLogSession(user, log);
        List<OpenAiClient.ChatTurn> history = recentTurns(session);

        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        LogAiResult suggestion = aiService.refineLogSummary(
                log,
                history,
                logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(log),
                userMessage
        );
        String reply = formatSuggestionReply(suggestion);
        saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                suggestion,
                null,
                findMessages(user, session.getId())
        );
    }

    @Transactional
    public AiChatResponse chatWithReport(UserAccount user, Long reportId, String userMessage) {
        aiRateLimiter.check(user);
        AiReport report = aiReportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
        AiChatSession session = getOrCreateReportSession(user, report);
        List<OpenAiClient.ChatTurn> history = recentTurns(session);

        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        AiReportResponse suggestion = aiService.refineReport(
                report.getTitle(),
                report.getPeriod(),
                report.getSummary(),
                report.getTags(),
                history,
                userMessage
        );
        String reply = formatReportSuggestionReply(suggestion);
        saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                null,
                suggestion,
                findMessages(user, session.getId())
        );
    }

    @Transactional
    public AiChatResponse chatWithCompanion(UserAccount user, Long sessionId, String userMessage) {
        aiRateLimiter.check(user);
        AiChatSession session = sessionId == null
                ? createGeneralSession(user, userMessage)
                : getSession(user, sessionId);

        if (session.getType() != AiChatSession.Type.GENERAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This session is not a general chat");
        }

        List<OpenAiClient.ChatTurn> history = recentTurns(session);
        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        String reply = aiService.chatWithCompanion(history, userMessage);
        saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                null,
                null,
                findMessages(user, session.getId())
        );
    }

    public record LogRefineStreamContext(
            AiChatSession session,
            MeaningLog log,
            List<LogImage> images,
            List<OpenAiClient.ChatTurn> history
    ) {}

    public record ReportRefineStreamContext(
            AiChatSession session,
            AiReport report,
            List<OpenAiClient.ChatTurn> history
    ) {}

    @Transactional
    public LogRefineStreamContext prepareLogRefineStream(UserAccount user, Long logId, String userMessage) {
        aiRateLimiter.check(user);
        MeaningLog log = meaningLogRepository.findByIdAndUser(logId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
        AiChatSession session = getOrCreateLogSession(user, log);
        List<OpenAiClient.ChatTurn> history = recentTurns(session);
        List<LogImage> images = logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(log);

        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return new LogRefineStreamContext(session, log, images, history);
    }

    @Transactional
    public ReportRefineStreamContext prepareReportRefineStream(UserAccount user, Long reportId, String userMessage) {
        aiRateLimiter.check(user);
        AiReport report = aiReportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
        AiChatSession session = getOrCreateReportSession(user, report);
        List<OpenAiClient.ChatTurn> history = recentTurns(session);

        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return new ReportRefineStreamContext(session, report, history);
    }

    /**
     * 流式版本：保存用户消息后立即返回 sessionId，
     * 由 Controller 层负责驱动 SSE 流推送 AI 回复，
     * 流结束后调用 persistStreamReply 写入 AI 消息。
     */
    @Transactional
    public AiChatSession prepareCompanionStream(UserAccount user, Long sessionId, String userMessage) {
        aiRateLimiter.check(user);
        AiChatSession session = sessionId == null
                ? createGeneralSession(user, userMessage)
                : getSession(user, sessionId);

        if (session.getType() != AiChatSession.Type.GENERAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This session is not a general chat");
        }

        saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return session;
    }

    @Transactional
    public void persistStreamReply(AiChatSession session, String reply) {
        saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);
    }

    public List<OpenAiClient.ChatTurn> buildCompanionHistory(AiChatSession session) {
        return recentTurns(session);
    }

    private AiChatSession getSession(UserAccount user, Long sessionId) {
        return sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    private AiChatSession getOrCreateLogSession(UserAccount user, MeaningLog log) {
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

    private AiChatSession getOrCreateReportSession(UserAccount user, AiReport report) {
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

    private AiChatSession createGeneralSession(UserAccount user, String firstMessage) {
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setType(AiChatSession.Type.GENERAL);
        session.setTitle(truncate(firstMessage, 40));
        return sessionRepository.save(session);
    }

    private List<OpenAiClient.ChatTurn> recentTurns(AiChatSession session) {
        return messageRepository.findTop16BySessionOrderByCreatedAtDesc(session)
                .stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                .map(message -> new OpenAiClient.ChatTurn(
                        message.getRole() == AiChatMessage.Role.USER ? "user" : "assistant",
                        message.getContent()
                ))
                .toList();
    }

    private void saveMessage(AiChatSession session, AiChatMessage.Role role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private String formatSuggestionReply(LogAiResult suggestion) {
        return """
                我先整理出一版预览，合适的话再应用到总结。
                标题：%s
                总结：%s
                标签：%s
                """.formatted(
                suggestion.title(),
                suggestion.summary(),
                suggestion.tags() == null ? "" : String.join("，", suggestion.tags())
        ).trim();
    }

    private String formatReportSuggestionReply(AiReportResponse suggestion) {
        return """
                我先整理出一版报告预览，合适的话再应用到报告。
                标题：%s
                正文：%s
                标签：%s
                """.formatted(
                suggestion.getTitle(),
                suggestion.getSummary(),
                suggestion.getTags() == null ? "" : suggestion.getTags()
        ).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "新的小记对话";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
