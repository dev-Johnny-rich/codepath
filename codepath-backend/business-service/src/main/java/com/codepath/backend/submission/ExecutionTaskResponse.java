package com.codepath.backend.submission;

import java.util.List;

public record ExecutionTaskResponse(
        Long submissionId,
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<TestCaseData> testCases
) {
    public record TestCaseData(Long id, String input, String expectedOutput) {
    }
}
