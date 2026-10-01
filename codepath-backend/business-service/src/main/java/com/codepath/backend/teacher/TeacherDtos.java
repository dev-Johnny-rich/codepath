package com.codepath.backend.teacher;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class TeacherDtos {
    private TeacherDtos() {}

    public record CourseRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 32) String language,
            String description,
            @NotBlank String status
    ) {}

    public record ProblemRequest(
            @NotNull Long courseId,
            @NotBlank @Size(max = 96) String slug,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String description,
            String inputDescription,
            String outputDescription,
            String starterCode,
            String functionSignature,
            @NotBlank String language,
            @Min(1) @Max(5) Integer difficulty,
            @Min(100) @Max(30000) Integer timeLimitMs,
            @Min(16) @Max(1024) Integer memoryLimitMb,
            @NotBlank String status
    ) {}

    public record TestCaseRequest(
            String inputData,
            @NotNull String expectedOutput,
            boolean hidden,
            @NotNull @DecimalMin("0.01") BigDecimal weight,
            @NotNull @Min(0) Integer sortOrder
    ) {}

    public record KnowledgeBindingRequest(
            @NotNull Long knowledgePointId,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weight,
            boolean primary
    ) {}
}
