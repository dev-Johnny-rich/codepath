INSERT INTO course (id, code, name, language, description, status)
VALUES (1, 'PYTHON_BASIC', 'Python 程序设计基础', 'python', 'CodePath 第一阶段课程', 'PUBLISHED');

INSERT INTO knowledge_point (id, course_id, code, name, description, difficulty, sort_order) VALUES
(1, 1, 'VARIABLE', '变量与数据类型', '变量赋值、基础数据类型和类型转换', 1, 10),
(2, 1, 'CONDITION', '条件判断', 'if、elif、else 与布尔表达式', 1, 20),
(3, 1, 'LOOP', '循环结构', 'for、while 与循环控制', 1, 30),
(4, 1, 'LIST', '列表与索引', '列表创建、访问、修改和遍历', 2, 40),
(5, 1, 'FUNCTION', '函数', '函数定义、参数、返回值和作用域', 2, 50),
(6, 1, 'BOUNDARY', '边界条件', '空输入、最大最小值和循环边界', 2, 60);

INSERT INTO knowledge_edge (course_id, source_id, target_id, edge_type, weight) VALUES
(1, 1, 2, 'PREREQUISITE_OF', 1.0),
(1, 2, 3, 'PREREQUISITE_OF', 1.0),
(1, 3, 4, 'PREREQUISITE_OF', 0.9),
(1, 3, 6, 'PREREQUISITE_OF', 0.8),
(1, 4, 5, 'PREREQUISITE_OF', 0.7);

INSERT INTO problem (
    id, course_id, slug, title, description, input_description, output_description,
    starter_code, language, difficulty, time_limit_ms, memory_limit_mb, status
) VALUES (
    1, 1, 'sum-list', '列表求和',
    '读取一行整数，输出所有整数之和。空行时输出 0。',
    '一行由空格分隔的整数，允许为空行。',
    '输出整数之和。',
    'line = input().strip()\nnums = list(map(int, line.split())) if line else []\n# 请计算并输出结果\n',
    'python', 1, 2000, 128, 'PUBLISHED'
);

INSERT INTO problem_knowledge_point (problem_id, knowledge_point_id, weight, is_primary) VALUES
(1, 3, 0.4, FALSE),
(1, 4, 1.0, TRUE),
(1, 5, 0.6, FALSE),
(1, 6, 0.8, FALSE);

INSERT INTO test_case (problem_id, input_data, expected_output, is_hidden, weight, sort_order) VALUES
(1, '1 2 3', '6', FALSE, 1.0, 1),
(1, '-3 1 2', '0', TRUE, 1.0, 2),
(1, '', '0', TRUE, 1.0, 3);
