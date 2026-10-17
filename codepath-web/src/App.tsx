import { useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import type { OnMount } from "@monaco-editor/react";
import {
  BookOpen, Bot, Check, ChevronRight, CircleAlert, Clock3, Code2, History,
  ArrowRight, BookMarked, ExternalLink, Lightbulb, ListChecks, LoaderCircle, LogIn, LogOut, Play, RotateCcw,
  Send, Sparkles, Target, TerminalSquare, TrendingUp, UserRound, X
} from "lucide-react";
import { api, AUTH_EXPIRED_EVENT, authStore } from "./api";
import type { AiGeneratedPractice, AiPracticeEvaluation, Course, HistoryItem, LearningAssistance, LearningPlan, Problem, PublicExample, Submission, User } from "./types";
import { DiscussPage, InterviewPage } from "./CommunityPages";
import { SelfPracticePage } from "./SelfPracticePage";
import { LearningPlanPage } from "./LearningPlanPage";
import { AiWidget } from "./AiWidget";
import { WelcomePage } from "./WelcomePage";
import { UserProfilePanel } from "./UserProfilePanel";
import { parseServerDate } from "./date";
import "./assistance.css";
import "./plan-path.css";
import "./practice-empty.css";

type PageName = "practice"|"plan"|"discuss"|"interview"|"self-practice";
type ResizeTarget = "left" | "right" | "bottom";
type PracticeWorkspaceDraft =
  | { kind:"problem"; problem:Problem; code:string }
  | { kind:"generated"; practice:AiGeneratedPractice; code:string };

const terminalStates = new Set(["COMPLETED", "FAILED"]);
const PRACTICE_WORKSPACE_KEY = "codepath-practice-workspace";
const DEFAULT_PANEL_SIZES = { left: 250, right: 350, bottom: 235 };
const MIN_PANEL_SIZES = { left: 180, right: 260, bottom: 120, editor: 160, center: 420 };
const RESIZER_SIZE = 8;
const statusText: Record<string, string> = {
  QUEUED: "排队中", PREPARING: "准备运行环境", COMPILING: "正在编译", RUNNING: "正在执行", ANALYZING: "分析结果",
  DIAGNOSING: "AI 诊断中", COMPLETED: "已完成", FAILED: "执行失败"
};

function difficulty(value: number) {
  return value <= 1 ? ["入门", "easy"] : value === 2 ? ["进阶", "medium"] : ["挑战", "hard"];
}

function storedPanelSize(key:keyof typeof DEFAULT_PANEL_SIZES) {
  const value = Number(localStorage.getItem(`codepath-panel-${key}`));
  return Number.isFinite(value) && value > 0 ? value : DEFAULT_PANEL_SIZES[key];
}

function storedPracticeWorkspace():PracticeWorkspaceDraft|null {
  try {
    const value=JSON.parse(localStorage.getItem(PRACTICE_WORKSPACE_KEY)||"null") as PracticeWorkspaceDraft|null;
    if(value?.kind==="problem"&&Number.isFinite(value.problem?.id)&&Number.isFinite(value.problem?.courseId)&&typeof value.code==="string")return value;
    if(value?.kind==="generated"&&value.practice?.id&&typeof value.code==="string")return value;
  } catch { /* Ignore an invalid or outdated local draft. */ }
  return null;
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
  const practiceWorkspace = useRef<PracticeWorkspaceDraft|null>(storedPracticeWorkspace());
  const initialPage = (["practice","plan","discuss","interview","self-practice"].includes(location.hash.slice(1)) ? location.hash.slice(1) : "practice") as PageName;
  const [page, setPageState] = useState<PageName>(initialPage);
  const [interviewKey, setInterviewKey] = useState(0);
  const setPage = (next:PageName) => {
    location.hash=next;
    setPageState(next);
    if(next === "interview") setInterviewKey(prev => prev + 1);
  };
  const [user, setUser] = useState<User | null>(authStore.user());
  const [authOpen, setAuthOpen] = useState(false);
  const [profileOpen,setProfileOpen]=useState(false);
  const [userAvatar,setUserAvatar]=useState("");
  const [courses, setCourses] = useState<Course[]>([]);
  const [courseId, setCourseId] = useState<number|undefined>(()=>practiceWorkspace.current?.kind==="problem"?practiceWorkspace.current.problem.courseId:undefined);
  const [problems, setProblems] = useState<Problem[]>([]);
  const [problem, setProblem] = useState<Problem|undefined>(()=>practiceWorkspace.current?.kind==="problem"?practiceWorkspace.current.problem:undefined);
  const [examples, setExamples] = useState<PublicExample[]>([]);
  const [examplesLoading, setExamplesLoading] = useState(false);
  const [examplesError, setExamplesError] = useState("");
  const [code, setCode] = useState(()=>practiceWorkspace.current?.code||"");
  const [submission, setSubmission] = useState<Submission>();
  const [runResult, setRunResult] = useState<Submission>();
  const [runAccess,setRunAccess]=useState<{id:number;token:string}>();
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tab, setTab] = useState<"run" | "result" | "history">("run");
  const [sideTab, setSideTab] = useState<"problem" | "coach" | "assist">("problem");
  const [assistance, setAssistance] = useState<LearningAssistance>();
  const [assistanceLoading, setAssistanceLoading] = useState(false);
  const [assistanceError, setAssistanceError] = useState("");
  const [generatedPractice, setGeneratedPractice] = useState<AiGeneratedPractice|undefined>(()=>{
    try { return JSON.parse(localStorage.getItem("codepath-generated-practice")||"null")||undefined; }
    catch { return undefined; }
  });
  const [activeGeneratedPractice,setActiveGeneratedPractice]=useState<AiGeneratedPractice|undefined>(()=>practiceWorkspace.current?.kind==="generated"?practiceWorkspace.current.practice:undefined);
  const [practiceEvaluation,setPracticeEvaluation]=useState<AiPracticeEvaluation>();
  const [practiceEvaluating,setPracticeEvaluating]=useState(false);
  const [practiceEvaluationError,setPracticeEvaluationError]=useState("");
  const [activePlan,setActivePlan]=useState<LearningPlan|null>();
  const [practiceGenerating, setPracticeGenerating] = useState(false);
  const [practiceGenerationError, setPracticeGenerationError] = useState("");
  const [hint, setHint] = useState("");
  const [hintLoading, setHintLoading] = useState(0);
  const [busy, setBusy] = useState(false);
  const [guestRun, setGuestRun] = useState(false);
  const [notice, setNotice] = useState("");
  const [panelSizes, setPanelSizes] = useState(() => ({
    left: storedPanelSize("left"), right: storedPanelSize("right"), bottom: storedPanelSize("bottom")
  }));
  const [resizeTarget, setResizeTarget] = useState<ResizeTarget | null>(null);
  const workspaceRef = useRef<HTMLDivElement>(null);
  const practiceEditorRef = useRef<Parameters<OnMount>[0]|null>(null);
  const pollRef = useRef<number>();
  const generatedRunEpoch = useRef(0);

  useEffect(() => { api.courses().then(data => { setCourses(data); if (data[0]) setCourseId(current=>current??data[0].id); }).catch(e => setNotice(e.message)); }, []);
  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);setUserAvatar("");setProfileOpen(false);setHistory([]);setActivePlan(undefined);
      setBusy(false);setNotice("");
    };
    const handleStorage = (event:StorageEvent) => {
      if(event.key==="codepath_access_token"&&!event.newValue)handleAuthExpired();
    };
    window.addEventListener(AUTH_EXPIRED_EVENT,handleAuthExpired);
    window.addEventListener("storage",handleStorage);
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT,handleAuthExpired);
      window.removeEventListener("storage",handleStorage);
    };
  },[]);
  useEffect(() => { if (!courseId) return; api.problems(courseId).then(data => {
    setProblems(data);
    setProblem(current => {
      if(current&&data.some(item=>item.id===current.id))return current;
      const draft=practiceWorkspace.current;
      return draft?.kind==="problem"&&draft.problem.courseId===courseId?draft.problem:undefined;
    });
  }).catch(e => setNotice(e.message)); }, [courseId]);
  useEffect(()=>{
    persistPracticeWorkspace(code);
  },[activeGeneratedPractice,problem,code]);
  useEffect(()=>{if(problem)loadExamples(problem.id);},[problem?.id]);
  useEffect(() => {
    if(!user){setActivePlan(undefined);return;}
    api.activeLearningPlan().then(setActivePlan).catch(()=>setActivePlan(null));
  },[user?.id,page]);
  useEffect(()=>{
    if(!user)return;
    api.userProfile().then(profile=>{
      setUserAvatar(profile.avatarDataUrl||"");
      if(profile.displayName!==user.displayName){
        const next={...user,displayName:profile.displayName};setUser(next);authStore.updateUser(next);
      }
    }).catch(()=>{});
  },[user?.id]);
  useEffect(() => () => { if (pollRef.current) window.clearTimeout(pollRef.current); }, []);
  useEffect(() => {
    localStorage.setItem("codepath-panel-left", String(Math.round(panelSizes.left)));
    localStorage.setItem("codepath-panel-right", String(Math.round(panelSizes.right)));
    localStorage.setItem("codepath-panel-bottom", String(Math.round(panelSizes.bottom)));
  }, [panelSizes]);

  function clamp(value:number,min:number,max:number) { return Math.min(Math.max(max,min),Math.max(min,value)); }
  function resizePanel(target:ResizeTarget,delta:number) {
    const workspace = workspaceRef.current?.getBoundingClientRect();
    setPanelSizes(current => {
      if(target === "bottom") {
        const maxBottom = Math.max(MIN_PANEL_SIZES.bottom,(workspace?.height || window.innerHeight - 62) - 48 - 54 - RESIZER_SIZE - MIN_PANEL_SIZES.editor);
        return {...current,bottom:clamp(current.bottom + delta,MIN_PANEL_SIZES.bottom,maxBottom)};
      }
      const totalWidth = workspace?.width || window.innerWidth;
      if(target === "left") {
        const maxLeft = Math.max(MIN_PANEL_SIZES.left,totalWidth - current.right - RESIZER_SIZE * 2 - MIN_PANEL_SIZES.center);
        return {...current,left:clamp(current.left + delta,MIN_PANEL_SIZES.left,maxLeft)};
      }
      const maxRight = Math.max(MIN_PANEL_SIZES.right,totalWidth - current.left - RESIZER_SIZE * 2 - MIN_PANEL_SIZES.center);
      return {...current,right:clamp(current.right + delta,MIN_PANEL_SIZES.right,maxRight)};
    });
  }
  function startResize(target:ResizeTarget,event:React.PointerEvent<HTMLDivElement>) {
    event.preventDefault();
    const startX = event.clientX;
    const startY = event.clientY;
    const startSizes = panelSizes;
    const workspace = workspaceRef.current?.getBoundingClientRect();
    setResizeTarget(target);
    const handleMove = (moveEvent:PointerEvent) => {
      const totalWidth = workspace?.width || window.innerWidth;
      if(target === "left") {
        const maxLeft = Math.max(MIN_PANEL_SIZES.left,totalWidth - startSizes.right - RESIZER_SIZE * 2 - MIN_PANEL_SIZES.center);
        setPanelSizes({...startSizes,left:clamp(startSizes.left + moveEvent.clientX - startX,MIN_PANEL_SIZES.left,maxLeft)});
      } else if(target === "right") {
        const maxRight = Math.max(MIN_PANEL_SIZES.right,totalWidth - startSizes.left - RESIZER_SIZE * 2 - MIN_PANEL_SIZES.center);
        setPanelSizes({...startSizes,right:clamp(startSizes.right - (moveEvent.clientX - startX),MIN_PANEL_SIZES.right,maxRight)});
      } else {
        const maxBottom = Math.max(MIN_PANEL_SIZES.bottom,(workspace?.height || window.innerHeight - 62) - 48 - 54 - RESIZER_SIZE - MIN_PANEL_SIZES.editor);
        setPanelSizes({...startSizes,bottom:clamp(startSizes.bottom - (moveEvent.clientY - startY),MIN_PANEL_SIZES.bottom,maxBottom)});
      }
    };
    const handleEnd = () => {
      setResizeTarget(null);
      window.removeEventListener("pointermove",handleMove);
      window.removeEventListener("pointerup",handleEnd);
      window.removeEventListener("pointercancel",handleEnd);
    };
    window.addEventListener("pointermove",handleMove);
    window.addEventListener("pointerup",handleEnd);
    window.addEventListener("pointercancel",handleEnd);
  }
  function resetPanel(target:ResizeTarget) {
    setPanelSizes(current => ({...current,[target]:DEFAULT_PANEL_SIZES[target]}));
  }

  function persistPracticeWorkspace(nextCode:string) {
    const draft:PracticeWorkspaceDraft|undefined=activeGeneratedPractice
      ? {kind:"generated",practice:activeGeneratedPractice,code:nextCode}
      : problem ? {kind:"problem",problem,code:nextCode} : undefined;
    if(!draft)return;
    practiceWorkspace.current=draft;
    localStorage.setItem(PRACTICE_WORKSPACE_KEY,JSON.stringify(draft));
  }

  function updatePracticeCode(nextCode:string) {
    setCode(nextCode);
    persistPracticeWorkspace(nextCode);
  }

  async function chooseProblem(item: Problem) {
    ++generatedRunEpoch.current; setActiveGeneratedPractice(undefined); setPracticeEvaluation(undefined); setPracticeEvaluating(false); setPracticeEvaluationError("");
    setProblem(item); setCode(item.starterCode || templates[item.language.toLowerCase()] || "");
    setSubmission(undefined); setRunResult(undefined); setRunAccess(undefined); setHint(""); setAssistance(undefined); setAssistanceError(""); setTab("run");
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
    setGuestRun(false); setBusy(true); setNotice(""); setHint(""); setRunResult(undefined); setRunAccess(undefined); setAssistance(undefined); setTab("result");
    try {
      const accepted = await api.submit(problem.id, problem.language, code);
      await poll(accepted.submissionId);
    } catch (e) { setNotice(e instanceof Error ? e.message : "提交失败"); setBusy(false); }
  }
  async function runCode() {
    if(activeGeneratedPractice){await runGeneratedPractice();return;}
    if (!problem || !code.trim()) return;
    setGuestRun(true); setBusy(true); setNotice(""); setHint(""); setSubmission(undefined); setAssistance(undefined); setAssistanceError(""); setTab("run");
    try { const accepted=await api.run(problem.id,problem.language,code); setRunAccess({id:accepted.submissionId,token:accepted.runToken}); await pollGuest(accepted.submissionId,accepted.runToken); }
    catch(e){setNotice(e instanceof Error?e.message:"运行失败");setBusy(false)}
  }
  async function runGeneratedPractice(){
    if(!user){setAuthOpen(true);return;}
    if(!activeGeneratedPractice||!code.trim())return;
    if(pollRef.current)window.clearTimeout(pollRef.current);
    const epoch=++generatedRunEpoch.current;
    setGuestRun(true);setBusy(true);setNotice("");setTab("run");setRunResult(undefined);setSubmission(undefined);setAssistance(undefined);setAssistanceError("");setPracticeEvaluation(undefined);setPracticeEvaluating(false);setPracticeEvaluationError("");
    try{
      const language=activeGeneratedPractice.language.toLowerCase().replace("c++","cpp");
      const accepted=await api.submitAiPractice(activeGeneratedPractice.id,language,code);
      await pollGeneratedPractice(accepted.submissionId,activeGeneratedPractice,epoch);
    }catch(e){if(epoch===generatedRunEpoch.current){setBusy(false);setNotice(e instanceof Error?e.message:"运行失败");}}
  }
  async function pollGeneratedPractice(id:number,item:AiGeneratedPractice,epoch:number){
    try{
      const detail=await api.submission(id);
      if(epoch!==generatedRunEpoch.current)return;
      setRunResult(detail);
      if(!terminalStates.has(detail.status)){pollRef.current=window.setTimeout(()=>pollGeneratedPractice(id,item,epoch),900);return;}
      setBusy(false);
      if(detail.status==="COMPLETED")await evaluateGeneratedPractice(item,detail,epoch);
    }catch(e){if(epoch===generatedRunEpoch.current){setBusy(false);setNotice(e instanceof Error?e.message:"读取运行结果失败");}}
  }
  async function evaluateGeneratedPractice(item:AiGeneratedPractice,detail:Submission,epoch:number){
    setPracticeEvaluating(true);setPracticeEvaluationError("");setTab("result");
    try{
      const next=await api.finalizeAiPractice(item.id,detail.id);
      if(epoch!==generatedRunEpoch.current)return;
      setPracticeEvaluation(next);
      setAssistance({
        submissionId:detail.id,errorType:next.errorType,
        rootCause:next.correct?"本次解答通过沙箱确定性评测":next.feedback,
        explanation:next.feedback,nextStep:next.nextStep,modelName:next.modelName,
        knowledge:next.masteryChanges.map(change=>{
          const point=item.knowledgePoints.find(candidate=>candidate.id===change.knowledgePointId);
          return {id:change.knowledgePointId,code:point?.code||"",name:change.knowledgePointName,
            description:"AI 专项练习评测证据",masteryBefore:change.masteryBefore,
            masteryAfter:change.masteryAfter,delta:change.delta,
            reason:next.correct?`${change.knowledgePointName}已在本次练习中正确运用`:`建议继续练习${change.knowledgePointName}`};
        })
      });
      if(next.correct)api.activeLearningPlan().then(setActivePlan).catch(()=>{});
    }catch(e){if(epoch===generatedRunEpoch.current)setPracticeEvaluationError(e instanceof Error?e.message:"AI 教练评估失败");}
    finally{if(epoch===generatedRunEpoch.current)setPracticeEvaluating(false);}
  }
  async function pollGuest(id:number,token:string) {
    try { const detail=await api.runDetail(id,token);setRunResult(detail);
      if(!terminalStates.has(detail.status))pollRef.current=window.setTimeout(()=>pollGuest(id,token),1100);else {setBusy(false);if(user)await loadRunAssistance(id,token);}
    } catch(e){setBusy(false);setNotice(e instanceof Error?e.message:"读取运行结果失败")}
  }
  async function poll(id: number) {
    try {
      const detail = await api.submission(id); setSubmission(detail);
      if (!terminalStates.has(detail.status)) pollRef.current = window.setTimeout(() => poll(id), 1100);
      else { setBusy(false); if (problem) api.history(problem.id).then(p => setHistory(p.content)); if(detail.status==="COMPLETED") loadAssistance(id); }
    } catch (e) { setBusy(false); setNotice(e instanceof Error ? e.message : "读取结果失败"); }
  }
  async function loadAssistance(submissionId:number) {
    if(!user)return;
    setAssistanceLoading(true);setAssistanceError("");
    try { setAssistance(await api.learningAssistance(submissionId)); }
    catch(e) { setAssistanceError(e instanceof Error?e.message:"辅助学习分析加载失败"); }
    finally { setAssistanceLoading(false); }
  }
  async function loadRunAssistance(submissionId:number,token:string) {
    if(!user)return;
    setAssistanceLoading(true);setAssistanceError("");
    try { setAssistance(await api.runLearningAssistance(submissionId,token)); }
    catch(e) { setAssistanceError(e instanceof Error?e.message:"运行分析加载失败"); }
    finally { setAssistanceLoading(false); }
  }
  async function generatePractice() {
    if(!submission)return;
    setPracticeGenerating(true);setPracticeGenerationError("");
    try {
      const generated=await api.generatePractice(submission.id);
      setGeneratedPractice(generated);
      localStorage.setItem("codepath-generated-practice",JSON.stringify(generated));
    } catch(e) { setPracticeGenerationError(e instanceof Error?e.message:"AI 生题失败"); }
    finally { setPracticeGenerating(false); }
  }
  function openGeneratedPractice(item:AiGeneratedPractice) {
    ++generatedRunEpoch.current;
    setGeneratedPractice(item);
    localStorage.setItem("codepath-generated-practice",JSON.stringify(item));
    setActiveGeneratedPractice(item);setCode(item.starterCode||templates[item.language.toLowerCase()]||"");
    setSubmission(undefined);setRunResult(undefined);setRunAccess(undefined);setAssistance(undefined);setAssistanceError("");setPracticeEvaluation(undefined);setPracticeEvaluating(false);setPracticeEvaluationError("");setNotice("");setTab("run");setSideTab("problem");
    setPage("practice");
  }
  function resetPractice() {
    if(!activeGeneratedPractice){
      setCode(problem?.starterCode||templates[(problem?.language||"").toLowerCase()]||"");
      return;
    }
    ++generatedRunEpoch.current;
    if(pollRef.current)window.clearTimeout(pollRef.current);
    practiceWorkspace.current=null;localStorage.removeItem(PRACTICE_WORKSPACE_KEY);
    setActiveGeneratedPractice(undefined);setProblem(undefined);setCode("");
    setSubmission(undefined);setRunResult(undefined);setRunAccess(undefined);setHistory([]);setExamples([]);
    setAssistance(undefined);setAssistanceError("");setHint("");setNotice("");setBusy(false);setGuestRun(false);
    setPracticeEvaluation(undefined);setPracticeEvaluating(false);setPracticeEvaluationError("");
    setTab("run");setSideTab("problem");
  }
  function openFreePractice() {
    const latestCode=practiceEditorRef.current?.getValue()??code;
    setCode(latestCode);
    persistPracticeWorkspace(latestCode);
    setPage("self-practice");
  }
  async function openPlanProblem(id:number) {
    try {
      const target=problems.find(item=>item.id===id)||await api.problem(id);
      if(target.courseId!==courseId)setCourseId(target.courseId);
      await chooseProblem(target);setPage("practice");
    } catch(e){setNotice(e instanceof Error?e.message:"题目加载失败");}
  }
  async function getHint(level: number) {
    if (!submission) return; setHintLoading(level); setSideTab("coach");
    try { const data = await api.hint(submission.id, level); setHint(data.content); }
    catch (e) { setHint(e instanceof Error ? e.message : "提示请求失败"); }
    finally { setHintLoading(0); }
  }
  function logout() { authStore.clear(); setUser(null); setUserAvatar(""); setProfileOpen(false); setHistory([]); setActivePlan(undefined); }
  const passed = submission && submission.totalCount > 0 && submission.passedCount === submission.totalCount;
  const coachResult = guestRun ? runResult : submission;
  const activeDiff = useMemo(() => difficulty(activeGeneratedPractice?.difficulty||problem?.difficulty||1), [problem,activeGeneratedPractice]);
  const hasExercise=!!(activeGeneratedPractice||problem);
  const activeTitle=activeGeneratedPractice?.title||problem?.title||"CodePath";
  const activeLanguage=activeGeneratedPractice?.language||problem?.language;

  if (!user) return <WelcomePage onSuccess={u=>{setUser(u);if(problem)api.history(problem.id).then(p=>setHistory(p.content));}}/>;

  return <div className="app-shell">
    <header>
      <div className="brand"><span className="brand-icon"><Code2 size={22}/></span><strong>CodePath</strong><span className="brand-tag">AI 编程教练</span></div>
      <nav className="top-nav"><button className={page==="practice"?"active":""} onClick={()=>setPage("practice")}>Practice</button><button className={page==="discuss"?"active":""} onClick={()=>setPage("discuss")}>Discuss</button><button className={page==="interview"?"active":""} onClick={()=>setPage("interview")}>Interview</button><button className={page==="self-practice"?"active":""} onClick={openFreePractice}>Self-Practice</button></nav>
      {user ? <div className="user-menu"><button className="user-summary" title="打开用户信息" onClick={()=>setProfileOpen(true)}><span className="avatar">{userAvatar?<img src={userAvatar} alt=""/>:user.displayName.slice(0,1)}</span><span className="user-summary-copy"><b>{user.displayName}</b><small>学习者 · 查看资料</small></span></button><button className="icon-btn" title="退出" onClick={logout}><LogOut size={17}/></button></div>
        : <button className="login-btn" onClick={() => setAuthOpen(true)}><LogIn size={17}/>登录 / 注册</button>}
    </header>

    {notice && <div className="toast"><CircleAlert size={17}/>{notice}<button onClick={() => setNotice("")}><X size={15}/></button></div>}
    {authOpen && <AuthDialog onClose={() => setAuthOpen(false)} onSuccess={u => { setUser(u); if (problem) api.history(problem.id).then(p => setHistory(p.content)); }}/>}
    <div ref={workspaceRef} className={`workspace resizable-workspace${resizeTarget ? ` resizing-${resizeTarget}` : ""}`}
      style={{gridTemplateColumns:`${panelSizes.left}px ${RESIZER_SIZE}px minmax(${MIN_PANEL_SIZES.center}px, 1fr) ${RESIZER_SIZE}px ${panelSizes.right}px`,display:page==="practice"?undefined:"none"}}>
      <aside className="problem-nav">
        <div className="nav-title"><BookOpen size={16}/><span>学习路径</span></div>
        {activePlan?<><div className="path-plan-head"><span>{activePlan.courseName}</span><b>{activePlan.title}</b><button onClick={()=>setPage("plan")}>管理计划<ChevronRight size={13}/></button></div><div className="progress-card"><div><span>计划完成度</span><b>{Math.round(activePlan.planProgress*100)}%</b></div><div className="progress"><i style={{width:`${Math.round(activePlan.planProgress*100)}%`}}/></div><small>{activePlan.completedMinutes} / {activePlan.totalMinutes} 分钟任务权重</small></div><div className="path-stages">{activePlan.stages.map(stage=><section key={stage.id}><h4><span>W{stage.weekNumber}</span>{stage.title}</h4>{stage.items.map(item=><button key={item.id} className={item.status==="COMPLETED"?"done":""} onClick={()=>item.problemId?openPlanProblem(item.problemId):setPage("plan")}><i>{item.status==="COMPLETED"?<Check size={11}/>:null}</i><span><b>{item.title}</b><small>{item.taskType} · {item.estimatedMinutes} 分钟</small></span><ChevronRight size={13}/></button>)}</section>)}</div></>:<div className="path-empty"><Sparkles size={26}/><b>{user?"还没有学习计划":"登录后开启个性化路径"}</b><p>AI 会根据目标、时间和掌握度生成学习路径。</p><button onClick={()=>user?setPage("plan"):setAuthOpen(true)}>{user?"生成学习计划":"登录 / 注册"}</button></div>}
      </aside>

      <div className="box-resizer vertical" role="separator" aria-label="调整学习路径区宽度" aria-orientation="vertical" aria-valuemin={MIN_PANEL_SIZES.left} aria-valuemax={900} aria-valuenow={Math.round(panelSizes.left)} tabIndex={0}
        title="左右拖动调整学习路径宽度，双击恢复"
        onPointerDown={event=>startResize("left",event)} onDoubleClick={()=>resetPanel("left")}
        onKeyDown={event=>{if(event.key==="ArrowLeft"){event.preventDefault();resizePanel("left",-20)}if(event.key==="ArrowRight"){event.preventDefault();resizePanel("left",20)}}}/>

      <main className="coding-area resizable-coding-area" style={{gridTemplateRows:`48px minmax(${MIN_PANEL_SIZES.editor}px, 1fr) 54px ${RESIZER_SIZE}px ${panelSizes.bottom}px`}}>
        <div className="editor-head">
          <div><span className="file-dot"/><b>{activeTitle}</b>{activeGeneratedPractice&&<span className="ai-source-badge"><Sparkles size={11}/>AI 生成</span>}{hasExercise&&<span className={`difficulty ${activeDiff[1]}`}>{activeDiff[0]}</span>}</div>
          {hasExercise&&<div className="editor-actions"><span className="language-pill">{activeLanguage}</span><button onClick={resetPractice} title={activeGeneratedPractice?"退出 AI 练习并返回欢迎界面":"恢复初始代码"}><RotateCcw size={15}/>重置</button></div>}
        </div>
        <div className="editor-wrap">
          {hasExercise?<Editor height="100%" theme="vs-dark" language={monacoLanguage(activeLanguage)} value={code}
            onMount={editor=>{practiceEditorRef.current=editor;}}
            onChange={v => updatePracticeCode(v || "")} loading={<div className="editor-loading"><LoaderCircle className="spin"/>加载编辑器…</div>}
            options={{ fontSize: 14, lineHeight: 23, minimap: { enabled: false }, automaticLayout: true, padding: { top: 16 }, scrollBeyondLastLine: false, wordWrap: "on", tabSize: 4 }} />:<PracticeWelcome/>}
        </div>
        <div className="submit-bar">
          <div className="shortcut"><TerminalSquare size={16}/><span>代码最大 100,000 字符</span></div>
          {activeGeneratedPractice?<button className="primary" disabled={busy||practiceEvaluating||!code.trim()} onClick={runGeneratedPractice}>{busy||practiceEvaluating?<LoaderCircle className="spin" size={17}/>:<ListChecks size={17}/>} {busy?statusText[runResult?.status||"QUEUED"]:practiceEvaluating?"AI 解释中":"提交确定性评测"}</button>:<><button className="secondary" disabled={busy||!problem} onClick={runCode} title="使用题目测试用例快速检查"><Play size={16}/>运行</button><button className="primary" disabled={busy || !problem} onClick={submit}>{busy ? <LoaderCircle className="spin" size={17}/> : <Send size={17}/>} {busy ? statusText[submission?.status || "QUEUED"] : "提交评测"}</button></>}
        </div>

        <div className="box-resizer horizontal" role="separator" aria-label="调整代码编辑区和结果区高度" aria-orientation="horizontal" aria-valuemin={MIN_PANEL_SIZES.bottom} aria-valuemax={1200} aria-valuenow={Math.round(panelSizes.bottom)} tabIndex={0}
          title="上下拖动调整编辑区与结果区，双击恢复"
          onPointerDown={event=>startResize("bottom",event)} onDoubleClick={()=>resetPanel("bottom")}
          onKeyDown={event=>{if(event.key==="ArrowUp"){event.preventDefault();resizePanel("bottom",20)}if(event.key==="ArrowDown"){event.preventDefault();resizePanel("bottom",-20)}}}/>

        <section className="bottom-panel">
          <div className="panel-tabs"><button className={tab === "run" ? "active" : ""} onClick={() => setTab("run")}><TerminalSquare size={15}/>运行结果</button><button className={tab === "result" ? "active" : ""} onClick={() => setTab("result")}><ListChecks size={15}/>{activeGeneratedPractice?"AI 评测":"评测结果"}</button>{!activeGeneratedPractice&&<button className={tab === "history" ? "active" : ""} onClick={() => setTab("history")}><History size={15}/>提交记录</button>}</div>
          <div className="panel-content">{tab === "run" ? <RunResult result={runResult} busy={busy&&guestRun} examples={activeGeneratedPractice?[]:examples} generated={!!activeGeneratedPractice}/> : tab === "result" ? activeGeneratedPractice?<AiPracticeResult evaluation={practiceEvaluation} evaluating={practiceEvaluating} error={practiceEvaluationError} onRetry={()=>runResult&&evaluateGeneratedPractice(activeGeneratedPractice,runResult,generatedRunEpoch.current)}/>:<Result submission={submission} busy={busy&&!guestRun} passed={!!passed}/> : <HistoryList items={history} onSelect={id => { setTab("result"); poll(id); }}/>}</div>
        </section>
      </main>

      <div className="box-resizer vertical" role="separator" aria-label="调整题目与 AI 面板宽度" aria-orientation="vertical" aria-valuemin={MIN_PANEL_SIZES.right} aria-valuemax={900} aria-valuenow={Math.round(panelSizes.right)} tabIndex={0}
        title="左右拖动调整右侧面板宽度，双击恢复"
        onPointerDown={event=>startResize("right",event)} onDoubleClick={()=>resetPanel("right")}
        onKeyDown={event=>{if(event.key==="ArrowLeft"){event.preventDefault();resizePanel("right",20)}if(event.key==="ArrowRight"){event.preventDefault();resizePanel("right",-20)}}}/>

      <aside className="detail-panel">
        <div className="side-tabs"><button className={sideTab === "problem" ? "active" : ""} onClick={() => setSideTab("problem")}><BookOpen size={15}/>题目</button><button className={sideTab === "coach" ? "active" : ""} onClick={() => setSideTab("coach")}><Bot size={15}/>AI 教练</button><button className={sideTab === "assist" ? "active" : ""} onClick={() => {setSideTab("assist");if(!assistance){if(submission)loadAssistance(submission.id);else if(runAccess)loadRunAssistance(runAccess.id,runAccess.token)}}}><BookMarked size={15}/>辅助学习</button></div>
        {sideTab === "problem" ? !hasExercise?<ProblemWelcome/>:<div className="problem-copy">
          <div className="problem-heading"><span>{activeGeneratedPractice?"AI 专项练习":`题目 ${problem?.id || "--"}`}</span><h1>{activeTitle}</h1><div>{activeGeneratedPractice?<span><Sparkles size={13}/>{activeGeneratedPractice.modelName}</span>:<><span><Clock3 size={13}/>{problem?.timeLimitMs || 0} ms</span><span>{problem?.memoryLimitMb || 0} MB</span></>}</div></div>
          <Article text={activeGeneratedPractice?.description||problem?.description}/><h3>输入说明</h3><Article text={activeGeneratedPractice?.inputDescription||problem?.inputDescription}/><h3>输出说明</h3><Article text={activeGeneratedPractice?.outputDescription||problem?.outputDescription}/>
          {activeGeneratedPractice?<><h3>公开运行示例</h3><div className="public-examples">{activeGeneratedPractice.publicExamples.map((example,index)=><div className="example-card" key={example.id}><b>示例 {index+1}</b><label>输入</label><pre>{example.input||"(空输入)"}</pre><label>期望输出</label><pre>{example.expectedOutput}</pre></div>)}</div><p className="no-example">共 {activeGeneratedPractice.totalTestCases} 个确定性测试点，其余为隐藏用例。</p><div className="generated-knowledge"><strong>本题练习</strong>{activeGeneratedPractice.knowledgePoints.map(item=><span key={item.id}>{item.name}</span>)}</div><div className="ai-evaluation-note"><Bot size={17}/><span>沙箱测试决定对错；AI 只解释错因。同一题只有首次完成评测会更新 BKT。</span></div></>:<><h3>公开运行示例</h3>{examplesLoading ? <p className="example-state"><LoaderCircle className="spin" size={13}/>正在加载示例…</p> : examples.length ? <div className="public-examples">{examples.map((example,index)=><div className="example-card" key={example.id}><b>示例 {index+1}</b><label>输入</label><pre>{example.input || "(空输入)"}</pre><label>期望输出</label><pre>{example.expectedOutput}</pre></div>)}</div> : examplesError ? <div className="example-error"><span>{examplesError}</span><button onClick={()=>problem&&loadExamples(problem.id)}>重试</button></div> : <p className="no-example">教师尚未配置公开示例</p>}</>}
        </div> : sideTab === "coach" ? <div className="coach-panel">
          <div className="coach-intro"><span><Bot size={22}/></span><div><b>CodePath Coach</b><small>基于你的代码和运行证据</small></div></div>
          {activeGeneratedPractice?<AiPracticeCoach evaluation={practiceEvaluation} evaluating={practiceEvaluating} error={practiceEvaluationError} hasRun={!!runResult} onRetry={()=>runResult&&evaluateGeneratedPractice(activeGeneratedPractice,runResult,generatedRunEpoch.current)}/>:<>
            {!coachResult?.diagnosis && !hint && <div className="coach-empty"><Sparkles size={30}/><b>先运行或提交一次代码</b><p>AI 会诊断根因，并引导你自己解决。</p></div>}
            {coachResult?.diagnosis && <div className="diagnosis-card"><span className="diagnosis-label">诊断 · {coachResult.diagnosis.errorType}</span><h3>{coachResult.diagnosis.rootCause}</h3><p>{coachResult.diagnosis.explanation}</p><small>置信度 {Math.round((coachResult.diagnosis.confidence || 0) * 100)}% · {coachResult.diagnosis.modelName}</small></div>}
            {submission && !guestRun && <div className="hints"><h3><Lightbulb size={16}/>分层提示</h3><p>先独立思考，卡住时再逐层打开。</p><div>{[1,2,3].map(n => <button key={n} onClick={() => getHint(n)} disabled={!!hintLoading}>{hintLoading === n ? <LoaderCircle className="spin" size={15}/> : n}<span>{["方向提示","定位错误","伪代码引导"][n-1]}</span></button>)}</div></div>}
            {runResult && guestRun && <div className="guest-note"><Sparkles size={16}/><div><b>快速运行结果</b><p>如需保存此次结果到提交历史和 Interview 档案，请点击“提交评测”。</p></div></div>}
            {hint && <div className="hint-answer"><div><Sparkles size={15}/><b>AI 提示</b></div><p>{hint}</p></div>}
          </>}
        </div> : <AssistancePanel user={user} evidence={submission||runResult} canGenerate={!!submission} assistance={assistance} loading={assistanceLoading} error={assistanceError} onRetry={()=>{if(submission)loadAssistance(submission.id);else if(runAccess)loadRunAssistance(runAccess.id,runAccess.token)}} generatedPractice={generatedPractice} generating={practiceGenerating} generationError={practiceGenerationError} onGenerate={generatePractice} onOpenGenerated={openGeneratedPractice} onNeedAuth={()=>setAuthOpen(true)}/>}
      </aside>
    </div>
    {page === "plan" ? <LearningPlanPage user={user} initialCourseId={courseId} activePlan={activePlan} onNeedAuth={()=>setAuthOpen(true)} onOpenProblem={openPlanProblem} onOpenGenerated={openGeneratedPractice} onPlanChange={setActivePlan}/> : page === "discuss" ? <DiscussPage user={user} problems={problems} onNeedAuth={()=>setAuthOpen(true)}/> : page === "interview" ? <InterviewPage key={interviewKey} user={user} onNeedAuth={()=>setAuthOpen(true)} onOpenGenerated={openGeneratedPractice}/> : page === "self-practice" ?
      <SelfPracticePage user={user} onNeedAuth={()=>setAuthOpen(true)} onExitGenerated={openFreePractice}/> : null}
    <AiWidget key={user.id} user={user} submissionId={submission?.id}/>
    {profileOpen&&<UserProfilePanel user={user} onClose={()=>setProfileOpen(false)} onSaved={profile=>{const next={...user,displayName:profile.displayName};setUser(next);authStore.updateUser(next);setUserAvatar(profile.avatarDataUrl);setProfileOpen(false);}}/>}
    {notice && <div className="toast"><CircleAlert size={17}/>{notice}<button onClick={() => setNotice("")}><X size={15}/></button></div>}
    {authOpen && <AuthDialog onClose={() => setAuthOpen(false)} onSuccess={u => { setUser(u); if (problem) api.history(problem.id).then(p => setHistory(p.content)); }}/>}
  </div>;
}

