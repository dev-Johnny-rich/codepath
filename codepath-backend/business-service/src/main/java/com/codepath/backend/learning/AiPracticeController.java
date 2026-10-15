package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.submission.CreateSubmissionRequest;
import com.codepath.backend.submission.SubmissionAcceptedResponse;
import com.codepath.backend.submission.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiPracticeController {
    private final AiPracticeService service;
    private final SubmissionService submissions;

    public AiPracticeController(AiPracticeService service, SubmissionService submissions) {
        this.service = service;
        this.submissions = submissions;
    }

    @PostMapping("/submissions/{submissionId}/generated-practice")
    public ApiResponse<AiPracticeService.GeneratedPractice> generate(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long submissionId) {
        return ApiResponse.ok(service.generate(principal.userId(), submissionId));
    }

    @PostMapping("/learning-plans/items/{itemId}/generated-practice")
    public ApiResponse<AiPracticeService.GeneratedPractice> generateForPlan(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long itemId) {
        return ApiResponse.ok(service.generateForPlanItem(principal.userId(), itemId));
    }

    @PostMapping("/learning-plans/{planId}/knowledge-test")
    public ApiResponse<AiPracticeService.GeneratedPractice> generateKnowledgeTest(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long planId) {
        return ApiResponse.ok(service.generateKnowledgeTest(principal.userId(), planId));
    }

    @GetMapping("/ai-practice/{id}")
    public ApiResponse<AiPracticeService.GeneratedPractice> get(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.findGenerated(principal.userId(), id));
    }

    @PostMapping("/ai-practice/{id}/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SubmissionAcceptedResponse> submit(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CreateSubmissionRequest request) {
        return ApiResponse.ok(submissions.createAiPractice(principal.userId(), id, request));
    }

    @PostMapping("/ai-practice/{id}/submissions/{submissionId}/evaluation")
    public ApiResponse<AiPracticeService.Evaluation> finalizeEvaluation(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long submissionId) {
        return ApiResponse.ok(service.finalizeEvaluation(principal.userId(), id, submissionId));
    }
}
