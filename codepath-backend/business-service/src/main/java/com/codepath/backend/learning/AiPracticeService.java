package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import com.codepath.backend.mastery.MasteryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AiPracticeService {
    private final JdbcClient jdbc;
    private final RestClient ai;
    private final String internalToken;
    private final MasteryService masteryService;

    public AiPracticeService(JdbcClient jdbc, RestClient.Builder builder,
                             @Value("${codepath.ai-service-url}") String aiUrl,
                             @Value("${codepath.internal-service-token}") String internalToken,
                             MasteryService masteryService) {
        this.jdbc = jdbc;
        this.ai = builder.baseUrl(aiUrl).build();
        this.internalToken = internalToken;
        this.masteryService = masteryService;
    }

    @Transactional
    public GeneratedPractice generate(Long userId, Long submissionId) {
        List<Map<String, Object>> analyses = jdbc.sql("""
                SELECT s.id, s.language, p.title, p.difficulty,
                       COALESCE(d.root_cause, d.explanation, '') AS diagnosis
                FROM submission s
                JOIN problem p ON p.id=s.problem_id
                LEFT JOIN diagnosis d ON d.submission_id=s.id
                WHERE s.id=:submissionId AND s.user_id=:userId
                """).param("submissionId", submissionId).param("userId", userId).query().listOfRows();
        if (analyses.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "提交记录不存在");

        List<Map<String, Object>> knowledge = jdbc.sql("""
                SELECT kp.id,kp.code,kp.name,kp.description,
                       COALESCE(sm.mastery,0.1500) AS mastery
                FROM submission s
                JOIN problem_knowledge_point pk ON pk.problem_id=s.problem_id
                JOIN knowledge_point kp ON kp.id=pk.knowledge_point_id
                LEFT JOIN student_mastery sm ON sm.user_id=s.user_id AND sm.knowledge_point_id=kp.id
                WHERE s.id=:submissionId AND s.user_id=:userId
                ORDER BY COALESCE(sm.mastery,0.1500) ASC,pk.is_primary DESC,pk.weight DESC
                LIMIT 5
                """).param("submissionId", submissionId).param("userId", userId).query().listOfRows();
        if (knowledge.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "当前题目尚未绑定知识点，无法生成专项练习");

        Map<String, Object> analysis = analyses.getFirst();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", Objects.toString(analysis.get("language"), "python"));
        payload.put("difficulty", ((Number) analysis.get("difficulty")).intValue());
        payload.put("sourceTitle", Objects.toString(analysis.get("title"), ""));
        payload.put("sourceDiagnosis", Objects.toString(analysis.get("diagnosis"), ""));
        payload.put("knowledgePoints", knowledge);

        Map response;
        try {
            response = ai.post().uri("/v1/practice/generate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 生题暂时不可用，请稍后重试");
        }
        if (response == null) throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 生题没有返回内容");

        String knowledgeIds = knowledge.stream().map(row -> Objects.toString(row.get("id")))
                .reduce((a, b) -> a + "," + b).orElseThrow();
        String knowledgeSummary = knowledge.stream()
                .map(row -> Objects.toString(row.get("name")) + "(" + row.get("mastery") + ")")
                .reduce((a, b) -> a + "、" + b).orElse("");
        jdbc.sql("""
                INSERT INTO ai_generated_problem(
                  user_id,source_submission_id,language,difficulty,title,description,
                  input_description,output_description,starter_code,stdin_data,
                  evaluation_criteria,knowledge_point_ids,knowledge_summary,model_name)
                VALUES (:userId,:submissionId,:language,:difficulty,:title,:description,
                  :inputDescription,:outputDescription,:starterCode,:stdin,
                  :criteria,:knowledgeIds,:knowledgeSummary,:modelName)
                """)
                .param("userId", userId).param("submissionId", submissionId)
                .param("language", Objects.toString(analysis.get("language"), "python"))
                .param("difficulty", ((Number) analysis.get("difficulty")).intValue())
                .param("title", text(response, "title", "AI 专项练习"))
                .param("description", text(response, "description", "请完成这道专项练习。"))
                .param("inputDescription", text(response, "inputDescription", "读取标准输入。"))
                .param("outputDescription", text(response, "outputDescription", "输出处理结果。"))
                .param("starterCode", text(response, "starterCode", ""))
                .param("stdin", text(response, "stdin", ""))
                .param("criteria", text(response, "evaluationCriteria", "程序应满足题目要求。"))
                .param("knowledgeIds", knowledgeIds).param("knowledgeSummary", knowledgeSummary)
                .param("modelName", text(response, "modelName", "deepseek-chat")).update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return findGenerated(userId, id);
    }

    @Transactional
    public GeneratedPractice generateForPlanItem(Long userId, Long itemId) {
        Map<String, Object> item = jdbc.sql("""
                SELECT i.id,i.title,i.description,c.language,kp.id AS knowledge_id,
                       kp.code AS knowledge_code,kp.name AS knowledge_name,
                       kp.description AS knowledge_description,kp.difficulty,
                       COALESCE(sm.mastery,0.1500) AS mastery
                FROM learning_plan_item i
                JOIN learning_plan_stage st ON st.id=i.stage_id
                JOIN learning_plan lp ON lp.id=st.plan_id
                JOIN course c ON c.id=lp.course_id
                LEFT JOIN knowledge_point kp ON kp.id=i.knowledge_point_id
                LEFT JOIN student_mastery sm ON sm.user_id=lp.user_id AND sm.knowledge_point_id=kp.id
                WHERE i.id=:itemId AND lp.user_id=:userId AND lp.status='ACTIVE'
                """).param("itemId", itemId).param("userId", userId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "计划任务不存在"));
        if (item.get("knowledge_id") == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "该计划任务尚未绑定知识点");
        }

        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("id", item.get("knowledge_id"));
        knowledge.put("code", item.get("knowledge_code"));
        knowledge.put("name", item.get("knowledge_name"));
        knowledge.put("description", item.get("knowledge_description"));
        knowledge.put("mastery", item.get("mastery"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", Objects.toString(item.get("language"), "python"));
        payload.put("difficulty", ((Number) item.get("difficulty")).intValue());
        payload.put("sourceTitle", Objects.toString(item.get("title"), "计划专项练习"));
        payload.put("sourceDiagnosis", Objects.toString(item.get("description"), ""));
        payload.put("knowledgePoints", List.of(knowledge));

        Map response;
        try {
            response = ai.post().uri("/v1/practice/generate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 生题暂时不可用，请稍后重试");
        }
        if (response == null) throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 生题没有返回内容");

        jdbc.sql("""
                INSERT INTO ai_generated_problem(
                  user_id,source_submission_id,plan_item_id,language,difficulty,title,description,
                  input_description,output_description,starter_code,stdin_data,
                  evaluation_criteria,knowledge_point_ids,knowledge_summary,model_name)
                VALUES (:userId,NULL,:itemId,:language,:difficulty,:title,:description,
                  :inputDescription,:outputDescription,:starterCode,:stdin,
                  :criteria,:knowledgeIds,:knowledgeSummary,:modelName)
                """).param("userId", userId).param("itemId", itemId)
                .param("language", Objects.toString(item.get("language"), "python"))
                .param("difficulty", ((Number) item.get("difficulty")).intValue())
                .param("title", text(response, "title", "AI 专项练习"))
                .param("description", text(response, "description", "请完成这道专项练习。"))
                .param("inputDescription", text(response, "inputDescription", "读取标准输入。"))
                .param("outputDescription", text(response, "outputDescription", "输出处理结果。"))
                .param("starterCode", text(response, "starterCode", ""))
                .param("stdin", text(response, "stdin", ""))
                .param("criteria", text(response, "evaluationCriteria", "程序应满足题目要求。"))
                .param("knowledgeIds", Objects.toString(item.get("knowledge_id")))
                .param("knowledgeSummary", Objects.toString(item.get("knowledge_name")) + "(" + item.get("mastery") + ")")
                .param("modelName", text(response, "modelName", "deepseek-chat")).update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return findGenerated(userId, id);
    }

    @Transactional
    public Evaluation evaluate(Long userId, Long generatedProblemId, EvaluationRequest request) {
        Map<String, Object> problem = findProblemRow(userId, generatedProblemId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", problem.get("title"));
        payload.put("description", problem.get("description"));
        payload.put("inputDescription", problem.get("input_description"));
        payload.put("outputDescription", problem.get("output_description"));
        payload.put("evaluationCriteria", problem.get("evaluation_criteria"));
        payload.put("knowledgeSummary", problem.get("knowledge_summary"));
        payload.put("language", problem.get("language"));
        payload.put("sourceCode", request.sourceCode());
        payload.put("stdin", request.stdin());
        payload.put("stdout", request.stdout());
        payload.put("stderr", request.stderr());

        Map response;
        try {
            response = ai.post().uri("/v1/practice/evaluate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 教练评估暂时不可用，请稍后重试");
        }
        if (response == null) throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 教练没有返回评估结果");
        double score = Math.max(0, Math.min(1, ((Number) response.getOrDefault("score", 0)).doubleValue()));
        boolean correct = Boolean.TRUE.equals(response.get("correct")) && score >= 0.8;
        String errorType = correct ? "NONE" : text(response, "errorType", "UNKNOWN");

        jdbc.sql("""
                INSERT INTO ai_practice_evaluation(
                  generated_problem_id,user_id,source_code,stdin_data,stdout_data,stderr_data,
                  is_correct,score,feedback,error_type,next_step,model_name)
                VALUES (:problemId,:userId,:sourceCode,:stdin,:stdout,:stderr,
                  :correct,:score,:feedback,:errorType,:nextStep,:modelName)
                """)
                .param("problemId", generatedProblemId).param("userId", userId)
                .param("sourceCode", request.sourceCode()).param("stdin", request.stdin())
                .param("stdout", request.stdout()).param("stderr", request.stderr())
                .param("correct", correct).param("score", BigDecimal.valueOf(score))
                .param("feedback", text(response, "feedback", "请检查代码与输出。"))
                .param("errorType", errorType).param("nextStep", text(response, "nextStep", "修改后重试。"))
                .param("modelName", text(response, "modelName", "deepseek-chat")).update();

        List<Long> knowledgeIds = Arrays.stream(Objects.toString(problem.get("knowledge_point_ids")).split(","))
                .filter(value -> !value.isBlank()).map(Long::valueOf).toList();
        List<MasteryService.MasteryChange> changes = masteryService.updateFromAiPractice(
                userId, knowledgeIds, score, errorType);
        if (correct && problem.get("plan_item_id") instanceof Number planItemId) {
            jdbc.sql("""
                    UPDATE learning_plan_item i
                    JOIN learning_plan_stage st ON st.id=i.stage_id
                    JOIN learning_plan lp ON lp.id=st.plan_id
                    SET i.status='COMPLETED',i.completion_source='AI_EVALUATED',i.completed_at=NOW(6)
                    WHERE i.id=:itemId AND lp.user_id=:userId AND lp.status='ACTIVE'
                    """).param("itemId", planItemId.longValue()).param("userId", userId).update();
        }
        return new Evaluation(correct, score, text(response, "feedback", ""), errorType,
                text(response, "nextStep", ""), text(response, "modelName", "deepseek-chat"), changes);
    }

    @Transactional(readOnly = true)
    public GeneratedPractice findGenerated(Long userId, Long id) {
        Map<String, Object> row = findProblemRow(userId, id);
        List<KnowledgeView> knowledge = jdbc.sql("""
                SELECT id,code,name FROM knowledge_point
                WHERE FIND_IN_SET(CAST(id AS CHAR),:ids)>0 ORDER BY sort_order,id
                """).param("ids", row.get("knowledge_point_ids")).query().listOfRows().stream()
                .map(item -> new KnowledgeView(((Number) item.get("id")).longValue(),
                        Objects.toString(item.get("code")), Objects.toString(item.get("name")))).toList();
        return new GeneratedPractice(
                ((Number) row.get("id")).longValue(), Objects.toString(row.get("title")),
                Objects.toString(row.get("description")), Objects.toString(row.get("input_description"), ""),
                Objects.toString(row.get("output_description"), ""), Objects.toString(row.get("starter_code"), ""),
                Objects.toString(row.get("stdin_data"), ""), Objects.toString(row.get("language")),
                ((Number) row.get("difficulty")).intValue(), knowledge, Objects.toString(row.get("model_name"), ""));
    }

    private Map<String, Object> findProblemRow(Long userId, Long id) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM ai_generated_problem WHERE id=:id AND user_id=:userId")
                .param("id", id).param("userId", userId).query().listOfRows();
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "AI 练习题不存在");
        return rows.getFirst();
    }

    private static String text(Map response, String key, String fallback) {
        Object value = response.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    public record KnowledgeView(Long id, String code, String name) {}
    public record GeneratedPractice(Long id, String title, String description, String inputDescription,
                                    String outputDescription, String starterCode, String stdin,
                                    String language, Integer difficulty, List<KnowledgeView> knowledgePoints,
                                    String modelName) {}
    public record EvaluationRequest(@NotBlank @Size(max=100_000) String sourceCode,
                                    @Size(max=100_000) String stdin,
                                    @Size(max=100_000) String stdout,
                                    @Size(max=100_000) String stderr) {}
    public record Evaluation(boolean correct, double score, String feedback, String errorType,
                             String nextStep, String modelName,
                             List<MasteryService.MasteryChange> masteryChanges) {}
}
