package com.codepath.backend.graph;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.neo4j.driver.Driver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeGraphController {
    private final Driver driver;
    private final JdbcClient jdbc;
    public KnowledgeGraphController(Driver driver, JdbcClient jdbc) { this.driver = driver; this.jdbc = jdbc; }

    @PostMapping("/teacher/courses/{courseId}/knowledge-graph/sync")
    public ApiResponse<Map<String, Object>> sync(@PathVariable Long courseId) {
        List<Map<String, Object>> nodes = jdbc.sql("SELECT id,name,description FROM knowledge_point WHERE course_id=:c")
            .param("c", courseId).query().listOfRows();
        List<Map<String, Object>> edges = jdbc.sql("""
            SELECT e.source_id source,e.target_id target,e.edge_type relation_type
            FROM knowledge_edge e WHERE e.course_id=:c
            """).param("c", courseId).query().listOfRows();
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                for (var n : nodes) tx.run("""
                    MERGE (k:KnowledgePoint {mysqlId:$id})
                    SET k.courseId=$courseId,k.name=$name,k.description=$description
                    """, Map.of("id", ((Number)n.get("id")).longValue(), "courseId", courseId,
                    "name", Objects.toString(n.get("name"), ""), "description", Objects.toString(n.get("description"), "")));
                for (var e : edges) tx.run("""
                    MATCH (a:KnowledgePoint {mysqlId:$source}),(b:KnowledgePoint {mysqlId:$target})
                    MERGE (a)-[r:PREREQUISITE]->(b) SET r.relationType=$type
                    """, Map.of("source", ((Number)e.get("source")).longValue(),
                    "target", ((Number)e.get("target")).longValue(), "type", Objects.toString(e.get("relation_type"), "PREREQUISITE")));
                return null;
            });
        }
        return ApiResponse.ok(Map.of("nodes", nodes.size(), "edges", edges.size()));
    }

    @GetMapping("/courses/{courseId}/knowledge-graph")
    public ApiResponse<Map<String, Object>> graph(@PathVariable Long courseId,
                                                  @AuthenticationPrincipal AppPrincipal user) {
        try (var session = driver.session()) {
            var records = session.run("""
                MATCH (k:KnowledgePoint {courseId:$courseId})
                RETURN k.mysqlId AS id,k.name AS name,k.description AS description ORDER BY k.mysqlId
                """, Map.of("courseId", courseId)).list();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Object> edges = new ArrayList<>();
            for (var record : records) {
                nodes.add(Map.of("id", record.get("id").asLong(), "name", record.get("name").asString(),
                    "description", record.get("description").asString("")));
            }
            var edgeRecords = session.run("""
                MATCH (a:KnowledgePoint {courseId:$courseId})-[r:PREREQUISITE]->(b:KnowledgePoint)
                RETURN a.mysqlId AS source,b.mysqlId AS target,r.relationType AS relationType
                """, Map.of("courseId", courseId)).list();
            for (var edge : edgeRecords) edges.add(Map.of("source", edge.get("source").asLong(),
                "target", edge.get("target").asLong(), "type", edge.get("relationType").asString("PREREQUISITE")));
            return ApiResponse.ok(Map.of("nodes", nodes, "edges", edges, "userId", user.userId()));
        }
    }
}