function AssistancePanel({user,evidence,canGenerate,assistance,loading,error,onRetry,generatedPractice,generating,generationError,onGenerate,onOpenGenerated,onNeedAuth}:{user:User|null;evidence?:Submission;canGenerate:boolean;assistance?:LearningAssistance;loading:boolean;error:string;onRetry:()=>void;generatedPractice?:AiGeneratedPractice;generating:boolean;generationError:string;onGenerate:()=>void;onOpenGenerated:(item:AiGeneratedPractice)=>void;onNeedAuth:()=>void}) {
  if(!user)return <div className="assist-panel"><div className="coach-empty"><BookMarked size={30}/><b>登录后开启个性化辅助</b><p>系统会跟踪知识点掌握度，并推荐下一步练习。</p><button className="primary assist-login" onClick={onNeedAuth}>登录 / 注册</button></div></div>;
  if(!evidence)return <div className="assist-panel"><div className="coach-empty"><Target size={30}/><b>先运行或提交一次代码</b><p>评测完成后，这里会给出下一步引导和知识点练习建议。</p></div></div>;
  if(loading)return <div className="assist-panel"><div className="assist-loading"><LoaderCircle className="spin"/><b>正在结合代码证据和 BKT 掌握度…</b></div></div>;
  if(error)return <div className="assist-panel"><div className="example-error"><span>{error}</span><button onClick={onRetry}>重试</button></div></div>;
  if(!assistance)return <div className="assist-panel"><div className="coach-empty"><BookMarked size={30}/><b>辅助学习等待分析</b></div></div>;
  return <div className="assist-panel">
    <div className="assist-hero"><span><Sparkles size={18}/></span><div><small>基于本次代码分析</small><b>{assistance.rootCause||"当前解法已通过检查"}</b></div></div>
    <section className="next-step"><h3><ArrowRight size={15}/>下一步引导</h3><p>{assistance.nextStep||assistance.explanation}</p><small>{assistance.errorType} · {assistance.modelName}</small></section>
    <section className="knowledge-focus"><h3><TrendingUp size={15}/>建议练习的知识</h3>{assistance.knowledge.map(item=><div className="knowledge-card" key={item.id}><div><b>{item.name}</b><span>{Math.round(item.masteryBefore*100)}% <ArrowRight size={11}/> {Math.round(item.masteryAfter*100)}%</span></div><div className="mastery-track"><i style={{width:`${Math.max(3,item.masteryAfter*100)}%`}}/></div><p>{item.reason}</p></div>)}</section>
    {canGenerate&&<section className="ai-practice-generate"><h3><Target size={15}/>AI 专项生题</h3><p>根据本次代码证据和当前 BKT 掌握度，生成一题针对性练习。</p>
      {generatedPractice?<button className="generated-practice-card" onClick={()=>onOpenGenerated(generatedPractice)}><span><Sparkles size={15}/></span><div><b>{generatedPractice.title}</b><small>{generatedPractice.knowledgePoints.map(item=>item.name).join(" · ")}</small><p>点击进入 Practice，在当前编辑器运行后由 AI 评测并更新掌握度。</p></div><ArrowRight size={16}/></button>:<button className="generate-practice-btn" disabled={generating} onClick={onGenerate}>{generating?<LoaderCircle className="spin" size={15}/>:<Sparkles size={15}/>} {generating?"AI 正在设计题目…":"生成一题专项练习"}</button>}
      {generationError&&<div className="practice-generation-error"><CircleAlert size={13}/>{generationError}<button onClick={onGenerate}>重试</button></div>}
      {generatedPractice&&<button className="regenerate-practice" disabled={generating} onClick={onGenerate}>{generating?"正在重新生成…":"换一道题"}</button>}
    </section>}
  </div>;
}

