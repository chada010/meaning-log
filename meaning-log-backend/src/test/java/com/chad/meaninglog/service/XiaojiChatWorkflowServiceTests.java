package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.dto.LogAiResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XiaojiChatWorkflowServiceTests {

    @Test
    void doesNotPersistLogStreamReplyWhenLogWasDeleted() {
        XiaojiChatSupportService supportService = mock(XiaojiChatSupportService.class);
        XiaojiChatWorkflowService service = new XiaojiChatWorkflowService(
                mock(XiaojiChatQueryService.class),
                supportService,
                mock(AiService.class),
                mock(AiRateLimiter.class)
        );
        AiChatSession session = new AiChatSession();
        session.setId(88L);
        session.setMeaningLogId(66L);
        when(supportService.lockLogForStreamReply(66L)).thenReturn(false);

        service.persistStreamReply(session, "late reply");

        verify(supportService).lockLogForStreamReply(66L);
        verify(supportService, never()).saveMessage(session, AiChatMessage.Role.ASSISTANT, "late reply");
    }

    @Test
    void persistsLogStreamReplyWhenLogStillExists() {
        XiaojiChatSupportService supportService = mock(XiaojiChatSupportService.class);
        XiaojiChatWorkflowService service = new XiaojiChatWorkflowService(
                mock(XiaojiChatQueryService.class),
                supportService,
                mock(AiService.class),
                mock(AiRateLimiter.class)
        );
        AiChatSession session = new AiChatSession();
        session.setId(88L);
        session.setMeaningLogId(66L);
        when(supportService.lockLogForStreamReply(66L)).thenReturn(true);

        service.persistStreamReply(session, "stream reply");

        verify(supportService).saveMessage(session, AiChatMessage.Role.ASSISTANT, "stream reply");
    }

    @Test
    void chatWithLogLocksLogBeforePersistingMessages() {
        XiaojiChatQueryService queryService = mock(XiaojiChatQueryService.class);
        XiaojiChatSupportService supportService = mock(XiaojiChatSupportService.class);
        AiService aiService = mock(AiService.class);
        XiaojiChatWorkflowService service = new XiaojiChatWorkflowService(
                queryService,
                supportService,
                aiService,
                mock(AiRateLimiter.class)
        );
        UserAccount user = new UserAccount();
        MeaningLog log = new MeaningLog();
        AiChatSession session = new AiChatSession();
        session.setId(88L);
        LogAiResult suggestion = new LogAiResult("Title", "Summary", List.of("tag"));
        when(supportService.getMeaningLogForUpdate(user, 66L)).thenReturn(log);
        when(supportService.getOrCreateLogSession(user, log)).thenReturn(session);
        when(supportService.buildRecentTurns(session)).thenReturn(List.of());
        when(supportService.findLogImages(log)).thenReturn(List.of());
        when(aiService.refineLogSummary(log, List.of(), List.of(), "Refine this log.")).thenReturn(suggestion);
        when(queryService.findMessages(user, session.getId())).thenReturn(List.of());

        service.chatWithLog(user, 66L, "Refine this log.");

        InOrder inOrder = inOrder(supportService);
        inOrder.verify(supportService).getMeaningLogForUpdate(user, 66L);
        inOrder.verify(supportService).saveMessage(session, AiChatMessage.Role.USER, "Refine this log.");
        inOrder.verify(supportService).saveMessage(eq(session), eq(AiChatMessage.Role.ASSISTANT), anyString());
        verify(supportService, never()).getMeaningLog(user, 66L);
    }

    @Test
    void prepareLogRefineStreamLocksLogBeforePersistingUserMessage() {
        XiaojiChatSupportService supportService = mock(XiaojiChatSupportService.class);
        XiaojiChatWorkflowService service = new XiaojiChatWorkflowService(
                mock(XiaojiChatQueryService.class),
                supportService,
                mock(AiService.class),
                mock(AiRateLimiter.class)
        );
        UserAccount user = new UserAccount();
        MeaningLog log = new MeaningLog();
        AiChatSession session = new AiChatSession();
        when(supportService.getMeaningLogForUpdate(user, 66L)).thenReturn(log);
        when(supportService.getOrCreateLogSession(user, log)).thenReturn(session);
        when(supportService.buildRecentTurns(session)).thenReturn(List.of());
        when(supportService.findLogImages(log)).thenReturn(List.of());

        service.prepareLogRefineStream(user, 66L, "Prepare stream reply.");

        InOrder inOrder = inOrder(supportService);
        inOrder.verify(supportService).getMeaningLogForUpdate(user, 66L);
        inOrder.verify(supportService).saveMessage(session, AiChatMessage.Role.USER, "Prepare stream reply.");
        verify(supportService, never()).getMeaningLog(user, 66L);
    }
}
