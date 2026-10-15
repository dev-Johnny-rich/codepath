import { Fragment, useEffect, useMemo, useState } from "react";
import { BookOpenText, BrainCircuit, CalendarDays, Check, ChevronRight, CircleAlert, ClipboardCheck, Clock3, LoaderCircle, RefreshCw, Sparkles, Target, X } from "lucide-react";
import { api } from "./api";
import type { AiGeneratedPractice, KnowledgeExplanation, LearningPlan, LearningPlanItem, LearningTrack, User } from "./types";
import "./learning-plan.css";
import "./plan-lessons.css";

type PlanForm = {goal:string;currentLevel:string;daysPerWeek:number;minutesPerDay:number;durationWeeks:number};
const defaults:PlanForm={goal:"系统掌握一门编程语言，并能独立完成常见编程任务",currentLevel:"BEGINNER",daysPerWeek:5,minutesPerDay:45,durationWeeks:4};
const taskLabels:Record<string,string>={LEARN:"学习",PRACTICE:"练习",REVIEW:"复习",ASSESSMENT:"测评"};

export function LearningPlanPage({user,initialCourseId,activePlan,onNeedAuth,onOpenProblem,onOpenGenerated,onPlanChange}:{user:User|null;initialCourseId?:number;activePlan?:LearningPlan|null;onNeedAuth:()=>void;onOpenProblem:(id:number)=>void;onOpenGenerated:(item:AiGeneratedPractice)=>void;onPlanChange:(plan:LearningPlan)=>void}){
  const [tracks,setTracks]=useState<LearningTrack[]>([]);
  const [courseId,setCourseId]=useState<number|undefined>(initialCourseId);
  const [plan,setPlan]=useState<LearningPlan|null>();
  const [form,setForm]=useState<PlanForm>(defaults);
  const [editing,setEditing]=useState(false);
  const [loading,setLoading]=useState(false);
  const [generating,setGenerating]=useState(false);
  const [updating,setUpdating]=useState<number>();
  const [starting,setStarting]=useState<number>();
  const [testing,setTesting]=useState(false);
  const [explaining,setExplaining]=useState<number>();
  const [explanation,setExplanation]=useState<{itemId:number;data:KnowledgeExplanation}>();
  const [error,setError]=useState("");
  const course=tracks.find(item=>item.id===courseId);

  useEffect(()=>{
    if(!user)return;
    setLoading(true);setError("");
    api.learningPlanTracks().then(data=>{
      setTracks(data);
      setCourseId(current=>data.some(item=>item.id===current)?current:data[0]?.id);
    }).catch(e=>setError(e instanceof Error?e.message:"学习方向加载失败")).finally(()=>setLoading(false));
  },[user?.id]);

  useEffect(()=>{
    if(!user||!courseId){setPlan(undefined);return;}
    setLoading(true);setError("");
    api.currentLearningPlan(courseId).then(data=>{
      setPlan(data);
      if(data)setForm({goal:data.goal,currentLevel:data.currentLevel,daysPerWeek:data.daysPerWeek,minutesPerDay:data.minutesPerDay,durationWeeks:data.durationWeeks});
    }).catch(e=>setError(e instanceof Error?e.message:"学习计划加载失败")).finally(()=>setLoading(false));
  },[user?.id,courseId]);

  const weeklyMinutes=useMemo(()=>form.daysPerWeek*form.minutesPerDay,[form.daysPerWeek,form.minutesPerDay]);
  const plannedKnowledgeCount=useMemo(()=>new Set(plan?.stages.flatMap(stage=>stage.items.map(item=>item.knowledgePointId).filter(Boolean))||[]).size,[plan]);
  async function generate(e:React.FormEvent){
    e.preventDefault();
    if(!user){onNeedAuth();return;}
    if(!courseId)return;
    setGenerating(true);setError("");
    try{const next=await api.generateLearningPlan({courseId,...form});setPlan(next);onPlanChange(next);setEditing(false);}
    catch(e){setError(e instanceof Error?e.message:"AI 计划生成失败");}
    finally{setGenerating(false);}
  }
  async function toggle(item:LearningPlanItem){
    setUpdating(item.id);setError("");
    try{const next=await api.completeLearningPlanItem(item.id,item.status!=="COMPLETED");setPlan(next);onPlanChange(next);}
    catch(e){setError(e instanceof Error?e.message:"任务状态更新失败");}
    finally{setUpdating(undefined);}
  }
  function changeTrack(nextId:number){
    const next=tracks.find(item=>item.id===nextId);
    setCourseId(nextId);setPlan(undefined);setEditing(false);setExplanation(undefined);setError("");
    setForm({...defaults,goal:next?`系统掌握 ${next.name}，并能独立完成常见编程任务`:defaults.goal});
  }
  async function startTask(item:LearningPlanItem){
    if(item.problemId){onOpenProblem(item.problemId);return;}
    setStarting(item.id);setError("");
    try{onOpenGenerated(await api.generatePlanPractice(item.id));}
    catch(e){setError(e instanceof Error?e.message:"AI 专项练习生成失败");}
    finally{setStarting(undefined);}
  }
  async function explain(item:LearningPlanItem){
    if(item.taskType!=="LEARN")return;
    if(explanation?.itemId===item.id){setExplanation(undefined);return;}
    setExplaining(item.id);setError("");
    try{setExplanation({itemId:item.id,data:await api.explainPlanItem(item.id)});}
    catch(e){setError(e instanceof Error?e.message:"AI 知识讲解生成失败");}
    finally{setExplaining(undefined);}
  }
  async function startKnowledgeTest(){
    if(!plan)return;
    setTesting(true);setError("");
    try{onOpenGenerated(await api.generatePlanKnowledgeTest(plan.id));}
    catch(e){setError(e instanceof Error?e.message:"AI 知识测试生成失败");}
    finally{setTesting(false);}
  }

  if(!user)return <div className="plan-auth"><div><BrainCircuit size={38}/><h1>AI 个性化学习计划</h1><p>登录后，AI 会结合课程知识图谱、练习记录和 BKT 掌握度制定滚动计划。</p><button className="primary" onClick={onNeedAuth}>登录后制定计划</button></div></div>;
  if(loading)return <div className="plan-loading"><LoaderCircle className="spin"/><span>正在读取学习计划…</span></div>;

  const showForm=!plan||editing;
  const restriction=activePlan||plan;
  const evidenceTask=(item:LearningPlanItem)=>item.taskType==="PRACTICE"||item.taskType==="ASSESSMENT";
  return <div className="plan-page">
    <aside className="plan-sidebar">
      <span className="plan-mark"><BrainCircuit size={24}/></span>
      <span className="eyebrow">AI LEARNING PLAN</span><h1>让计划跟着你的能力变化</h1>
      <p>AI 负责规划与调整，BKT 负责衡量真实掌握度。两种进度分开展示。</p>
      <div className="plan-principle"><Target size={16}/><div><b>学习目标</b><span>{plan?.goal||form.goal}</span></div></div>
      <div className="plan-principle"><CalendarDays size={16}/><div><b>时间预算</b><span>每周 {plan?plan.daysPerWeek*plan.minutesPerDay:weeklyMinutes} 分钟</span></div></div>
      <div className="plan-principle"><RefreshCw size={16}/><div><b>滚动调整</b><span>重新制定会归档旧计划，不清除 BKT 记录</span></div></div>
    </aside>
    <main className="plan-main">
      <div className="plan-top"><div><span>{course?.language?.toUpperCase()||"学习方向"}</span><h2>{showForm?(plan?"重新制定学习计划":"制定你的第一份计划"):plan.title}</h2></div>{plan&&!showForm&&<button disabled={!plan.canRegenerate} title={plan.canRegenerate?"根据最新学习数据重新制定":`下次可用：${plan.nextRegenerationAt||"7 天后"}`} onClick={()=>setEditing(true)}><RefreshCw size={14}/>{plan.canRegenerate?"重新制定":"7 天内计划已锁定"}</button>}</div>
      {error&&<div className="plan-error"><CircleAlert size={15}/>{error}</div>}
      {showForm?<form className="plan-form" onSubmit={generate}>
        <div className="plan-form-intro"><Sparkles size={20}/><div><b>AI 需要了解你的目标和节奏</b><p>你的目标和学习概况将用于生成计划。请不要填写隐私信息。</p></div></div>
        <label className="track-field"><span>选择学习语言</span><select value={courseId||""} onChange={e=>changeTrack(Number(e.target.value))}>{tracks.map(item=><option value={item.id} key={item.id}>{item.name}</option>)}</select><small>{course?.description}</small></label>
        <label className="goal-field"><span>你想达成什么目标？</span><textarea required maxLength={500} value={form.goal} onChange={e=>setForm({...form,goal:e.target.value})}/></label>
        <div className="plan-fields"><label><span>当前水平</span><select value={form.currentLevel} onChange={e=>setForm({...form,currentLevel:e.target.value})}><option value="BEGINNER">刚入门</option><option value="BASIC">有基础</option><option value="INTERMEDIATE">能独立做基础题</option><option value="ADVANCED">希望进阶提升</option></select></label><label><span>每周学习天数</span><input type="number" min={1} max={7} value={form.daysPerWeek} onChange={e=>setForm({...form,daysPerWeek:Number(e.target.value)})}/></label><label><span>每天时长（分钟）</span><input type="number" min={15} max={300} step={15} value={form.minutesPerDay} onChange={e=>setForm({...form,minutesPerDay:Number(e.target.value)})}/></label><label><span>计划周期（周）</span><input type="number" min={1} max={24} value={form.durationWeeks} onChange={e=>setForm({...form,durationWeeks:Number(e.target.value)})}/></label></div>
        {restriction&&!restriction.canRegenerate&&<div className="plan-cooldown"><Clock3 size={14}/><span>完整计划每 168 小时可重新生成一次。下次可用：{restriction.nextRegenerationAt?.replace("T"," ").slice(0,16)}</span></div>}
        <div className="plan-form-actions">{plan&&<button type="button" onClick={()=>setEditing(false)}>取消</button>}<button className="primary" disabled={generating||!form.goal.trim()||!!(restriction&&!restriction.canRegenerate)}>{generating?<LoaderCircle className="spin" size={16}/>:<Sparkles size={16}/>} {generating?"AI 正在编排计划…":"生成个性化计划"}</button></div>
      </form>:plan&&<>
        <div className="plan-metrics"><Metric title="计划完成度" value={plan.planProgress} detail={`${plan.completedMinutes} / ${plan.totalMinutes} 分钟权重`} accent="cyan"/><Metric title="能力掌握度" value={plan.abilityMastery} detail="由 BKT 证据计算" accent="blue"/><div className="plan-summary"><b>{plan.durationWeeks} 周滚动计划</b><p>{plan.summary}</p><small>{plan.modelName}</small></div><section className="plan-knowledge-test"><span><ClipboardCheck size={21}/></span><div><small>AI KNOWLEDGE TEST</small><b>测试当前知识点</b><p>AI 会结合计划任务完成情况和 BKT 掌握度，优先抽取薄弱、证据不足的知识点生成综合编程题。完成 AI 评测后会自动归档到 Interview，并更新能力掌握度。</p><em>当前计划覆盖 {plannedKnowledgeCount} 个知识点</em></div><button disabled={testing||plannedKnowledgeCount===0} onClick={startKnowledgeTest}>{testing?<LoaderCircle className="spin" size={15}/>:<Sparkles size={15}/>} {testing?"AI 正在生成测试…":"开始 AI 测试"}</button></section></div>
        <div className="plan-timeline">{plan.stages.map(stage=>{const completed=stage.items.filter(i=>i.status==="COMPLETED").length;return <section className="plan-stage" key={stage.id}><div className="stage-rail"><span>{stage.weekNumber}</span><i/></div><div className="stage-body"><div className="stage-head"><div><small>WEEK {stage.weekNumber}</small><h3>{stage.title}</h3><p>{stage.objective}</p></div><em>{completed}/{stage.items.length}</em></div><div className="plan-items">{stage.items.map(item=><Fragment key={item.id}><article className={`${item.status==="COMPLETED"?"done ":""}${item.taskType==="LEARN"?"learnable":""}`}><button className="task-check" title={evidenceTask(item)?"需要通过运行评估完成":"确认任务状态"} disabled={updating===item.id||evidenceTask(item)} onClick={()=>toggle(item)}>{updating===item.id?<LoaderCircle className="spin" size={14}/>:item.status==="COMPLETED"?<Check size={14}/>:null}</button><button className="task-content" disabled={item.taskType!=="LEARN"||explaining===item.id} onClick={()=>explain(item)}><span className="task-meta"><span>{taskLabels[item.taskType]||item.taskType}</span><small><Clock3 size={11}/>{item.estimatedMinutes} 分钟</small>{item.knowledgePointName&&<small>{item.knowledgePointName} 目标 {Math.round(item.targetMastery*100)}%</small>}</span><b>{item.title}</b><span className="task-description">{item.description}</span></button>{item.taskType==="LEARN"?<button className="task-open" disabled={explaining===item.id} onClick={()=>explain(item)}>{explaining===item.id?<LoaderCircle className="spin" size={14}/>:<BookOpenText size={14}/>} {explanation?.itemId===item.id?"收起讲解":"AI 讲解"}</button>:evidenceTask(item)&&<button className="task-open" title={item.status==="COMPLETED"?"重复练习不会再次增加计划进度":undefined} disabled={starting===item.id} onClick={()=>startTask(item)}>{starting===item.id?<LoaderCircle className="spin" size={14}/>:null}{item.status==="COMPLETED"?"继续练习":item.problemId?"去练习":"AI 生成练习"}<ChevronRight size={14}/></button>}</article>{explanation?.itemId===item.id&&<KnowledgeLesson data={explanation.data} onClose={()=>setExplanation(undefined)}/>}</Fragment>)}</div></div></section>})}</div>
      </>}
    </main>
  </div>;
}

function KnowledgeLesson({data,onClose}:{data:KnowledgeExplanation;onClose:()=>void}){
  return <section className="knowledge-lesson"><header><div><span><Sparkles size={13}/> AI 知识讲解</span><h4>{data.title}</h4><p>{data.overview}</p></div><button title="收起讲解" onClick={onClose}><X size={15}/></button></header><div className="lesson-sections">{data.sections.map((section,index)=><article key={`${section.title}-${index}`}><h5>{section.title}</h5><p>{section.content}</p>{section.code&&<pre><code>{section.code}</code></pre>}</article>)}</div><div className="lesson-footer"><div><b>记住这几点</b><ul>{data.keyPoints.map((point,index)=><li key={index}>{point}</li>)}</ul></div><div className="lesson-check"><b>想一想</b><p>{data.checkQuestion}</p></div><small>{data.modelName}</small></div></section>;
}

function Metric({title,value,detail,accent}:{title:string;value:number;detail:string;accent:string}){
  const percent=Math.round(value*100);
  return <div className={`plan-metric ${accent}`}><div className="metric-ring" style={{"--progress":`${percent*3.6}deg`} as React.CSSProperties}><span>{percent}<small>%</small></span></div><div><b>{title}</b><p>{detail}</p></div></div>;
}
