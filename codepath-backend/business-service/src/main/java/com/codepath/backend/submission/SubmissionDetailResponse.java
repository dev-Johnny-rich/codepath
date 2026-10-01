package com.codepath.backend.submission;

import com.codepath.backend.diagnosis.Diagnosis;

import java.time.LocalDateTime;
import java.util.List;

public record SubmissionDetailResponse(
        Long id,
        Long problemId,
        String language,
        SubmissionStatus status,
        Integer passedCount,
        Integer totalCount,
        Long runtimeMs,
        Long memoryKb,
        String errorMessage,
        LocalDateTime createdAt,
        List<SubmissionTestResult> testResults,
        Diagnosis diagnosis
) {
}
