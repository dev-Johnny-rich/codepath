package com.codepath.backend.teacher;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.course.Course;
import com.codepath.backend.problem.Problem;
import com.codepath.backend.problem.TestCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherContentController {
    private final TeacherContentService service;

    public TeacherContentController(TeacherContentService service) {
        this.service = service;
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Course> createCourse(@AuthenticationPrincipal AppPrincipal principal,
                                            @Valid @RequestBody TeacherDtos.CourseRequest request) {
        return ApiResponse.ok(service.createCourse(principal, request));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<Course> updateCourse(@AuthenticationPrincipal AppPrincipal principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody TeacherDtos.CourseRequest request) {
        return ApiResponse.ok(service.updateCourse(principal, id, request));
    }

    @GetMapping("/courses/{courseId}/problems")
    public ApiResponse<List<Problem>> problems(@AuthenticationPrincipal AppPrincipal principal,
                                               @PathVariable Long courseId) {
        return ApiResponse.ok(service.listProblems(principal, courseId));
    }

    @PostMapping("/problems")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Problem> createProblem(@AuthenticationPrincipal AppPrincipal principal,
                                              @Valid @RequestBody TeacherDtos.ProblemRequest request) {
        return ApiResponse.ok(service.createProblem(principal, request));
    }

    @PutMapping("/problems/{id}")
    public ApiResponse<Problem> updateProblem(@AuthenticationPrincipal AppPrincipal principal,
                                              @PathVariable Long id,
                                              @Valid @RequestBody TeacherDtos.ProblemRequest request) {
        return ApiResponse.ok(service.updateProblem(principal, id, request));
    }

    @GetMapping("/problems/{problemId}/test-cases")
    public ApiResponse<List<TestCase>> testCases(@AuthenticationPrincipal AppPrincipal principal,
                                                 @PathVariable Long problemId) {
        return ApiResponse.ok(service.testCases(principal, problemId));
    }

    @PostMapping("/problems/{problemId}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TestCase> createTestCase(@AuthenticationPrincipal AppPrincipal principal,
                                                @PathVariable Long problemId,
                                                @Valid @RequestBody TeacherDtos.TestCaseRequest request) {
        return ApiResponse.ok(service.createTestCase(principal, problemId, request));
    }

    @PutMapping("/problems/{problemId}/test-cases/{testCaseId}")
    public ApiResponse<TestCase> updateTestCase(@AuthenticationPrincipal AppPrincipal principal,
                                                @PathVariable Long problemId,
                                                @PathVariable Long testCaseId,
                                                @Valid @RequestBody TeacherDtos.TestCaseRequest request) {
        return ApiResponse.ok(service.updateTestCase(principal, problemId, testCaseId, request));
    }

    @DeleteMapping("/problems/{problemId}/test-cases/{testCaseId}")
    public ApiResponse<Void> deleteTestCase(@AuthenticationPrincipal AppPrincipal principal,
                                            @PathVariable Long problemId,
                                            @PathVariable Long testCaseId) {
        service.deleteTestCase(principal, problemId, testCaseId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/problems/{problemId}/knowledge-points")
    public ApiResponse<Void> bindKnowledgePoints(@AuthenticationPrincipal AppPrincipal principal,
                                                 @PathVariable Long problemId,
                                                 @Valid @RequestBody List<TeacherDtos.KnowledgeBindingRequest> bindings) {
        service.bindKnowledgePoints(principal, problemId, bindings);
        return ApiResponse.ok(null);
    }
}
