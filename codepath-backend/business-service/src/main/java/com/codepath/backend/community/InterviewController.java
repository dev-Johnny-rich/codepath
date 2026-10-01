package com.codepath.backend.community;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/interview")
public class InterviewController {
    private final JdbcClient jdbc;
    public InterviewController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping("/assessments")
    public ApiResponse<List<Map<String,Object>>> assessments(@AuthenticationPrincipal AppPrincipal user) {
        return ApiResponse.ok(jdbc.sql("""
            SELECT s.id,s.problem_id,p.title problem_title,p.difficulty,s.language,s.status,
                   s.passed_count,s.total_count,s.runtime_ms,s.memory_kb,s.error_message,s.created_at,
                   d.error_type,d.root_cause,d.confidence,d.next_action,d.model_name
            FROM submission s JOIN problem p ON p.id=s.problem_id
            LEFT JOIN diagnosis d ON d.submission_id=s.id
            WHERE s.user_id=:userId ORDER BY s.created_at DESC LIMIT 200
            """).param("userId",user.userId()).query().listOfRows());
    }
}
