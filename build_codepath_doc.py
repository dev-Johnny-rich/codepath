from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.text import WD_BREAK
import os
import uuid
import zipfile
import tempfile
import xml.etree.ElementTree as ET


OUTPUT = "/Users/johnny/Desktop/AI项目/智能学习助手/智能学习助手/CodePath_AI编程教练项目方案.docx"
FONT_PATH = "/System/Library/Fonts/Supplemental/NISC18030.ttf"


def set_cell_shading(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_border(cell, color="D9D9D9", size="6"):
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = "w:" + edge
        element = borders.find(qn(tag))
        if element is None:
            element = OxmlElement(tag)
            borders.append(element)
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), size)
        element.set(qn("w:color"), color)


def set_cell_margins(cell, top=100, start=110, bottom=100, end=110):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for m, v in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn("w:" + m))
        if node is None:
            node = OxmlElement("w:" + m)
            tc_mar.append(node)
        node.set(qn("w:w"), str(v))
        node.set(qn("w:type"), "dxa")


def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_run_font(run, east_asia="GB18030 Bitmap", latin="Aptos", size=None, bold=None, color="000000"):
    run.font.name = latin
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), east_asia)
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), latin)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), latin)
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    run.font.color.rgb = RGBColor.from_string(color)


def set_keep_with_next(paragraph):
    paragraph.paragraph_format.keep_with_next = True


