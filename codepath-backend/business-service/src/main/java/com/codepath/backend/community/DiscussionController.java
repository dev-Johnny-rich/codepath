package com.codepath.backend.community;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/discussions")
public class DiscussionController {
    private final JdbcClient jdbc;
    public DiscussionController(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record PostRequest(@NotBlank @Size(max=180) String title,
                              @NotBlank @Size(max=20000) String content,
                              Long problemId, @Size(max=255) String tags) {}
    public record CommentRequest(@NotBlank @Size(max=5000) String content) {}

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required=false) Long problemId,
                                                       @RequestParam(defaultValue="") String q) {
        String like = "%" + q.trim() + "%";
        return ApiResponse.ok(jdbc.sql("""
            SELECT p.id,p.title,p.content,p.tags,p.problem_id,p.view_count,p.created_at,
                   u.id author_id,u.display_name author_name,u.role author_role,
                   pr.title problem_title,COUNT(c.id) comment_count
            FROM discussion_post p JOIN app_user u ON u.id=p.user_id
            LEFT JOIN problem pr ON pr.id=p.problem_id
            LEFT JOIN discussion_comment c ON c.post_id=p.id AND c.status='PUBLISHED'
            WHERE p.status='PUBLISHED' AND (:problemId IS NULL OR p.problem_id=:problemId)
              AND (:q='' OR p.title LIKE :like OR p.content LIKE :like OR p.tags LIKE :like)
            GROUP BY p.id,p.title,p.content,p.tags,p.problem_id,p.view_count,p.created_at,
                     u.id,u.display_name,u.role,pr.title
            ORDER BY p.created_at DESC LIMIT 100
            """).param("problemId", problemId, Types.BIGINT).param("q", q.trim()).param("like", like)
            .query().listOfRows());
    }

    @PostMapping
    public ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal AppPrincipal user,
                                                   @Valid @RequestBody PostRequest request) {
        jdbc.sql("INSERT INTO discussion_post(user_id,problem_id,title,content,tags) VALUES (:u,:p,:t,:c,:tags)")
            .param("u",user.userId()).param("p",request.problemId(),Types.BIGINT).param("t",request.title())
            .param("c",request.content()).param("tags",request.tags(),Types.VARCHAR).update();
        return ApiResponse.ok(Map.of("id", jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single()));
    }

    @GetMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String,Object>> detail(@PathVariable Long id) {
        jdbc.sql("UPDATE discussion_post SET view_count=view_count+1 WHERE id=:id AND status='PUBLISHED'").param("id",id).update();
        List<Map<String,Object>> posts=jdbc.sql("""
            SELECT p.id,p.title,p.content,p.tags,p.problem_id,p.view_count,p.created_at,
                   u.id author_id,u.display_name author_name,u.role author_role,pr.title problem_title
            FROM discussion_post p JOIN app_user u ON u.id=p.user_id LEFT JOIN problem pr ON pr.id=p.problem_id
            WHERE p.id=:id AND p.status='PUBLISHED'
            """).param("id",id).query().listOfRows();
        if(posts.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"讨论不存在");
        List<Map<String,Object>> comments=jdbc.sql("""
            SELECT c.id,c.content,c.created_at,u.id author_id,u.display_name author_name,u.role author_role
            FROM discussion_comment c JOIN app_user u ON u.id=c.user_id
            WHERE c.post_id=:id AND c.status='PUBLISHED' ORDER BY c.created_at
            """).param("id",id).query().listOfRows();
        Map<String,Object> result=new LinkedHashMap<>(posts.getFirst()); result.put("comments",comments);
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String,Object>> comment(@AuthenticationPrincipal AppPrincipal user,@PathVariable Long id,
                                                    @Valid @RequestBody CommentRequest request) {
        Integer count=jdbc.sql("SELECT COUNT(*) FROM discussion_post WHERE id=:id AND status='PUBLISHED'").param("id",id).query(Integer.class).single();
        if(count==0) throw new BusinessException(HttpStatus.NOT_FOUND,"讨论不存在");
        jdbc.sql("INSERT INTO discussion_comment(post_id,user_id,content) VALUES (:p,:u,:c)")
            .param("p",id).param("u",user.userId()).param("c",request.content()).update();
        return ApiResponse.ok(Map.of("id",jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single()));
    }
}
