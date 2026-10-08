package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning-plans")
public class LearningPlanController {
    private final LearningPlanService service;

    public LearningPlanController(LearningPlanService service) {
        this.service = service;
    }

    @GetMapping("/tracks")
    public ApiResponse<java.util.List<LearningPlanService.TrackView>> tracks() {
        return ApiResponse.ok(service.tracks());
    }

    @GetMapping("/current")
    public ApiResponse<LearningPlanService.LearningPlanView> current(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam Long courseId) {
        return ApiResponse.ok(service.current(principal.userId(), courseId));
    }

    @GetMapping("/active")
    public ApiResponse<LearningPlanService.LearningPlanView> active(
            @AuthenticationPrincipal AppPrincipal principal) {
        return ApiResponse.ok(service.active(principal.userId()));
    }

    @PostMapping("/generate")
    public ApiResponse<LearningPlanService.LearningPlanView> generate(
            @AuthenticationPrincipal AppPrincipal principal,
            @Valid @RequestBody LearningPlanService.GenerateRequest request) {
        return ApiResponse.ok(service.generate(principal.userId(), request));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<LearningPlanService.LearningPlanView> complete(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long itemId,
            @RequestBody LearningPlanService.CompletionRequest request) {
        return ApiResponse.ok(service.setCompleted(principal.userId(), itemId, request.completed()));
    }
}
