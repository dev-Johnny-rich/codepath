package com.codepath.backend.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DiscussionRealtimePublisher extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(DiscussionRealtimePublisher.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public DiscussionRealtimePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
    }

    public void publishAfterCommit(String type, Long postId) {
        Runnable publish = () -> broadcast(type, postId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void broadcast(String type, Long postId) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "postId", postId,
                    "occurredAt", Instant.now().toString()
            ));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize discussion event", exception);
            return;
        }

        sessions.forEach((id, session) -> {
            if (!session.isOpen()) {
                sessions.remove(id);
                return;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException exception) {
                sessions.remove(id);
                try {
                    session.close();
                } catch (IOException ignored) {
                    // Connection is already unusable.
                }
                log.debug("Removed disconnected discussion WebSocket session {}", id);
            }
        });
    }
}
