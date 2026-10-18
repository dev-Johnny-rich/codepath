import { useEffect, useRef, useState } from "react";
import { Clock3, Pause, Play, RotateCcw, TimerReset, X } from "lucide-react";

type SavedTimer={duration:number;remaining:number;running:boolean;endsAt?:number};
const DEFAULT_DURATION=25*60;

function timerKey(userId:number){return `codepath-focus-timer:${userId}`;}
function storedTimer(userId:number):SavedTimer {
  try {
    const value=JSON.parse(localStorage.getItem(timerKey(userId))||"null") as SavedTimer|null;
    if(value&&Number.isFinite(value.duration)&&Number.isFinite(value.remaining)){
      if(value.running&&value.endsAt){
        const remaining=Math.max(0,Math.ceil((value.endsAt-Date.now())/1000));
        return {...value,remaining,running:remaining>0};
      }
      return {...value,running:false};
    }
  } catch { /* Ignore invalid saved timer state. */ }
  return {duration:DEFAULT_DURATION,remaining:DEFAULT_DURATION,running:false};
}
function formatTime(seconds:number){
  const hours=Math.floor(seconds/3600),minutes=Math.floor((seconds%3600)/60),secs=seconds%60;
  return hours>0?`${String(hours).padStart(2,"0")}:${String(minutes).padStart(2,"0")}:${String(secs).padStart(2,"0")}`:`${String(minutes).padStart(2,"0")}:${String(secs).padStart(2,"0")}`;
}
function createAudioContext(){
  const AudioContextClass=window.AudioContext||(window as typeof window&{webkitAudioContext?:typeof AudioContext}).webkitAudioContext;
  return AudioContextClass?new AudioContextClass():null;
}
async function playCompleteTone(context:AudioContext|null){
  if(!context)return;
  try {
    if(context.state==="suspended")await context.resume();
    const start=context.currentTime+.03;
    // Two bell-like notes: a bright “ding”, followed by a lower “dong”.
    [[880,0,.52],[659.25,.46,.72]].forEach(([frequency,delay,length])=>{
      [1,2.01].forEach((multiple,index)=>{
        const oscillator=context.createOscillator(),gain=context.createGain();
        oscillator.type=index===0?"sine":"triangle";
        oscillator.frequency.setValueAtTime(frequency*multiple,start+delay);
        gain.gain.setValueAtTime(index===0?.0001:.00005,start+delay);
        gain.gain.exponentialRampToValueAtTime(index===0?.2:.035,start+delay+.018);
        gain.gain.exponentialRampToValueAtTime(.0001,start+delay+length);
        oscillator.connect(gain);gain.connect(context.destination);
        oscillator.start(start+delay);oscillator.stop(start+delay+length+.02);
      });
    });
  } catch { /* Audio feedback is optional. */ }
}

