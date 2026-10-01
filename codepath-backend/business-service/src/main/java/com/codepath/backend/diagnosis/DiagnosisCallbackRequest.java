package com.codepath.backend.diagnosis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record DiagnosisCallbackRequest(
        @NotBlank String errorType,
        @NotBlank String rootCause,
        @NotBlank String explanation,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        @Min(1) @Max(5) Integer hintLevel,
        String nextAction,
        String modelName,
        @NotBlank String promptVersion,
        @Valid @NotNull List<Evidence> evidence
) {
    public record Evidence(
            @NotBlank String evidenceType,
            Integer lineNumber,
            @NotBlank String content,
            String sourceReference
    ) {
    }
}
