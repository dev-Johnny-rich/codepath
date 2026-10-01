package com.codepath.backend.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExecutionCallbackRequest(
        @NotNull Integer passedCount,
        @NotNull Integer totalCount,
        Long runtimeMs,
        Long memoryKb,
        String errorMessage,
        boolean infrastructureFailure,
        @Valid @NotNull List<ExecutionTestResultRequest> results
) {
}
