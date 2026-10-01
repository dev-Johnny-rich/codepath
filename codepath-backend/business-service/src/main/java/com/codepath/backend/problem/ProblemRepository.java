package com.codepath.backend.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    List<Problem> findByCourseIdAndStatusOrderByDifficultyAscIdAsc(Long courseId, String status);
    List<Problem> findByCourseIdOrderByIdDesc(Long courseId);
    boolean existsBySlug(String slug);
}
