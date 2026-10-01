package com.codepath.backend.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SelfPracticeRunRequest(
        @NotBlank String language,
        @NotBlank @Size(max = 100_000) String sourceCode,
        @Size(max = 100_000) String stdin
) {
}
