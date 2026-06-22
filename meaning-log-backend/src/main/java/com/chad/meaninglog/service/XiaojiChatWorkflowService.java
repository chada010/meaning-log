package com.chad.meaninglog.service;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class XiaojiChatWorkflowService {

    private final XiaojiChatQueryService xiaojiChatQueryService;
    private final XiaojiChatSupportService xiaojiChatSupportService;
    private final AiService aiService;
    private final AiRateLimiter aiRateLimiter;

    @Transactional
    public AiChatResponse chatWithLog(UserAccount user, Long logId, String userMessage) {
        aiRateLimiter.check(user);
        MeaningLog log = xiaojiChatSupportService.getMeaningLog(user, logId);
        AiChatSession session = xiaojiChatSupportService.getOrCreateLogSession(user, log);
        List<OpenAiClient.ChatTurn> history = xiaojiChatSupportService.buildRecentTurns(session);

        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        LogAiResult suggestion = aiService.refineLogSummary(
                log,
                history,
                xiaojiChatSupportService.findLogImages(log),
                userMessage
        );
        String reply = formatSuggestionReply(suggestion);
        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                suggestion,
                null,
                xiaojiChatQueryService.findMessages(user, session.getId())
        );
    }

    @Transactional
    public AiChatResponse chatWithReport(UserAccount user, Long reportId, String userMessage) {
        aiRateLimiter.check(user);
        AiReport report = xiaojiChatSupportService.getAiReport(user, reportId);
        AiChatSession session = xiaojiChatSupportService.getOrCreateReportSession(user, report);
        List<OpenAiClient.ChatTurn> history = xiaojiChatSupportService.buildRecentTurns(session);

        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        AiReportResponse suggestion = aiService.refineReport(
                report.getTitle(),
                report.getPeriod(),
                report.getSummary(),
                report.getTags(),
                history,
                userMessage
        );
        String reply = formatReportSuggestionReply(suggestion);
        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                null,
                suggestion,
                xiaojiChatQueryService.findMessages(user, session.getId())
        );
    }

    @Transactional
    public AiChatResponse chatWithCompanion(UserAccount user, Long sessionId, String userMessage) {
        aiRateLimiter.check(user);
        AiChatSession session = sessionId == null
                ? xiaojiChatSupportService.createGeneralSession(user, userMessage)
                : xiaojiChatSupportService.getSession(user, sessionId);

        assertGeneralSession(session);

        List<OpenAiClient.ChatTurn> history = xiaojiChatSupportService.buildRecentTurns(session);
        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        String reply = aiService.chatWithCompanion(history, userMessage);
        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);

        return new AiChatResponse(
                session.getId(),
                reply,
                null,
                null,
                xiaojiChatQueryService.findMessages(user, session.getId())
        );
    }

    @Transactional
    public XiaojiChatService.LogRefineStreamContext prepareLogRefineStream(UserAccount user, Long logId, String userMessage) {
        aiRateLimiter.check(user);
        MeaningLog log = xiaojiChatSupportService.getMeaningLog(user, logId);
        AiChatSession session = xiaojiChatSupportService.getOrCreateLogSession(user, log);
        List<OpenAiClient.ChatTurn> history = xiaojiChatSupportService.buildRecentTurns(session);
        List<LogImage> images = xiaojiChatSupportService.findLogImages(log);

        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return new XiaojiChatService.LogRefineStreamContext(session, log, images, history);
    }

    @Transactional
    public XiaojiChatService.ReportRefineStreamContext prepareReportRefineStream(
            UserAccount user, Long reportId, String userMessage
    ) {
        aiRateLimiter.check(user);
        AiReport report = xiaojiChatSupportService.getAiReport(user, reportId);
        AiChatSession session = xiaojiChatSupportService.getOrCreateReportSession(user, report);
        List<OpenAiClient.ChatTurn> history = xiaojiChatSupportService.buildRecentTurns(session);

        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return new XiaojiChatService.ReportRefineStreamContext(session, report, history);
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
                ? xiaojiChatSupportService.createGeneralSession(user, userMessage)
                : xiaojiChatSupportService.getSession(user, sessionId);

        assertGeneralSession(session);

        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.USER, userMessage);
        return session;
    }

    @Transactional
    public void persistStreamReply(AiChatSession session, String reply) {
        xiaojiChatSupportService.saveMessage(session, AiChatMessage.Role.ASSISTANT, reply);
    }

    @Transactional(readOnly = true)
    public List<OpenAiClient.ChatTurn> buildCompanionHistory(AiChatSession session) {
        return xiaojiChatSupportService.buildRecentTurns(session);
    }

    private void assertGeneralSession(AiChatSession session) {
        if (session.getType() != AiChatSession.Type.GENERAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This session is not a general chat");
        }
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
}
