package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiPracticeController {
    private final AiPracticeService service;

    public AiPracticeController(AiPracticeService service) {
        this.service = service;
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

    @GetMapping("/ai-practice/{id}")
    public ApiResponse<AiPracticeService.GeneratedPractice> get(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(service.findGenerated(principal.userId(), id));
    }

    @PostMapping("/ai-practice/{id}/evaluate")
    public ApiResponse<AiPracticeService.Evaluation> evaluate(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AiPracticeService.EvaluationRequest request) {
        return ApiResponse.ok(service.evaluate(principal.userId(), id, request));
    }
}
