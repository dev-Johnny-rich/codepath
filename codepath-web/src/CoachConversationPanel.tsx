import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { Bot, ChevronRight, History, LoaderCircle, MessageSquarePlus, Send, Sparkles, UserRound } from "lucide-react";
import { api } from "./api";
import type { CoachConversation, User } from "./types";

type ChatMessage = { role:"user"|"assistant"; content:string };

export function CoachConversationPanel({user,submissionId,problemTitle,diagnosis}:{user:User;submissionId?:number;problemTitle:string;diagnosis?:string}) {
  const welcome:ChatMessage={role:"assistant",content:diagnosis
    ? `我已经看到你在「${problemTitle}」中的运行证据：${diagnosis}\n\n你可以把你的思路告诉我，我们一起定位问题。`
    : `我们正在练习「${problemTitle}」。你可以直接描述思路、粘贴报错，或者问我为什么这段代码没有按预期运行。`};
  const [messages,setMessages]=useState<ChatMessage[]>([welcome]);
  const [input,setInput]=useState("");
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState("");
  const [conversationId,setConversationId]=useState<number>();
  const [historyOpen,setHistoryOpen]=useState(false);
  const [historyLoading,setHistoryLoading]=useState(false);
  const [conversations,setConversations]=useState<CoachConversation[]>([]);
  const endRef=useRef<HTMLDivElement>(null);

  useEffect(()=>{if(!historyOpen)endRef.current?.scrollIntoView({behavior:"smooth"});},[messages,loading,historyOpen]);

  async function loadHistory(){
    setHistoryLoading(true);setError("");
    try{setConversations(await api.coachConversations());}
    catch(e){setError(e instanceof Error?e.message:"历史对话加载失败");}
    finally{setHistoryLoading(false);}
  }
  function newConversation(){
    setConversationId(undefined);setMessages([welcome]);setInput("");setError("");setHistoryOpen(false);
  }
  async function openConversation(item:CoachConversation){
    setHistoryLoading(true);setError("");
    try{
      const saved=await api.coachMessages(item.id);
      setConversationId(item.id);
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
      let id=conversationId;
      if(!id){
        const title=`${problemTitle} · ${content.length>16?`${content.slice(0,16)}…`:content}`;
        const created=await api.createCoachConversation(submissionId,title);
        id=created.id;setConversationId(id);
      }
      const answer=await api.sendCoachMessage(id,content);
      setMessages(old=>[...old,{role:"assistant",content:answer.content}]);
    }catch(e){setError(e instanceof Error?e.message:"暂时无法联系 AI 教练");}
    finally{setLoading(false);}
  }
  function onKeyDown(e:KeyboardEvent<HTMLTextAreaElement>){if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();void send();}}
  const quickPrompts=["帮我梳理解题思路","检查我的代码逻辑","解释当前报错"];

  return <section className="coach-conversation" aria-label="AI 教练对话">
    <header className="coach-conversation-head"><span><Bot size={18}/></span><div><b>AI 教练</b><small><i/>{loading?"正在思考":"在线 · 结合当前题目"}</small></div><nav><button className={historyOpen?"active":""} onClick={()=>{setHistoryOpen(value=>!value);if(!historyOpen)void loadHistory();}} title="历史对话"><History size={15}/></button><button onClick={newConversation} title="新建对话"><MessageSquarePlus size={15}/></button></nav></header>
    <div className="coach-context"><Sparkles size={13}/><span>{submissionId?"已结合当前提交与运行证据":`当前题目 · ${problemTitle}`}</span></div>
    {historyOpen?<div className="coach-conversation-history">{historyLoading?<div className="coach-history-empty"><LoaderCircle className="spin" size={20}/>正在加载…</div>:conversations.length?conversations.map(item=><button key={item.id} onClick={()=>openConversation(item)}><History size={14}/><span><b>{item.title}</b><small>{item.preview||`${item.messageCount} 条消息`}</small></span><ChevronRight size={14}/></button>):<div className="coach-history-empty"><History size={22}/><b>还没有历史对话</b><span>发送第一条消息后会自动保存。</span></div>}</div>:<div className="coach-conversation-messages">
      {messages.map((message,index)=><div className={`coach-bubble ${message.role}`} key={index}><span>{message.role==="assistant"?<Bot size={14}/>:<UserRound size={14}/>}</span><p>{message.content}</p></div>)}
      {loading&&<div className="coach-bubble assistant"><span><Bot size={14}/></span><p className="coach-typing"><i/><i/><i/></p></div>}
      {error&&<div className="coach-conversation-error">{error}</div>}
      <div ref={endRef}/>
    </div>}
    {!historyOpen&&<><div className="coach-quick-prompts">{quickPrompts.map(prompt=><button key={prompt} onClick={()=>setInput(prompt)}>{prompt}</button>)}</div><form className="coach-conversation-input" onSubmit={send}><textarea value={input} onChange={e=>setInput(e.target.value)} onKeyDown={onKeyDown} maxLength={4000} placeholder="输入你的问题或思路…"/><button disabled={loading||!input.trim()} title="发送"><Send size={16}/></button></form></>}
    {historyOpen&&<button className="coach-new-chat" onClick={newConversation}><MessageSquarePlus size={15}/>开始新对话</button>}
  </section>;
}
