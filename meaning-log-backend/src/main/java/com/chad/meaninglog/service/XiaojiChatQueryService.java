package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class XiaojiChatQueryService {

    private final AiChatSessionRepository sessionRepository;
    private final XiaojiChatSupportService xiaojiChatSupportService;

    @Transactional(readOnly = true)
    public List<AiChatSessionResponse> findGeneralSessions(UserAccount user) {
        return sessionRepository.findByUserAndTypeOrderByUpdatedAtDesc(user, AiChatSession.Type.GENERAL)
                .stream()
                .map(AiChatSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiChatMessageResponse> findMessages(UserAccount user, Long sessionId) {
        AiChatSession session = xiaojiChatSupportService.getSession(user, sessionId);
        return xiaojiChatSupportService.findMessages(session).stream()
                .map(AiChatMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiChatResponse findLogMessages(UserAccount user, Long logId) {
        MeaningLog log = xiaojiChatSupportService.getMeaningLog(user, logId);
        return sessionRepository.findFirstByUserAndTypeAndMeaningLogOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.LOG,
                log
        ).map(session -> new AiChatResponse(
                session.getId(),
                null,
                null,
                null,
                xiaojiChatSupportService.findMessages(session).stream()
                        .map(AiChatMessageResponse::from)
                        .toList()
        )).orElseGet(() -> new AiChatResponse(null, null, null, null, List.of()));
    }

    @Transactional(readOnly = true)
    public AiChatResponse findReportMessages(UserAccount user, Long reportId) {
        AiReport report = xiaojiChatSupportService.getAiReport(user, reportId);
        return sessionRepository.findFirstByUserAndTypeAndAiReportOrderByUpdatedAtDesc(
                user,
                AiChatSession.Type.REPORT,
                report
        ).map(session -> new AiChatResponse(
                session.getId(),
                null,
                null,
                null,
                xiaojiChatSupportService.findMessages(session).stream()
                        .map(AiChatMessageResponse::from)
                        .toList()
        )).orElseGet(() -> new AiChatResponse(null, null, null, null, List.of()));
    }
}
