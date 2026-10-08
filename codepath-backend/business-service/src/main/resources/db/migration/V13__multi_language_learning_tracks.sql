INSERT INTO course (code,name,language,description,status) VALUES
('JAVA_PLAN','Java 编程学习路径','java','用于 AI 个性化学习计划的 Java 知识体系','PLANNING_ONLY'),
('CPP_PLAN','C++ 编程学习路径','cpp','用于 AI 个性化学习计划的 C++ 知识体系','PLANNING_ONLY'),
('JAVASCRIPT_PLAN','JavaScript 学习路径','javascript','用于 AI 个性化学习计划的 JavaScript 知识体系','PLANNING_ONLY'),
('GO_PLAN','Go 编程学习路径','go','用于 AI 个性化学习计划的 Go 知识体系','PLANNING_ONLY');

INSERT INTO knowledge_point (course_id,code,name,description,difficulty,sort_order)
SELECT id,'JAVA_SYNTAX','Java 语法与类型','变量、基本类型、运算符和流程控制',1,10 FROM course WHERE code='JAVA_PLAN'
UNION ALL SELECT id,'JAVA_OOP','面向对象','类、对象、封装、继承与多态',1,20 FROM course WHERE code='JAVA_PLAN'
UNION ALL SELECT id,'JAVA_COLLECTION','集合框架','List、Set、Map 与泛型',2,30 FROM course WHERE code='JAVA_PLAN'
UNION ALL SELECT id,'JAVA_EXCEPTION','异常与 IO','异常处理、文件和流',2,40 FROM course WHERE code='JAVA_PLAN'
UNION ALL SELECT id,'JAVA_CONCURRENCY','并发编程','线程、同步与并发容器',3,50 FROM course WHERE code='JAVA_PLAN'
UNION ALL SELECT id,'JAVA_PROJECT','Java 项目实践','工程结构、测试和小型项目',3,60 FROM course WHERE code='JAVA_PLAN';

INSERT INTO knowledge_point (course_id,code,name,description,difficulty,sort_order)
SELECT id,'CPP_SYNTAX','C++ 语法与类型','基本类型、控制流和函数',1,10 FROM course WHERE code='CPP_PLAN'
UNION ALL SELECT id,'CPP_MEMORY','指针与内存','指针、引用、生命周期和 RAII',2,20 FROM course WHERE code='CPP_PLAN'
UNION ALL SELECT id,'CPP_OOP','C++ 面向对象','类、继承、虚函数与多态',2,30 FROM course WHERE code='CPP_PLAN'
UNION ALL SELECT id,'CPP_STL','STL 容器与算法','vector、map、iterator 和 algorithm',2,40 FROM course WHERE code='CPP_PLAN'
UNION ALL SELECT id,'CPP_MODERN','现代 C++','智能指针、lambda、移动语义',3,50 FROM course WHERE code='CPP_PLAN'
UNION ALL SELECT id,'CPP_PROJECT','C++ 项目实践','编译、调试、测试和小型项目',3,60 FROM course WHERE code='CPP_PLAN';

INSERT INTO knowledge_point (course_id,code,name,description,difficulty,sort_order)
SELECT id,'JS_SYNTAX','JavaScript 语法','值、类型、函数和作用域',1,10 FROM course WHERE code='JAVASCRIPT_PLAN'
UNION ALL SELECT id,'JS_ARRAY_OBJECT','数组与对象','数据结构、遍历与解构',1,20 FROM course WHERE code='JAVASCRIPT_PLAN'
UNION ALL SELECT id,'JS_DOM','DOM 与事件','页面节点、事件模型和表单',2,30 FROM course WHERE code='JAVASCRIPT_PLAN'
UNION ALL SELECT id,'JS_ASYNC','异步编程','Promise、async/await 和事件循环',2,40 FROM course WHERE code='JAVASCRIPT_PLAN'
UNION ALL SELECT id,'JS_MODULE','模块与工程化','ES Modules、npm、构建与测试',3,50 FROM course WHERE code='JAVASCRIPT_PLAN'
UNION ALL SELECT id,'JS_PROJECT','JavaScript 项目实践','完成一个可交互的 Web 小项目',3,60 FROM course WHERE code='JAVASCRIPT_PLAN';

INSERT INTO knowledge_point (course_id,code,name,description,difficulty,sort_order)
SELECT id,'GO_SYNTAX','Go 语法与类型','变量、控制流、函数和包',1,10 FROM course WHERE code='GO_PLAN'
UNION ALL SELECT id,'GO_COLLECTION','Slice 与 Map','切片、映射和字符串处理',1,20 FROM course WHERE code='GO_PLAN'
UNION ALL SELECT id,'GO_STRUCT','结构体与接口','struct、method 和 interface',2,30 FROM course WHERE code='GO_PLAN'
UNION ALL SELECT id,'GO_ERROR','错误处理与 IO','error、defer、文件和网络 IO',2,40 FROM course WHERE code='GO_PLAN'
UNION ALL SELECT id,'GO_CONCURRENCY','Goroutine 与 Channel','并发、channel 和 context',3,50 FROM course WHERE code='GO_PLAN'
UNION ALL SELECT id,'GO_PROJECT','Go 项目实践','模块、测试与命令行或 Web 服务',3,60 FROM course WHERE code='GO_PLAN';
