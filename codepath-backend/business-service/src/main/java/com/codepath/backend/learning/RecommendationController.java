package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RecommendationService.RecommendationView>> list(
            @AuthenticationPrincipal AppPrincipal principal, @RequestParam Long courseId) {
        return ApiResponse.ok(service.generate(principal.userId(), courseId));
    }

    @GetMapping("/next")
    public ApiResponse<RecommendationService.RecommendationView> next(
            @AuthenticationPrincipal AppPrincipal principal, @RequestParam Long courseId) {
        return ApiResponse.ok(service.next(principal.userId(), courseId));
    }
}