function Article({ text }: { text?: string }) { return <div className="article">{text ? text.split("\n").map((line,i) => <p key={i}>{line || "\u00a0"}</p>) : <p className="muted">暂无说明</p>}</div>; }

function PracticeWelcome(){
  return <div className="practice-welcome"><span><Code2 size={44}/></span><strong>CodePath</strong><p>选择学习路径中的练习，开始你的编程训练。</p></div>;
}

function ProblemWelcome(){
  return <div className="problem-welcome"><span><Sparkles size={28}/></span><b>快来练习吧</b><p>从学习路径选择一道练习，题目说明和公开示例会显示在这里。</p></div>;
}

function RunResult({result,busy,examples,generated=false}:{result?:Submission;busy:boolean;examples:PublicExample[];generated?:boolean}) {
  if(!result)return null;
  const executed=!!result.testResults?.length&&result.testResults.every(item=>item.status==="PASSED");
  return <div className="terminal-result"><div className="terminal-toolbar"><span/><span/><span/><b>CodePath Sandbox</b><em>{busy?statusText[result.status]:generated?`${result.runtimeMs||0} ms · ${executed?"执行完成":"执行异常"}`:`${result.runtimeMs||0} ms · ${result.passedCount}/${result.totalCount} 通过`}</em></div><div className="terminal-output">
    <p className="command"><i>$</i> run {result.language} submission #{result.id}</p>
    {busy&&<p className="terminal-pending"><LoaderCircle className="spin" size={14}/>{statusText[result.status]||result.status}...</p>}
    {result.errorMessage&&<pre className="stderr">{result.errorMessage}</pre>}
    {result.testResults?.map((item,index)=><div className="terminal-case" key={item.id}><p><span className={item.status==="PASSED"?"term-ok":"term-bad"}>[{generated&&item.status==="PASSED"?"EXECUTED":item.status}]</span> {generated?"沙箱运行":`公开示例 ${index+1}`} <small>{item.runtimeMs||0} ms</small></p>{examples[index]&&<><label>stdin</label><pre>{examples[index].input || "(空输入)"}</pre><label>expected</label><pre>{examples[index].expectedOutput}</pre></>}{item.actualOutput!=null&&<><label>stdout</label><pre>{item.actualOutput || "(无输出)"}</pre></>}{item.stderr&&item.stderr!==result.errorMessage&&<><label>stderr</label><pre className="stderr">{item.stderr}</pre></>}</div>)}
    {!busy&&<p className="process-end">{generated?`Process finished · ${executed?"代码已执行，正确性以 AI 评测为准":"运行异常"}`:`Process finished with ${result.passedCount===result.totalCount?"exit code 0":"errors"}`}</p>}
  </div></div>
}

