package com.codepath.backend.mastery;

import com.codepath.backend.course.KnowledgePoint;
import com.codepath.backend.course.KnowledgePointRepository;
import com.codepath.backend.submission.Submission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MasteryService {
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentMasteryRepository masteryRepository;
    private final MasteryEventRepository eventRepository;
    private final JdbcClient jdbc;
    private final RestClient ai;
    private final String internalToken;

    public MasteryService(KnowledgePointRepository knowledgePointRepository,
                          StudentMasteryRepository masteryRepository,
                          MasteryEventRepository eventRepository,
                          JdbcClient jdbc,
                          RestClient.Builder restClientBuilder,
                          @Value("${codepath.ai-service-url}") String aiServiceUrl,
                          @Value("${codepath.internal-service-token}") String internalToken) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.masteryRepository = masteryRepository;
        this.eventRepository = eventRepository;
        this.jdbc = jdbc;
        this.ai = restClientBuilder.baseUrl(aiServiceUrl).build();
        this.internalToken = internalToken;
    }

    @Transactional
    public void updateFromSubmission(Submission submission, String errorType) {
        List<KnowledgePoint> points = knowledgePointRepository.findByProblemId(submission.getProblemId());
        if (points.isEmpty()) {
            return;
        }

        BigDecimal passRate = submission.getTotalCount() == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(submission.getPassedCount())
                .divide(BigDecimal.valueOf(submission.getTotalCount()), 4, RoundingMode.HALF_UP);
        for (KnowledgePoint point : points) {
            StudentMastery mastery = masteryRepository
                    .findByUserIdAndKnowledgePointId(submission.getUserId(), point.getId())
                    .orElseGet(() -> StudentMastery.initialize(submission.getUserId(), point.getId()));
            double weight = jdbc.sql("""
                    SELECT weight FROM problem_knowledge_point
                    WHERE problem_id=:problemId AND knowledge_point_id=:knowledgePointId
                    """)
                    .param("problemId", submission.getProblemId())
                    .param("knowledgePointId", point.getId())
                    .query(Double.class).optional().orElse(1.0);

            BigDecimal before;
            BigDecimal delta;
            String model;
            try {
                BktResponse response = ai.post().uri("/v1/mastery/update")
                        .header("X-Internal-Token", internalToken)
                        .body(new BktRequest(
                                mastery.getMastery().doubleValue(), mastery.getEvidenceCount(),
                                passRate.doubleValue(), weight))
                        .retrieve().body(BktResponse.class);
                if (response == null) throw new IllegalStateException("BKT 服务未返回结果");
                BigDecimal next = BigDecimal.valueOf(response.masteryAfter()).setScale(4, RoundingMode.HALF_UP);
                before = mastery.applyBkt(next);
                delta = mastery.getMastery().subtract(before).setScale(5, RoundingMode.HALF_UP);
                model = response.modelName();
            } catch (RuntimeException unavailable) {
                // AI 服务短暂不可用时保留可解释的降级规则，不能阻塞提交完成。
                delta = passRate.compareTo(BigDecimal.ONE) == 0
                        ? new BigDecimal("0.08000")
                        : passRate.compareTo(new BigDecimal("0.5")) >= 0
                        ? new BigDecimal("-0.03000") : new BigDecimal("-0.06000");
                before = mastery.apply(delta);
                model = "fixed-delta-fallback";
            }
            masteryRepository.save(mastery);
            eventRepository.save(MasteryEvent.create(
                    submission.getUserId(), point.getId(), submission.getId(),
                    "BKT_UPDATED",
                    delta, before, mastery.getMastery(),
                    "模型=" + model + "，测试通过率=" + passRate
                            + "，知识点权重=" + weight + "，错因类型=" + errorType));
        }
    }

    @Transactional
    public List<MasteryChange> updateFromAiPractice(Long userId, List<Long> knowledgePointIds,
                                                     double score, String errorType) {
        BigDecimal passRate = BigDecimal.valueOf(Math.max(0, Math.min(1, score)))
                .setScale(4, RoundingMode.HALF_UP);
        List<MasteryChange> changes = new ArrayList<>();
        for (Long knowledgePointId : knowledgePointIds.stream().distinct().toList()) {
            KnowledgePoint point = knowledgePointRepository.findById(knowledgePointId).orElse(null);
            if (point == null) continue;
            StudentMastery mastery = masteryRepository.findByUserIdAndKnowledgePointId(userId, knowledgePointId)
                    .orElseGet(() -> StudentMastery.initialize(userId, knowledgePointId));
            BigDecimal before;
            BigDecimal delta;
            String model;
            try {
                BktResponse response = ai.post().uri("/v1/mastery/update")
                        .header("X-Internal-Token", internalToken)
                        .body(new BktRequest(mastery.getMastery().doubleValue(), mastery.getEvidenceCount(),
                                passRate.doubleValue(), 1.0))
                        .retrieve().body(BktResponse.class);
                if (response == null) throw new IllegalStateException("BKT 服务未返回结果");
                BigDecimal next = BigDecimal.valueOf(response.masteryAfter()).setScale(4, RoundingMode.HALF_UP);
                before = mastery.applyBkt(next);
                delta = mastery.getMastery().subtract(before).setScale(5, RoundingMode.HALF_UP);
                model = response.modelName();
            } catch (RuntimeException unavailable) {
                delta = passRate.compareTo(new BigDecimal("0.8")) >= 0
                        ? new BigDecimal("0.06000")
                        : passRate.compareTo(new BigDecimal("0.5")) >= 0
                        ? new BigDecimal("-0.02000") : new BigDecimal("-0.05000");
                before = mastery.apply(delta);
                model = "fixed-delta-fallback";
            }
            masteryRepository.save(mastery);
            eventRepository.save(MasteryEvent.create(userId, knowledgePointId, null,
                    "AI_PRACTICE_EVALUATED", delta, before, mastery.getMastery(),
                    "模型=" + model + "，AI 教练评分=" + passRate + "，错因类型=" + errorType));
            changes.add(new MasteryChange(knowledgePointId, point.getName(), before,
                    mastery.getMastery(), delta, model));
        }
        return changes;
    }

    @Transactional(readOnly = true)
    public List<StudentMastery> list(Long userId, Long courseId) {
        return masteryRepository.findByUserIdAndCourseId(userId, courseId);
    }

    private record BktRequest(double currentMastery, int evidenceCount,
                              double passRate, double knowledgeWeight) {}
    private record BktResponse(double masteryBefore, double masteryAfter, double delta,
                               double predictedSuccess, String modelName) {}

    public record MasteryChange(Long knowledgePointId, String knowledgePointName,
                                BigDecimal masteryBefore, BigDecimal masteryAfter,
                                BigDecimal delta, String modelName) {}
}
