package com.codepath.backend.submission;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GuestRunController {
    private final SubmissionService submissions;
    private final JdbcClient jdbc;
    public GuestRunController(SubmissionService submissions, JdbcClient jdbc) {
        this.submissions = submissions; this.jdbc = jdbc;
    }

    public record GuestRunAccepted(Long submissionId, String runToken, SubmissionStatus status) {}

    @PostMapping("/problems/{problemId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public ApiResponse<GuestRunAccepted> create(@PathVariable Long problemId,
                                                @Valid @RequestBody CreateSubmissionRequest request) {
        SubmissionAcceptedResponse accepted = submissions.createGuest(problemId, request);
        return ApiResponse.ok(registerGuestRun(accepted));
    }

    @PostMapping("/self-practice/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public ApiResponse<GuestRunAccepted> createSelfPractice(@Valid @RequestBody SelfPracticeRunRequest request) {
        return ApiResponse.ok(registerGuestRun(submissions.createSelfPractice(request)));
    }

    private GuestRunAccepted registerGuestRun(SubmissionAcceptedResponse accepted) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        jdbc.sql("INSERT INTO guest_run(submission_id,access_token_hash,expires_at) VALUES (:s,:h,:e)")
            .param("s", accepted.submissionId()).param("h", hash(token))
            .param("e", LocalDateTime.now().plusHours(2)).update();
        return new GuestRunAccepted(accepted.submissionId(), token, accepted.status());
    }

    @GetMapping("/runs/{submissionId}")
    public ApiResponse<SubmissionDetailResponse> detail(@PathVariable Long submissionId,
                                                         @RequestHeader("X-Run-Token") String token) {
        Integer allowed = jdbc.sql("""
            SELECT COUNT(*) FROM guest_run WHERE submission_id=:s AND access_token_hash=:h AND expires_at>NOW()
            """).param("s", submissionId).param("h", hash(token)).query(Integer.class).single();
        if (allowed == 0) throw new BusinessException(HttpStatus.FORBIDDEN, "运行凭证无效或已过期");
        return ApiResponse.ok(submissions.detailForGuest(submissionId));
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
