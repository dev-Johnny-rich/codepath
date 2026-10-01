package com.codepath.backend.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubmissionRequest(
        @NotBlank String language,
        @NotBlank @Size(max = 100_000) String sourceCode
) {
}