function AiPracticeCoach({evaluation,evaluating,error,hasRun,onRetry}:{evaluation?:AiPracticeEvaluation;evaluating:boolean;error:string;hasRun:boolean;onRetry:()=>void}){
  if(evaluating)return <div className="coach-empty"><LoaderCircle className="spin" size={28}/><b>AI 教练正在解释</b><p>对错已由沙箱测试确定，正在整理错因和下一步。</p></div>;
  if(error)return <div className="ai-evaluation-error"><CircleAlert size={15}/><span>{error}</span><button onClick={onRetry}>重试评测</button></div>;
  if(!evaluation)return <div className="coach-empty"><Sparkles size={30}/><b>{hasRun?"等待 AI 解释":"先提交一次代码"}</b><p>沙箱会运行全部公开和隐藏用例，AI 不参与决定对错。</p></div>;
  return <div className="diagnosis-card"><span className="diagnosis-label">AI 评测 · {evaluation.errorType}</span><h3>{evaluation.correct?"解答正确":"解答还需要调整"}</h3><p>{evaluation.feedback}</p><div className="evaluation-next"><Sparkles size={13}/>{evaluation.nextStep}</div><small>得分 {Math.round(evaluation.score*100)}% · {evaluation.modelName}</small></div>;
}

function AiPracticeResult({evaluation,evaluating,error,onRetry}:{evaluation?:AiPracticeEvaluation;evaluating:boolean;error:string;onRetry:()=>void}){
  if(evaluating)return <div className="ai-judging"><LoaderCircle className="spin" size={17}/><span>沙箱已完成判定，AI 教练正在生成解释…</span></div>;
  if(error)return <div className="ai-evaluation-error"><CircleAlert size={15}/><span>{error}</span><button onClick={onRetry}>重试评估</button></div>;
  if(!evaluation)return <div className="empty-result"><Bot size={27}/><div><b>等待 AI 评测</b><p>运行代码后，AI 会结合题意和终端输出判断对错。</p></div></div>;
  return <div className={`ai-evaluation ${evaluation.correct?"correct":"incorrect"}`}><div className="evaluation-head">{evaluation.correct?<Check size={18}/>:<Bot size={18}/>}<span><small>沙箱确定性评测</small><b>{evaluation.correct?"解答正确":"还需要调整"}</b></span><strong>{Math.round(evaluation.score*100)}</strong></div><p>{evaluation.feedback}</p><div className="evaluation-next"><Sparkles size={13}/>{evaluation.nextStep}</div>{evaluation.masteryChanges.length>0&&<div className="mastery-changes"><span><TrendingUp size={13}/>BKT 掌握度已更新</span>{evaluation.masteryChanges.map(item=><div key={item.knowledgePointId}><b>{item.knowledgePointName}</b><em>{Math.round(item.masteryBefore*100)}% → {Math.round(item.masteryAfter*100)}%</em></div>)}</div>}{!evaluation.evidenceApplied&&<div className="evaluation-next"><ListChecks size={13}/>该题已有首次可信证据，本次重试不再重复更新 BKT。</div>}<small>{evaluation.errorType} · {evaluation.modelName}</small></div>;
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
  return <div className="history-list">{items.map(item => <button key={item.id} onClick={() => onSelect(item.id)}><span className={item.passedCount === item.totalCount ? "history-ok" : "history-bad"}>{item.passedCount === item.totalCount ? <Check size={14}/> : <CircleAlert size={14}/>}</span><b>#{item.id}</b><span>{item.language}</span><span>{item.passedCount}/{item.totalCount} 通过</span><small>{parseServerDate(item.createdAt).toLocaleString("zh-CN")}</small><ChevronRight size={14}/></button>)}</div>;
}

const templates: Record<string,string> = {
  python: "# 在这里编写你的代码\n\ndef solve():\n    pass\n\nif __name__ == \"__main__\":\n    solve()\n",
  java: "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // 在这里编写你的代码\n    }\n}\n",
  cpp: "#include <iostream>\nusing namespace std;\n\nint main() {\n    // 在这里编写你的代码\n    return 0;\n}\n",
  go: "package main\n\nimport \"fmt\"\n\nfunc main() {\n    // 在这里编写你的代码\n    fmt.Println(\"Hello, CodePath!\")\n}\n"
};
function monacoLanguage(value?: string) { const v = value?.toLowerCase(); return v === "cpp" || v === "c++" ? "cpp" : v === "java" ? "java" : v === "go" || v === "golang" ? "go" : "python"; }

export default App;
