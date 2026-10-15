import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { Bot, ChevronRight, History, LoaderCircle, Maximize2, MessageSquarePlus, Send, Sparkles, X } from "lucide-react";
import { api } from "./api";
import type { CoachConversation, User } from "./types";
import { parseServerDate } from "./date";
import "./ai-history.css";

type ChatMessage = { role:"user"|"assistant"; content:string };
const welcome:ChatMessage={role:"assistant",content:"嗨，我是 CodePath AI 教练。你可以问我代码报错、解题思路或编程知识。"};

export function AiWidget({user,submissionId}:{user:User;submissionId?:number}) {
  const [open,setOpen]=useState(false);
  const [historyOpen,setHistoryOpen]=useState(false);
  const [historyLoading,setHistoryLoading]=useState(false);
  const [conversations,setConversations]=useState<CoachConversation[]>([]);
  const [conversationId,setConversationId]=useState<number>();
  const [conversationTitle,setConversationTitle]=useState("新对话");
  const [messages,setMessages]=useState<ChatMessage[]>([welcome]);
  const [input,setInput]=useState("");
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState("");
  const endRef=useRef<HTMLDivElement>(null);

  useEffect(()=>{if(open&&!historyOpen)endRef.current?.scrollIntoView({behavior:"smooth"});},[messages,loading,open,historyOpen]);
  useEffect(()=>{if(open)void loadHistory();},[open,user.id]);

  async function loadHistory(){
    setHistoryLoading(true);setError("");
    try{setConversations(await api.coachConversations());}
    catch(e){setError(e instanceof Error?e.message:"历史对话加载失败");}
    finally{setHistoryLoading(false);}
  }
  function newConversation(){
    setConversationId(undefined);setConversationTitle("新对话");setMessages([welcome]);
    setInput("");setError("");setHistoryOpen(false);
  }
  async function openConversation(item:CoachConversation){
    if(loading)return;
    setHistoryLoading(true);setError("");
    try{
      const saved=await api.coachMessages(item.id);
      setConversationId(item.id);setConversationTitle(item.title);
      setMessages(saved.length?saved.map(message=>({role:message.role,content:message.content})):[welcome]);
      setHistoryOpen(false);
    }catch(e){setError(e instanceof Error?e.message:"对话内容加载失败");}
    finally{setHistoryLoading(false);}
  }
  async function send(e?:FormEvent){
    e?.preventDefault();
    const content=input.trim();
    if(!content||loading)return;
    setInput("");setError("");setMessages(old=>[...old,{role:"user",content}]);setLoading(true);
    try{
      let answer:{content:string;modelName:string};
      let id=conversationId;
      if(!id){
        const title=content.length>22?`${content.slice(0,22)}…`:content;
        const created=await api.createCoachConversation(submissionId,title||"AI 小助手");
        id=created.id;setConversationId(id);setConversationTitle(title||"AI 小助手");
      }
      answer=await api.sendCoachMessage(id,content);
      void loadHistory();
      setMessages(old=>[...old,{role:"assistant",content:answer.content}]);
    }catch(e){setError(e instanceof Error?e.message:"暂时无法联系 AI 教练");}
    finally{setLoading(false);}
  }
  function onKeyDown(e:KeyboardEvent<HTMLTextAreaElement>){if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();void send();}}
  return <div className={`ai-widget ${open?"open":""}`}>
    {open&&<section className="ai-chatbox" aria-label="AI 编程教练">
      <header className="ai-chat-head"><span><Bot size={20}/></span><div><b>{historyOpen?"历史对话":conversationTitle}</b><small><i/>{loading?"正在思考":"CodePath AI 教练"}</small></div><nav><button className={historyOpen?"active":""} title="历史对话" onClick={()=>{setHistoryOpen(value=>!value);if(!historyOpen)void loadHistory();}}><History size={16}/></button><button title="新建对话" onClick={newConversation}><MessageSquarePlus size={16}/></button></nav><button title="关闭" onClick={()=>setOpen(false)}><X size={17}/></button></header>
      <div className="ai-chat-context"><Sparkles size={13}/>{historyOpen?`已保存 ${conversations.length} 个对话`:submissionId?"已结合当前提交与错误证据":"可以询问编程知识和解题思路"}</div>
      {historyOpen?<div className="ai-history-list">{historyLoading?<div className="ai-history-empty"><LoaderCircle className="spin" size={20}/><span>正在读取历史对话…</span></div>:conversations.length?conversations.map(item=><button className={item.id===conversationId?"current":""} key={item.id} onClick={()=>openConversation(item)}><History size={15}/><span><b>{item.title}</b><small>{item.preview}</small><em>{item.messageCount} 条消息 · {formatDate(item.updatedAt)}</em></span><ChevronRight size={15}/></button>):<div className="ai-history-empty"><History size={24}/><b>还没有历史对话</b><span>发出第一条消息后，会自动保存在这里。</span></div>}{error&&<div className="ai-chat-error">{error}</div>}</div>:<div className="ai-chat-messages">
        {messages.map((message,index)=><div key={index} className={`ai-message ${message.role}`}><span>{message.role==="assistant"?<Bot size={14}/>:user.displayName.slice(0,1)}</span><p>{message.content}</p></div>)}
        {loading&&<div className="ai-message assistant"><span><Bot size={14}/></span><p className="typing"><i/><i/><i/></p></div>}
        {error&&<div className="ai-chat-error">{error}</div>}
        <div ref={endRef}/>
      </div>}
      {historyOpen?<div className="ai-history-action"><button onClick={newConversation}><MessageSquarePlus size={15}/>开始新对话</button></div>:<form className="ai-chat-input" onSubmit={send}><textarea value={input} onChange={e=>setInput(e.target.value)} onKeyDown={onKeyDown} maxLength={4000} placeholder="问问代码错在哪…"/><button disabled={loading||!input.trim()} title="发送"><Send size={16}/></button></form>}
      <div className="ai-chat-foot"><Maximize2 size={11}/><span>Enter 发送 · 对话已保存</span></div>
    </section>}
    <button className="ai-orb" onClick={()=>setOpen(value=>!value)} aria-label={open?"关闭 AI 助手":"打开 AI 助手"}><span className="ai-orb-ring"/><span className="ai-orb-icon">{open?<X size={22}/>:<Bot size={24}/>}</span>{!open&&<i/>}</button>
  </div>;
}

function formatDate(value:string){
  const date=parseServerDate(value);
  if(Number.isNaN(date.getTime()))return "最近更新";
  return new Intl.DateTimeFormat("zh-CN",{month:"numeric",day:"numeric",hour:"2-digit",minute:"2-digit"}).format(date);
}
