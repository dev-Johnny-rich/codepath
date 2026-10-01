package com.codepath.backend.mastery;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.auth.AppPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/mastery")
public class MasteryController {
    private final MasteryService masteryService;

    public MasteryController(MasteryService masteryService) {
        this.masteryService = masteryService;
    }

    @GetMapping
    public ApiResponse<List<StudentMastery>> list(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long courseId) {
        return ApiResponse.ok(masteryService.list(principal.userId(), courseId));
    }
}
