package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import com.codepath.backend.mastery.MasteryService;
import com.codepath.backend.problem.TestCase;
import com.codepath.backend.problem.TestCaseRepository;
import com.codepath.backend.submission.SubmissionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiPracticeService {
    private final JdbcClient jdbc;
    private final RestClient ai;
    private final String internalToken;
    private final MasteryService masteryService;
    private final TestCaseRepository testCaseRepository;

    public AiPracticeService(JdbcClient jdbc, RestClient.Builder builder,
                             @Value("${codepath.ai-service-url}") String aiUrl,
                             @Value("${codepath.internal-service-token}") String internalToken,
                             MasteryService masteryService,
                             TestCaseRepository testCaseRepository) {
        this.jdbc = jdbc;
        this.ai = builder.baseUrl(aiUrl).build();
        this.internalToken = internalToken;
        this.masteryService = masteryService;
        this.testCaseRepository = testCaseRepository;
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
        persistTestCases(id, response);
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
        persistTestCases(id, response);
        return findGenerated(userId, id);
    }

    @Transactional
    public GeneratedPractice generateKnowledgeTest(Long userId, Long planId) {
        Map<String, Object> plan = jdbc.sql("""
                SELECT lp.id,lp.title,c.language FROM learning_plan lp
                JOIN course c ON c.id=lp.course_id
                WHERE lp.id=:planId AND lp.user_id=:userId AND lp.status='ACTIVE'
                """).param("planId", planId).param("userId", userId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "当前学习计划不存在"));

        List<Map<String, Object>> knowledge = jdbc.sql("""
                SELECT kp.id,kp.code,kp.name,kp.description,kp.difficulty,
                       COALESCE(sm.mastery,0.1500) mastery,COALESCE(sm.evidence_count,0) evidence_count,
                       COUNT(i.id) planned_tasks,
                       SUM(CASE WHEN i.status='COMPLETED' THEN 1 ELSE 0 END) completed_tasks
                FROM learning_plan_stage st
                JOIN learning_plan_item i ON i.stage_id=st.id
                JOIN knowledge_point kp ON kp.id=i.knowledge_point_id
                LEFT JOIN student_mastery sm ON sm.user_id=:userId AND sm.knowledge_point_id=kp.id
                WHERE st.plan_id=:planId
                GROUP BY kp.id,kp.code,kp.name,kp.description,kp.difficulty,sm.mastery,sm.evidence_count,kp.sort_order
                ORDER BY (SUM(CASE WHEN i.status='COMPLETED' THEN 1 ELSE 0 END)/COUNT(i.id)) ASC,
                         COALESCE(sm.mastery,0.1500) ASC,COALESCE(sm.evidence_count,0) ASC,kp.sort_order
                LIMIT 5
                """).param("planId", planId).param("userId", userId).query().listOfRows();
        if (knowledge.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该计划尚未绑定可测试的知识点");
        }

        int difficulty = (int) Math.round(knowledge.stream()
                .mapToInt(row -> ((Number) row.get("difficulty")).intValue()).average().orElse(1));
        String coverage = knowledge.stream().map(row -> String.format("%s(计划完成%d/%d，BKT %.0f%%)",
                        Objects.toString(row.get("name")), ((Number) row.get("completed_tasks")).intValue(),
                        ((Number) row.get("planned_tasks")).intValue(),
                        ((Number) row.get("mastery")).doubleValue() * 100))
                .reduce((a, b) -> a + "、" + b).orElse("");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", Objects.toString(plan.get("language"), "python"));
        payload.put("difficulty", Math.max(1, Math.min(5, difficulty)));
        payload.put("sourceTitle", Objects.toString(plan.get("title"), "学习计划") + " · 知识点测试");
        payload.put("sourceDiagnosis", "根据计划任务完成情况和 BKT 掌握度生成一道综合测试题，优先验证：" + coverage);
        payload.put("knowledgePoints", knowledge);

        Map response;
        try {
            response = ai.post().uri("/v1/practice/generate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException unavailable) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 知识测试生成暂时不可用，请稍后重试");
        }
        if (response == null) throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 没有返回测试题");

        String knowledgeIds = knowledge.stream().map(row -> Objects.toString(row.get("id")))
                .reduce((a, b) -> a + "," + b).orElseThrow();
        jdbc.sql("""
                INSERT INTO ai_generated_problem(
                  user_id,source_submission_id,plan_item_id,source_type,language,difficulty,title,description,
                  input_description,output_description,starter_code,stdin_data,
                  evaluation_criteria,knowledge_point_ids,knowledge_summary,model_name)
                VALUES (:userId,NULL,NULL,'KNOWLEDGE_TEST',:language,:difficulty,:title,:description,
                  :inputDescription,:outputDescription,:starterCode,:stdin,
                  :criteria,:knowledgeIds,:knowledgeSummary,:modelName)
                """).param("userId", userId)
                .param("language", Objects.toString(plan.get("language"), "python"))
                .param("difficulty", Math.max(1, Math.min(5, difficulty)))
                .param("title", text(response, "title", "AI 知识点综合测试"))
                .param("description", text(response, "description", "请完成这道综合测试题。"))
                .param("inputDescription", text(response, "inputDescription", "读取标准输入。"))
                .param("outputDescription", text(response, "outputDescription", "输出处理结果。"))
                .param("starterCode", text(response, "starterCode", ""))
                .param("stdin", text(response, "stdin", ""))
                .param("criteria", text(response, "evaluationCriteria", "程序应满足题目要求。"))
                .param("knowledgeIds", knowledgeIds).param("knowledgeSummary", coverage)
                .param("modelName", text(response, "modelName", "deepseek-chat")).update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        persistTestCases(id, response);
        return findGenerated(userId, id);
    }

    @Transactional
    public Evaluation finalizeEvaluation(Long userId, Long generatedProblemId, Long submissionId) {
        Map<String, Object> problem = findProblemRow(userId, generatedProblemId);
        List<Map<String, Object>> previous = jdbc.sql("""
                SELECT * FROM ai_practice_evaluation
                WHERE submission_id=:submissionId AND user_id=:userId AND generated_problem_id=:problemId
                """).param("submissionId", submissionId).param("userId", userId)
                .param("problemId", generatedProblemId).query().listOfRows();
        if (!previous.isEmpty()) return evaluationFromRow(previous.getFirst());

        Map<String, Object> submission = jdbc.sql("""
                SELECT id,source_code,status,passed_count,total_count,error_message
                FROM submission
                WHERE id=:submissionId AND user_id=:userId AND ai_generated_problem_id=:problemId
                """).param("submissionId", submissionId).param("userId", userId)
                .param("problemId", generatedProblemId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "AI 题评测记录不存在"));
        if (!SubmissionStatus.COMPLETED.name().equals(Objects.toString(submission.get("status")))) {
            throw new BusinessException(HttpStatus.CONFLICT, "沙箱评测尚未完成");
        }
        int passedCount = ((Number) submission.get("passed_count")).intValue();
        int totalCount = ((Number) submission.get("total_count")).intValue();
        if (totalCount < 5) throw new BusinessException(HttpStatus.CONFLICT, "可信测试用例不足");
        boolean correct = passedCount == totalCount;
        double score = (double) passedCount / totalCount;
        List<Map<String, Object>> results = jdbc.sql("""
                SELECT r.status,r.actual_output,r.stderr,t.is_hidden,t.sort_order
                FROM submission_test_result r JOIN test_case t ON t.id=r.test_case_id
                WHERE r.submission_id=:submissionId ORDER BY t.sort_order,t.id
                """).param("submissionId", submissionId).query().listOfRows();
        String errorType = correct ? "NONE" : results.stream()
                .map(row -> Objects.toString(row.get("status"), "UNKNOWN"))
                .filter(status -> !"PASSED".equals(status)).findFirst().orElse("UNKNOWN");
        String failureSummary = results.stream().filter(row -> !"PASSED".equals(row.get("status")))
                .limit(5).map(row -> "测试点" + (row.get("sort_order")) + ": " + row.get("status")
                        + ", stdout=" + Objects.toString(row.get("actual_output"), "")
                        + ", stderr=" + Objects.toString(row.get("stderr"), ""))
                .reduce((a, b) -> a + "\n" + b).orElse("全部测试点通过");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", problem.get("title"));
        payload.put("description", problem.get("description"));
        payload.put("inputDescription", problem.get("input_description"));
        payload.put("outputDescription", problem.get("output_description"));
        payload.put("evaluationCriteria", problem.get("evaluation_criteria"));
        payload.put("knowledgeSummary", problem.get("knowledge_summary"));
        payload.put("language", problem.get("language"));
        payload.put("sourceCode", submission.get("source_code"));
        payload.put("correct", correct);
        payload.put("score", score);
        payload.put("passedCount", passedCount);
        payload.put("totalCount", totalCount);
        payload.put("failureSummary", failureSummary);

        Map response = Map.of();
        try {
            response = ai.post().uri("/v1/practice/evaluate")
                    .header("X-Internal-Token", internalToken).body(payload)
                    .retrieve().body(Map.class);
        } catch (RuntimeException ignored) {
            // 解释服务不可用不得改变沙箱的确定性判定。
        }
        if (response == null) response = Map.of();
        String feedback = text(response, "feedback", correct
                ? "全部确定性测试用例已通过。" : "有测试用例未通过，请根据运行结果检查边界条件。");
        String nextStep = text(response, "nextStep", correct
                ? "可以继续练习下一个知识点。" : "先定位第一个未通过的测试点。");
        String modelName = text(response, "modelName", response.isEmpty() ? "deterministic-rule-engine" : "deepseek-chat");

        int claimed = jdbc.sql("""
                UPDATE ai_generated_problem SET bkt_evidence_submission_id=:submissionId
                WHERE id=:problemId AND user_id=:userId AND bkt_evidence_submission_id IS NULL
                """).param("submissionId", submissionId).param("problemId", generatedProblemId)
                .param("userId", userId).update();
        boolean evidenceApplied = claimed == 1;

        jdbc.sql("""
                INSERT INTO ai_practice_evaluation(
                  generated_problem_id,user_id,submission_id,source_code,stdin_data,stdout_data,stderr_data,
                  passed_count,total_count,evidence_applied,is_correct,score,feedback,error_type,next_step,model_name)
                VALUES (:problemId,:userId,:submissionId,:sourceCode,'','',:failureSummary,
                  :passed,:total,:evidenceApplied,:correct,:score,:feedback,:errorType,:nextStep,:modelName)
                """)
                .param("problemId", generatedProblemId).param("userId", userId).param("submissionId", submissionId)
                .param("sourceCode", submission.get("source_code")).param("failureSummary", failureSummary)
                .param("passed", passedCount).param("total", totalCount).param("evidenceApplied", evidenceApplied)
                .param("correct", correct).param("score", BigDecimal.valueOf(score))
                .param("feedback", feedback).param("errorType", errorType).param("nextStep", nextStep)
                .param("modelName", modelName).update();

        List<Long> knowledgeIds = Arrays.stream(Objects.toString(problem.get("knowledge_point_ids")).split(","))
                .filter(value -> !value.isBlank()).map(Long::valueOf).toList();
        List<MasteryService.MasteryChange> changes = evidenceApplied
                ? masteryService.updateFromAiPractice(userId, submissionId, knowledgeIds, score, errorType)
                : List.of();
        if (correct && problem.get("plan_item_id") instanceof Number planItemId) {
            jdbc.sql("""
                    UPDATE learning_plan_item i
                    JOIN learning_plan_stage st ON st.id=i.stage_id
                    JOIN learning_plan lp ON lp.id=st.plan_id
                    SET i.status='COMPLETED',i.completion_source='AI_EVALUATED',i.completed_at=NOW(6)
                    WHERE i.id=:itemId AND lp.user_id=:userId AND lp.status='ACTIVE'
                    """).param("itemId", planItemId.longValue()).param("userId", userId).update();
        }
        return new Evaluation(submissionId, correct, score, feedback, errorType,
                nextStep, modelName, evidenceApplied, changes);
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
        List<PublicExample> examples = testCaseRepository
                .findByGeneratedProblemIdAndHiddenFalseOrderBySortOrderAsc(id).stream()
                .map(test -> new PublicExample(test.getId(), Objects.toString(test.getInputData(), ""),
                        test.getExpectedOutput())).toList();
        int totalTestCases = Math.toIntExact(testCaseRepository.countByGeneratedProblemId(id));
        return new GeneratedPractice(
                ((Number) row.get("id")).longValue(), Objects.toString(row.get("title")),
                Objects.toString(row.get("description")), Objects.toString(row.get("input_description"), ""),
                Objects.toString(row.get("output_description"), ""), Objects.toString(row.get("starter_code"), ""),
                Objects.toString(row.get("stdin_data"), ""), Objects.toString(row.get("language")),
                ((Number) row.get("difficulty")).intValue(), knowledge, examples, totalTestCases,
                Objects.toString(row.get("model_name"), ""));
    }

    @SuppressWarnings("unchecked")
    private void persistTestCases(Long generatedProblemId, Map response) {
        Object raw = response.get("testCases");
        if (!(raw instanceof List<?> cases) || cases.size() < 5) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 未生成足够的确定性测试用例");
        }
        int publicCount = 0;
        int hiddenCount = 0;
        int sortOrder = 1;
        Set<String> inputs = new HashSet<>();
        for (Object item : cases.stream().limit(10).toList()) {
            if (!(item instanceof Map<?, ?> value)) continue;
            String input = Objects.toString(value.get("input"), "");
            String expected = Objects.toString(value.get("expectedOutput"), "");
            boolean hidden = Boolean.TRUE.equals(value.get("hidden"));
            if (expected.length() > 20_000 || input.length() > 20_000) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 测试用例超过长度限制");
            }
            if (expected.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 测试用例的标准输出不能为空");
            }
            if (!inputs.add(input)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 测试用例的输入不能重复");
            }
            testCaseRepository.save(TestCase.createForAiPractice(
                    generatedProblemId, input, expected, hidden, sortOrder++));
            if (hidden) hiddenCount++; else publicCount++;
        }
        if (publicCount < 2 || hiddenCount < 3) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 测试用例需至少 2 个公开和 3 个隐藏用例");
        }
    }

    private Evaluation evaluationFromRow(Map<String, Object> row) {
        return new Evaluation(((Number) row.get("submission_id")).longValue(),
                asBoolean(row.get("is_correct")),
                ((Number) row.get("score")).doubleValue(), Objects.toString(row.get("feedback"), ""),
                Objects.toString(row.get("error_type"), "UNKNOWN"), Objects.toString(row.get("next_step"), ""),
                Objects.toString(row.get("model_name"), ""),
                asBoolean(row.get("evidence_applied")),
                List.of());
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(Objects.toString(value, "false"));
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
    public record PublicExample(Long id, String input, String expectedOutput) {}
    public record GeneratedPractice(Long id, String title, String description, String inputDescription,
                                    String outputDescription, String starterCode, String stdin,
                                    String language, Integer difficulty, List<KnowledgeView> knowledgePoints,
                                    List<PublicExample> publicExamples, int totalTestCases, String modelName) {}
    public record Evaluation(Long submissionId, boolean correct, double score, String feedback, String errorType,
                             String nextStep, String modelName, boolean evidenceApplied,
                             List<MasteryService.MasteryChange> masteryChanges) {}
}