export function FocusTimer({userId}:{userId:number}){
  const initial=useRef(storedTimer(userId));
  const [open,setOpen]=useState(false);
  const [duration,setDuration]=useState(initial.current.duration);
  const [remaining,setRemaining]=useState(initial.current.remaining);
  const [running,setRunning]=useState(initial.current.running);
  const [endsAt,setEndsAt]=useState(initial.current.endsAt);
  const [hours,setHours]=useState(Math.floor(initial.current.duration/3600));
  const [minutes,setMinutes]=useState(Math.floor((initial.current.duration%3600)/60));
  const [completed,setCompleted]=useState(initial.current.remaining===0);
  const rootRef=useRef<HTMLDivElement>(null);
  const audioContextRef=useRef<AudioContext|null>(null);
  const completionPlayed=useRef(false);

  useEffect(()=>{
    const close=(event:MouseEvent)=>{if(!rootRef.current?.contains(event.target as Node))setOpen(false);};
    document.addEventListener("mousedown",close);return()=>document.removeEventListener("mousedown",close);
  },[]);
  useEffect(()=>()=>{void audioContextRef.current?.close();},[]);
  useEffect(()=>{
    if(!running||!endsAt)return;
    const update=()=>{
      const next=Math.max(0,Math.ceil((endsAt-Date.now())/1000));setRemaining(next);
      if(next===0){setRunning(false);setCompleted(true);if(!completionPlayed.current){completionPlayed.current=true;void playCompleteTone(audioContextRef.current);}}
    };
    update();const timer=window.setInterval(update,500);return()=>window.clearInterval(timer);
  },[running,endsAt]);
  useEffect(()=>{
    localStorage.setItem(timerKey(userId),JSON.stringify({duration,remaining,running,endsAt:running?endsAt:undefined} satisfies SavedTimer));
  },[userId,duration,remaining,running,endsAt]);

  function choose(seconds:number){
    if(running)return;setDuration(seconds);setRemaining(seconds);setHours(Math.floor(seconds/3600));setMinutes(Math.floor((seconds%3600)/60));setCompleted(false);completionPlayed.current=false;
  }
  function applyCustom(nextHours:number,nextMinutes:number){
    const safeHours=Math.max(0,Math.min(12,nextHours||0)),safeMinutes=Math.max(0,Math.min(59,nextMinutes||0));
    setHours(safeHours);setMinutes(safeMinutes);
    if(!running){const seconds=Math.max(60,safeHours*3600+safeMinutes*60);setDuration(seconds);setRemaining(seconds);setCompleted(false);completionPlayed.current=false;}
  }
  function toggle(){
    if(running){setRunning(false);setEndsAt(undefined);return;}
    if(!audioContextRef.current)audioContextRef.current=createAudioContext();
    void audioContextRef.current?.resume();
    const next=remaining>0?remaining:duration;setRemaining(next);setEndsAt(Date.now()+next*1000);setRunning(true);setCompleted(false);completionPlayed.current=false;
  }
  function reset(){setRunning(false);setEndsAt(undefined);setRemaining(duration);setCompleted(false);completionPlayed.current=false;}
  const progress=duration>0?Math.max(0,Math.min(1,remaining/duration)):0;

  return <div className={`focus-timer${running?" running":""}${completed?" completed":""}`} ref={rootRef}>
    <button className="focus-timer-trigger" onClick={()=>setOpen(value=>!value)} aria-expanded={open} aria-label="专注计时器"><Clock3 size={17}/>{(running||remaining!==duration)&&<span>{completed?"完成":formatTime(remaining)}</span>}</button>
    {open&&<section className="focus-timer-popover" aria-label="番茄钟">
      <header><span><TimerReset size={18}/></span><div><b>专注计时</b><small>{running?"保持节奏，专注当下":completed?"本轮专注已完成":"设置你的学习时长"}</small></div><button onClick={()=>setOpen(false)} title="关闭"><X size={16}/></button></header>
      <div className="focus-clock" style={{background:`conic-gradient(#178b78 ${progress*360}deg,#e5ded3 0deg)`}}><div><strong>{formatTime(remaining)}</strong><span>{running?"专注中":completed?"已完成":"准备开始"}</span></div></div>
      <div className="focus-presets">{[[20,"20 分钟"],[25,"25 分钟"],[45,"45 分钟"],[60,"1 小时"]].map(([value,label])=><button key={value} disabled={running} className={duration===Number(value)*60?"active":""} onClick={()=>choose(Number(value)*60)}>{label}</button>)}</div>
      <div className="focus-custom"><label><span>小时</span><input type="number" min="0" max="12" disabled={running} value={hours} onChange={e=>applyCustom(Number(e.target.value),minutes)}/></label><i>:</i><label><span>分钟</span><input type="number" min="0" max="59" disabled={running} value={minutes} onChange={e=>applyCustom(hours,Number(e.target.value))}/></label></div>
      <div className="focus-actions"><button className="focus-reset" onClick={reset}><RotateCcw size={15}/>重置</button><button className="focus-primary" onClick={toggle}>{running?<><Pause size={16}/>暂停</>:<><Play size={16}/>{remaining<duration&&remaining>0?"继续":"开始专注"}</>}</button></div>
    </section>}
  </div>;
}
