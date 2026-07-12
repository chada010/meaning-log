package com.chad.meaninglog.service;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class XiaojiChatService {

    private final XiaojiChatQueryService xiaojiChatQueryService;
    private final XiaojiChatWorkflowService xiaojiChatWorkflowService;

    public List<AiChatSessionResponse> findGeneralSessions(UserAccount user) {
        return xiaojiChatQueryService.findGeneralSessions(user);
    }

    public List<AiChatMessageResponse> findMessages(UserAccount user, Long sessionId) {
        return xiaojiChatQueryService.findMessages(user, sessionId);
    }

    public AiChatResponse findLogMessages(UserAccount user, Long logId) {
        return xiaojiChatQueryService.findLogMessages(user, logId);
    }

    public AiChatResponse findReportMessages(UserAccount user, Long reportId) {
        return xiaojiChatQueryService.findReportMessages(user, reportId);
    }

    public AiChatResponse chatWithLog(UserAccount user, Long logId, String userMessage) {
        return xiaojiChatWorkflowService.chatWithLog(user, logId, userMessage);
    }

    public AiChatResponse chatWithReport(UserAccount user, Long reportId, String userMessage) {
        return xiaojiChatWorkflowService.chatWithReport(user, reportId, userMessage);
    }

    public AiChatResponse chatWithCompanion(UserAccount user, Long sessionId, String userMessage) {
        return xiaojiChatWorkflowService.chatWithCompanion(user, sessionId, userMessage);
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

    public LogRefineStreamContext prepareLogRefineStream(UserAccount user, Long logId, String userMessage) {
        return xiaojiChatWorkflowService.prepareLogRefineStream(user, logId, userMessage);
    }

    public ReportRefineStreamContext prepareReportRefineStream(UserAccount user, Long reportId, String userMessage) {
        return xiaojiChatWorkflowService.prepareReportRefineStream(user, reportId, userMessage);
    }

    /**
     * 流式版本：保存用户消息后立即返回 sessionId，
     * 由 Controller 层负责驱动 SSE 流推送 AI 回复，
     * 流结束后调用 persistStreamReply 写入 AI 消息。
     */
    public AiChatSession prepareCompanionStream(UserAccount user, Long sessionId, String userMessage) {
        return xiaojiChatWorkflowService.prepareCompanionStream(user, sessionId, userMessage);
    }

    public void persistStreamReply(AiChatSession session, String reply) {
        xiaojiChatWorkflowService.persistStreamReply(session, reply);
    }

    public List<OpenAiClient.ChatTurn> buildCompanionHistory(AiChatSession session) {
        return xiaojiChatWorkflowService.buildCompanionHistory(session);
    }

    public void deleteLogChats(MeaningLog log) {
        xiaojiChatWorkflowService.deleteLogChats(log);
    }
}
