package com.codepath.backend.submission;

import java.time.LocalDateTime;

public record SubmissionHistoryItem(
        Long id,
        Long problemId,
        String language,
        SubmissionStatus status,
        Integer passedCount,
        Integer totalCount,
        Long runtimeMs,
        LocalDateTime createdAt
) {}
