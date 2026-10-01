package com.codepath.backend.coach;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class CoachController {
    private final JdbcClient jdbc;
    private final RestClient ai;

    public CoachController(JdbcClient jdbc, RestClient.Builder builder,
                           @Value("${codepath.ai-service-url}") String aiUrl) {
        this.jdbc = jdbc;
        this.ai = builder.baseUrl(aiUrl).build();
    }

    public record ConversationRequest(Long submissionId, String title) {}
    public record MessageRequest(@NotBlank String content) {}
    public record HintRequest(@Min(1) @Max(3) int level, String question) {}
    public record GuestChatMessage(
            @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 4000) String content) {}
    public record GuestChatRequest(
            @NotEmpty @Size(max = 20) List<@Valid GuestChatMessage> messages) {}

    @PostMapping("/coach/guest-chat")
    public ApiResponse<Map<String, Object>> guestChat(@Valid @RequestBody GuestChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", request.messages());
        payload.put("context", "当前是游客通用编程问答，没有用户私有提交数据。");
        Map response = ai.post().uri("/v1/coach/chat").body(payload).retrieve().body(Map.class);
        return ApiResponse.ok(Map.of(
                "content", Objects.toString(response.get("content")),
                "modelName", Objects.toString(response.get("modelName"))));
    }

    @PostMapping("/coach/conversations")
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal AppPrincipal user,
                                                    @RequestBody ConversationRequest request) {
        jdbc.sql("INSERT INTO ai_conversation(user_id,submission_id,title) VALUES (:u,:s,:t)")
            .param("u", user.userId()).param("s", request.submissionId())
            .param("t", request.title() == null ? "AI 编程教练" : request.title()).update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return ApiResponse.ok(Map.of("id", id));
    }

    @GetMapping("/coach/conversations/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@AuthenticationPrincipal AppPrincipal user,
                                                            @PathVariable Long id) {
        verifyConversation(id, user.userId());
        return ApiResponse.ok(jdbc.sql("SELECT id,role,content,model_name,created_at FROM ai_message WHERE conversation_id=:id ORDER BY id")
            .param("id", id).query().listOfRows());
    }

    @PostMapping("/coach/conversations/{id}/messages")
    public ApiResponse<Map<String, Object>> message(@AuthenticationPrincipal AppPrincipal user,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody MessageRequest request) {
        Map<String, Object> conversation = verifyConversation(id, user.userId());
        insertMessage(id, "user", request.content(), null);
        List<Map<String, Object>> history = jdbc.sql("SELECT role,content FROM ai_message WHERE conversation_id=:id ORDER BY id DESC LIMIT 20")
            .param("id", id).query().listOfRows();
        Collections.reverse(history);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", history);
        payload.put("context", buildContext((Number) conversation.get("submission_id"), request.content()));
        Map response = ai.post().uri("/v1/coach/chat").body(payload).retrieve().body(Map.class);
        String content = Objects.toString(response.get("content"));
        String model = Objects.toString(response.get("modelName"));
        insertMessage(id, "assistant", content, model);
        return ApiResponse.ok(Map.of("content", content, "modelName", model));
    }

    @PostMapping("/submissions/{submissionId}/hints")
    public ApiResponse<Map<String, Object>> hint(@AuthenticationPrincipal AppPrincipal user,
                                                 @PathVariable Long submissionId,
                                                 @Valid @RequestBody HintRequest request) {
        Integer owned = jdbc.sql("SELECT COUNT(*) FROM submission WHERE id=:id AND user_id=:u")
            .param("id", submissionId).param("u", user.userId()).query(Integer.class).single();
        if (owned == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "提交记录不存在");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", List.of(Map.of("role", "user", "content",
            request.question() == null ? "请给我下一层提示" : request.question())));
        payload.put("context", buildContext(submissionId, request.question()));
        payload.put("hintLevel", request.level());
        Map response = ai.post().uri("/v1/coach/hint").body(payload).retrieve().body(Map.class);
        return ApiResponse.ok(response);
    }

    private Map<String, Object> verifyConversation(Long id, Long userId) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id,submission_id FROM ai_conversation WHERE id=:id AND user_id=:u")
            .param("id", id).param("u", userId).query().listOfRows();
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        return rows.getFirst();
    }

    private void insertMessage(Long id, String role, String content, String model) {
        jdbc.sql("INSERT INTO ai_message(conversation_id,role,content,model_name) VALUES (:id,:r,:c,:m)")
            .param("id", id).param("r", role).param("c", content).param("m", model).update();
    }

    private String buildContext(Number submissionId, String query) {
        StringBuilder context = new StringBuilder();
        Long courseId = null;
        if (submissionId != null) {
            List<Map<String, Object>> rows = jdbc.sql("""
                SELECT p.course_id,p.title,p.description,s.language,s.source_code,d.error_type,d.root_cause,d.explanation
                FROM submission s JOIN problem p ON p.id=s.problem_id LEFT JOIN diagnosis d ON d.submission_id=s.id
                WHERE s.id=:id
                """).param("id", submissionId.longValue()).query().listOfRows();
            Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.getFirst();
            if (!row.isEmpty()) { courseId = ((Number) row.get("course_id")).longValue(); context.append(row); }
        }
        if (courseId != null) {
            String like = "%" + (query == null ? "" : query) + "%";
            List<Map<String, Object>> resources = jdbc.sql("""
                SELECT title,content FROM teaching_resource WHERE course_id=:c AND status='PUBLISHED'
                  AND (:q='%%' OR title LIKE :q OR content LIKE :q) LIMIT 5
                """).param("c", courseId).param("q", like).query().listOfRows();
            context.append("\n教学资料:").append(resources);
        }
        return context.length() > 30000 ? context.substring(0, 30000) : context.toString();
    }
}
