import { useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import {
  BookOpen, Bot, Check, ChevronRight, CircleAlert, Clock3, Code2, History,
  ExternalLink, Lightbulb, ListChecks, LoaderCircle, LogIn, LogOut, Play, RotateCcw,
  Send, Sparkles, TerminalSquare, UserRound, X
} from "lucide-react";
import { api, authStore } from "./api";
import type { Course, HistoryItem, Problem, PublicExample, Submission, User } from "./types";
import { DiscussPage, InterviewPage } from "./CommunityPages";
import { SelfPracticePage } from "./SelfPracticePage";
import { AiWidget } from "./AiWidget";

type PageName = "practice"|"discuss"|"interview"|"self-practice";

const terminalStates = new Set(["COMPLETED", "FAILED"]);
const statusText: Record<string, string> = {
  QUEUED: "排队中", RUNNING: "正在执行", ANALYZING: "分析结果",
  DIAGNOSING: "AI 诊断中", COMPLETED: "已完成", FAILED: "执行失败"
};

function difficulty(value: number) {
  return value <= 1 ? ["入门", "easy"] : value === 2 ? ["进阶", "medium"] : ["挑战", "hard"];
}

function AuthDialog({ onClose, onSuccess }: { onClose: () => void; onSuccess: (user: User) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [form, setForm] = useState({ account: "", password: "", email: "", username: "", displayName: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setError(""); setLoading(true);
    try {
      const data = mode === "login" ? await api.login(form.account, form.password)
        : await api.register(form.email, form.username, form.password, form.displayName);
      authStore.save(data); onSuccess(data.user); onClose();
    } catch (e) { setError(e instanceof Error ? e.message : "请求失败"); }
    finally { setLoading(false); }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <div className="auth-card" onMouseDown={e => e.stopPropagation()}>
      <button className="close-btn" onClick={onClose}><X size={18}/></button>
      <div className="auth-mark"><Code2 size={25}/></div>
      <h2>{mode === "login" ? "欢迎回到 CodePath" : "创建学习账号"}</h2>
      <p>{mode === "login" ? "登录后提交代码，获得 AI 错误诊断" : "开始你的个性化编程学习路径"}</p>
      <form onSubmit={submit}>
        {mode === "register" && <>
          <label>昵称<input required value={form.displayName} onChange={e => setForm({...form, displayName:e.target.value})} placeholder="例如：小明"/></label>
          <label>用户名<input required minLength={3} value={form.username} onChange={e => setForm({...form, username:e.target.value})} placeholder="3-64 个字符"/></label>
          <label>邮箱<input required type="email" value={form.email} onChange={e => setForm({...form, email:e.target.value})} placeholder="name@example.com"/></label>
        </>}
        {mode === "login" && <label>账号<input required value={form.account} onChange={e => setForm({...form, account:e.target.value})} placeholder="用户名或邮箱"/></label>}
        <label>密码<input required type="password" minLength={8} value={form.password} onChange={e => setForm({...form, password:e.target.value})} placeholder="至少 8 位"/></label>
        {error && <div className="form-error"><CircleAlert size={15}/>{error}</div>}
        <button className="primary wide" disabled={loading}>{loading && <LoaderCircle className="spin" size={17}/>} {mode === "login" ? "登录" : "注册并登录"}</button>
      </form>
      <button className="switch-auth" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}>
        {mode === "login" ? "还没有账号？立即注册" : "已有账号？返回登录"}
      </button>
    </div>
  </div>;
}

function App() {
  const initialPage = (["practice","discuss","interview","self-practice"].includes(location.hash.slice(1)) ? location.hash.slice(1) : "practice") as PageName;
  const [page, setPageState] = useState<PageName>(initialPage);
  const setPage = (next:PageName) => { location.hash=next; setPageState(next); };
  const [user, setUser] = useState<User | null>(authStore.user());
  const [authOpen, setAuthOpen] = useState(false);
  const [courses, setCourses] = useState<Course[]>([]);
  const [courseId, setCourseId] = useState<number>();
  const [problems, setProblems] = useState<Problem[]>([]);
  const [problem, setProblem] = useState<Problem>();
  const [examples, setExamples] = useState<PublicExample[]>([]);
  const [examplesLoading, setExamplesLoading] = useState(false);
  const [examplesError, setExamplesError] = useState("");
  const [code, setCode] = useState("");
  const [submission, setSubmission] = useState<Submission>();
  const [runResult, setRunResult] = useState<Submission>();
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tab, setTab] = useState<"run" | "result" | "history">("run");
  const [sideTab, setSideTab] = useState<"problem" | "coach">("problem");
  const [hint, setHint] = useState("");
  const [hintLoading, setHintLoading] = useState(0);
  const [busy, setBusy] = useState(false);
  const [guestRun, setGuestRun] = useState(false);
  const [notice, setNotice] = useState("");
  const pollRef = useRef<number>();

  useEffect(() => { api.courses().then(data => { setCourses(data); if (data[0]) setCourseId(data[0].id); }).catch(e => setNotice(e.message)); }, []);
  useEffect(() => { if (!courseId) return; api.problems(courseId).then(data => { setProblems(data); if (data[0]) chooseProblem(data[0]); }).catch(e => setNotice(e.message)); }, [courseId]);
  useEffect(() => () => { if (pollRef.current) window.clearTimeout(pollRef.current); }, []);

  async function chooseProblem(item: Problem) {
    setProblem(item); setCode(item.starterCode || templates[item.language.toLowerCase()] || "");
    setSubmission(undefined); setRunResult(undefined); setHint(""); setTab("run");
    loadExamples(item.id);
    if (user) api.history(item.id).then(p => setHistory(p.content)).catch(() => {});
  }
  async function loadExamples(problemId:number) {
    setExamplesLoading(true); setExamplesError("");
    for(let attempt=0;attempt<3;attempt++) {
      try { setExamples(await api.examples(problemId)); setExamplesLoading(false); return; }
      catch(e) { if(attempt<2) await new Promise(resolve=>setTimeout(resolve,500*(attempt+1))); else setExamplesError(e instanceof Error?e.message:"公开示例加载失败"); }
    }
    setExamples([]); setExamplesLoading(false);
  }
  async function submit() {
    if (!user) { setAuthOpen(true); return; }
    if (!problem || !code.trim()) return;
    setGuestRun(false); setBusy(true); setNotice(""); setHint(""); setTab("result");
    try {
      const accepted = await api.submit(problem.id, problem.language, code);
      await poll(accepted.submissionId);
    } catch (e) { setNotice(e instanceof Error ? e.message : "提交失败"); setBusy(false); }
  }
  async function runCode() {
    if (!problem || !code.trim()) return;
    setGuestRun(true); setBusy(true); setNotice(""); setHint(""); setTab("run");
    try { const accepted=await api.run(problem.id,problem.language,code); await pollGuest(accepted.submissionId,accepted.runToken); }
    catch(e){setNotice(e instanceof Error?e.message:"运行失败");setBusy(false)}
  }
  async function pollGuest(id:number,token:string) {
    try { const detail=await api.runDetail(id,token);setRunResult(detail);
      if(!terminalStates.has(detail.status))pollRef.current=window.setTimeout(()=>pollGuest(id,token),1100);else setBusy(false);
    } catch(e){setBusy(false);setNotice(e instanceof Error?e.message:"读取运行结果失败")}
  }
  async function poll(id: number) {
    try {
      const detail = await api.submission(id); setSubmission(detail);
      if (!terminalStates.has(detail.status)) pollRef.current = window.setTimeout(() => poll(id), 1100);
      else { setBusy(false); if (problem) api.history(problem.id).then(p => setHistory(p.content)); }
    } catch (e) { setBusy(false); setNotice(e instanceof Error ? e.message : "读取结果失败"); }
  }
  async function getHint(level: number) {
    if (!submission) return; setHintLoading(level); setSideTab("coach");
    try { const data = await api.hint(submission.id, level); setHint(data.content); }
    catch (e) { setHint(e instanceof Error ? e.message : "提示请求失败"); }
    finally { setHintLoading(0); }
  }
  function logout() { authStore.clear(); setUser(null); setHistory([]); }
  const passed = submission && submission.totalCount > 0 && submission.passedCount === submission.totalCount;
  const coachResult = guestRun ? runResult : submission;
  const activeDiff = useMemo(() => problem ? difficulty(problem.difficulty) : ["", ""], [problem]);

  return <div className="app-shell">
    <header>
      <div className="brand"><span className="brand-icon"><Code2 size={22}/></span><strong>CodePath</strong><span className="brand-tag">AI 编程教练</span></div>
      <nav className="top-nav"><button className={page==="practice"?"active":""} onClick={()=>setPage("practice")}>Practice</button><button className={page==="discuss"?"active":""} onClick={()=>setPage("discuss")}>Discuss</button><button className={page==="interview"?"active":""} onClick={()=>setPage("interview")}>Interview</button><button className={page==="self-practice"?"active":""} onClick={()=>setPage("self-practice")}>Self-Practice</button></nav>
      {user ? <div className="user-menu"><span className="avatar">{user.displayName.slice(0,1)}</span><div><b>{user.displayName}</b><small>{user.role}</small></div><button className="icon-btn" title="退出" onClick={logout}><LogOut size={17}/></button></div>
        : <button className="login-btn" onClick={() => setAuthOpen(true)}><LogIn size={17}/>登录 / 注册</button>}
    </header>

    {page === "practice" ? <div className="workspace">
      <aside className="problem-nav">
        <div className="nav-title"><BookOpen size={16}/><span>学习路径</span></div>
        <select value={courseId || ""} onChange={e => setCourseId(Number(e.target.value))}>
          {courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <div className="progress-card"><div><span>课程进度</span><b>0 / {problems.length}</b></div><div className="progress"><i/></div><small>完成题目后会更新掌握度</small></div>
        <div className="leetcode-card">
          <div><strong>去 LeetCode 上练题吧</strong><span>可以把题目拿回这里来做哦</span></div>
          <a href="https://leetcode.cn/problemset/" target="_blank" rel="noopener noreferrer">打开题库<ExternalLink size={13}/></a>
        </div>
        <div className="problem-list">
          {problems.map((p, i) => { const d = difficulty(p.difficulty); return <button key={p.id} className={problem?.id === p.id ? "active" : ""} onClick={() => chooseProblem(p)}>
            <span className="problem-index">{String(i + 1).padStart(2,"0")}</span><span className="problem-name"><b>{p.title}</b><small><em className={d[1]}>{d[0]}</em>{p.language}</small></span><ChevronRight size={15}/>
          </button>; })}
        </div>
      </aside>

      <main className="coding-area">
        <div className="editor-head">
          <div><span className="file-dot"/><b>{problem?.title || "选择题目"}</b><span className={`difficulty ${activeDiff[1]}`}>{activeDiff[0]}</span></div>
          <div className="editor-actions"><span className="language-pill">{problem?.language || "--"}</span><button onClick={() => problem && setCode(problem.starterCode || templates[problem.language] || "")}><RotateCcw size={15}/>重置</button></div>
        </div>
        <div className="editor-wrap">
          <Editor height="100%" theme="vs-dark" language={monacoLanguage(problem?.language)} value={code}
            onChange={v => setCode(v || "")} loading={<div className="editor-loading"><LoaderCircle className="spin"/>加载编辑器…</div>}
            options={{ fontSize: 14, lineHeight: 23, minimap: { enabled: false }, automaticLayout: true, padding: { top: 16 }, scrollBeyondLastLine: false, wordWrap: "on", tabSize: 4 }} />
        </div>
        <div className="submit-bar">
          <div className="shortcut"><TerminalSquare size={16}/><span>代码最大 100,000 字符</span></div>
          <button className="secondary" disabled={busy||!problem} onClick={runCode} title="无需登录，使用题目测试用例快速检查"><Play size={16}/>运行</button>
          <button className="primary" disabled={busy || !problem} onClick={submit}>{busy ? <LoaderCircle className="spin" size={17}/> : <Send size={17}/>} {busy ? statusText[submission?.status || "QUEUED"] : "提交评测"}</button>
        </div>

        <section className="bottom-panel">
          <div className="panel-tabs"><button className={tab === "run" ? "active" : ""} onClick={() => setTab("run")}><TerminalSquare size={15}/>运行结果</button><button className={tab === "result" ? "active" : ""} onClick={() => setTab("result")}><ListChecks size={15}/>评测结果</button><button className={tab === "history" ? "active" : ""} onClick={() => setTab("history")}><History size={15}/>提交记录</button></div>
          <div className="panel-content">{tab === "run" ? <RunResult result={runResult} busy={busy&&guestRun} examples={examples}/> : tab === "result" ? <Result submission={submission} busy={busy&&!guestRun} passed={!!passed}/> : <HistoryList items={history} onSelect={id => { setTab("result"); poll(id); }}/>}</div>
        </section>
      </main>

      <aside className="detail-panel">
        <div className="side-tabs"><button className={sideTab === "problem" ? "active" : ""} onClick={() => setSideTab("problem")}><BookOpen size={15}/>题目</button><button className={sideTab === "coach" ? "active" : ""} onClick={() => setSideTab("coach")}><Bot size={15}/>AI 教练</button></div>
        {sideTab === "problem" ? <div className="problem-copy">
          <div className="problem-heading"><span>题目 {problem?.id || "--"}</span><h1>{problem?.title || "正在加载"}</h1><div><span><Clock3 size={13}/>{problem?.timeLimitMs || 0} ms</span><span>{problem?.memoryLimitMb || 0} MB</span></div></div>
          <Article text={problem?.description}/><h3>输入说明</h3><Article text={problem?.inputDescription}/><h3>输出说明</h3><Article text={problem?.outputDescription}/>
          <h3>公开运行示例</h3>{examplesLoading ? <p className="example-state"><LoaderCircle className="spin" size={13}/>正在加载示例…</p> : examples.length ? <div className="public-examples">{examples.map((example,index)=><div className="example-card" key={example.id}><b>示例 {index+1}</b><label>输入</label><pre>{example.input || "(空输入)"}</pre><label>期望输出</label><pre>{example.expectedOutput}</pre></div>)}</div> : examplesError ? <div className="example-error"><span>{examplesError}</span><button onClick={()=>problem&&loadExamples(problem.id)}>重试</button></div> : <p className="no-example">教师尚未配置公开示例</p>}
        </div> : <div className="coach-panel">
          <div className="coach-intro"><span><Bot size={22}/></span><div><b>CodePath Coach</b><small>基于你的代码和运行证据</small></div></div>
          {!coachResult?.diagnosis && !hint && <div className="coach-empty"><Sparkles size={30}/><b>先运行或提交一次代码</b><p>AI 会诊断根因，并引导你自己解决。</p></div>}
          {coachResult?.diagnosis && <div className="diagnosis-card"><span className="diagnosis-label">诊断 · {coachResult.diagnosis.errorType}</span><h3>{coachResult.diagnosis.rootCause}</h3><p>{coachResult.diagnosis.explanation}</p><small>置信度 {Math.round((coachResult.diagnosis.confidence || 0) * 100)}% · {coachResult.diagnosis.modelName}</small></div>}
          {submission && !guestRun && <div className="hints"><h3><Lightbulb size={16}/>分层提示</h3><p>先独立思考，卡住时再逐层打开。</p><div>{[1,2,3].map(n => <button key={n} onClick={() => getHint(n)} disabled={!!hintLoading}>{hintLoading === n ? <LoaderCircle className="spin" size={15}/> : n}<span>{["方向提示","定位错误","伪代码引导"][n-1]}</span></button>)}</div></div>}
          {runResult && guestRun && <div className="guest-note"><Sparkles size={16}/><div><b>这是游客运行</b><p>登录后提交可保存历史、解锁分层提示并进入 Interview 档案。</p></div></div>}
          {hint && <div className="hint-answer"><div><Sparkles size={15}/><b>AI 提示</b></div><p>{hint}</p></div>}
        </div>}
      </aside>
    </div> : page === "discuss" ? <DiscussPage user={user} problems={problems} onNeedAuth={()=>setAuthOpen(true)}/> : page === "interview" ? <InterviewPage user={user} onNeedAuth={()=>setAuthOpen(true)}/> : <SelfPracticePage/>}
    <AiWidget key={user?.id ?? "guest"} user={user} onNeedAuth={()=>setAuthOpen(true)} submissionId={submission?.id}/>
    {notice && <div className="toast"><CircleAlert size={17}/>{notice}<button onClick={() => setNotice("")}><X size={15}/></button></div>}
    {authOpen && <AuthDialog onClose={() => setAuthOpen(false)} onSuccess={u => { setUser(u); if (problem) api.history(problem.id).then(p => setHistory(p.content)); }}/>}
  </div>;
}

function Article({ text }: { text?: string }) { return <div className="article">{text ? text.split("\n").map((line,i) => <p key={i}>{line || "\u00a0"}</p>) : <p className="muted">暂无说明</p>}</div>; }

function RunResult({result,busy,examples}:{result?:Submission;busy:boolean;examples:PublicExample[]}) {
  if(!result)return <div className="empty-result"><TerminalSquare size={27}/><div><b>终端输出将显示在这里</b><p>无需登录，点击“运行”快速检查代码。</p></div></div>;
  return <div className="terminal-result"><div className="terminal-toolbar"><span/><span/><span/><b>CodePath Sandbox</b><em>{busy?statusText[result.status]:`${result.runtimeMs||0} ms · ${result.passedCount}/${result.totalCount} 通过`}</em></div><div className="terminal-output">
    <p className="command"><i>$</i> run {result.language} submission #{result.id}</p>
    {busy&&<p className="terminal-pending"><LoaderCircle className="spin" size={14}/>{statusText[result.status]||result.status}...</p>}
    {result.errorMessage&&<pre className="stderr">{result.errorMessage}</pre>}
    {result.testResults?.map((item,index)=><div className="terminal-case" key={item.id}><p><span className={item.status==="PASSED"?"term-ok":"term-bad"}>[{item.status}]</span> 公开示例 {index+1} <small>{item.runtimeMs||0} ms</small></p>{examples[index]&&<><label>stdin</label><pre>{examples[index].input || "(空输入)"}</pre><label>expected</label><pre>{examples[index].expectedOutput}</pre></>}{item.actualOutput!=null&&<><label>stdout</label><pre>{item.actualOutput || "(无输出)"}</pre></>}{item.stderr&&item.stderr!==result.errorMessage&&<><label>stderr</label><pre className="stderr">{item.stderr}</pre></>}</div>)}
    {!busy&&<p className="process-end">Process finished with {result.passedCount===result.totalCount?"exit code 0":"errors"}</p>}
  </div></div>
}

function Result({ submission, busy, passed }: { submission?: Submission; busy: boolean; passed: boolean }) {
  if (!submission) return <div className="empty-result"><TerminalSquare size={27}/><div><b>运行结果将显示在这里</b><p>写好代码后点击“运行”或“提交评测”。</p></div></div>;
  return <div className="result-view">
    <div className={`result-summary ${passed ? "success" : busy ? "pending" : "failed"}`}>
      {busy ? <LoaderCircle className="spin"/> : passed ? <Check/> : <CircleAlert/>}<div><b>{busy ? statusText[submission.status] : passed ? "恭喜，全部通过" : submission.errorMessage || "还有测试用例未通过"}</b><span>{submission.passedCount}/{submission.totalCount} 通过 · {submission.runtimeMs || 0} ms</span></div>
    </div>
    <div className="cases">{submission.testResults?.map((r,i) => <div className="case" key={r.id}><span className={r.status === "PASSED" ? "case-ok" : "case-bad"}>{r.status === "PASSED" ? <Check size={14}/> : <X size={14}/>}</span><b>测试点 {i+1}</b><em>{r.status}</em><small>{r.runtimeMs || 0} ms</small>{r.stderr && <code>{r.stderr}</code>}</div>)}</div>
  </div>;
}

function HistoryList({ items, onSelect }: { items: HistoryItem[]; onSelect: (id:number) => void }) {
  if (!items.length) return <div className="empty-result"><History size={26}/><div><b>暂无提交记录</b><p>完成第一次评测后会出现在这里。</p></div></div>;
  return <div className="history-list">{items.map(item => <button key={item.id} onClick={() => onSelect(item.id)}><span className={item.passedCount === item.totalCount ? "history-ok" : "history-bad"}>{item.passedCount === item.totalCount ? <Check size={14}/> : <CircleAlert size={14}/>}</span><b>#{item.id}</b><span>{item.language}</span><span>{item.passedCount}/{item.totalCount} 通过</span><small>{new Date(item.createdAt).toLocaleString("zh-CN")}</small><ChevronRight size={14}/></button>)}</div>;
}

const templates: Record<string,string> = {
  python: "# 在这里编写你的代码\n\ndef solve():\n    pass\n\nif __name__ == \"__main__\":\n    solve()\n",
  java: "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // 在这里编写你的代码\n    }\n}\n",
  cpp: "#include <iostream>\nusing namespace std;\n\nint main() {\n    // 在这里编写你的代码\n    return 0;\n}\n"
};
function monacoLanguage(value?: string) { const v = value?.toLowerCase(); return v === "cpp" || v === "c++" ? "cpp" : v === "java" ? "java" : "python"; }

export default App;
