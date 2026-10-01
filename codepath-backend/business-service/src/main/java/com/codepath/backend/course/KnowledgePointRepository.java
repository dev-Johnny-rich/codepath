package com.codepath.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
    List<KnowledgePoint> findByCourseIdOrderBySortOrderAsc(Long courseId);

    @Query(value = """
            SELECT kp.*
            FROM knowledge_point kp
            JOIN problem_knowledge_point pkp ON pkp.knowledge_point_id = kp.id
            WHERE pkp.problem_id = :problemId
            ORDER BY pkp.is_primary DESC, pkp.weight DESC
            """, nativeQuery = true)
    List<KnowledgePoint> findByProblemId(Long problemId);
}
