import { useEffect, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { Bot, Braces, Check, CircleAlert, Code2, Keyboard, LoaderCircle, Play, RotateCcw, Sparkles, TerminalSquare, TrendingUp } from "lucide-react";
import { api } from "./api";
import type { AiGeneratedPractice, AiPracticeEvaluation, Submission, User } from "./types";
import "./ai-practice.css";

const done = new Set(["COMPLETED", "FAILED"]);
const labels: Record<string,string> = { QUEUED:"排队中", RUNNING:"运行中", ANALYZING:"整理结果", DIAGNOSING:"分析运行信息", COMPLETED:"运行完成", FAILED:"运行失败" };
const templates: Record<string,string> = {
  python: 'name = input().strip()\nprint(f"Hello, {name}!")\n',
  java: 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String name = sc.nextLine();\n        System.out.println("Hello, " + name + "!");\n    }\n}\n',
  cpp: '#include <iostream>\n#include <string>\nusing namespace std;\n\nint main() {\n    string name;\n    getline(cin, name);\n    cout << "Hello, " << name << "!" << endl;\n    return 0;\n}\n'
};

export function SelfPracticePage({practice,user,onNeedAuth,onExitGenerated}:{practice?:AiGeneratedPractice;user:User|null;onNeedAuth:()=>void;onExitGenerated:()=>void}) {
  const initialLanguage=(practice?.language||"python").toLowerCase().replace("c++","cpp");
  const [language,setLanguage] = useState(initialLanguage);
  const [code,setCode] = useState(practice?.starterCode||templates[initialLanguage]||templates.python);
  const [stdin,setStdin] = useState(practice?.stdin||"CodePath");
  const [result,setResult] = useState<Submission>();
  const [evaluation,setEvaluation] = useState<AiPracticeEvaluation>();
  const [busy,setBusy] = useState(false);
  const [evaluating,setEvaluating] = useState(false);
  const [error,setError] = useState("");
  const [evaluationError,setEvaluationError] = useState("");
  const timer = useRef<number>();
  const runEpoch = useRef(0);

  useEffect(()=>()=>{ if(timer.current) clearTimeout(timer.current); },[]);
  useEffect(()=>{
    const nextLanguage=(practice?.language||"python").toLowerCase().replace("c++","cpp");
    setLanguage(nextLanguage);setCode(practice?.starterCode||templates[nextLanguage]||templates.python);
    setStdin(practice?.stdin||"CodePath");setResult(undefined);setEvaluation(undefined);
    setError("");setEvaluationError("");
  },[practice?.id]);

  function changeLanguage(value:string){
    if(practice)return;
    setLanguage(value);setCode(templates[value]);setResult(undefined);setEvaluation(undefined);setError("");
  }
  async function evaluate(data:Submission,epoch:number){
    if(!practice||!user)return;
    const test=data.testResults?.[0];
    setEvaluating(true);setEvaluationError("");
    try {
      const next=await api.evaluatePractice(practice.id,code,stdin,test?.actualOutput||"",test?.stderr||data.errorMessage||"");
      if(epoch===runEpoch.current)setEvaluation(next);
    } catch(e){if(epoch===runEpoch.current)setEvaluationError(e instanceof Error?e.message:"AI 教练评估失败");}
    finally {if(epoch===runEpoch.current)setEvaluating(false);}
  }
  async function poll(id:number,token:string,epoch:number){
    try {
      const data=await api.runDetail(id,token);
      if(epoch!==runEpoch.current)return;
      setResult(data);
      if(!done.has(data.status)) timer.current=window.setTimeout(()=>poll(id,token,epoch),900);
      else {setBusy(false);if(practice&&data.status==="COMPLETED")await evaluate(data,epoch);}
    } catch(e){if(epoch===runEpoch.current){setBusy(false);setError(e instanceof Error?e.message:"读取运行结果失败");}}
  }
  async function run(){
    if(!code.trim())return;
    if(practice&&!user){onNeedAuth();return;}
    if(timer.current)clearTimeout(timer.current);
    const epoch=++runEpoch.current;
    setBusy(true);setError("");setEvaluationError("");setResult(undefined);setEvaluation(undefined);
    try { const accepted=await api.selfPractice(language,code,stdin); if(epoch===runEpoch.current)await poll(accepted.submissionId,accepted.runToken,epoch); }
    catch(e){if(epoch===runEpoch.current){setBusy(false);setError(e instanceof Error?e.message:"运行失败");}}
  }
  function reset(){
    ++runEpoch.current;
    if(timer.current)clearTimeout(timer.current);
    setBusy(false);setEvaluating(false);setResult(undefined);setEvaluation(undefined);setError("");setEvaluationError("");
    if(practice){onExitGenerated();return;}
    setCode(templates[language]);
  }

  const test=result?.testResults?.[0];
  return <div className={`self-page${practice?" generated-mode":""}`}>
    <aside className="self-guide">
      <div className="self-badge">{practice?<Sparkles size={21}/>:<Braces size={21}/>}</div>
      {practice?<>
        <span className="eyebrow">AI GENERATED PRACTICE</span><h1>{practice.title}</h1>
        <p>{practice.description}</p>
        <div className="generated-spec"><b>输入说明</b><p>{practice.inputDescription}</p><b>输出说明</b><p>{practice.outputDescription}</p></div>
        <div className="generated-knowledge"><strong>本题练习</strong>{practice.knowledgePoints.map(item=><span key={item.id}>{item.name}</span>)}</div>
        <div className="ai-evaluation-note"><Bot size={17}/><span>终端运行完成后，AI 教练会结合题意、代码和 stdout/stderr 判定结果，并将评分写入 BKT。</span></div>
      </>:<>
        <span className="eyebrow">SELF-PRACTICE</span><h1>自由编程实验室</h1>
        <p>不绑定题目，不比较标准答案。写你想写的程序，将终端输入交给它，然后观察真实的 stdout 和 stderr。</p>
        <div className="self-rule"><b>01</b><span><strong>选择语言</strong>Python / Java / C++</span></div>
        <div className="self-rule"><b>02</b><span><strong>写入输入</strong>每行对应一次标准输入</span></div>
        <div className="self-rule"><b>03</b><span><strong>查看终端</strong>保留程序原始输出与报错</span></div>
        <div className="sandbox-note"><TerminalSquare size={16}/><span>代码在隔离的 Docker 沙箱中运行，禁止网络访问。</span></div>
      </>}
    </aside>
    <main className="self-workbench">
      <div className="self-toolbar"><div><Code2 size={16}/><b>{practice?practice.title:`main.${language==="python"?"py":language==="java"?"java":"cpp"}`}</b></div><div><select value={language} disabled={!!practice} onChange={e=>changeLanguage(e.target.value)}><option value="python">Python</option><option value="java">Java</option><option value="cpp">C++</option></select><button onClick={reset}><RotateCcw size={14}/>重置</button><button className="primary" disabled={busy||evaluating||!code.trim()} onClick={run}>{busy?<LoaderCircle className="spin" size={16}/>:evaluating?<Bot size={16}/>:<Play size={16}/>} {busy?(labels[result?.status||"QUEUED"]||"运行中"):evaluating?"AI 评估中":"运行代码"}</button></div></div>
      <div className="self-editor"><Editor height="100%" theme="vs-dark" language={language==="cpp"?"cpp":language} value={code} onChange={v=>setCode(v||"")} options={{fontSize:14,lineHeight:23,minimap:{enabled:false},automaticLayout:true,padding:{top:16},scrollBeyondLastLine:false,tabSize:4}}/></div>
      <div className="self-console-grid">
        <section className="stdin-panel"><div className="console-title"><Keyboard size={15}/><b>终端输入 stdin</b><span>{practice?"AI 题目预置输入":"可换行"}</span></div><textarea value={stdin} onChange={e=>setStdin(e.target.value)} spellCheck={false} placeholder="在这里输入程序需要读取的内容…"/></section>
        <section className="stdout-panel"><div className="console-title"><TerminalSquare size={15}/><b>终端输出</b>{result&&<span>{result.runtimeMs||0} ms</span>}</div><div className="free-terminal">{error?<p className="free-error"><CircleAlert size={14}/>{error}</p>:busy&&!test?<p className="free-wait"><LoaderCircle className="spin" size={14}/>{labels[result?.status||"QUEUED"]}…</p>:test?<><div className="exit-line"><i>$</i> {language} main {"<"} stdin</div>{test.actualOutput&&<pre>{test.actualOutput}</pre>}{test.stderr&&<pre className="stderr">{test.stderr}</pre>} {!test.actualOutput&&!test.stderr&&<span className="no-output">(程序没有产生输出)</span>}<div className={test.stderr?"exit-bad":"exit-ok"}>Process finished · {test.stderr?"运行异常":"执行完成，等待 AI 评估"}</div></>:<span className="no-output">点击“运行代码”后，输出会显示在这里。</span>}
          {evaluating&&<div className="ai-judging"><LoaderCircle className="spin" size={15}/><span>AI 教练正在对照题意、代码与终端证据…</span></div>}
          {evaluationError&&<div className="ai-evaluation-error"><CircleAlert size={14}/><span>{evaluationError}</span><button onClick={()=>result&&evaluate(result,runEpoch.current)}>重试评估</button></div>}
          {evaluation&&<div className={`ai-evaluation ${evaluation.correct?"correct":"incorrect"}`}><div className="evaluation-head">{evaluation.correct?<Check size={18}/>:<Bot size={18}/>}<span><small>AI 教练评估</small><b>{evaluation.correct?"解答正确":"还需要调整"}</b></span><strong>{Math.round(evaluation.score*100)}</strong></div><p>{evaluation.feedback}</p><div className="evaluation-next"><Sparkles size={13}/>{evaluation.nextStep}</div>{evaluation.masteryChanges.length>0&&<div className="mastery-changes"><span><TrendingUp size={13}/>BKT 掌握度已更新</span>{evaluation.masteryChanges.map(item=><div key={item.knowledgePointId}><b>{item.knowledgePointName}</b><em>{Math.round(item.masteryBefore*100)}% → {Math.round(item.masteryAfter*100)}%</em></div>)}</div>}<small>{evaluation.errorType} · {evaluation.modelName}</small></div>}
        </div></section>
      </div>
    </main>
  </div>;
}
