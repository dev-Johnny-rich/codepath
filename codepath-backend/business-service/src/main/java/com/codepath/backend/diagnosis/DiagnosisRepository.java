package com.codepath.backend.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    Optional<Diagnosis> findBySubmissionId(Long submissionId);
}
