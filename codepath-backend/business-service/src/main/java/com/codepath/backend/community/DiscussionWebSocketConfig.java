package com.codepath.backend.community;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class DiscussionWebSocketConfig implements WebSocketConfigurer {
    private final DiscussionRealtimePublisher publisher;

    public DiscussionWebSocketConfig(DiscussionRealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(publisher, "/ws/discussions")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
