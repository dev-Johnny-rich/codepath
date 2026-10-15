package com.codepath.backend.submission;

import com.codepath.backend.common.BusinessException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;

@Entity
@Table(name = "submission")
@Getter
@NoArgsConstructor
public class Submission {
    private static final Map<SubmissionStatus, EnumSet<SubmissionStatus>> TRANSITIONS = Map.of(
            SubmissionStatus.QUEUED, EnumSet.of(SubmissionStatus.PREPARING, SubmissionStatus.FAILED),
            SubmissionStatus.PREPARING, EnumSet.of(SubmissionStatus.COMPILING, SubmissionStatus.FAILED),
            SubmissionStatus.COMPILING, EnumSet.of(SubmissionStatus.RUNNING, SubmissionStatus.ANALYZING, SubmissionStatus.FAILED),
            SubmissionStatus.RUNNING, EnumSet.of(SubmissionStatus.ANALYZING, SubmissionStatus.FAILED),
            SubmissionStatus.ANALYZING, EnumSet.of(SubmissionStatus.DIAGNOSING, SubmissionStatus.COMPLETED, SubmissionStatus.FAILED),
            SubmissionStatus.DIAGNOSING, EnumSet.of(SubmissionStatus.UPDATING_MASTERY, SubmissionStatus.FAILED),
            SubmissionStatus.UPDATING_MASTERY, EnumSet.of(SubmissionStatus.COMPLETED, SubmissionStatus.FAILED),
            SubmissionStatus.COMPLETED, EnumSet.noneOf(SubmissionStatus.class),
            SubmissionStatus.FAILED, EnumSet.noneOf(SubmissionStatus.class)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "ai_generated_problem_id")
    private Long aiGeneratedProblemId;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "runtime_ms")
    private Long runtimeMs;

    @Column(name = "memory_kb")
    private Long memoryKb;

    @Column(name = "error_message", columnDefinition = "MEDIUMTEXT")
    private String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static Submission queued(Long userId, Long problemId, String language, String sourceCode, int totalCount) {
        Submission submission = new Submission();
        submission.userId = userId;
        submission.problemId = problemId;
        submission.language = language;
        submission.sourceCode = sourceCode;
        submission.status = SubmissionStatus.QUEUED;
        submission.passedCount = 0;
        submission.totalCount = totalCount;
        return submission;
    }

    public static Submission queuedForAiPractice(Long userId, Long generatedProblemId, String language,
                                                 String sourceCode, int totalCount) {
        Submission submission = new Submission();
        submission.userId = userId;
        submission.aiGeneratedProblemId = generatedProblemId;
        submission.language = language;
        submission.sourceCode = sourceCode;
        submission.status = SubmissionStatus.QUEUED;
        submission.passedCount = 0;
        submission.totalCount = totalCount;
        return submission;
    }

    public void transitionTo(SubmissionStatus next) {
        if (!TRANSITIONS.get(status).contains(next)) {
            throw new BusinessException(HttpStatus.CONFLICT, "非法提交状态变化：" + status + " -> " + next);
        }
        status = next;
        if (next == SubmissionStatus.PREPARING) {
            startedAt = LocalDateTime.now();
        }
        if (next == SubmissionStatus.COMPLETED || next == SubmissionStatus.FAILED) {
            completedAt = LocalDateTime.now();
        }
    }

    public void applyExecutionSummary(int passedCount, int totalCount, Long runtimeMs,
                                      Long memoryKb, String errorMessage) {
        this.passedCount = passedCount;
        this.totalCount = totalCount;
        this.runtimeMs = runtimeMs;
        this.memoryKb = memoryKb;
        this.errorMessage = errorMessage;
    }
}
