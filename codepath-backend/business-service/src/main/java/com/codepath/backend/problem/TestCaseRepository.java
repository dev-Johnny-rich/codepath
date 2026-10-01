package com.codepath.backend.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdOrderBySortOrderAsc(Long problemId);
    List<TestCase> findByProblemIdAndHiddenFalseOrderBySortOrderAsc(Long problemId);
    long countByProblemId(Long problemId);
    long countByProblemIdAndHiddenFalse(Long problemId);
}
