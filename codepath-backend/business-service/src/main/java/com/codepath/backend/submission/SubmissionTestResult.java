package com.codepath.backend.submission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission_test_result")
@Getter
@NoArgsConstructor
public class SubmissionTestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "test_case_id", nullable = false)
    private Long testCaseId;

    @Column(nullable = false)
    private String status;

    @Column(name = "actual_output", columnDefinition = "MEDIUMTEXT")
    private String actualOutput;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String stderr;

    @Column(name = "runtime_ms")
    private Long runtimeMs;

    @Column(name = "memory_kb")
    private Long memoryKb;

    public static SubmissionTestResult create(Long submissionId, Long testCaseId, String status,
                                              String actualOutput, String stderr, Long runtimeMs, Long memoryKb) {
        SubmissionTestResult result = new SubmissionTestResult();
        result.submissionId = submissionId;
        result.testCaseId = testCaseId;
        result.status = status;
        result.actualOutput = actualOutput;
        result.stderr = stderr;
        result.runtimeMs = runtimeMs;
        result.memoryKb = memoryKb;
        return result;
    }
}
