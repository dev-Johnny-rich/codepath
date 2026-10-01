package com.codepath.backend.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExecutionTestResultRequest(
        @NotNull Long testCaseId,
        @NotBlank String status,
        String actualOutput,
        String stderr,
        Long runtimeMs,
        Long memoryKb
) {
}
