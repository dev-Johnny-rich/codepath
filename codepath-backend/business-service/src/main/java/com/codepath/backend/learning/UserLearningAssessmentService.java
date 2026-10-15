package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserLearningAssessmentService {
    private final JdbcClient jdbc;
    private final RestClient ai;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public UserLearningAssessmentService(JdbcClient jdbc, RestClient.Builder builder, ObjectMapper objectMapper,
                                         @Value("${codepath.ai-service-url}") String aiUrl,
                                         @Value("${codepath.internal-service-token}") String internalToken) {
        this.jdbc = jdbc;
        this.ai = builder.baseUrl(aiUrl).build();
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AssessmentView current(Long userId) {
        return jdbc.sql("""
                SELECT overall_level,score,summary,strengths_json,weaknesses_json,actions_json,
                       next_focus,model_name,evidence_json,generated_at
                FROM user_learning_assessment WHERE user_id=:userId
                """).param("userId", userId).query().listOfRows().stream().findFirst()
                .map(this::toView).orElse(null);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public AssessmentView generate(Long userId) {
        Map<String, Object> profile = jdbc.sql("""
                SELECT u.display_name,COALESCE(p.major,'') major,COALESCE(p.personality,'') personality,
                       COALESCE(p.habit,'') habit
                FROM app_user u LEFT JOIN user_profile p ON p.user_id=u.id WHERE u.id=:userId
                """).param("userId", userId).query().singleRow();

        Map<String, Object> metricsRow = jdbc.sql("""
                SELECT COUNT(*) total_submissions,
                       COALESCE(SUM(CASE WHEN total_count>0 AND passed_count=total_count THEN 1 ELSE 0 END),0) passed_submissions,
                       COALESCE(AVG(CASE WHEN total_count>0 THEN passed_count/total_count ELSE NULL END),0) average_pass_rate,
                       COUNT(DISTINCT DATE(created_at)) active_days
                FROM submission WHERE user_id=:userId AND created_at>=DATE_SUB(NOW(6),INTERVAL 30 DAY)
                """).param("userId", userId).query().singleRow();
        Map<String, Object> aiMetrics = jdbc.sql("""
                SELECT COUNT(*) ai_practice_count,COALESCE(AVG(score),0) ai_practice_average
                FROM ai_practice_evaluation WHERE user_id=:userId
                  AND created_at>=DATE_SUB(NOW(6),INTERVAL 30 DAY)
                """).param("userId", userId).query().singleRow();
        List<String> errors = jdbc.sql("""
                SELECT CONCAT(d.error_type,' (',COUNT(*),'次)') label
                FROM diagnosis d JOIN submission s ON s.id=d.submission_id
                WHERE s.user_id=:userId AND d.error_type<>'NONE'
                  AND d.created_at>=DATE_SUB(NOW(6),INTERVAL 30 DAY)
                GROUP BY d.error_type ORDER BY COUNT(*) DESC LIMIT 5
                """).param("userId", userId).query(String.class).list();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalSubmissions", number(metricsRow, "total_submissions").intValue());
        metrics.put("passedSubmissions", number(metricsRow, "passed_submissions").intValue());
        metrics.put("averagePassRate", bounded(number(metricsRow, "average_pass_rate").doubleValue()));
        metrics.put("activeDays", number(metricsRow, "active_days").intValue());
        metrics.put("aiPracticeCount", number(aiMetrics, "ai_practice_count").intValue());
        metrics.put("aiPracticeAverage", bounded(number(aiMetrics, "ai_practice_average").doubleValue()));
        metrics.put("recentErrors", errors);

        List<Map<String, Object>> mastery = jdbc.sql("""
                SELECT kp.name,sm.mastery,sm.evidence_count
                FROM student_mastery sm JOIN knowledge_point kp ON kp.id=sm.knowledge_point_id
                WHERE sm.user_id=:userId ORDER BY sm.evidence_count DESC,sm.mastery ASC LIMIT 30
                """).param("userId", userId).query().listOfRows().stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", row.get("name"));
            value.put("mastery", bounded(number(row, "mastery").doubleValue()));
            value.put("evidenceCount", number(row, "evidence_count").intValue());
            return value;
        }).toList();

        Map<String, Object> plan = jdbc.sql("""
                SELECT lp.title,c.name course_name,c.language,lp.goal,lp.current_level,
                  (SELECT COUNT(*) FROM learning_plan_stage st JOIN learning_plan_item i ON i.stage_id=st.id
                    WHERE st.plan_id=lp.id) total_items,
                  (SELECT COUNT(*) FROM learning_plan_stage st JOIN learning_plan_item i ON i.stage_id=st.id
                    WHERE st.plan_id=lp.id AND i.status='COMPLETED') completed_items
                FROM learning_plan lp JOIN course c ON c.id=lp.course_id
                WHERE lp.user_id=:userId AND lp.status='ACTIVE' ORDER BY lp.created_at DESC LIMIT 1
                """).param("userId", userId).query().listOfRows().stream().findFirst().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("title", row.get("title"));
            value.put("courseName", row.get("course_name"));
            value.put("language", row.get("language"));
            value.put("goal", row.get("goal"));
            value.put("currentLevel", row.get("current_level"));
            value.put("totalItems", number(row, "total_items").intValue());
            value.put("completedItems", number(row, "completed_items").intValue());
            return value;
        }).orElse(null);

        Map<String, Object> safeProfile = new LinkedHashMap<>();
        safeProfile.put("displayName", Objects.toString(profile.get("display_name"), "学习者"));
        safeProfile.put("major", Objects.toString(profile.get("major"), ""));
        safeProfile.put("personality", Objects.toString(profile.get("personality"), ""));
        safeProfile.put("habit", Objects.toString(profile.get("habit"), ""));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", safeProfile);
        payload.put("plan", plan);
        payload.put("metrics", metrics);
        payload.put("mastery", mastery);

        Map<String, Object> response;
        try {
            response = ai.post().uri("/v1/learning-assessments/generate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 学习评估暂时不可用，请稍后重试");
        }
        if (response == null || response.get("summary") == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 没有返回有效的学习评估");
        }
        Evidence evidence = evidence(metrics, plan, mastery.size());
        String strengths = json(response.getOrDefault("strengths", List.of()));
        String weaknesses = json(response.getOrDefault("weaknesses", List.of()));
        String actions = json(response.getOrDefault("actions", List.of()));
        String evidenceJson = json(evidence);
        jdbc.sql("""
                INSERT INTO user_learning_assessment(user_id,overall_level,score,summary,strengths_json,
                  weaknesses_json,actions_json,next_focus,model_name,evidence_json,generated_at)
                VALUES (:userId,:level,:score,:summary,:strengths,:weaknesses,:actions,:focus,:model,:evidence,NOW(6))
                ON DUPLICATE KEY UPDATE overall_level=VALUES(overall_level),score=VALUES(score),summary=VALUES(summary),
                  strengths_json=VALUES(strengths_json),weaknesses_json=VALUES(weaknesses_json),actions_json=VALUES(actions_json),
                  next_focus=VALUES(next_focus),model_name=VALUES(model_name),evidence_json=VALUES(evidence_json),generated_at=NOW(6)
                """).param("userId", userId)
                .param("level", text(response, "overallLevel", "数据不足"))
                .param("score", Math.max(0, Math.min(100, number(response, "score").intValue())))
                .param("summary", text(response, "summary", "当前学习数据不足。"))
                .param("strengths", strengths).param("weaknesses", weaknesses).param("actions", actions)
                .param("focus", text(response, "nextFocus", "完成一次编程练习"))
                .param("model", text(response, "modelName", "deepseek-chat"))
                .param("evidence", evidenceJson).update();
        return current(userId);
    }

    private Evidence evidence(Map<String, Object> metrics, Map<String, Object> plan, int masteryCount) {
        int total = ((Number) metrics.get("totalSubmissions")).intValue();
        int passed = ((Number) metrics.get("passedSubmissions")).intValue();
        double average = ((Number) metrics.get("averagePassRate")).doubleValue();
        int days = ((Number) metrics.get("activeDays")).intValue();
        int aiCount = ((Number) metrics.get("aiPracticeCount")).intValue();
        int completed = plan == null ? 0 : ((Number) plan.get("completedItems")).intValue();
        int planTotal = plan == null ? 0 : ((Number) plan.get("totalItems")).intValue();
        return new Evidence(total, passed, average, days, aiCount, completed, planTotal, masteryCount);
    }

    private AssessmentView toView(Map<String, Object> row) {
        try {
            return new AssessmentView(Objects.toString(row.get("overall_level")), number(row, "score").intValue(),
                    Objects.toString(row.get("summary")), strings(row.get("strengths_json")),
                    strings(row.get("weaknesses_json")), strings(row.get("actions_json")),
                    Objects.toString(row.get("next_focus")), Objects.toString(row.get("model_name"), ""),
                    objectMapper.readValue(Objects.toString(row.get("evidence_json")), Evidence.class),
                    dateTime(row.get("generated_at")));
        } catch (JsonProcessingException malformed) {
            throw new IllegalStateException("学习评估数据无法解析", malformed);
        }
    }

    private List<String> strings(Object raw) throws JsonProcessingException {
        return objectMapper.readValue(Objects.toString(raw), new TypeReference<>() {});
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("学习评估无法保存", error); }
    }

    private static Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number : 0;
    }

    private static String text(Map<String, Object> row, String key, String fallback) {
        String value = Objects.toString(row.get(key), "").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static double bounded(double value) { return Math.max(0, Math.min(1, value)); }

    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("学习评估时间格式无效");
    }

    public record Evidence(int totalSubmissions, int passedSubmissions, double averagePassRate,
                           int activeDays, int aiPracticeCount, int completedPlanItems,
                           int totalPlanItems, int masteryKnowledgePoints) {}

    public record AssessmentView(String overallLevel, int score, String summary, List<String> strengths,
                                 List<String> weaknesses, List<String> actions, String nextFocus,
                                 String modelName, Evidence evidence, LocalDateTime generatedAt) {}
}
