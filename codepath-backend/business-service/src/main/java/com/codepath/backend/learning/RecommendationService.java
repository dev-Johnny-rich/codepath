package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class RecommendationService {
    private final JdbcTemplate jdbc;

    public RecommendationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<RecommendationView> generate(Long userId, Long courseId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.id, p.title, p.difficulty,
                       coalesce(avg(sm.mastery), 0) as avg_mastery,
                       count(distinct s.id) as attempts
                from problem p
                left join problem_knowledge_point pk on pk.problem_id = p.id
                left join student_mastery sm on sm.knowledge_point_id = pk.knowledge_point_id and sm.user_id = ?
                left join submission s on s.problem_id = p.id and s.user_id = ?
                where p.course_id = ? and p.status = 'PUBLISHED'
                  and not exists (
                    select 1 from submission passed
                    where passed.problem_id = p.id and passed.user_id = ?
                      and passed.status = 'COMPLETED' and passed.total_count > 0
                      and passed.passed_count = passed.total_count
                  )
                group by p.id, p.title, p.difficulty
                """, userId, userId, courseId, userId);

        Double overall = jdbc.queryForObject("""
                select coalesce(avg(sm.mastery), 0) from student_mastery sm
                join knowledge_point kp on kp.id = sm.knowledge_point_id
                where sm.user_id = ? and kp.course_id = ?
                """, Double.class, userId, courseId);
        int targetDifficulty = 1 + (int) Math.round((overall == null ? 0 : overall) * 2);
        List<RecommendationView> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long problemId = ((Number) row.get("id")).longValue();
            int difficulty = ((Number) row.get("difficulty")).intValue();
            double mastery = ((Number) row.get("avg_mastery")).doubleValue();
            int attempts = ((Number) row.get("attempts")).intValue();
            double weakness = 1 - mastery;
            double difficultyFit = 1 - Math.min(1, Math.abs(difficulty - targetDifficulty) / 4.0);
            double novelty = attempts == 0 ? 1 : 0.35;
            BigDecimal score = BigDecimal.valueOf(0.55 * weakness + 0.25 * difficultyFit + 0.20 * novelty)
                    .setScale(5, RoundingMode.HALF_UP);
            Long knowledgePointId = weakestKnowledgePoint(userId, problemId);
            String reason = String.format("薄弱度 %.0f%%，难度匹配 %.0f%%，%s",
                    weakness * 100, difficultyFit * 100, attempts == 0 ? "尚未练习" : "建议再次巩固");
            result.add(new RecommendationView(problemId, String.valueOf(row.get("title")),
                    knowledgePointId, score, reason));
        }
        result.sort(Comparator.comparing(RecommendationView::score).reversed());
        jdbc.update("delete from recommendation where user_id = ? and course_id = ? and status = 'PENDING'",
                userId, courseId);
        for (RecommendationView item : result.stream().limit(20).toList()) {
            jdbc.update("insert into recommendation " +
                            "(user_id, course_id, problem_id, knowledge_point_id, score, reason, status) " +
                            "values (?, ?, ?, ?, ?, ?, 'PENDING')",
                    userId, courseId, item.problemId(), item.knowledgePointId(), item.score(), item.reason());
        }
        return result.stream().limit(20).toList();
    }

    @Transactional
    public RecommendationView next(Long userId, Long courseId) {
        return generate(userId, courseId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "当前课程暂无可推荐题目"));
    }

    private Long weakestKnowledgePoint(Long userId, Long problemId) {
        List<Long> ids = jdbc.query("""
                select pk.knowledge_point_id from problem_knowledge_point pk
                left join student_mastery sm on sm.knowledge_point_id = pk.knowledge_point_id and sm.user_id = ?
                where pk.problem_id = ? order by coalesce(sm.mastery, 0) asc, pk.weight desc limit 1
                """, (rs, rowNum) -> rs.getLong(1), userId, problemId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public record RecommendationView(Long problemId, String title, Long knowledgePointId,
                                     BigDecimal score, String reason) {}
}
