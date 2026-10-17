import { useEffect, useRef, useState } from "react";
import { api, discussionSocketUrl } from "./api";
import type { AiGeneratedPractice, CoachMessage, DiscussionPost, GeneratedQuestionSummary, Problem, User } from "./types";
import { parseServerDate } from "./date";
import { ArrowLeft, Bot, BrainCircuit, ChevronRight, CircleAlert, Eye, LoaderCircle, MessageCircle, Plus, Search, Send, Sparkles, Target, X } from "lucide-react";

export function DiscussPage({user,problems,onNeedAuth}:{user:User|null;problems:Problem[];onNeedAuth:()=>void}){
  const [posts,setPosts]=useState<DiscussionPost[]>([]),[selected,setSelected]=useState<DiscussionPost>(),[loading,setLoading]=useState(true);
  const [q,setQ]=useState(""),[composer,setComposer]=useState(false),[form,setForm]=useState({title:"",content:"",tags:"",problemId:""}),[reply,setReply]=useState(""),[error,setError]=useState(""),[live,setLive]=useState(false);
  const queryRef=useRef(q),selectedRef=useRef(selected);
  useEffect(()=>{queryRef.current=q},[q]);
  useEffect(()=>{selectedRef.current=selected},[selected]);
  async function load(search=""){setLoading(true);try{setPosts(await api.discussions(search));}catch(e){setError((e as Error).message)}finally{setLoading(false)}}
  useEffect(()=>{load()},[]);
  useEffect(()=>{
    let socket:WebSocket|undefined,reconnect:number|undefined,stopped=false,delay=1000;
    const connect=()=>{
      if(stopped)return;
      socket=new WebSocket(discussionSocketUrl());
      socket.onopen=()=>{delay=1000;setLive(true)};
      socket.onmessage=event=>{
        let update:{type?:string;postId?:number}={};
        try{update=JSON.parse(event.data)}catch{return}
        api.discussions(queryRef.current).then(setPosts).catch(()=>undefined);
        const current=selectedRef.current;
        if(current&&update.postId===current.id)api.discussion(current.id,false).then(setSelected).catch(()=>undefined);
      };
      socket.onclose=()=>{setLive(false);if(!stopped){reconnect=window.setTimeout(connect,delay);delay=Math.min(delay*2,10000)}};
      socket.onerror=()=>socket?.close();
    };
    connect();
    return()=>{stopped=true;setLive(false);if(reconnect)window.clearTimeout(reconnect);socket?.close()};
  },[]);
  async function open(post:DiscussionPost){setSelected(await api.discussion(post.id));}
  async function publish(e:React.FormEvent){e.preventDefault();if(!user){onNeedAuth();return}try{const r=await api.createDiscussion(form.title,form.content,form.tags,form.problemId?Number(form.problemId):undefined);setComposer(false);setForm({title:"",content:"",tags:"",problemId:""});await load();open({id:r.id} as DiscussionPost)}catch(e){setError((e as Error).message)}}
  async function sendReply(){if(!user){onNeedAuth();return}if(!selected||!reply.trim())return;try{await api.comment(selected.id,reply);setReply("");setSelected(await api.discussion(selected.id,false));}catch(e){setError((e as Error).message)}}
  return <div className="feature-page discuss-page"><div className="feature-hero"><div><span className="eyebrow"><MessageCircle size={14}/> DISCUSS</span><h1>和学习者一起，把问题说清楚</h1><p>分享解题思路、讨论错误根因，用解释来验证真正的理解。</p></div><button className="primary create-post" onClick={()=>user?setComposer(true):onNeedAuth()}><Plus size={17}/>发起讨论</button></div>
    <div className="discuss-layout"><aside className="discussion-filter"><b>讨论广场</b><button className="active"># 全部讨论</button><button># 解题思路</button><button># 错误诊断</button><button># 学习方法</button><div className="discuss-note"><Sparkles size={17}/><b>讨论准则</b><p>说出你已尝试过什么，比只要一个答案更有价值。</p></div></aside>
      <main className="discussion-feed"><div className="feed-tools"><div><Search size={16}/><input value={q} onChange={e=>setQ(e.target.value)} onKeyDown={e=>e.key==="Enter"&&load(q)} placeholder="搜索讨论、标签或题目…"/></div><span className="discussion-count"><i className={live?"live":"offline"}/>{live?"实时同步":"正在重连"} · {posts.length} 条讨论</span></div>
        {loading?<div className="feature-loading"><LoaderCircle className="spin"/>加载讨论…</div>:posts.length===0?<div className="feature-empty"><MessageCircle/><b>还没有讨论</b><p>成为第一个发起问题的人。</p></div>:posts.map(post=><button className="post-card" key={post.id} onClick={()=>open(post)}><span className="post-avatar">{post.author_name?.slice(0,1)}</span><div className="post-body"><div className="post-meta"><b>{post.author_name}</b><em>{post.author_role}</em><span>{timeAgo(post.created_at)}</span></div><h2>{post.title}</h2><p>{post.content}</p><div className="post-footer"><span className="tags">{post.tags?.split(",").filter(Boolean).map(t=><i key={t}>#{t.trim()}</i>)}</span><span><Eye size={13}/>{post.view_count}</span><span><MessageCircle size={13}/>{post.comment_count||0}</span></div></div><ChevronRight size={18}/></button>)}</main>
    </div>
    {composer&&<div className="modal-backdrop" onMouseDown={()=>setComposer(false)}><form className="compose-card" onMouseDown={e=>e.stopPropagation()} onSubmit={publish}><button type="button" className="close-btn" onClick={()=>setComposer(false)}><X size={18}/></button><span className="eyebrow">NEW DISCUSSION</span><h2>发起一个有价值的讨论</h2><label>标题<input required maxLength={180} value={form.title} onChange={e=>setForm({...form,title:e.target.value})} placeholder="简洁说清你想讨论的问题"/></label><label>关联题目<select value={form.problemId} onChange={e=>setForm({...form,problemId:e.target.value})}><option value="">不关联题目</option>{problems.map(p=><option value={p.id} key={p.id}>{p.title}</option>)}</select></label><label>正文<textarea required maxLength={20000} value={form.content} onChange={e=>setForm({...form,content:e.target.value})} placeholder="描述你的思路、已尝试的方法和具体疑问…"/></label><label>标签<input value={form.tags} onChange={e=>setForm({...form,tags:e.target.value})} placeholder="Python, 边界条件, 循环（逗号分隔）"/></label><button className="primary wide"><Send size={16}/>发布讨论</button></form></div>}
    {selected&&<div className="discussion-drawer"><button className="drawer-back" onClick={()=>setSelected(undefined)}><ArrowLeft size={16}/>返回讨论</button><div className="drawer-content"><span className="eyebrow">DISCUSSION #{selected.id}</span><h1>{selected.title}</h1><div className="post-meta"><b>{selected.author_name}</b><em>{selected.author_role}</em><span>{parseServerDate(selected.created_at).toLocaleString("zh-CN")}</span></div><p className="post-full">{selected.content}</p>{selected.problem_title&&<div className="linked-problem"><Target size={15}/>关联题目：{selected.problem_title}</div>}<div className="reply-title"><b>{selected.comments?.length||0} 条回复</b></div>{selected.comments?.map(c=><div className="reply-card" key={c.id}><span className="post-avatar">{c.author_name.slice(0,1)}</span><div><div className="post-meta"><b>{c.author_name}</b><em>{c.author_role}</em><span>{timeAgo(c.created_at)}</span></div><p>{c.content}</p></div></div>)}<div className="reply-box"><textarea value={reply} onChange={e=>setReply(e.target.value)} placeholder={user?"写下你的思考…":"登录后参与讨论"}/><button className="primary" onClick={sendReply}><Send size={15}/>回复</button></div></div></div>}
    {error&&<div className="toast"><CircleAlert size={16}/>{error}<button onClick={()=>setError("")}><X size={14}/></button></div>}
  </div>
}

export function InterviewPage({user,onNeedAuth,onOpenGenerated}:{user:User|null;onNeedAuth:()=>void;onOpenGenerated:(item:AiGeneratedPractice)=>void}){
  const [questions,setQuestions]=useState<GeneratedQuestionSummary[]>([]),[loading,setLoading]=useState(false),[opening,setOpening]=useState<number>(),[error,setError]=useState("");
  const [conversationId,setConversationId]=useState<number>(),[messages,setMessages]=useState<CoachMessage[]>([]),[answer,setAnswer]=useState(""),[starting,setStarting]=useState(false),[sending,setSending]=useState(false);
  const chatEndRef=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    if(!user)return;
    setLoading(true);setError("");
    Promise.all([api.generatedQuestions(),api.coachConversations()]).then(async([nextQuestions,conversations])=>{
      setQuestions(nextQuestions);
      const latest=conversations.find(item=>item.title==="AI 模拟面试");
      if(latest){setConversationId(latest.id);setMessages(await api.coachMessages(latest.id))}
      else{setConversationId(undefined);setMessages([])}
    }).catch(e=>setError(e instanceof Error?e.message:"面试官加载失败")).finally(()=>setLoading(false));
  },[user?.id]);
  useEffect(()=>{chatEndRef.current?.scrollIntoView({behavior:"smooth",block:"nearest"})},[messages,sending]);
  async function openQuestion(id:number){setOpening(id);setError("");try{onOpenGenerated(await api.generatedPractice(id))}catch(e){setError(e instanceof Error?e.message:"题目打开失败")}finally{setOpening(undefined)}}
  async function startInterview(){
    setStarting(true);setError("");setMessages([]);
    try{
      const conversation=await api.createCoachConversation(undefined,"AI 模拟面试");
      setConversationId(conversation.id);
      const response=await api.sendCoachMessage(conversation.id,"请开始一场 Python 编程技术面试。请先简短介绍面试规则，然后只问第一个问题，等待我回答。");
      setMessages([{id:Date.now(),role:"assistant",content:response.content,modelName:response.modelName,createdAt:new Date().toISOString()}]);
    }catch(e){setConversationId(undefined);setError(e instanceof Error?e.message:"面试启动失败")}finally{setStarting(false)}
  }
  async function sendAnswer(content=answer){
    const value=content.trim();if(!conversationId||!value||sending)return;
    setMessages(current=>[...current,{id:-Date.now(),role:"user",content:value,createdAt:new Date().toISOString()}]);setAnswer("");setSending(true);setError("");
    try{
      const response=await api.sendCoachMessage(conversationId,value);
      setMessages(current=>[...current,{id:Date.now(),role:"assistant",content:response.content,modelName:response.modelName,createdAt:new Date().toISOString()}]);
    }catch(e){setError(e instanceof Error?e.message:"面试官暂时无法回复")}finally{setSending(false)}
  }
  if(!user)return <div className="feature-page locked-page"><div><Bot size={42}/><h1>AI 模拟面试官</h1><p>登录后进行技术问答、追问和能力总结。</p><button className="primary" onClick={onNeedAuth}>登录开始</button></div></div>;
  const visibleMessages=messages.filter(message=>!(message.role==="user"&&message.content.startsWith("请开始一场 Python 编程技术面试")));
  return <div className="feature-page interview-page"><div className="feature-hero interview-hero"><div><span className="eyebrow"><Bot size={14}/> AI INTERVIEWER</span><h1>{user.displayName}，准备好开始技术面试了吗？</h1><p>AI 面试官会逐题提问、针对回答追问，并在结束时给出具体反馈。</p></div><button className="primary" disabled={starting} onClick={startInterview}>{starting?<LoaderCircle className="spin" size={17}/>:<Plus size={17}/>} {conversationId?"开始新面试":"开始面试"}</button></div>
    <section className="interviewer-section"><aside className="interviewer-guide"><span className="interviewer-avatar"><BrainCircuit size={31}/></span><small>CODEPATH INTERVIEWER</small><h2>AI 技术面试官</h2><p>一次只问一题，会根据你的回答动态调整难度。</p><ul><li>基础概念与 Python 语法</li><li>算法思路与复杂度</li><li>调试、边界与工程思维</li></ul>{conversationId&&visibleMessages.length>0&&<button onClick={()=>sendAnswer("请结束本次面试，根据我的所有回答，总结优势、待提升点和下一步建议。")} disabled={sending}>结束并生成总结</button>}</aside>
      <div className="interviewer-chat"><header><div><span className={conversationId?"online":""}/><b>{conversationId?"面试进行中":"尚未开始"}</b></div><small>回答不需要完美，请展示你的思考过程</small></header><div className="interview-messages">{loading?<div className="interview-empty"><LoaderCircle className="spin"/>正在加载面试会话…</div>:visibleMessages.length===0?<div className="interview-empty"><Bot/><b>这里是你的模拟面试室</b><p>点击“开始面试”，AI 面试官会给出第一个问题。</p></div>:visibleMessages.map(message=><article className={`interview-message ${message.role}`} key={message.id}><span>{message.role==="assistant"?<Bot size={17}/>:user.displayName.slice(0,1)}</span><div><small>{message.role==="assistant"?"AI 面试官":"我"}</small><p>{message.content}</p></div></article>)}{sending&&<article className="interview-message assistant"><span><Bot size={17}/></span><div><small>AI 面试官</small><p className="interview-typing"><i/><i/><i/></p></div></article>}<div ref={chatEndRef}/></div><div className="interview-composer"><textarea value={answer} onChange={e=>setAnswer(e.target.value)} onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();sendAnswer()}}} disabled={!conversationId||starting} placeholder={conversationId?"说出你的结论和思考过程…":"开始面试后在这里回答"}/><button onClick={()=>sendAnswer()} disabled={!conversationId||!answer.trim()||sending}><Send size={17}/></button></div></div>
    </section>
    <section className="generated-question-section"><div className="section-head"><div><b>AI 生成题</b><span>自动同步最近 10 道，点击即可进入 Practice 作答</span></div><em>{questions.length}/10</em></div>{loading?<div className="feature-loading"><LoaderCircle className="spin"/>正在同步 AI 题目…</div>:questions.length===0?<div className="feature-empty compact"><Sparkles/><b>还没有 AI 生成题</b></div>:<div className="generated-question-grid">{questions.map(question=><button key={question.id} onClick={()=>openQuestion(question.id)} disabled={opening===question.id}><span><Sparkles size={15}/></span><div><small>{question.source_type==="KNOWLEDGE_TEST"?"计划知识测试":"AI 专项练习"}</small><b>{question.title}</b><p>{question.language.toUpperCase()} · 难度 {question.difficulty} · {question.attempt_count?`已评测 ${question.attempt_count} 次`:"待作答"}</p></div>{opening===question.id?<LoaderCircle className="spin" size={15}/>:<ChevronRight size={15}/>}</button>)}</div>}</section>
    {error&&<div className="toast"><CircleAlert size={16}/>{error}<button onClick={()=>setError("")}><X size={14}/></button></div>}
  </div>
}

function timeAgo(v:string){const date=parseServerDate(v),s=Math.max(0,(Date.now()-date.getTime())/1000);if(s<60)return"刚刚";if(s<3600)return`${Math.floor(s/60)} 分钟前`;if(s<86400)return`${Math.floor(s/3600)} 小时前`;return date.toLocaleDateString("zh-CN")}
