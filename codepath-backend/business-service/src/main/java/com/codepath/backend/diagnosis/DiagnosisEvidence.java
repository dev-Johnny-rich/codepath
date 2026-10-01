package com.codepath.backend.diagnosis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diagnosis_evidence")
@Getter
@NoArgsConstructor
public class DiagnosisEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_reference")
    private String sourceReference;

    public static DiagnosisEvidence create(Long diagnosisId, String evidenceType, Integer lineNumber,
                                           String content, String sourceReference) {
        DiagnosisEvidence evidence = new DiagnosisEvidence();
        evidence.diagnosisId = diagnosisId;
        evidence.evidenceType = evidenceType;
        evidence.lineNumber = lineNumber;
        evidence.content = content;
        evidence.sourceReference = sourceReference;
        return evidence;
    }
}
