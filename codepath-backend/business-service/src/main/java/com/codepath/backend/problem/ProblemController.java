package com.codepath.backend.problem;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public ProblemController(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @GetMapping
    public ApiResponse<List<Problem>> list(@RequestParam Long courseId) {
        return ApiResponse.ok(problemRepository.findByCourseIdAndStatusOrderByDifficultyAscIdAsc(courseId, "PUBLISHED"));
    }

    @GetMapping("/{problemId}")
    public ApiResponse<Problem> detail(@PathVariable Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));
        return ApiResponse.ok(problem);
    }

    public record PublicExample(Long id, String input, String expectedOutput) {}

    @GetMapping("/{problemId}/examples")
    public ApiResponse<List<PublicExample>> examples(@PathVariable Long problemId) {
        problemRepository.findById(problemId)
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));
        return ApiResponse.ok(testCaseRepository.findByProblemIdAndHiddenFalseOrderBySortOrderAsc(problemId)
                .stream().map(item -> new PublicExample(item.getId(), item.getInputData(), item.getExpectedOutput()))
                .toList());
    }
}
