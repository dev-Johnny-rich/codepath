package com.codepath.backend.mastery;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "mastery_event")
@NoArgsConstructor
public class MasteryEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "delta_value", nullable = false)
    private BigDecimal deltaValue;

    @Column(name = "mastery_before", nullable = false)
    private BigDecimal masteryBefore;

    @Column(name = "mastery_after", nullable = false)
    private BigDecimal masteryAfter;

    @Column(nullable = false)
    private String reason;

    public static MasteryEvent create(Long userId, Long knowledgePointId, Long submissionId,
                                      String eventType, BigDecimal delta, BigDecimal before,
                                      BigDecimal after, String reason) {
        MasteryEvent event = new MasteryEvent();
        event.userId = userId;
        event.knowledgePointId = knowledgePointId;
        event.submissionId = submissionId;
        event.eventType = eventType;
        event.deltaValue = delta;
        event.masteryBefore = before;
        event.masteryAfter = after;
        event.reason = reason;
        return event;
    }
}
