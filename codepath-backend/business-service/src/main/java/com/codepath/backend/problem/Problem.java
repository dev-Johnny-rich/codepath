package com.codepath.backend.problem;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problem")
@Getter
@NoArgsConstructor
public class Problem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "input_description", columnDefinition = "TEXT")
    private String inputDescription;

    @Column(name = "output_description", columnDefinition = "TEXT")
    private String outputDescription;

    @Column(name = "starter_code", columnDefinition = "MEDIUMTEXT")
    private String starterCode;

    @Column(name = "function_signature")
    private String functionSignature;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private Integer memoryLimitMb;

    @Column(nullable = false)
    private String status;

    public static Problem create(Long createdBy, Long courseId, String slug, String title,
                                 String description, String inputDescription, String outputDescription,
                                 String starterCode, String functionSignature, String language,
                                 Integer difficulty, Integer timeLimitMs, Integer memoryLimitMb,
                                 String status) {
        Problem problem = new Problem();
        problem.createdBy = createdBy;
        problem.courseId = courseId;
        problem.slug = slug;
        problem.title = title;
        problem.description = description;
        problem.inputDescription = inputDescription;
        problem.outputDescription = outputDescription;
        problem.starterCode = starterCode;
        problem.functionSignature = functionSignature;
        problem.language = language.toLowerCase();
        problem.difficulty = difficulty;
        problem.timeLimitMs = timeLimitMs;
        problem.memoryLimitMb = memoryLimitMb;
        problem.status = status;
        return problem;
    }

    public void update(String title, String description, String inputDescription,
                       String outputDescription, String starterCode, String functionSignature,
                       String language, Integer difficulty, Integer timeLimitMs,
                       Integer memoryLimitMb, String status) {
        this.title = title;
        this.description = description;
        this.inputDescription = inputDescription;
        this.outputDescription = outputDescription;
        this.starterCode = starterCode;
        this.functionSignature = functionSignature;
        this.language = language.toLowerCase();
        this.difficulty = difficulty;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.status = status;
    }
}
