package com.codepath.backend.submission;

import com.codepath.backend.common.BusinessException;
import com.codepath.backend.diagnosis.Diagnosis;
import com.codepath.backend.diagnosis.DiagnosisRepository;
import com.codepath.backend.problem.Problem;
import com.codepath.backend.problem.ProblemRepository;
import com.codepath.backend.problem.TestCase;
import com.codepath.backend.problem.TestCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubmissionService {
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository resultRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcClient jdbc;

    public SubmissionService(ProblemRepository problemRepository,
                             TestCaseRepository testCaseRepository,
                             SubmissionRepository submissionRepository,
                             SubmissionTestResultRepository resultRepository,
                             DiagnosisRepository diagnosisRepository,
                             ApplicationEventPublisher eventPublisher,
                             JdbcClient jdbc) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.eventPublisher = eventPublisher;
        this.jdbc = jdbc;
    }

    @Transactional
    public SubmissionAcceptedResponse create(Long userId, Long problemId, CreateSubmissionRequest request) {
        return createInternal(userId, problemId, request, false);
    }

    @Transactional
    public SubmissionAcceptedResponse createGuest(Long problemId, CreateSubmissionRequest request) {
        return createInternal(0L, problemId, request, true);
    }

    @Transactional
    public SubmissionAcceptedResponse createSelfPractice(SelfPracticeRunRequest request) {
        String language = request.language().trim().toLowerCase();
        if (language.equals("c++")) language = "cpp";
        if (!List.of("python", "java", "cpp", "go").contains(language)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "自由练习仅支持 Python、Java、C++ 和 Go");
        }
        // problem_id=1 仅用于满足现有提交表外键；自由练习不会参与题目成绩和掌握度。
        Submission submission = Submission.queued(0L, 1L, language, request.sourceCode(), 1);
        submissionRepository.save(submission);
        jdbc.sql("INSERT INTO self_practice_input(submission_id,input_data) VALUES (:id,:input)")
                .param("id", submission.getId())
                .param("input", request.stdin() == null ? "" : request.stdin())
                .update();
        eventPublisher.publishEvent(new ExecutionQueuedEvent(submission.getId()));
        return new SubmissionAcceptedResponse(submission.getId(), submission.getStatus());
    }

    private SubmissionAcceptedResponse createInternal(Long userId, Long problemId, CreateSubmissionRequest request,
                                                      boolean publicExamplesOnly) {
        Problem problem = problemRepository.findById(problemId)
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));

        if (!problem.getLanguage().equalsIgnoreCase(request.language())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "第一阶段只支持题目指定的语言：" + problem.getLanguage());
        }

        int totalCount = Math.toIntExact(publicExamplesOnly
                ? testCaseRepository.countByProblemIdAndHiddenFalse(problemId)
                : testCaseRepository.countByProblemId(problemId));
        if (totalCount == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, publicExamplesOnly
                    ? "题目尚未配置公开运行示例" : "题目尚未配置测试用例");
        }

        Submission submission = Submission.queued(
                userId, problemId, request.language().toLowerCase(), request.sourceCode(), totalCount);
        submissionRepository.save(submission);
        eventPublisher.publishEvent(new ExecutionQueuedEvent(submission.getId()));
        return new SubmissionAcceptedResponse(submission.getId(), submission.getStatus());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(Long userId, Long submissionId) {
        Submission submission = getSubmission(submissionId);
        if (!submission.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该提交");
        }
        return toDetail(submission);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse detailForGuest(Long submissionId) {
        Submission submission = getSubmission(submissionId);
        if (submission.getUserId() != 0L) throw new BusinessException(HttpStatus.FORBIDDEN, "该记录不是游客运行");
        return toDetail(submission);
    }

    private SubmissionDetailResponse toDetail(Submission submission) {
        Long submissionId = submission.getId();
        List<SubmissionTestResult> results = resultRepository.findBySubmissionIdOrderByTestCaseIdAsc(submissionId);
        Diagnosis diagnosis = diagnosisRepository.findBySubmissionId(submissionId).orElse(null);
        return new SubmissionDetailResponse(
                submission.getId(), submission.getProblemId(), submission.getLanguage(), submission.getStatus(),
                submission.getPassedCount(), submission.getTotalCount(), submission.getRuntimeMs(),
                submission.getMemoryKb(), submission.getErrorMessage(), submission.getCreatedAt(), results, diagnosis);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionHistoryItem> history(Long userId, Long problemId, Pageable pageable) {
        Page<Submission> page = problemId == null
                ? submissionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : submissionRepository.findByUserIdAndProblemIdOrderByCreatedAtDesc(userId, problemId, pageable);
        return page.map(item -> new SubmissionHistoryItem(
                item.getId(), item.getProblemId(), item.getLanguage(), item.getStatus(),
                item.getPassedCount(), item.getTotalCount(), item.getRuntimeMs(), item.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public ExecutionTaskResponse executionTask(Long submissionId) {
        Submission submission = getSubmission(submissionId);
        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));
        List<String> selfInputs = jdbc.sql("SELECT input_data FROM self_practice_input WHERE submission_id=:id")
                .param("id", submissionId).query(String.class).list();
        if (!selfInputs.isEmpty()) {
            return new ExecutionTaskResponse(
                    submission.getId(), submission.getLanguage(), submission.getSourceCode(),
                    5000, 256,
                    List.of(new ExecutionTaskResponse.TestCaseData(1L, selfInputs.getFirst(), null)));
        }
        List<TestCase> selectedCases = submission.getUserId() == 0L
                ? testCaseRepository.findByProblemIdAndHiddenFalseOrderBySortOrderAsc(problem.getId())
                : testCaseRepository.findByProblemIdOrderBySortOrderAsc(problem.getId());
        List<ExecutionTaskResponse.TestCaseData> testCases = selectedCases
                .stream()
                .map(test -> new ExecutionTaskResponse.TestCaseData(
                        test.getId(), test.getInputData(), test.getExpectedOutput()))
                .toList();
        return new ExecutionTaskResponse(
                submission.getId(), submission.getLanguage(), submission.getSourceCode(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(), testCases);
    }

    @Transactional
    public void markRunning(Long submissionId) {
        Submission submission = getSubmission(submissionId);
        submission.transitionTo(SubmissionStatus.PREPARING);
    }

    @Transactional
    public void markStage(Long submissionId, SubmissionStatus status) {
        if (status != SubmissionStatus.COMPILING && status != SubmissionStatus.RUNNING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的执行阶段：" + status);
        }
        getSubmission(submissionId).transitionTo(status);
    }

    @Transactional
    public void applyExecutionResult(Long submissionId, ExecutionCallbackRequest request) {
        Submission submission = getSubmission(submissionId);
        resultRepository.deleteAllForSubmission(submissionId);
        for (ExecutionTestResultRequest item : request.results()) {
            resultRepository.save(SubmissionTestResult.create(
                    submissionId, item.testCaseId(), item.status(), item.actualOutput(),
                    item.stderr(), item.runtimeMs(), item.memoryKb()));
        }

        submission.applyExecutionSummary(
                request.passedCount(), request.totalCount(), request.runtimeMs(),
                request.memoryKb(), request.errorMessage());

        if (request.infrastructureFailure()) {
            submission.transitionTo(SubmissionStatus.FAILED);
            return;
        }

        submission.transitionTo(SubmissionStatus.ANALYZING);
        submission.transitionTo(SubmissionStatus.DIAGNOSING);
        eventPublisher.publishEvent(new DiagnosisQueuedEvent(submissionId));
    }

    private Submission getSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "提交不存在"));
    }
}
