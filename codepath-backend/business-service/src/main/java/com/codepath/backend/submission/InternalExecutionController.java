package com.codepath.backend.submission;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/submissions")
public class InternalExecutionController {
    private final SubmissionService submissionService;
    private final String internalToken;

    public InternalExecutionController(
            SubmissionService submissionService,
            @Value("${codepath.internal-service-token}") String internalToken) {
        this.submissionService = submissionService;
        this.internalToken = internalToken;
    }

    @GetMapping("/{submissionId}/execution-task")
    public ApiResponse<ExecutionTaskResponse> executionTask(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long submissionId) {
        verify(token);
        return ApiResponse.ok(submissionService.executionTask(submissionId));
    }

    @PostMapping("/{submissionId}/started")
    public ApiResponse<Void> started(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long submissionId) {
        verify(token);
        submissionService.markRunning(submissionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{submissionId}/execution-result")
    public ApiResponse<Void> executionResult(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long submissionId,
            @Valid @RequestBody ExecutionCallbackRequest request) {
        verify(token);
        submissionService.applyExecutionResult(submissionId, request);
        return ApiResponse.ok(null);
    }

    private void verify(String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "无效的内部服务令牌");
        }
    }
}
