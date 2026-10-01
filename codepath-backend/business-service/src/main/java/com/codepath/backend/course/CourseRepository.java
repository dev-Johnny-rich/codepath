package com.codepath.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByStatusOrderByIdAsc(String status);
    boolean existsByCode(String code);
}
