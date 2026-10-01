package com.codepath.backend.coach;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TeachingResourceController {
    private final JdbcClient jdbc;

    public TeachingResourceController(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record ResourceRequest(@NotNull Long courseId, Long knowledgePointId,
                                  @NotBlank String title, @NotBlank String resourceType,
                                  @NotBlank String content, String sourceUrl, String status) {}

    @PostMapping("/teacher/resources")
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal AppPrincipal user,
                                                    @Valid @RequestBody ResourceRequest request) {
        jdbc.sql("""
            INSERT INTO teaching_resource(course_id, knowledge_point_id, title, resource_type, content, source_url, status, created_by)
            VALUES (:courseId, :knowledgeId, :title, :type, :content, :url, :status, :userId)
            """).param("courseId", request.courseId()).param("knowledgeId", request.knowledgePointId())
            .param("title", request.title()).param("type", request.resourceType())
            .param("content", request.content()).param("url", request.sourceUrl())
            .param("status", request.status() == null ? "PUBLISHED" : request.status())
            .param("userId", user.userId()).update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/teacher/resources/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ResourceRequest request) {
        jdbc.sql("""
            UPDATE teaching_resource SET course_id=:courseId, knowledge_point_id=:knowledgeId,
              title=:title, resource_type=:type, content=:content, source_url=:url, status=:status WHERE id=:id
            """).param("courseId", request.courseId()).param("knowledgeId", request.knowledgePointId())
            .param("title", request.title()).param("type", request.resourceType())
            .param("content", request.content()).param("url", request.sourceUrl())
            .param("status", request.status() == null ? "PUBLISHED" : request.status()).param("id", id).update();
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/teacher/resources/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbc.sql("DELETE FROM teaching_resource WHERE id=:id").param("id", id).update();
        return ApiResponse.ok(null);
    }

    @GetMapping("/resources")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam Long courseId,
                                                         @RequestParam(defaultValue = "") String q) {
        String like = "%" + q.trim() + "%";
        return ApiResponse.ok(jdbc.sql("""
            SELECT id, course_id, knowledge_point_id, title, resource_type, content, source_url, updated_at
            FROM teaching_resource WHERE course_id=:courseId AND status='PUBLISHED'
              AND (:q='' OR title LIKE :like OR content LIKE :like)
            ORDER BY updated_at DESC LIMIT 20
            """).param("courseId", courseId).param("q", q.trim()).param("like", like).query().listOfRows());
    }
}
