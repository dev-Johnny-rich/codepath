package com.codepath.backend.mastery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentMasteryRepository extends JpaRepository<StudentMastery, Long> {
    Optional<StudentMastery> findByUserIdAndKnowledgePointId(Long userId, Long knowledgePointId);
    List<StudentMastery> findByUserId(Long userId);

    @Query(value = """
            SELECT sm.* FROM student_mastery sm
            JOIN knowledge_point kp ON kp.id = sm.knowledge_point_id
            WHERE sm.user_id = :userId AND kp.course_id = :courseId
            ORDER BY kp.sort_order
            """, nativeQuery = true)
    List<StudentMastery> findByUserIdAndCourseId(Long userId, Long courseId);
}
