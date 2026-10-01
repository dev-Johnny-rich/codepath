package com.codepath.backend.course;

import com.codepath.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    public CourseController(CourseRepository courseRepository,
                            KnowledgePointRepository knowledgePointRepository) {
        this.courseRepository = courseRepository;
        this.knowledgePointRepository = knowledgePointRepository;
    }

    @GetMapping
    public ApiResponse<List<Course>> list() {
        return ApiResponse.ok(courseRepository.findByStatusOrderByIdAsc("PUBLISHED"));
    }

    @GetMapping("/{courseId}/knowledge-points")
    public ApiResponse<List<KnowledgePoint>> knowledgePoints(@PathVariable Long courseId) {
        return ApiResponse.ok(knowledgePointRepository.findByCourseIdOrderBySortOrderAsc(courseId));
    }
}
