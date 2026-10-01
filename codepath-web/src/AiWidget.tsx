import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { Bot, Maximize2, Send, Sparkles, X } from "lucide-react";
import { api } from "./api";
import type { User } from "./types";

type ChatMessage = { role:"user"|"assistant"; content:string };

export function AiWidget({user,onNeedAuth,submissionId}:{user:User|null;onNeedAuth:()=>void;submissionId?:number}) {
  const [open,setOpen]=useState(false);
  const [conversationId,setConversationId]=useState<number>();
  const [messages,setMessages]=useState<ChatMessage[]>([{role:"assistant",content:"嗨，我是 CodePath AI 教练。你可以问我代码报错、解题思路或编程知识。"}]);
  const [input,setInput]=useState("");
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState("");
  const endRef=useRef<HTMLDivElement>(null);
  useEffect(()=>{if(open)endRef.current?.scrollIntoView({behavior:"smooth"});},[messages,loading,open]);
  async function send(e?:FormEvent){
    e?.preventDefault();
    const content=input.trim();
    if(!content||loading)return;
    setInput("");setError("");setMessages(old=>[...old,{role:"user",content}]);setLoading(true);
    try{
      let answer:{content:string;modelName:string};
      if(user){
        let id=conversationId;
        if(!id){const created=await api.createCoachConversation(submissionId,"AI 小助手");id=created.id;setConversationId(id);}
        answer=await api.sendCoachMessage(id,content);
      }else{
        answer=await api.guestCoachMessage([...messages,{role:"user" as const,content}].slice(1));
      }
      setMessages(old=>[...old,{role:"assistant",content:answer.content}]);
    }catch(e){setError(e instanceof Error?e.message:"暂时无法联系 AI 教练");}
    finally{setLoading(false);}
  }
  function onKeyDown(e:KeyboardEvent<HTMLTextAreaElement>){if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();void send();}}
  return <div className={`ai-widget ${open?"open":""}`}>
    {open&&<section className="ai-chatbox" aria-label="AI 编程教练">
      <header className="ai-chat-head"><span><Bot size={20}/></span><div><b>CodePath AI</b><small><i/>{loading?"正在思考":"在线教练"}</small></div><button title="关闭" onClick={()=>setOpen(false)}><X size={17}/></button></header>
      <div className="ai-chat-context"><Sparkles size={13}/>{submissionId?"已结合当前提交与错误证据":"可以询问编程知识和解题思路"}</div>
      <div className="ai-chat-messages">
        {messages.map((message,index)=><div key={index} className={`ai-message ${message.role}`}><span>{message.role==="assistant"?<Bot size={14}/>:user?.displayName.slice(0,1)}</span><p>{message.content}</p></div>)}
        {loading&&<div className="ai-message assistant"><span><Bot size={14}/></span><p className="typing"><i/><i/><i/></p></div>}
        {error&&<div className="ai-chat-error">{error}</div>}
        <div ref={endRef}/>
      </div>
      <form className="ai-chat-input" onSubmit={send}><textarea value={input} onChange={e=>setInput(e.target.value)} onKeyDown={onKeyDown} maxLength={4000} placeholder="问问代码错在哪…"/><button disabled={loading||!input.trim()} title="发送"><Send size={16}/></button></form>
      <div className="ai-chat-foot"><Maximize2 size={11}/><span>{user?"Enter 发送 · 对话已保存":"游客可提问 · 登录后可保存对话"}</span>{!user&&<button onClick={onNeedAuth}>登录</button>}</div>
    </section>}
    <button className="ai-orb" onClick={()=>setOpen(value=>!value)} aria-label={open?"关闭 AI 助手":"打开 AI 助手"}><span className="ai-orb-ring"/><span className="ai-orb-icon">{open?<X size={22}/>:<Bot size={24}/>}</span>{!open&&<i/>}</button>
  </div>;
}
