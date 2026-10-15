package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/learning-assessment")
public class UserLearningAssessmentController {
    private final UserLearningAssessmentService service;

    public UserLearningAssessmentController(UserLearningAssessmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<UserLearningAssessmentService.AssessmentView> current(
            @AuthenticationPrincipal AppPrincipal principal) {
        return ApiResponse.ok(service.current(principal.userId()));
    }

    @PostMapping
    public ApiResponse<UserLearningAssessmentService.AssessmentView> generate(
            @AuthenticationPrincipal AppPrincipal principal) {
        return ApiResponse.ok(service.generate(principal.userId()));
    }
}
