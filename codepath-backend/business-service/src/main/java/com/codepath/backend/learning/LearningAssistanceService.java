package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class LearningAssistanceService {
    private final JdbcClient jdbc;

    public LearningAssistanceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public LearningAssistanceView get(Long userId, Long submissionId) {
        List<Map<String, Object>> analyses = jdbc.sql("""
                SELECT p.course_id, d.error_type, d.root_cause, d.explanation,
                       d.next_action, d.model_name
                FROM submission s
                JOIN problem p ON p.id=s.problem_id
                LEFT JOIN diagnosis d ON d.submission_id=s.id
                WHERE s.id=:submissionId AND s.user_id=:userId
                """)
                .param("submissionId", submissionId)
                .param("userId", userId)
                .query().listOfRows();
        if (analyses.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "提交记录不存在");
        }
        Map<String, Object> analysis = analyses.getFirst();
        if (analysis.get("error_type") == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "代码分析仍在生成，请稍后重试");
        }
        String errorType = text(analysis.get("error_type"));

        List<KnowledgeGuidance> knowledge = jdbc.sql("""
                SELECT kp.id, kp.code, kp.name, kp.description,
                       COALESCE(me.mastery_before, sm.mastery, 0.1500) AS mastery_before,
                       COALESCE(me.mastery_after, sm.mastery, 0.1500) AS mastery_after,
                       COALESCE(me.delta_value, 0) AS delta_value
                FROM submission s
                JOIN problem_knowledge_point pk ON pk.problem_id=s.problem_id
                JOIN knowledge_point kp ON kp.id=pk.knowledge_point_id
                LEFT JOIN student_mastery sm ON sm.user_id=s.user_id AND sm.knowledge_point_id=kp.id
                LEFT JOIN mastery_event me ON me.submission_id=s.id AND me.knowledge_point_id=kp.id
                WHERE s.id=:submissionId AND s.user_id=:userId
                ORDER BY pk.is_primary DESC, pk.weight DESC
                """)
                .param("submissionId", submissionId)
                .param("userId", userId)
                .query().listOfRows().stream()
                .map(row -> {
                    BigDecimal after = decimal(row.get("mastery_after"));
                    String reason = focusReason(errorType, text(row.get("name")), after);
                    return new KnowledgeGuidance(
                            ((Number) row.get("id")).longValue(), text(row.get("code")),
                            text(row.get("name")), text(row.get("description")),
                            decimal(row.get("mastery_before")), after,
                            decimal(row.get("delta_value")), reason);
                }).toList();

        return new LearningAssistanceView(
                submissionId, errorType, text(analysis.get("root_cause")),
                text(analysis.get("explanation")), text(analysis.get("next_action")),
                text(analysis.get("model_name")), knowledge);
    }

    private static String focusReason(String errorType, String name, BigDecimal mastery) {
        int percent = mastery.multiply(BigDecimal.valueOf(100)).intValue();
        if ("NONE".equalsIgnoreCase(errorType)) {
            return name + "已在本次代码中正确运用，当前掌握度 " + percent + "%";
        }
        return "本次" + errorType + "错误与" + name + "相关，建议针对练习，当前掌握度 " + percent + "%";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(String.valueOf(value));
    }

    public record LearningAssistanceView(
            Long submissionId, String errorType, String rootCause, String explanation,
            String nextStep, String modelName, List<KnowledgeGuidance> knowledge) {}

    public record KnowledgeGuidance(
            Long id, String code, String name, String description,
            BigDecimal masteryBefore, BigDecimal masteryAfter, BigDecimal delta,
            String reason) {}
}
