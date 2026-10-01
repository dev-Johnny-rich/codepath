package com.codepath.backend.submission;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/problems/{problemId}/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SubmissionAcceptedResponse> create(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long problemId,
            @Valid @RequestBody CreateSubmissionRequest request) {
        return ApiResponse.ok(submissionService.create(principal.userId(), problemId, request));
    }

    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<SubmissionDetailResponse> detail(
            @AuthenticationPrincipal AppPrincipal principal,
            @PathVariable Long submissionId) {
        return ApiResponse.ok(submissionService.detail(principal.userId(), submissionId));
    }

    @GetMapping("/submissions")
    public ApiResponse<Page<SubmissionHistoryItem>> history(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam(required = false) Long problemId,
            Pageable pageable) {
        return ApiResponse.ok(submissionService.history(principal.userId(), problemId, pageable));
    }
}
