package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiChatMessage;
import org.junit.jupiter.api.Test;

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
}
