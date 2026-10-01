-- 入门演示题的所有测试点用于教学讲解，全部公开。
-- 后续正式题仍可由教师在测试用例后台分别设置公开/隐藏。
UPDATE test_case
SET is_hidden = FALSE
WHERE problem_id = 1;