def add_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run()
    fld_char1 = OxmlElement("w:fldChar")
    fld_char1.set(qn("w:fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = " PAGE "
    fld_char2 = OxmlElement("w:fldChar")
    fld_char2.set(qn("w:fldCharType"), "end")
    run._r.append(fld_char1)
    run._r.append(instr_text)
    run._r.append(fld_char2)
    set_run_font(run, size=9, color="666666")


def add_para(doc, text="", style=None, bold_lead=None, align=None, keep=False):
    p = doc.add_paragraph(style=style)
    if bold_lead and text.startswith(bold_lead):
        r1 = p.add_run(bold_lead)
        set_run_font(r1, size=11, bold=True)
        r2 = p.add_run(text[len(bold_lead):])
        set_run_font(r2, size=11)
    else:
        r = p.add_run(text)
        set_run_font(r, size=11)
    if align is not None:
        p.alignment = align
    if keep:
        set_keep_with_next(p)
    return p


def add_bullet(doc, text, level=0):
    style = "List Bullet" if level == 0 else "List Bullet 2"
    p = doc.add_paragraph(style=style)
    r = p.add_run(text)
    set_run_font(r, size=11)
    return p


def add_number(doc, text, level=0):
    style = "List Number" if level == 0 else "List Number 2"
    p = doc.add_paragraph(style=style)
    r = p.add_run(text)
    set_run_font(r, size=11)
    return p


def add_code(doc, text):
    p = doc.add_paragraph(style="CodeBlock")
    p.paragraph_format.keep_together = True
    for i, line in enumerate(text.splitlines()):
        if i:
            p.add_run().add_break()
        r = p.add_run(line)
        set_run_font(r, east_asia="GB18030 Bitmap", latin="Menlo", size=9.5, color="202124")
    return p


def add_table(doc, headers, rows, widths=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    hdr = table.rows[0]
    set_repeat_table_header(hdr)
    for i, title in enumerate(headers):
        cell = hdr.cells[i]
        set_cell_shading(cell, "1F4E78")
        set_cell_border(cell)
        set_cell_margins(cell)
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(title)
        set_run_font(r, size=10, bold=True, color="FFFFFF")
        if widths:
            cell.width = Inches(widths[i])
    for ri, row_data in enumerate(rows):
        row = table.add_row()
        for i, value in enumerate(row_data):
            cell = row.cells[i]
            set_cell_border(cell)
            set_cell_margins(cell)
            if ri % 2 == 1:
                set_cell_shading(cell, "F3F7FA")
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT if len(str(value)) > 16 else WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(str(value))
            set_run_font(r, size=9.5)
            if widths:
                cell.width = Inches(widths[i])
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return table


def heading(doc, text, level=1):
    p = doc.add_paragraph(style=f"Heading {level}")
    r = p.add_run(text)
    set_run_font(r, size={1: 17, 2: 14, 3: 12}.get(level, 11), bold=True)
    return p


def embed_font(docx_path, font_path, font_name):
    """Embed an OOXML-obfuscated TrueType font for portable CJK rendering."""
    key = uuid.uuid4()
    key_hex = key.hex.upper()
    key_text = "{" + str(key).upper() + "}"
    key_bytes = bytes.fromhex(key_hex)[::-1]
    with open(font_path, "rb") as fh:
        font_data = bytearray(fh.read())
    for i in range(min(32, len(font_data))):
        font_data[i] ^= key_bytes[i % 16]

    with zipfile.ZipFile(docx_path, "r") as zin:
        entries = {name: zin.read(name) for name in zin.namelist()}

    rels_path = "word/_rels/fontTable.xml.rels"
    rel_id = "rIdCodePathCJK"
    rels_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rIdCodePathCJK" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/font" '
        'Target="fonts/codepath-cjk.odttf"/>'
        '</Relationships>'
    )

    font_table_xml = entries["word/fontTable.xml"].decode("utf-8")
    font_entry = (
        f'<w:font w:name="{font_name}">'
        f'<w:family w:val="auto"/>'
        f'<w:embedRegular r:id="{rel_id}" w:fontKey="{key_text}" w:subsetted="false"/>'
        f'</w:font>'
    )
    font_table_xml = font_table_xml.replace("</w:fonts>", font_entry + "</w:fonts>")

    content_types_xml = entries["[Content_Types].xml"].decode("utf-8")
    if 'Extension="odttf"' not in content_types_xml:
        odttf_type = (
            '<Default Extension="odttf" '
            'ContentType="application/vnd.openxmlformats-officedocument.obfuscatedFont"/>'
        )
        content_types_xml = content_types_xml.replace("</Types>", odttf_type + "</Types>")

    entries["word/fontTable.xml"] = font_table_xml.encode("utf-8")
    entries[rels_path] = rels_xml.encode("utf-8")
    entries["[Content_Types].xml"] = content_types_xml.encode("utf-8")
    entries["word/fonts/codepath-cjk.odttf"] = bytes(font_data)

    fd, temp_path = tempfile.mkstemp(suffix=".docx", dir=os.path.dirname(docx_path))
    os.close(fd)
    try:
        with zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for name, data in entries.items():
                zout.writestr(name, data)
        os.replace(temp_path, docx_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


doc = Document()
section = doc.sections[0]
section.page_width = Inches(8.5)
section.page_height = Inches(11)
section.top_margin = Inches(0.75)
section.bottom_margin = Inches(0.72)
section.left_margin = Inches(0.82)
section.right_margin = Inches(0.82)

# Base styles
styles = doc.styles
normal = styles["Normal"]
normal.font.name = "Aptos"
normal._element.rPr.rFonts.set(qn("w:eastAsia"), "GB18030 Bitmap")
normal.font.size = Pt(11)
normal.font.color.rgb = RGBColor(0, 0, 0)
normal.paragraph_format.space_after = Pt(6)
normal.paragraph_format.line_spacing = 1.18

title_style = styles["Title"]
title_style.font.name = "Aptos Display"
title_style._element.rPr.rFonts.set(qn("w:eastAsia"), "GB18030 Bitmap")
title_style.font.size = Pt(28)
title_style.font.bold = True
title_style.font.color.rgb = RGBColor(0, 0, 0)
title_style.paragraph_format.space_after = Pt(14)

for name, size, before, after in (("Heading 1", 17, 18, 8), ("Heading 2", 14, 14, 6), ("Heading 3", 12, 10, 4)):
    st = styles[name]
    st.font.name = "Aptos Display"
    st._element.rPr.rFonts.set(qn("w:eastAsia"), "GB18030 Bitmap")
    st.font.size = Pt(size)
    st.font.bold = True
    st.font.color.rgb = RGBColor(0, 0, 0)
    st.paragraph_format.space_before = Pt(before)
    st.paragraph_format.space_after = Pt(after)
    st.paragraph_format.keep_with_next = True

code_style = styles.add_style("CodeBlock", WD_STYLE_TYPE.PARAGRAPH)
code_style.font.name = "Menlo"
code_style._element.rPr.rFonts.set(qn("w:eastAsia"), "GB18030 Bitmap")
code_style.font.size = Pt(9.5)
code_style.paragraph_format.left_indent = Inches(0.28)
code_style.paragraph_format.right_indent = Inches(0.18)
code_style.paragraph_format.space_before = Pt(5)
code_style.paragraph_format.space_after = Pt(8)
code_style.paragraph_format.line_spacing = 1.08
ppr = code_style.element.get_or_add_pPr()
shd = OxmlElement("w:shd")
shd.set(qn("w:fill"), "F2F4F7")
ppr.append(shd)

# Header and footer
header = section.header
hp = header.paragraphs[0]
hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
hr = hp.add_run("CodePath 项目规划记录")
set_run_font(hr, size=9, color="666666")
add_page_number(section.footer.paragraphs[0])

# Cover page
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(110)
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("CodePath AI编程教练项目方案")
set_run_font(r, size=28, bold=True)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(10)
r = p.add_run("原项目分析 技术架构 接口设计 数据模型 团队分工")
set_run_font(r, size=14, color="333333")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(35)
r = p.add_run("根据本次项目讨论整理")
set_run_font(r, size=11, color="666666")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("2026年9月30日")
set_run_font(r, size=11, color="666666")

doc.add_page_break()

heading(doc, "文档说明", 1)
add_para(doc, "本文件将本次对话重组为一份可执行的项目方案，而不是逐字聊天记录。内容覆盖对现有智能学习助手的分析、CodePath 的产品定位、学生提交代码后的完整处理流程、前后端职责、API、数据库、知识图谱、算法、三人团队分工和八周开发计划。")
add_para(doc, "核心结论：三人团队应放弃大而全的学习平台路线，聚焦一条能够完整演示和评测的主线——学生提交代码后，系统根据测试结果、代码结构和历史学习数据诊断错误原因，更新知识点掌握度，并推荐下一步练习。")

heading(doc, "目录", 1)
for item in [
    "一 现有智能学习助手分析",
    "二 CodePath 项目定义",
    "三 核心工作流程",
    "四 技术架构",
    "五 前端设计",
    "六 后端设计",
    "七 API 接口设计",
    "八 MySQL 数据库设计",
    "九 Neo4j 双知识图谱",
    "十 AI 诊断与掌握度算法",
    "十一 三人团队分工",
    "十二 八周开发计划",
    "十三 MVP 与验收标准",
    "附录 对话需求记录",
]:
    add_para(doc, item)

doc.add_page_break()

heading(doc, "一 现有智能学习助手分析", 1)
add_para(doc, "本次讨论首先检查了一个已经打包并在本地 80 端口运行的智能学习助手。该项目不是单一聊天页面，而是一个包含资料、问答、测评、笔记、计划、画像、论坛、地图和数字人的综合学习平台。")

heading(doc, "系统组成", 2)
add_table(doc, ["层级", "实现", "主要职责"], [
    ["前端", "Vue 与 Nginx", "登录、首页、资料、测评、计划、笔记、评估和论坛页面"],
    ["业务后端", "Spring Boot", "用户、资料、计划、论坛和传统业务数据"],
    ["AI 后端", "FastAPI", "RAG、出题、画像、多智能体、数字人和推荐"],
    ["数据层", "MySQL Redis Neo4j ChromaDB", "业务数据、缓存、知识图谱、向量和会话记忆"],
    ["模型", "通义千问", "问答、规划、画像、知识抽取和内容生成"],
], [1.05, 1.65, 4.15])

heading(doc, "主要能力", 2)
for text in [
    "学习资料上传、文本分块、向量化和基于资料的问答。",
    "自动生成测验、保存答题历史、收藏错题和生成学习评估。",
    "根据行为日志和对话记忆生成学习画像与每日任务。",
    "通过安全检测、意图识别、任务规划、反思审核和子智能体执行复杂请求。",
    "支持思维导图、代码示例、学习文档、图片、视频推荐和数字人讲解。",
]:
    add_bullet(doc, text)

heading(doc, "值得借鉴的部分", 2)
add_para(doc, "该项目的主要价值在于系统完整度。它证明了学习资料、RAG、测评、画像和计划可以串成一个可部署的平台，并通过 Docker Compose 降低部署成本。对于课程设计或毕业设计，完整页面、真实数据和一键部署都能提升演示效果。")

heading(doc, "主要不足", 2)
for lead, text in [
    ("功能堆叠", "论坛、地图、数字人、视频、笔记和测评同时存在，但核心用户价值不够集中。"),
    ("个性化偏提示词", "每日任务主要把画像、薄弱点、平均分和近期对话交给大模型，缺少可验证的推荐算法。"),
    ("图谱偏资料侧", "当前图谱主要用于沿共享实体扩展文本块，没有系统表达学生对每个知识点的掌握状态。"),
    ("多智能体形式化", "规划和反思都依赖大模型调用，如果没有质量评测，难以证明优于一次调用。"),
    ("安全链路未贯通", "智能体具备安全检查，但论坛存量内容仍可能直接展示，说明审核没有覆盖全部写入和展示路径。"),
    ("二次开发受限", "前端只有编译产物，Java 后端只有打包文件，适合学习和体验，不适合直接修改为新项目。"),
]:
    add_para(doc, f"{lead}：{text}", bold_lead=f"{lead}：")

heading(doc, "方向建议", 2)
add_para(doc, "新项目不应复制一个同样大而全的学习平台，而应选择更窄、更深、可以用数据证明效果的方向。因此确定 CodePath 的核心主题为基于双知识图谱与错因诊断的自适应编程学习系统。")

doc.add_page_break()

heading(doc, "二 CodePath 项目定义", 1)
add_para(doc, "CodePath 是面向 Python 初学者的 AI 编程教练。系统通过代码运行结果、测试用例、抽象语法树和历史学习数据，判断学生具体卡在哪个知识点、为什么出错，以及下一步练什么最合适。")

heading(doc, "核心问题", 2)
add_number(doc, "学生具体卡在哪个知识点。")
add_number(doc, "本次错误属于知识缺失、概念混淆、边界条件遗漏还是实现失误。")
add_number(doc, "哪一个讲解、例题或练习最适合作为下一步学习任务。")

heading(doc, "核心价值", 2)
add_para(doc, "每次错误都应转化为一条可追踪的学习证据，并直接影响学生后续的学习路径。系统不只给出正确答案，而是保存诊断依据、掌握度变化和推荐原因。")

heading(doc, "第一版范围", 2)
add_table(doc, ["必须完成", "可以后做", "暂不开发"], [[
    "Python 代码沙箱\n测试用例\n错因诊断\n知识图谱\n掌握度\n可解释推荐",
    "AI 变式题\n教师管理端\n间隔复习\n多模型对比",
    "论坛\n地图\n数字人\n视频搜索\n多语言支持",
]], [2.3, 2.3, 2.25])

heading(doc, "三 核心工作流程", 1)
workflow_steps = [
    "教师创建课程并上传课程资料。",
    "系统分块资料，提取知识点、知识关系和相关文本证据。",
    "学生完成初始测评，系统初始化个人掌握度。",
    "推荐引擎依据薄弱程度、遗忘风险和先修关系选择下一道题。",
    "学生在 Monaco 编辑器中编写并提交代码。",
    "后端将代码放入受限 Docker 容器，运行公开和隐藏测试用例。",
    "静态分析器提取 AST 特征，运行器输出错误、耗时和失败用例。",
    "AI 结合题目、代码、测试结果、课程资料和历史记录生成结构化诊断。",
    "系统校验诊断内容，并更新相关知识点掌握度。",
    "推荐引擎生成下一项学习任务，并给出推荐原因。",
]
for index, step in enumerate(workflow_steps, start=1):
    add_para(doc, f"{index}. {step}")

heading(doc, "提交状态机", 2)
add_code(doc, "QUEUED\n  → RUNNING\n  → ANALYZING\n  → DIAGNOSING\n  → UPDATING_MASTERY\n  → COMPLETED\n\n任意阶段异常 → FAILED")

heading(doc, "一次提交的返回策略", 2)
add_para(doc, "正式提交接口不等待全部诊断完成，而是立即返回 submissionId 和 QUEUED 状态。前端每隔一秒查询状态；后期可以升级为 SSE 或 WebSocket。这样可以避免模型调用和代码运行阻塞 HTTP 请求。")

doc.add_page_break()

heading(doc, "四 技术架构", 1)
add_para(doc, "三人团队建议采用模块化单体。所有业务和 AI API 放在一个 FastAPI 工程中，代码执行通过独立 Worker 和 Docker 沙箱隔离。这样既保留清晰边界，也避免维护 Java 与 Python 两套后端。")
add_table(doc, ["层级", "推荐技术", "说明"], [
    ["前端", "Vue 3 TypeScript Element Plus", "统一页面、表单、状态和接口调用"],
    ["代码编辑器", "Monaco Editor", "提供接近 IDE 的编辑体验"],
    ["可视化", "ECharts", "知识图谱、掌握度曲线和错误分布"],
    ["后端", "FastAPI SQLAlchemy", "业务 API 与 AI 调度"],
    ["异步任务", "Celery Redis", "代码运行、资料处理和 AI 诊断"],
    ["业务数据库", "MySQL 8", "用户、课程、题目、提交和推荐"],
    ["图数据库", "Neo4j", "课程知识图谱和学习者掌握图谱"],
    ["代码执行", "Docker 沙箱", "受限执行不可信代码"],
    ["部署", "Docker Compose Nginx", "本地和服务器一致部署"],
], [1.2, 2.3, 3.5])

heading(doc, "系统边界", 2)
add_code(doc, "Vue 前端\n   ↓ REST API\nFastAPI 模块化后端\n   ├─ 用户与课程\n   ├─ 题库与提交\n   ├─ 掌握度与推荐\n   └─ AI 诊断与 RAG\n          ↓\nRedis 队列 → Docker 沙箱 → 测试与 AST 分析\n          ↓\nMySQL 业务数据 + Neo4j 双知识图谱 + 大模型 API")

heading(doc, "存储职责", 2)
add_para(doc, "MySQL 保存权威业务数据和可审计记录，包括提交、诊断、掌握度和推荐。Neo4j 保存知识点之间、学生与知识点之间的关系，用于先修查询、图形展示和关系推理。Redis 只承担缓存、任务队列和短期状态。")

heading(doc, "五 前端设计", 1)
add_table(doc, ["页面", "路由", "主要内容"], [
    ["登录注册", "/login  /register", "登录、注册、Token 刷新"],
    ["课程首页", "/courses/:id/dashboard", "总体掌握度、今日任务、待复习知识点"],
    ["知识图谱", "/courses/:id/knowledge-graph", "知识关系、个人掌握度、点击详情"],
    ["编程练习", "/practice/:problemId", "题目、Monaco、运行、提交、历史记录"],
    ["诊断报告", "/submissions/:id/report", "错因、证据、代码行、建议"],
    ["掌握度看板", "/courses/:id/mastery", "掌握度变化、错误分布、证据时间线"],
    ["学习路径", "/courses/:id/learning-path", "推荐任务、原因、完成状态"],
    ["课程管理", "/admin/courses", "资料、知识点、题目和测试用例"],
], [1.15, 2.3, 3.55])

heading(doc, "编程练习页面", 2)
add_para(doc, "左侧显示题目描述、输入输出、难度和涉及知识点；右侧使用 Monaco 编辑器；底部显示运行结果、测试用例和提交按钮。公开测试用于运行，隐藏测试用于正式提交。")

heading(doc, "诊断报告页面", 2)
add_para(doc, "诊断报告应首先展示主要错因、相关知识点和置信度，然后列出测试用例、代码行和历史记录等证据，最后给出补救讲解与下一步任务。错误代码行要与解释同步高亮。")
add_code(doc, "主要错因：边界条件遗漏\n相关知识点：列表索引与循环边界\n置信度：91%\n\n证据：\n1 普通数组测试通过\n2 空数组测试失败\n3 循环从索引 1 开始\n\n建议：复习 range 的起止规则并完成边界专项练习")

doc.add_page_break()

heading(doc, "六 后端设计", 1)
add_code(doc, "backend/\n├── main.py\n├── core/\n├── modules/\n│   ├── auth/\n│   ├── courses/\n│   ├── materials/\n│   ├── knowledge/\n│   ├── problems/\n│   ├── submissions/\n│   ├── diagnosis/\n│   ├── mastery/\n│   └── recommendations/\n├── ai/\n├── sandbox/\n├── workers/\n└── tests/")

heading(doc, "模块职责", 2)
add_table(doc, ["模块", "职责"], [
    ["用户与权限", "注册、登录、JWT、学生教师管理员角色"],
    ["课程与资料", "课程管理、文件上传、分块、处理状态"],
    ["题库", "题目、知识点绑定、难度、测试用例和审核"],
    ["提交", "提交状态机、幂等处理、历史记录和结果查询"],
    ["代码沙箱", "受限容器、测试注入、超时、内存和网络限制"],
    ["静态分析", "AST、语法结构、代码特征和典型模式"],
    ["AI 诊断", "RAG、诊断 Prompt、结构化输出和证据校验"],
    ["掌握度", "BKT 或简化规则、事件记录、遗忘计算"],
    ["推荐", "计算优先级、选择题目、生成解释和复习安排"],
], [1.45, 5.55])

heading(doc, "代码沙箱安全要求", 2)
for text in [
    "禁止容器访问外部网络。",
    "每次执行使用临时容器或强隔离执行环境。",
    "限制 CPU、内存、运行时间、进程数和输出大小。",
    "不以特权模式运行，不挂载宿主机敏感目录。",
    "执行结束后清理临时文件和容器。",
    "学生代码绝不能直接在 FastAPI 主进程中执行。",
]:
    add_bullet(doc, text)

heading(doc, "静态分析输出", 2)
add_code(doc, '{\n  "syntaxError": null,\n  "runtimeError": null,\n  "passedTests": 3,\n  "totalTests": 5,\n  "failedCases": [{\n    "input": "[]",\n    "expected": "0",\n    "actual": "IndexError"\n  }],\n  "codeFeatures": {\n    "hasLoop": true,\n    "hasFunction": true,\n    "usesRecursion": false\n  }\n}')

heading(doc, "七 API 接口设计", 1)
add_para(doc, "统一前缀使用 /api/v1。接口成功和错误响应使用统一结构，并为所有异步任务返回可查询的状态字段。")

heading(doc, "用户与课程接口", 2)
add_table(doc, ["方法", "地址", "用途"], [
    ["POST", "/auth/register", "注册"],
    ["POST", "/auth/login", "登录"],
    ["POST", "/auth/refresh", "刷新 Token"],
    ["GET", "/users/me", "当前用户"],
    ["POST", "/courses", "创建课程"],
    ["GET", "/courses", "课程列表"],
    ["GET", "/courses/{id}", "课程详情"],
    ["POST", "/courses/{id}/enroll", "加入课程"],
    ["POST", "/courses/{id}/materials", "上传资料"],
    ["GET", "/materials/{id}/status", "资料处理状态"],
    ["GET", "/courses/{id}/knowledge-graph", "知识图谱"],
], [0.75, 3.1, 3.15])

heading(doc, "题目与提交接口", 2)
add_table(doc, ["方法", "地址", "用途"], [
    ["POST", "/courses/{id}/problems", "创建题目"],
    ["POST", "/courses/{id}/problems/generate", "生成变式题"],
    ["GET", "/problems/{id}", "题目详情"],
    ["GET", "/courses/{id}/problems/next", "推荐题目"],
    ["POST", "/problems/{id}/run", "运行公开测试"],
    ["POST", "/problems/{id}/submissions", "正式提交"],
    ["GET", "/submissions/{id}", "提交状态"],
    ["GET", "/submissions/{id}/test-results", "测试结果"],
    ["GET", "/submissions/{id}/diagnosis", "诊断报告"],
], [0.75, 3.2, 3.05])

heading(doc, "掌握度与推荐接口", 2)
add_table(doc, ["方法", "地址", "用途"], [
    ["GET", "/courses/{id}/mastery", "当前掌握度"],
    ["GET", "/courses/{id}/mastery/history", "掌握度历史"],
    ["GET", "/courses/{id}/recommendations", "推荐任务"],
    ["GET", "/courses/{id}/learning-path", "学习路径"],
    ["GET", "/courses/{id}/reviews/today", "今日复习"],
    ["POST", "/recommendations/{id}/complete", "完成推荐"],
], [0.75, 3.3, 2.95])

heading(doc, "提交请求与响应", 2)
add_code(doc, 'POST /api/v1/problems/42/submissions\n\n{\n  "language": "python",\n  "code": "def solve(nums):\\n    return sum(nums)"\n}\n\n响应\n{\n  "submissionId": 1024,\n  "status": "QUEUED"\n}')

doc.add_page_break()

heading(doc, "八 MySQL 数据库设计", 1)
add_para(doc, "MySQL 是业务事实来源。关键表必须保留 created_at、updated_at 等审计字段，并在用户、课程、题目、提交和知识点外键上建立索引。")

table_rows = [
    ["users", "用户", "id username email password_hash role created_at"],
    ["courses", "课程", "id name description language creator_id status"],
    ["course_enrollments", "选课", "course_id user_id enrolled_at"],
    ["materials", "资料", "course_id filename file_url processing_status error_message"],
    ["knowledge_points", "知识点", "course_id name description difficulty importance"],
    ["knowledge_relations", "知识关系", "source_point_id target_point_id relation_type weight"],
    ["problems", "题目", "title description difficulty starter_code reference_solution limits"],
    ["problem_knowledge_points", "题目知识点", "problem_id knowledge_point_id weight"],
    ["test_cases", "测试用例", "problem_id input_data expected_output is_hidden weight"],
    ["submissions", "代码提交", "user_id problem_id code status score execution metrics"],
    ["test_case_results", "测试结果", "submission_id test_case_id status actual_output error"],
    ["diagnoses", "诊断", "submission_id category summary confidence evidence remediation"],
    ["mastery_records", "当前掌握度", "user_id knowledge_point_id mastery confidence next_review"],
    ["mastery_events", "掌握度事件", "previous new reason evidence submission_id created_at"],
    ["recommendations", "推荐", "user_id knowledge_point_id problem_id reason priority status"],
    ["learning_events", "行为事件", "user_id event_type object_type object_id event_data"],
]
add_table(doc, ["表名", "用途", "关键字段"], table_rows, [1.8, 1.25, 4.0])

heading(doc, "关键一致性原则", 2)
add_bullet(doc, "mastery_records 保存最新状态，mastery_events 保存每次变化，二者不可混用。")
add_bullet(doc, "一次 submission 只能触发一次正式掌握度更新，Worker 重试时必须保持幂等。")
add_bullet(doc, "diagnoses 保存 model_name 和 prompt_version，便于复现实验结果。")
add_bullet(doc, "测试用例应区分公开与隐藏，隐藏用例不得返回给前端。")

heading(doc, "九 Neo4j 双知识图谱", 1)
heading(doc, "课程知识图谱", 2)
add_code(doc, "(:Course)\n(:KnowledgePoint)\n(:MaterialChunk)\n(:Problem)\n\n(KnowledgePoint)-[:PREREQUISITE_OF]->(KnowledgePoint)\n(MaterialChunk)-[:EXPLAINS]->(KnowledgePoint)\n(Problem)-[:ASSESSES]->(KnowledgePoint)")

heading(doc, "学习者掌握图谱", 2)
add_code(doc, "(:Learner)\n(:KnowledgePoint)\n(:Misconception)\n(:Submission)\n\n(Learner)-[:MASTERY {probability, confidence, updatedAt}]->(KnowledgePoint)\n(Learner)-[:HAS_MISCONCEPTION]->(Misconception)\n(Submission)-[:EVIDENCE_FOR]->(KnowledgePoint)\n(Misconception)-[:RELATED_TO]->(KnowledgePoint)")
add_para(doc, "MySQL 中的 mastery_records 是掌握度权威数据。Neo4j 中的 MASTERY 关系用于图查询和可视化，可以通过异步任务同步，不能反过来让两个数据库同时成为权威来源。")

doc.add_page_break()

heading(doc, "十 AI 诊断与掌握度算法", 1)
heading(doc, "诊断输入", 2)
for text in [
    "题目描述、参考解法和目标知识点。",
    "学生源代码和具体代码行。",
    "编译、运行和测试用例结果。",
    "AST 特征与规则分析结果。",
    "学生近期错误、掌握度和已使用提示。",
    "从课程资料检索到的相关讲解。",
]:
    add_bullet(doc, text)

heading(doc, "固定错误分类", 2)
add_table(doc, ["编码", "含义"], [
    ["CONCEPT_GAP", "知识缺失"],
    ["MISCONCEPTION", "概念混淆"],
    ["BOUNDARY_CONDITION", "边界条件错误"],
    ["IMPLEMENTATION", "实现错误"],
    ["SYNTAX", "语法错误"],
    ["RUNTIME", "运行时错误"],
    ["CARELESS", "粗心错误"],
    ["TRANSFER", "知识迁移失败"],
], [2.7, 4.3])

heading(doc, "结构化诊断输出", 2)
add_code(doc, '{\n  "primaryCategory": "BOUNDARY_CONDITION",\n  "conceptId": 17,\n  "confidence": 0.91,\n  "summary": "循环边界设置错误",\n  "evidence": [\n    {"source": "TEST_CASE", "description": "空数组触发异常"},\n    {"source": "CODE_LINE", "line": 4, "description": "循环从索引1开始"}\n  ],\n  "misconception": "认为数组索引从1开始",\n  "remediation": {\n    "explanation": "Python列表索引从0开始",\n    "recommendedAction": "完成数组边界专项练习"\n  }\n}')

heading(doc, "掌握度更新", 2)
add_para(doc, "第一版可以使用透明的规则模型，后续再升级为贝叶斯知识追踪。规则必须能够解释每次变化，避免掌握度成为无法说明来源的数字。")
add_table(doc, ["学习证据", "示例变化"], [
    ["答对且未使用提示", "+0.08"],
    ["答对但使用提示", "+0.03"],
    ["知识缺失", "−0.08"],
    ["概念混淆", "−0.06"],
    ["粗心错误", "−0.02"],
    ["长期未复习", "乘以遗忘系数"],
], [4.5, 2.5])

heading(doc, "推荐排序", 2)
add_code(doc, "推荐分数 =\n0.35 × 薄弱程度\n+ 0.25 × 遗忘风险\n+ 0.20 × 先修重要度\n+ 0.10 × 最近错误频率\n+ 0.10 × 学习目标相关度")
add_para(doc, "推荐系统负责决定学什么，大模型负责生成怎么讲。推荐结果必须保存原因，例如某学生最近三次混淆 wait 与 sleep，相关知识点掌握度下降，因此优先安排线程通信专项练习。")

heading(doc, "诊断质量验证", 2)
add_para(doc, "建议准备 100 至 200 份人工标注的错误代码，对比仅让模型看代码、加入测试结果、再加入 AST 和历史掌握度三种方案。评价错因分类准确率、教师解释评分、诊断耗时和后续练习正确率。")

doc.add_page_break()

heading(doc, "十一 三人团队分工", 1)
add_table(doc, ["成员", "主要职责", "核心交付"], [
    ["成员 A\n前端与产品", "Vue 页面、Monaco、提交轮询、诊断报告、图谱和掌握度图表、演示流程", "前端工程\nAPI SDK\n可视化页面"],
    ["成员 B\n后端与沙箱", "FastAPI、MySQL、用户课程题库、提交状态机、Docker 沙箱、Redis、部署", "业务 API\n数据库\n运行系统\n部署环境"],
    ["成员 C\nAI 与算法", "资料处理、Neo4j、RAG、诊断 Schema、AST 组合、掌握度和推荐算法", "AI 诊断\n双图谱\n推荐算法\n实验结果"],
], [1.25, 3.85, 1.95])

heading(doc, "共同工作", 2)
for text in [
    "第一周共同冻结 OpenAPI 接口、状态枚举和数据库字段。",
    "所有成员通过同一个 Docker Compose 环境开发和联调。",
    "每个接口先提供 Mock 数据，避免前端等待后端全部完成。",
    "重要代码至少由另一名成员评审。",
    "每周跑通一次端到端演示，不能等到最后两周才集成。",
]:
    add_bullet(doc, text)

heading(doc, "十二 八周开发计划", 1)
add_table(doc, ["周期", "目标", "主要成果"], [
    ["第1周", "范围与契约", "页面原型、错误分类、数据库、API、仓库和 Compose"],
    ["第2周", "基础业务", "登录、课程、题库、知识点结构和诊断样例"],
    ["第3周", "编程练习", "Monaco、Docker 沙箱、测试用例、提交状态机和 AST"],
    ["第4周", "资料与图谱", "资料上传、分块、知识抽取、Neo4j 和图谱页面"],
    ["第5周", "错因诊断", "结构化诊断、证据校验、报告页面和失败降级"],
    ["第6周", "掌握度推荐", "掌握度事件、遗忘计算、推荐排序和学习路径"],
    ["第7周", "联调测试", "安全、幂等、异常、数据一致性和端到端测试"],
    ["第8周", "实验答辩", "标注数据、模型对比、演示脚本、论文图表"],
], [0.85, 1.55, 4.65])

heading(doc, "每周演示检查点", 2)
add_para(doc, "第 3 周必须跑通提交代码和测试；第 5 周必须跑通有证据的错因诊断；第 6 周必须展示一次掌握度变化和下一题推荐。如果任何检查点没有完成，应暂停增加新功能。")

heading(doc, "十三 MVP 与验收标准", 1)
add_code(doc, "登录\n→ 选择 Python 课程\n→ 获取推荐题\n→ 编写代码\n→ Docker 运行\n→ 测试用例失败\n→ AI 诊断错因\n→ 更新掌握度\n→ 推荐针对性练习")

heading(doc, "功能验收", 2)
for text in [
    "学生能够完成登录、选课、获取题目、运行和正式提交。",
    "沙箱能够终止无限循环并拦截网络访问。",
    "测试结果、代码证据和诊断报告可以关联到同一次提交。",
    "诊断输出符合固定 JSON Schema，异常时有可理解的降级结果。",
    "掌握度变化具有对应 mastery_event 和文字原因。",
    "系统能根据变化推荐下一道题，并显示推荐原因。",
]:
    add_bullet(doc, text)

heading(doc, "答辩演示建议", 2)
add_para(doc, "准备一个学生连续答错三道 Python 列表或 Java 多线程题的故事。系统应发现学生不是完全不会，而是持续混淆某个概念；随后展示诊断证据、掌握度下降、专项讲解、针对性练习和掌握度恢复。这个演示能够同时体现知识图谱、学习画像、错因诊断、自适应推荐和大模型应用。")

heading(doc, "项目创新表述", 2)
add_para(doc, "本项目将课程知识图谱与学习者动态掌握图谱相结合，通过代码执行、测试结果、代码结构和学习历史等多源证据诊断错误成因，并依据知识追踪和遗忘规律生成可解释、可验证的个性化学习路径。")

doc.add_page_break()

heading(doc, "附录 对话需求记录", 1)
heading(doc, "原始需求一", 2)
add_para(doc, "这是别人的一个打包好的项目，你先读一下，我已经在本地 80 端口打开。然后你给我一个思路和创新点，我最近也要做一个项目出来。")

heading(doc, "原始需求二", 2)
add_para(doc, "如果我要做一个 CodePath 会诊断错误原因的 AI 编程教练，是什么工作流程。需要讲清楚前端、后端、API、数据库以及三个人的团队分工，并给出清晰的开发线路。")

heading(doc, "原始需求三", 2)
add_para(doc, "把本次对话整理为 Word 文档。")

heading(doc, "最终决策摘要", 2)
add_para(doc, "项目名称暂定为 CodePath。第一版只支持 Python，采用 Vue 3、FastAPI、MySQL、Neo4j、Redis 和 Docker 沙箱。团队以错因诊断、掌握度更新和可解释推荐为核心，按前端、业务后端与沙箱、AI 与算法三个方向分工，并在八周内完成可演示和可评测的 MVP。")

# Widow/orphan control and document metadata
for p in doc.paragraphs:
    p.paragraph_format.widow_control = True

doc.core_properties.title = "CodePath AI编程教练项目方案"
doc.core_properties.subject = "智能学习助手分析与 CodePath 项目设计"
doc.core_properties.author = "项目团队"
doc.core_properties.keywords = "CodePath AI编程教练 错因诊断 知识图谱 自适应学习"

doc.save(OUTPUT)
embed_font(OUTPUT, FONT_PATH, "GB18030 Bitmap")
print(OUTPUT)
