INSERT INTO problem (
    id, course_id, slug, title, description, input_description, output_description,
    starter_code, language, difficulty, time_limit_ms, memory_limit_mb, status
) VALUES
(2, 1, 'even-or-odd', '奇偶判断',
 '读取一个整数，判断它是奇数还是偶数。', '一个整数 n。', '偶数输出 EVEN，奇数输出 ODD。',
 'n = int(input())\n# 请判断并输出 EVEN 或 ODD\n', 'python', 1, 2000, 128, 'PUBLISHED'),
(3, 1, 'maximum-of-three', '三数最大值',
 '读取三个整数，输出其中的最大值。', '一行三个由空格分隔的整数。', '输出一个整数，表示最大值。',
 'a, b, c = map(int, input().split())\n# 请输出最大值\n', 'python', 1, 2000, 128, 'PUBLISHED'),
(4, 1, 'count-even', '统计偶数',
 '统计整数列表中偶数的个数。', '第一行是整数 n；第二行是 n 个整数。', '输出偶数的个数。',
 'n = int(input())\nnums = list(map(int, input().split()))\n# 请统计偶数个数\n', 'python', 1, 2000, 128, 'PUBLISHED'),
(5, 1, 'stable-deduplicate', '列表去重',
 '删除列表中重复的元素，并保留元素第一次出现的顺序。', '一行由空格分隔的整数。', '一行去重后的整数，数字之间用空格分隔。',
 'nums = list(map(int, input().split()))\nresult = []\n# 按原顺序去重后输出\n', 'python', 2, 2000, 128, 'PUBLISHED'),
(6, 1, 'reverse-string', '字符串反转',
 '将输入的一行字符串反转后输出。', '一行字符串，可包含空格。', '输出反转后的字符串。',
 'text = input()\n# 请输出反转后的字符串\n', 'python', 1, 2000, 128, 'PUBLISHED'),
(7, 1, 'prime-number', '素数判断',
 '判断给定整数是否为素数。素数是大于 1 且只有 1 和自身两个正因数的整数。', '一个整数 n（0 <= n <= 100000）。', '是素数输出 YES，否则输出 NO。',
 'n = int(input())\n# 请判断 n 是否为素数\n', 'python', 2, 2000, 128, 'PUBLISHED'),
(8, 1, 'fibonacci-sequence', '斐波那契数列',
 '输出斐波那契数列的前 n 项。数列从 0、1 开始。', '一个整数 n（1 <= n <= 30）。', '一行 n 个整数，用空格分隔。',
 'n = int(input())\n# 生成并输出前 n 项\n', 'python', 2, 2000, 128, 'PUBLISHED'),
(9, 1, 'binary-search', '二分查找',
 '在升序整数列表中查找目标值第一次出现的下标，下标从 0 开始。',
 '第一行是 n 和 target；第二行是 n 个升序整数。', '找到则输出第一次出现的下标，否则输出 -1。',
 'n, target = map(int, input().split())\nnums = list(map(int, input().split()))\n# 请用二分查找输出答案\n', 'python', 3, 2000, 128, 'PUBLISHED');

INSERT INTO problem_knowledge_point (problem_id, knowledge_point_id, weight, is_primary) VALUES
(2, 2, 1.0, TRUE), (2, 6, 0.5, FALSE),
(3, 2, 1.0, TRUE), (3, 6, 0.7, FALSE),
(4, 3, 0.8, FALSE), (4, 4, 1.0, TRUE),
(5, 3, 0.6, FALSE), (5, 4, 1.0, TRUE),
(6, 3, 0.6, FALSE), (6, 4, 0.8, TRUE),
(7, 2, 0.7, FALSE), (7, 3, 1.0, TRUE), (7, 6, 0.9, FALSE),
(8, 3, 1.0, TRUE), (8, 5, 0.7, FALSE), (8, 6, 0.5, FALSE),
(9, 3, 0.8, FALSE), (9, 4, 1.0, TRUE), (9, 5, 0.6, FALSE), (9, 6, 0.9, FALSE);

-- 用户已明确要求当前版本的测试点全部公开，因此这里均设为 is_hidden=FALSE。
INSERT INTO test_case (problem_id, input_data, expected_output, is_hidden, weight, sort_order) VALUES
(2, '8', 'EVEN', FALSE, 1.0, 1), (2, '7', 'ODD', FALSE, 1.0, 2), (2, '-4', 'EVEN', FALSE, 1.0, 3),
(3, '3 9 5', '9', FALSE, 1.0, 1), (3, '-1 -7 -3', '-1', FALSE, 1.0, 2), (3, '6 6 2', '6', FALSE, 1.0, 3),
(4, '5\n1 2 3 4 6', '3', FALSE, 1.0, 1), (4, '4\n1 3 5 7', '0', FALSE, 1.0, 2), (4, '3\n0 -2 9', '2', FALSE, 1.0, 3),
(5, '1 2 1 3 2', '1 2 3', FALSE, 1.0, 1), (5, '5 5 5', '5', FALSE, 1.0, 2), (5, '-1 0 -1 2', '-1 0 2', FALSE, 1.0, 3),
(6, 'hello', 'olleh', FALSE, 1.0, 1), (6, 'Code Path', 'htaP edoC', FALSE, 1.0, 2), (6, 'a', 'a', FALSE, 1.0, 3),
(7, '2', 'YES', FALSE, 1.0, 1), (7, '17', 'YES', FALSE, 1.0, 2), (7, '1', 'NO', FALSE, 1.0, 3),
(8, '1', '0', FALSE, 1.0, 1), (8, '5', '0 1 1 2 3', FALSE, 1.0, 2), (8, '8', '0 1 1 2 3 5 8 13', FALSE, 1.0, 3),
(9, '5 4\n1 2 4 4 7', '2', FALSE, 1.0, 1), (9, '4 8\n1 3 5 7', '-1', FALSE, 1.0, 2), (9, '1 9\n9', '0', FALSE, 1.0, 3);
