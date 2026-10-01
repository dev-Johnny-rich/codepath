package com.codepath.backend.mastery;

import com.codepath.backend.course.KnowledgePoint;
import com.codepath.backend.course.KnowledgePointRepository;
import com.codepath.backend.submission.Submission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class MasteryService {
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentMasteryRepository masteryRepository;
    private final MasteryEventRepository eventRepository;

    public MasteryService(KnowledgePointRepository knowledgePointRepository,
                          StudentMasteryRepository masteryRepository,
                          MasteryEventRepository eventRepository) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.masteryRepository = masteryRepository;
        this.eventRepository = eventRepository;
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
        BigDecimal delta = passRate.compareTo(BigDecimal.ONE) == 0
                ? new BigDecimal("0.08000")
                : passRate.compareTo(new BigDecimal("0.5")) >= 0
                ? new BigDecimal("-0.03000")
                : new BigDecimal("-0.06000");

        for (KnowledgePoint point : points) {
            StudentMastery mastery = masteryRepository
                    .findByUserIdAndKnowledgePointId(submission.getUserId(), point.getId())
                    .orElseGet(() -> StudentMastery.initialize(submission.getUserId(), point.getId()));
            BigDecimal before = mastery.apply(delta);
            masteryRepository.save(mastery);
            eventRepository.save(MasteryEvent.create(
                    submission.getUserId(), point.getId(), submission.getId(),
                    passRate.compareTo(BigDecimal.ONE) == 0 ? "SUBMISSION_PASSED" : "DIAGNOSIS_CONFIRMED",
                    delta, before, mastery.getMastery(),
                    "测试通过率=" + passRate + "，错因类型=" + errorType));
        }
    }

    @Transactional(readOnly = true)
    public List<StudentMastery> list(Long userId, Long courseId) {
        return masteryRepository.findByUserIdAndCourseId(userId, courseId);
    }
}
