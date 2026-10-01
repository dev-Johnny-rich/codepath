package com.codepath.backend.diagnosis;

import com.codepath.backend.course.KnowledgePoint;

import java.util.List;

public record DiagnosisTaskResponse(
        Long submissionId,
        Long userId,
        Long problemId,
        String problemTitle,
        String problemDescription,
        String language,
        String sourceCode,
        String errorMessage,
        Integer passedCount,
        Integer totalCount,
        List<TestEvidence> testResults,
        List<KnowledgePoint> knowledgePoints
) {
    public record TestEvidence(Long testCaseId, String status, String actualOutput, String stderr) {
    }
}
