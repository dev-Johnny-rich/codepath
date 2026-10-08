package com.codepath.backend.teacher;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.auth.UserRole;
import com.codepath.backend.common.BusinessException;
import com.codepath.backend.course.Course;
import com.codepath.backend.course.CourseRepository;
import com.codepath.backend.problem.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TeacherContentService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> LANGUAGES = Set.of("python", "java", "cpp", "c++", "go");
    private final CourseRepository courseRepository;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final JdbcTemplate jdbc;

    public TeacherContentService(CourseRepository courseRepository,
                                 ProblemRepository problemRepository,
                                 TestCaseRepository testCaseRepository,
                                 JdbcTemplate jdbc) {
        this.courseRepository = courseRepository;
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public Course createCourse(AppPrincipal principal, TeacherDtos.CourseRequest request) {
        validateStatus(request.status());
        validateLanguage(request.language());
        if (courseRepository.existsByCode(request.code())) {
            throw new BusinessException(HttpStatus.CONFLICT, "课程编码已存在");
        }
        return courseRepository.save(Course.create(
                principal.userId(), request.code(), request.name(), request.language(),
                request.description(), request.status()));
    }

    @Transactional
    public Course updateCourse(AppPrincipal principal, Long id, TeacherDtos.CourseRequest request) {
        Course course = course(id);
        authorize(principal, course.getOwnerUserId());
        validateStatus(request.status());
        validateLanguage(request.language());
        course.update(request.name(), request.language(), request.description(), request.status());
        return course;
    }

    @Transactional(readOnly = true)
    public List<Problem> listProblems(AppPrincipal principal, Long courseId) {
        authorize(principal, course(courseId).getOwnerUserId());
        return problemRepository.findByCourseIdOrderByIdDesc(courseId);
    }

    @Transactional
    public Problem createProblem(AppPrincipal principal, TeacherDtos.ProblemRequest request) {
        Course course = course(request.courseId());
        authorize(principal, course.getOwnerUserId());
        validateStatus(request.status());
        validateLanguage(request.language());
        if (problemRepository.existsBySlug(request.slug())) {
            throw new BusinessException(HttpStatus.CONFLICT, "题目标识已存在");
        }
        return problemRepository.save(Problem.create(
                principal.userId(), request.courseId(), request.slug(), request.title(),
                request.description(), request.inputDescription(), request.outputDescription(),
                request.starterCode(), request.functionSignature(), normalizeLanguage(request.language()),
                request.difficulty(), request.timeLimitMs(), request.memoryLimitMb(), request.status()));
    }

    @Transactional
    public Problem updateProblem(AppPrincipal principal, Long id, TeacherDtos.ProblemRequest request) {
        Problem problem = problem(id);
        authorize(principal, course(problem.getCourseId()).getOwnerUserId());
        if (!problem.getCourseId().equals(request.courseId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能通过更新接口移动题目所属课程");
        }
        validateStatus(request.status());
        validateLanguage(request.language());
        problem.update(request.title(), request.description(), request.inputDescription(),
                request.outputDescription(), request.starterCode(), request.functionSignature(),
                normalizeLanguage(request.language()), request.difficulty(), request.timeLimitMs(),
                request.memoryLimitMb(), request.status());
        return problem;
    }

    @Transactional(readOnly = true)
    public List<TestCase> testCases(AppPrincipal principal, Long problemId) {
        Problem problem = problem(problemId);
        authorize(principal, course(problem.getCourseId()).getOwnerUserId());
        return testCaseRepository.findByProblemIdOrderBySortOrderAsc(problemId);
    }

    @Transactional
    public TestCase createTestCase(AppPrincipal principal, Long problemId,
                                   TeacherDtos.TestCaseRequest request) {
        Problem problem = problem(problemId);
        authorize(principal, course(problem.getCourseId()).getOwnerUserId());
        return testCaseRepository.save(TestCase.create(problemId, request.inputData(),
                request.expectedOutput(), request.hidden(), request.weight(), request.sortOrder()));
    }

    @Transactional
    public TestCase updateTestCase(AppPrincipal principal, Long problemId, Long testCaseId,
                                   TeacherDtos.TestCaseRequest request) {
        Problem problem = problem(problemId);
        authorize(principal, course(problem.getCourseId()).getOwnerUserId());
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .filter(item -> item.getProblemId().equals(problemId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "测试用例不存在"));
        testCase.update(request.inputData(), request.expectedOutput(), request.hidden(),
                request.weight(), request.sortOrder());
        return testCase;
    }

    @Transactional
    public void deleteTestCase(AppPrincipal principal, Long problemId, Long testCaseId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .filter(item -> item.getProblemId().equals(problemId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "测试用例不存在"));
        authorize(principal, course(problem(problemId).getCourseId()).getOwnerUserId());
        testCaseRepository.delete(testCase);
    }

    @Transactional
    public void bindKnowledgePoints(AppPrincipal principal, Long problemId,
                                    List<TeacherDtos.KnowledgeBindingRequest> bindings) {
        Problem problem = problem(problemId);
        authorize(principal, course(problem.getCourseId()).getOwnerUserId());
        jdbc.update("delete from problem_knowledge_point where problem_id = ?", problemId);
        for (var binding : bindings) {
            jdbc.update("insert into problem_knowledge_point " +
                            "(problem_id, knowledge_point_id, weight, is_primary) values (?, ?, ?, ?)",
                    problemId, binding.knowledgePointId(), binding.weight(), binding.primary());
        }
    }

    private Course course(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "课程不存在"));
    }

    private Problem problem(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "题目不存在"));
    }

    private static void authorize(AppPrincipal principal, Long ownerId) {
        if (principal.role() != UserRole.ADMIN && !principal.userId().equals(ownerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能管理自己创建的课程内容");
        }
    }

    private static void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无效的发布状态");
        }
    }

    private static void validateLanguage(String language) {
        if (!LANGUAGES.contains(language.toLowerCase())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 Python、Java 和 C++");
        }
    }

    private static String normalizeLanguage(String language) {
        return "c++".equalsIgnoreCase(language) ? "cpp" : language.toLowerCase();
    }
}
