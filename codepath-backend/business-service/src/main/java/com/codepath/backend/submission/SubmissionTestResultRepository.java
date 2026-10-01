package com.codepath.backend.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, Long> {
    List<SubmissionTestResult> findBySubmissionIdOrderByTestCaseIdAsc(Long submissionId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SubmissionTestResult result where result.submissionId = :submissionId")
    void deleteAllForSubmission(@Param("submissionId") Long submissionId);
}
