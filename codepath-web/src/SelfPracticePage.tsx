import { useEffect, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { Braces, CircleAlert, Code2, Keyboard, LoaderCircle, Play, RotateCcw, TerminalSquare } from "lucide-react";
import { api } from "./api";
import type { Submission } from "./types";

const done = new Set(["COMPLETED", "FAILED"]);
const labels: Record<string,string> = { QUEUED:"排队中", RUNNING:"运行中", ANALYZING:"整理结果", DIAGNOSING:"分析运行信息", COMPLETED:"运行完成", FAILED:"运行失败" };
const templates: Record<string,string> = {
  python: 'name = input().strip()\nprint(f"Hello, {name}!")\n',
  java: 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String name = sc.nextLine();\n        System.out.println("Hello, " + name + "!");\n    }\n}\n',
  cpp: '#include <iostream>\n#include <string>\nusing namespace std;\n\nint main() {\n    string name;\n    getline(cin, name);\n    cout << "Hello, " << name << "!" << endl;\n    return 0;\n}\n'
};

export function SelfPracticePage() {
  const [language,setLanguage] = useState("python");
  const [code,setCode] = useState(templates.python);
  const [stdin,setStdin] = useState("CodePath");
  const [result,setResult] = useState<Submission>();
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState("");
  const timer = useRef<number>();
  useEffect(()=>()=>{ if(timer.current) clearTimeout(timer.current); },[]);
  function changeLanguage(value:string){ setLanguage(value); setCode(templates[value]); setResult(undefined); setError(""); }
  async function poll(id:number,token:string){
    try {
      const data=await api.runDetail(id,token); setResult(data);
      if(!done.has(data.status)) timer.current=window.setTimeout(()=>poll(id,token),900); else setBusy(false);
    } catch(e){setBusy(false);setError(e instanceof Error?e.message:"读取运行结果失败");}
  }
  async function run(){
    if(!code.trim())return;
    if(timer.current)clearTimeout(timer.current);
    setBusy(true);setError("");setResult(undefined);
    try { const accepted=await api.selfPractice(language,code,stdin); await poll(accepted.submissionId,accepted.runToken); }
    catch(e){setBusy(false);setError(e instanceof Error?e.message:"运行失败");}
  }
  const test=result?.testResults?.[0];
  return <div className="self-page">
    <aside className="self-guide">
      <div className="self-badge"><Braces size={21}/></div>
      <span className="eyebrow">SELF-PRACTICE</span><h1>自由编程实验室</h1>
      <p>不绑定题目，不比较标准答案。写你想写的程序，将终端输入交给它，然后观察真实的 stdout 和 stderr。</p>
      <div className="self-rule"><b>01</b><span><strong>选择语言</strong>Python / Java / C++</span></div>
      <div className="self-rule"><b>02</b><span><strong>写入输入</strong>每行对应一次标准输入</span></div>
      <div className="self-rule"><b>03</b><span><strong>查看终端</strong>保留程序原始输出与报错</span></div>
      <div className="sandbox-note"><TerminalSquare size={16}/><span>代码在隔离的 Docker 沙箱中运行，禁止网络访问。</span></div>
    </aside>
    <main className="self-workbench">
      <div className="self-toolbar"><div><Code2 size={16}/><b>main.{language==="python"?"py":language==="java"?"java":"cpp"}</b></div><div><select value={language} onChange={e=>changeLanguage(e.target.value)}><option value="python">Python</option><option value="java">Java</option><option value="cpp">C++</option></select><button onClick={()=>setCode(templates[language])}><RotateCcw size={14}/>重置</button><button className="primary" disabled={busy||!code.trim()} onClick={run}>{busy?<LoaderCircle className="spin" size={16}/>:<Play size={16}/>} {busy?(labels[result?.status||"QUEUED"]||"运行中"):"运行代码"}</button></div></div>
      <div className="self-editor"><Editor height="100%" theme="vs-dark" language={language==="cpp"?"cpp":language} value={code} onChange={v=>setCode(v||"")} options={{fontSize:14,lineHeight:23,minimap:{enabled:false},automaticLayout:true,padding:{top:16},scrollBeyondLastLine:false,tabSize:4}}/></div>
      <div className="self-console-grid">
        <section className="stdin-panel"><div className="console-title"><Keyboard size={15}/><b>终端输入 stdin</b><span>可换行</span></div><textarea value={stdin} onChange={e=>setStdin(e.target.value)} spellCheck={false} placeholder="在这里输入程序需要读取的内容…"/></section>
        <section className="stdout-panel"><div className="console-title"><TerminalSquare size={15}/><b>终端输出</b>{result&&<span>{result.runtimeMs||0} ms</span>}</div><div className="free-terminal">{error?<p className="free-error"><CircleAlert size={14}/>{error}</p>:busy&&!test?<p className="free-wait"><LoaderCircle className="spin" size={14}/>{labels[result?.status||"QUEUED"]}…</p>:test?<><div className="exit-line"><i>$</i> {language} main {"<"} stdin</div>{test.actualOutput&&<pre>{test.actualOutput}</pre>}{test.stderr&&<pre className="stderr">{test.stderr}</pre>} {!test.actualOutput&&!test.stderr&&<span className="no-output">(程序没有产生输出)</span>}<div className={test.status==="PASSED"?"exit-ok":"exit-bad"}>Process finished · {test.status}</div></>:<span className="no-output">点击“运行代码”后，输出会显示在这里。</span>}</div></section>
      </div>
    </main>
  </div>;
}
