package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
public class LearningAssistanceController {
    private final LearningAssistanceService service;

    public LearningAssistanceController(LearningAssistanceService service) {
        this.service = service;
    }

    @GetMapping("/{submissionId}/learning-assistance")
    public ApiResponse<LearningAssistanceService.LearningAssistanceView> get(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long submissionId) {
        return ApiResponse.ok(service.get(principal.userId(), submissionId));
    }
}
