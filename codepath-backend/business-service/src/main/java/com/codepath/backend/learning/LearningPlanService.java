package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LearningPlanService {
    private static final Set<String> TASK_TYPES = Set.of("LEARN", "PRACTICE", "REVIEW", "ASSESSMENT");
    private final JdbcClient jdbc;
    private final RestClient ai;
    private final String internalToken;

    public LearningPlanService(JdbcClient jdbc, RestClient.Builder builder,
                               @Value("${codepath.ai-service-url}") String aiUrl,
                               @Value("${codepath.internal-service-token}") String internalToken) {
        this.jdbc = jdbc;
        this.ai = builder.baseUrl(aiUrl).build();
        this.internalToken = internalToken;
    }

    @Transactional
    public LearningPlanView generate(Long userId, GenerateRequest request) {
        LocalDateTime nextAllowed = jdbc.sql("""
                SELECT regenerate_available_at FROM learning_plan
                WHERE user_id=:userId AND status='ACTIVE' ORDER BY created_at DESC LIMIT 1
                """).param("userId", userId).query(LocalDateTime.class).optional().orElse(null);
        if (nextAllowed != null && nextAllowed.isAfter(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "每 168 小时可重新生成一次，下次可用时间：" +
                            nextAllowed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
        Map<String, Object> course = jdbc.sql("""
                SELECT id,name,language FROM course
                WHERE id=:courseId AND status IN ('PUBLISHED','PLANNING_ONLY')
                """).param("courseId", request.courseId()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));

        List<Map<String, Object>> knowledgeRows = jdbc.sql("""
                SELECT kp.id,kp.name,kp.description,kp.difficulty,
                       COALESCE(sm.mastery,0.1500) AS mastery
                FROM knowledge_point kp
                LEFT JOIN student_mastery sm ON sm.knowledge_point_id=kp.id AND sm.user_id=:userId
                WHERE kp.course_id=:courseId ORDER BY kp.sort_order,kp.id
                """).param("userId", userId).param("courseId", request.courseId()).query().listOfRows();
        if (knowledgeRows.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "课程尚未配置知识点");

        List<Map<String, Object>> problemRows = jdbc.sql("""
                SELECT p.id,p.title,p.difficulty,
                       COALESCE(GROUP_CONCAT(pk.knowledge_point_id ORDER BY pk.knowledge_point_id),'') AS knowledge_ids
                FROM problem p
                LEFT JOIN problem_knowledge_point pk ON pk.problem_id=p.id
                WHERE p.course_id=:courseId AND p.status='PUBLISHED'
                GROUP BY p.id,p.title,p.difficulty ORDER BY p.difficulty,p.id
                """).param("courseId", request.courseId()).query().listOfRows();

        List<Map<String, Object>> problems = problemRows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("title", row.get("title"));
            item.put("difficulty", row.get("difficulty"));
            item.put("knowledgePointIds", parseIds(Objects.toString(row.get("knowledge_ids"), "")));
            return item;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseName", course.get("name"));
        payload.put("language", course.get("language"));
        payload.put("goal", request.goal());
        payload.put("currentLevel", request.currentLevel());
        payload.put("daysPerWeek", request.daysPerWeek());
        payload.put("minutesPerDay", request.minutesPerDay());
        payload.put("durationWeeks", request.durationWeeks());
        payload.put("knowledgePoints", knowledgeRows);
        payload.put("problems", problems);

        Map response;
        try {
            response = ai.post().uri("/v1/learning-plans/generate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 学习计划暂时不可用，请稍后重试");
        }
        if (response == null || !(response.get("stages") instanceof List<?> stages) || stages.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 没有返回有效的学习计划");
        }

        jdbc.sql("UPDATE learning_plan SET status='ARCHIVED' WHERE user_id=:userId AND status='ACTIVE'")
                .param("userId", userId).update();
        jdbc.sql("""
                INSERT INTO learning_plan(user_id,course_id,goal,current_level,days_per_week,
                  minutes_per_day,duration_weeks,title,summary,status,model_name,regenerate_available_at)
                VALUES (:userId,:courseId,:goal,:level,:days,:minutes,:weeks,:title,:summary,
                  'ACTIVE',:model,DATE_ADD(NOW(6),INTERVAL 7 DAY))
                """).param("userId", userId).param("courseId", request.courseId())
                .param("goal", request.goal()).param("level", request.currentLevel())
                .param("days", request.daysPerWeek()).param("minutes", request.minutesPerDay())
                .param("weeks", request.durationWeeks()).param("title", text(response, "title", "AI 个性化学习计划"))
                .param("summary", text(response, "summary", "根据当前掌握度生成的滚动学习计划。"))
                .param("model", text(response, "modelName", "deepseek-chat")).update();
        Long planId = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();

        Set<Long> validKnowledge = new HashSet<>(knowledgeRows.stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList());
        Set<Long> validProblems = new HashSet<>(problemRows.stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList());
        int stageOrder = 0;
        int itemCount = 0;
        for (Object rawStage : stages) {
            if (!(rawStage instanceof Map stage)) continue;
            int weekNumber = boundedInt(stage.get("weekNumber"), stageOrder + 1, 1, request.durationWeeks());
            jdbc.sql("""
                    INSERT INTO learning_plan_stage(plan_id,week_number,title,objective,sort_order)
                    VALUES (:planId,:week,:title,:objective,:sortOrder)
                    """).param("planId", planId).param("week", weekNumber)
                    .param("title", text(stage, "title", "第 " + weekNumber + " 周"))
                    .param("objective", text(stage, "objective", "完成本阶段学习任务。"))
                    .param("sortOrder", stageOrder++).update();
            Long stageId = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
            Object rawItems = stage.get("items");
            if (!(rawItems instanceof List<?> items)) continue;
            int itemOrder = 0;
            for (Object rawItem : items) {
                if (!(rawItem instanceof Map item)) continue;
                Long knowledgeId = validId(item.get("knowledgePointId"), validKnowledge);
                Long problemId = validId(item.get("problemId"), validProblems);
                String taskType = text(item, "taskType", "LEARN").toUpperCase();
                if (!TASK_TYPES.contains(taskType)) taskType = "LEARN";
                double target = boundedDouble(item.get("targetMastery"), 0.8, 0.5, 0.99);
                jdbc.sql("""
                        INSERT INTO learning_plan_item(stage_id,knowledge_point_id,problem_id,task_type,
                          title,description,estimated_minutes,target_mastery,status,sort_order)
                        VALUES (:stageId,:knowledgeId,:problemId,:taskType,:title,:description,
                          :minutes,:target,'PENDING',:sortOrder)
                        """).param("stageId", stageId).param("knowledgeId", knowledgeId)
                        .param("problemId", problemId).param("taskType", taskType)
                        .param("title", text(item, "title", "学习任务"))
                        .param("description", text(item, "description", "按计划完成本项任务。"))
                        .param("minutes", boundedInt(item.get("estimatedMinutes"), 30, 5, 300))
                        .param("target", BigDecimal.valueOf(target)).param("sortOrder", itemOrder++).update();
                itemCount++;
            }
        }
        if (itemCount == 0) throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 返回的计划不包含有效任务");
        return findById(userId, planId);
    }

    @Transactional
    public LearningPlanView current(Long userId, Long courseId) {
        Long id = jdbc.sql("""
                SELECT id FROM learning_plan WHERE user_id=:userId AND course_id=:courseId AND status='ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """).param("userId", userId).param("courseId", courseId).query(Long.class).optional().orElse(null);
        return id == null ? null : findById(userId, id);
    }

    @Transactional
    public LearningPlanView active(Long userId) {
        Long id = jdbc.sql("""
                SELECT id FROM learning_plan WHERE user_id=:userId AND status='ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """).param("userId", userId).query(Long.class).optional().orElse(null);
        return id == null ? null : findById(userId, id);
    }

    @Transactional(readOnly = true)
    public List<TrackView> tracks() {
        return jdbc.sql("""
                SELECT id,name,language,description FROM course
                WHERE status IN ('PUBLISHED','PLANNING_ONLY') ORDER BY id
                """).query().listOfRows().stream().map(row -> new TrackView(
                ((Number) row.get("id")).longValue(), Objects.toString(row.get("name")),
                Objects.toString(row.get("language")), Objects.toString(row.get("description"), ""))).toList();
    }

    @Transactional
    public LearningPlanView setCompleted(Long userId, Long itemId, boolean completed) {
        Map<String, Object> owner = jdbc.sql("""
                SELECT p.id AS plan_id,i.task_type,i.problem_id FROM learning_plan_item i
                JOIN learning_plan_stage s ON s.id=i.stage_id JOIN learning_plan p ON p.id=s.plan_id
                WHERE i.id=:itemId AND p.user_id=:userId AND p.status='ACTIVE'
                """).param("itemId", itemId).param("userId", userId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "计划任务不存在"));
        String taskType = Objects.toString(owner.get("task_type"));
        if (completed && ("PRACTICE".equals(taskType) || "ASSESSMENT".equals(taskType))) {
            throw new BusinessException(HttpStatus.CONFLICT, "编程练习和测评必须通过运行评估后完成");
        }
        if (completed) {
            jdbc.sql("""
                    UPDATE learning_plan_item SET status='COMPLETED',completion_source='USER_CONFIRMED',
                      completed_at=NOW(6) WHERE id=:itemId
                    """)
                    .param("itemId", itemId).update();
        } else {
            jdbc.sql("""
                    UPDATE learning_plan_item SET status='PENDING',completion_source=NULL,
                      completed_at=NULL WHERE id=:itemId
                    """)
                    .param("itemId", itemId).update();
        }
        return findById(userId, ((Number) owner.get("plan_id")).longValue());
    }

    @Transactional(readOnly = true)
    public KnowledgeExplanationView explain(Long userId, Long itemId) {
        Map<String, Object> item = jdbc.sql("""
                SELECT i.task_type,i.title AS item_title,i.description AS item_description,
                       kp.name AS knowledge_name,kp.description AS knowledge_description,
                       c.name AS course_name,c.language,p.current_level,
                       COALESCE(sm.mastery,0.1500) AS mastery
                FROM learning_plan_item i
                JOIN learning_plan_stage st ON st.id=i.stage_id
                JOIN learning_plan p ON p.id=st.plan_id
                JOIN course c ON c.id=p.course_id
                LEFT JOIN knowledge_point kp ON kp.id=i.knowledge_point_id
                LEFT JOIN student_mastery sm ON sm.user_id=p.user_id
                  AND sm.knowledge_point_id=i.knowledge_point_id
                WHERE i.id=:itemId AND p.user_id=:userId AND p.status='ACTIVE'
                """).param("itemId", itemId).param("userId", userId)
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "计划任务不存在"));
        if (!"LEARN".equals(Objects.toString(item.get("task_type")))) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有学习任务可以生成知识讲解");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseName", text(item, "course_name", "编程课程"));
        payload.put("language", text(item, "language", "Python"));
        payload.put("currentLevel", text(item, "current_level", "BEGINNER"));
        payload.put("itemTitle", text(item, "item_title", "知识学习"));
        payload.put("itemDescription", text(item, "item_description", "学习本知识点。"));
        payload.put("knowledgePointName", text(item, "knowledge_name",
                text(item, "item_title", "知识点")));
        payload.put("knowledgePointDescription", item.get("knowledge_description"));
        payload.put("mastery", item.get("mastery"));

        Map response;
        try {
            response = ai.post().uri("/v1/learning-plans/explain")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 知识讲解暂时不可用，请稍后重试");
        }
        if (response == null || !(response.get("sections") instanceof List<?> rawSections)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 没有返回有效的知识讲解");
        }
        List<ExplanationSection> sections = rawSections.stream()
                .filter(Map.class::isInstance).map(Map.class::cast)
                .map(section -> new ExplanationSection(
                        text(section, "title", "知识讲解"),
                        text(section, "content", ""),
                        section.get("code") == null ? null : section.get("code").toString()))
                .filter(section -> !section.content().isBlank()).toList();
        List<String> keyPoints = response.get("keyPoints") instanceof List<?> points
                ? points.stream().filter(Objects::nonNull).map(Object::toString)
                    .filter(point -> !point.isBlank()).toList()
                : List.of();
        return new KnowledgeExplanationView(
                text(response, "title", text(item, "item_title", "知识讲解")),
                text(response, "overview", text(item, "item_description", "")),
                sections, keyPoints,
                text(response, "checkQuestion", "请用自己的话复述这个知识点。"),
                text(response, "modelName", "deepseek-chat"));
    }

    private LearningPlanView findById(Long userId, Long planId) {
        syncEvidence(userId, planId);
        Map<String, Object> plan = jdbc.sql("""
                SELECT p.*,c.name AS course_name FROM learning_plan p JOIN course c ON c.id=p.course_id
                WHERE p.id=:planId AND p.user_id=:userId
                """).param("planId", planId).param("userId", userId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "学习计划不存在"));
        List<Map<String, Object>> stageRows = jdbc.sql("""
                SELECT * FROM learning_plan_stage WHERE plan_id=:planId ORDER BY sort_order,id
                """).param("planId", planId).query().listOfRows();
        List<StageView> stages = new ArrayList<>();
        int total = 0;
        int completed = 0;
        int totalMinutes = 0;
        int completedMinutes = 0;
        for (Map<String, Object> stage : stageRows) {
            Long stageId = ((Number) stage.get("id")).longValue();
            List<ItemView> items = jdbc.sql("""
                    SELECT i.*,kp.name AS knowledge_name,p.title AS problem_title
                    FROM learning_plan_item i
                    LEFT JOIN knowledge_point kp ON kp.id=i.knowledge_point_id
                    LEFT JOIN problem p ON p.id=i.problem_id
                    WHERE i.stage_id=:stageId ORDER BY i.sort_order,i.id
                    """).param("stageId", stageId).query().listOfRows().stream().map(row -> new ItemView(
                            ((Number) row.get("id")).longValue(), Objects.toString(row.get("task_type")),
                            Objects.toString(row.get("title")), Objects.toString(row.get("description")),
                            numberLong(row.get("knowledge_point_id")), Objects.toString(row.get("knowledge_name"), ""),
                            numberLong(row.get("problem_id")), Objects.toString(row.get("problem_title"), ""),
                            ((Number) row.get("estimated_minutes")).intValue(),
                            ((Number) row.get("target_mastery")).doubleValue(),
                            Objects.toString(row.get("status")))).toList();
            total += items.size();
            completed += (int) items.stream().filter(item -> "COMPLETED".equals(item.status())).count();
            totalMinutes += items.stream().mapToInt(ItemView::estimatedMinutes).sum();
            completedMinutes += items.stream().filter(item -> "COMPLETED".equals(item.status()))
                    .mapToInt(ItemView::estimatedMinutes).sum();
            stages.add(new StageView(stageId, ((Number) stage.get("week_number")).intValue(),
                    Objects.toString(stage.get("title")), Objects.toString(stage.get("objective")), items));
        }
        double ability = jdbc.sql("""
                SELECT COALESCE(AVG(COALESCE(sm.mastery,0.1500)),0.1500)
                FROM knowledge_point kp LEFT JOIN student_mastery sm
                  ON sm.knowledge_point_id=kp.id AND sm.user_id=:userId
                WHERE kp.course_id=:courseId
                """).param("userId", userId).param("courseId", plan.get("course_id"))
                .query(Double.class).optional().orElse(0.15);
        return new LearningPlanView(planId, ((Number) plan.get("course_id")).longValue(),
                Objects.toString(plan.get("course_name")), Objects.toString(plan.get("title")),
                Objects.toString(plan.get("summary")), Objects.toString(plan.get("goal")),
                Objects.toString(plan.get("current_level")), ((Number) plan.get("days_per_week")).intValue(),
                ((Number) plan.get("minutes_per_day")).intValue(), ((Number) plan.get("duration_weeks")).intValue(),
                total, completed, totalMinutes, completedMinutes,
                totalMinutes == 0 ? 0 : (double) completedMinutes / totalMinutes, ability,
                plan.get("regenerate_available_at") == null ? null : plan.get("regenerate_available_at").toString(),
                plan.get("regenerate_available_at") == null ||
                        !((LocalDateTime) plan.get("regenerate_available_at")).isAfter(LocalDateTime.now()),
                Objects.toString(plan.get("model_name")), stages);
    }

    private void syncEvidence(Long userId, Long planId) {
        jdbc.sql("""
                UPDATE learning_plan_item i
                JOIN learning_plan_stage st ON st.id=i.stage_id
                LEFT JOIN student_mastery sm ON sm.user_id=:userId AND sm.knowledge_point_id=i.knowledge_point_id
                SET i.status='COMPLETED',i.completion_source='SYSTEM_EVIDENCE',
                    i.completed_at=COALESCE(i.completed_at,NOW(6))
                WHERE st.plan_id=:planId AND i.status='PENDING' AND (
                  (i.problem_id IS NOT NULL AND EXISTS(
                    SELECT 1 FROM submission s WHERE s.user_id=:userId AND s.problem_id=i.problem_id
                      AND s.total_count>0 AND s.passed_count=s.total_count AND s.status='COMPLETED'))
                  OR (i.knowledge_point_id IS NOT NULL AND sm.evidence_count>0 AND sm.mastery>=i.target_mastery)
                )
                """).param("userId", userId).param("planId", planId).update();
    }

    private static List<Long> parseIds(String value) {
        if (value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).filter(part -> !part.isBlank()).map(Long::valueOf).toList();
    }

    private static Long validId(Object value, Set<Long> valid) {
        if (!(value instanceof Number number)) return null;
        long id = number.longValue();
        return valid.contains(id) ? id : null;
    }

    private static Long numberLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static int boundedInt(Object value, int fallback, int min, int max) {
        int result = value instanceof Number number ? number.intValue() : fallback;
        return Math.max(min, Math.min(max, result));
    }

    private static double boundedDouble(Object value, double fallback, double min, double max) {
        double result = value instanceof Number number ? number.doubleValue() : fallback;
        return Math.max(min, Math.min(max, result));
    }

    private static String text(Map map, String key, String fallback) {
        Object value = map.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    public record GenerateRequest(@NotNull Long courseId,
                                  @NotBlank @Size(max=500) String goal,
                                  @NotBlank @Size(max=32) String currentLevel,
                                  @Min(1) @Max(7) int daysPerWeek,
                                  @Min(15) @Max(300) int minutesPerDay,
                                  @Min(1) @Max(24) int durationWeeks) {}
    public record CompletionRequest(boolean completed) {}
    public record ExplanationSection(String title, String content, String code) {}
    public record KnowledgeExplanationView(String title, String overview,
                                           List<ExplanationSection> sections,
                                           List<String> keyPoints, String checkQuestion,
                                           String modelName) {}
    public record TrackView(Long id, String name, String language, String description) {}
    public record LearningPlanView(Long id, Long courseId, String courseName, String title, String summary,
                                   String goal, String currentLevel, int daysPerWeek, int minutesPerDay,
                                   int durationWeeks, int totalItems, int completedItems,
                                   int totalMinutes, int completedMinutes,
                                   double planProgress, double abilityMastery,
                                   String nextRegenerationAt, boolean canRegenerate, String modelName,
                                   List<StageView> stages) {}
    public record StageView(Long id, int weekNumber, String title, String objective, List<ItemView> items) {}
    public record ItemView(Long id, String taskType, String title, String description,
                           Long knowledgePointId, String knowledgePointName,
                           Long problemId, String problemTitle, int estimatedMinutes,
                           double targetMastery, String status) {}
}
