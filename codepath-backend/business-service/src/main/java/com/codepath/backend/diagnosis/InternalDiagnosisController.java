package com.codepath.backend.diagnosis;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/diagnoses")
public class InternalDiagnosisController {
    private final DiagnosisService diagnosisService;
    private final String internalToken;

    public InternalDiagnosisController(
            DiagnosisService diagnosisService,
            @Value("${codepath.internal-service-token}") String internalToken) {
        this.diagnosisService = diagnosisService;
        this.internalToken = internalToken;
    }

    @GetMapping("/{submissionId}/task")
    public ApiResponse<DiagnosisTaskResponse> task(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long submissionId) {
        verify(token);
        return ApiResponse.ok(diagnosisService.task(submissionId));
    }

    @PostMapping("/{submissionId}/result")
    public ApiResponse<Void> result(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long submissionId,
            @Valid @RequestBody DiagnosisCallbackRequest request) {
        verify(token);
        diagnosisService.complete(submissionId, request);
        return ApiResponse.ok(null);
    }

    private void verify(String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "无效的内部服务令牌");
        }
    }
}
