package com.codepath.backend.diagnosis;

import com.codepath.backend.common.BusinessException;
import com.codepath.backend.course.KnowledgePointRepository;
import com.codepath.backend.mastery.MasteryService;
import com.codepath.backend.learning.WrongBookService;
import com.codepath.backend.problem.Problem;
import com.codepath.backend.problem.ProblemRepository;
import com.codepath.backend.submission.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DiagnosisService {
    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository resultRepository;
    private final ProblemRepository problemRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final DiagnosisEvidenceRepository evidenceRepository;
    private final MasteryService masteryService;
    private final WrongBookService wrongBookService;

    public DiagnosisService(SubmissionRepository submissionRepository,
                            SubmissionTestResultRepository resultRepository,
                            ProblemRepository problemRepository,
                            KnowledgePointRepository knowledgePointRepository,
                            DiagnosisRepository diagnosisRepository,
                            DiagnosisEvidenceRepository evidenceRepository,
                            MasteryService masteryService,
                            WrongBookService wrongBookService) {
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.problemRepository = problemRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.evidenceRepository = evidenceRepository;
        this.masteryService = masteryService;
        this.wrongBookService = wrongBookService;
    }

    @Transactional(readOnly = true)
    public DiagnosisTaskResponse task(Long submissionId) {
        Submission submission = getSubmission(submissionId);
        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));
        List<DiagnosisTaskResponse.TestEvidence> results = resultRepository
                .findBySubmissionIdOrderByTestCaseIdAsc(submissionId)
                .stream()
                .map(item -> new DiagnosisTaskResponse.TestEvidence(
                        item.getTestCaseId(), item.getStatus(), item.getActualOutput(), item.getStderr()))
                .toList();
        return new DiagnosisTaskResponse(
                submission.getId(), submission.getUserId(), submission.getProblemId(),
                problem.getTitle(), problem.getDescription(), submission.getLanguage(),
                submission.getSourceCode(), submission.getErrorMessage(),
                submission.getPassedCount(), submission.getTotalCount(), results,
                knowledgePointRepository.findByProblemId(problem.getId()));
    }

    @Transactional
    public void complete(Long submissionId, DiagnosisCallbackRequest request) {
        Submission submission = getSubmission(submissionId);
        if (submission.getStatus() != SubmissionStatus.DIAGNOSING) {
            throw new BusinessException(HttpStatus.CONFLICT, "提交当前不在诊断状态");
        }
        if (diagnosisRepository.findBySubmissionId(submissionId).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该提交已经完成诊断");
        }

        Diagnosis diagnosis = diagnosisRepository.save(Diagnosis.create(
                submissionId, request.errorType(), request.rootCause(), request.explanation(),
                request.confidence(), request.hintLevel(), request.nextAction(),
                request.modelName(), request.promptVersion()));
        for (DiagnosisCallbackRequest.Evidence item : request.evidence()) {
            evidenceRepository.save(DiagnosisEvidence.create(
                    diagnosis.getId(), item.evidenceType(), item.lineNumber(),
                    item.content(), item.sourceReference()));
        }

        submission.transitionTo(SubmissionStatus.UPDATING_MASTERY);
        if (submission.getUserId() != 0L) {
            masteryService.updateFromSubmission(submission, request.errorType());
            wrongBookService.applyDiagnosis(submission.getUserId(), submission.getProblemId(),
                    submission.getId(), request.errorType());
        }
        submission.transitionTo(SubmissionStatus.COMPLETED);
    }

    private Submission getSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "提交不存在"));
    }
}
