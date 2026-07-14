package com.chad.meaninglog.config;

import com.chad.meaninglog.service.community.CommunityRedisKeys;
import com.chad.meaninglog.service.community.NotificationSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.nio.charset.StandardCharsets;

/**
 * 订阅 {@code community:notify:*} 频道, 将 Pub/Sub 消息转发到本机 SSE emitter。
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CommunityNotificationSubscriber {

    private final NotificationSseManager sseManager;

    @Bean
    public RedisMessageListenerContainer communityNotificationContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter listener = new MessageListenerAdapter(this, "onMessage");
        container.addMessageListener(listener, new PatternTopic(CommunityRedisKeys.NOTIFY_CHANNEL_PREFIX + "*"));
        return container;
    }

    public void onMessage(byte[] payload, byte[] channelBytes) {
        try {
            String channel = new String(channelBytes, StandardCharsets.UTF_8);
            String message = new String(payload, StandardCharsets.UTF_8);
            String suffix = channel.substring(CommunityRedisKeys.NOTIFY_CHANNEL_PREFIX.length());
            Long userId = Long.parseLong(suffix);
            sseManager.push(userId, message);
        } catch (Exception e) {
            log.warn("处理通知 pub/sub 消息失败", e);
        }
    }
}
