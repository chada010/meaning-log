package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.repository.AiChatMessageRepository;
import com.chad.meaninglog.repository.AiChatSessionRepository;
import com.chad.meaninglog.repository.AiReportRepository;
import com.chad.meaninglog.repository.LogImageRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class XiaojiChatSupportServiceTests {

    @Test
    void deletingLogChatsDeletesMessagesBeforeSessions() {
        AiChatSessionRepository sessionRepository = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messageRepository = mock(AiChatMessageRepository.class);
        XiaojiChatSupportService service = new XiaojiChatSupportService(
                sessionRepository,
                messageRepository,
                mock(MeaningLogRepository.class),
                mock(AiReportRepository.class),
                mock(LogImageRepository.class)
        );
        MeaningLog log = new MeaningLog();
        log.setId(99L);

        service.deleteLogChats(log);

        InOrder inOrder = inOrder(messageRepository, sessionRepository);
        inOrder.verify(messageRepository).deleteByMeaningLogId(99L);
        inOrder.verify(sessionRepository).deleteByMeaningLog(log);
    }
}
