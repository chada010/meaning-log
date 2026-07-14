package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.mq.NotificationEnvelope;
import com.chad.meaninglog.service.community.NotificationSseManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationListenerTests {

    @Test
    void pushesEnvelopePayloadToSseManager() {
        NotificationSseManager sseManager = mock(NotificationSseManager.class);
        NotificationListener listener = new NotificationListener(sseManager);
        NotificationEnvelope envelope = new NotificationEnvelope(42L, "{\"type\":\"LIKE\"}");

        listener.handle(envelope);

        verify(sseManager).push(42L, "{\"type\":\"LIKE\"}");
    }

    @Test
    void skipsEnvelopeWithoutReceiverId() {
        NotificationSseManager sseManager = mock(NotificationSseManager.class);
        NotificationListener listener = new NotificationListener(sseManager);

        listener.handle(new NotificationEnvelope(null, "payload"));
        listener.handle(null);

        verifyNoInteractions(sseManager);
    }
}
