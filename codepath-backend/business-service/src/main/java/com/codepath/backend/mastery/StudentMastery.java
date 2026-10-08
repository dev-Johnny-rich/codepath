package com.codepath.backend.mastery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_mastery")
@Getter
@NoArgsConstructor
public class StudentMastery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(nullable = false)
    private BigDecimal mastery;

    @Column(name = "evidence_count", nullable = false)
    private Integer evidenceCount;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt;

    @Column(name = "next_review_at")
    private LocalDateTime nextReviewAt;

    public static StudentMastery initialize(Long userId, Long knowledgePointId) {
        StudentMastery value = new StudentMastery();
        value.userId = userId;
        value.knowledgePointId = knowledgePointId;
        value.mastery = new BigDecimal("0.1500");
        value.evidenceCount = 0;
        return value;
    }

    public BigDecimal apply(BigDecimal delta) {
        BigDecimal before = mastery;
        mastery = mastery.add(delta).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        evidenceCount += 1;
        lastPracticedAt = LocalDateTime.now();
        nextReviewAt = LocalDateTime.now().plusDays(mastery.doubleValue() >= 0.8 ? 14 : 3);
        return before;
    }

    public BigDecimal applyBkt(BigDecimal nextMastery) {
        BigDecimal before = mastery;
        mastery = nextMastery.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        evidenceCount += 1;
        lastPracticedAt = LocalDateTime.now();
        nextReviewAt = LocalDateTime.now().plusDays(mastery.doubleValue() >= 0.8 ? 14 : 3);
        return before;
    }
}
