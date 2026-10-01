package com.codepath.backend.learning;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wrong_book")
@Getter
@NoArgsConstructor
public class WrongBook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "problem_id", nullable = false) private Long problemId;
    @Column(name = "latest_submission_id") private Long latestSubmissionId;
    @Column(name = "error_type") private String errorType;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "mistake_count", nullable = false) private Integer mistakeCount;
    @Column(name = "last_mistake_at", nullable = false) private LocalDateTime lastMistakeAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    public static WrongBook create(Long userId, Long problemId, Long submissionId, String errorType) {
        WrongBook item = new WrongBook();
        item.userId = userId;
        item.problemId = problemId;
        item.latestSubmissionId = submissionId;
        item.errorType = errorType;
        item.status = "OPEN";
        item.mistakeCount = 1;
        item.lastMistakeAt = LocalDateTime.now();
        return item;
    }

    public void record(Long submissionId, String errorType) {
        this.latestSubmissionId = submissionId;
        this.errorType = errorType;
        this.status = "OPEN";
        this.mistakeCount += 1;
        this.lastMistakeAt = LocalDateTime.now();
        this.resolvedAt = null;
    }

    public void resolve() {
        this.status = "RESOLVED";
        this.resolvedAt = LocalDateTime.now();
    }

    public void update(String status, String notes) {
        this.status = status;
        this.notes = notes;
        this.resolvedAt = "RESOLVED".equals(status) ? LocalDateTime.now() : null;
    }
}
