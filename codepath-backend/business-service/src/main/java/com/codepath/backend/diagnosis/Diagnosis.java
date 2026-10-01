package com.codepath.backend.diagnosis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosis")
@Getter
@NoArgsConstructor
public class Diagnosis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private Long submissionId;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String explanation;

    private BigDecimal confidence;

    @Column(name = "hint_level", nullable = false)
    private Integer hintLevel;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Diagnosis create(Long submissionId, String errorType, String rootCause,
                                   String explanation, BigDecimal confidence, Integer hintLevel,
                                   String nextAction, String modelName, String promptVersion) {
        Diagnosis diagnosis = new Diagnosis();
        diagnosis.submissionId = submissionId;
        diagnosis.errorType = errorType;
        diagnosis.rootCause = rootCause;
        diagnosis.explanation = explanation;
        diagnosis.confidence = confidence;
        diagnosis.hintLevel = hintLevel;
        diagnosis.nextAction = nextAction;
        diagnosis.modelName = modelName;
        diagnosis.promptVersion = promptVersion;
        diagnosis.status = "COMPLETED";
        return diagnosis;
    }
}
