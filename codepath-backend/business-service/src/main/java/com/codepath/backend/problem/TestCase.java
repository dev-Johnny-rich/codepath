package com.codepath.backend.problem;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "test_case")
@Getter
@NoArgsConstructor
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "input_data", columnDefinition = "MEDIUMTEXT")
    private String inputData;

    @Column(name = "expected_output", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String expectedOutput;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static TestCase create(Long problemId, String inputData, String expectedOutput,
                                  boolean hidden, BigDecimal weight, Integer sortOrder) {
        TestCase testCase = new TestCase();
        testCase.problemId = problemId;
        testCase.inputData = inputData;
        testCase.expectedOutput = expectedOutput;
        testCase.hidden = hidden;
        testCase.weight = weight;
        testCase.sortOrder = sortOrder;
        return testCase;
    }

    public void update(String inputData, String expectedOutput, boolean hidden,
                       BigDecimal weight, Integer sortOrder) {
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
        this.hidden = hidden;
        this.weight = weight;
        this.sortOrder = sortOrder;
    }
}
