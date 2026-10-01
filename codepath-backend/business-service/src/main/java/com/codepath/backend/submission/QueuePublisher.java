package com.codepath.backend.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class QueuePublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String executionQueue;
    private final String diagnosisQueue;

    public QueuePublisher(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          @Value("${codepath.queues.execution}") String executionQueue,
                          @Value("${codepath.queues.diagnosis}") String diagnosisQueue) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.executionQueue = executionQueue;
        this.diagnosisQueue = diagnosisQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishExecution(ExecutionQueuedEvent event) {
        push(executionQueue, Map.of("submissionId", event.submissionId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDiagnosis(DiagnosisQueuedEvent event) {
        push(diagnosisQueue, Map.of("submissionId", event.submissionId()));
    }

    private void push(String queue, Map<String, Object> payload) {
        try {
            redisTemplate.opsForList().rightPush(queue, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务序列化失败", exception);
        }
    }
}
