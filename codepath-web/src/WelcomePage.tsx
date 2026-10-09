import { FormEvent, useState } from "react";
import { ArrowRight, Bot, CheckCircle2, CircleAlert, Code2, Eye, EyeOff, LoaderCircle, ShieldCheck, Sparkles, TerminalSquare } from "lucide-react";
import { api, authStore } from "./api";
import type { User } from "./types";

export function WelcomePage({onSuccess}:{onSuccess:(user:User)=>void}) {
  const [mode,setMode]=useState<"login"|"register">("login");
  const [form,setForm]=useState({account:"",password:"",email:"",username:"",displayName:""});
  const [showPassword,setShowPassword]=useState(false);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState("");
  async function submit(e:FormEvent){
    e.preventDefault();setError("");setLoading(true);
    try{
      const data=mode==="login"?await api.login(form.account,form.password):await api.register(form.email,form.username,form.password,form.displayName);
      authStore.save(data);onSuccess(data.user);
    }catch(e){setError(e instanceof Error?e.message:"请求失败");}
    finally{setLoading(false);}
  }
  function switchMode(next:"login"|"register"){setMode(next);setError("");}
  return <main className="welcome-page">
    <section className="welcome-visual">
      <div className="welcome-glow glow-one"/><div className="welcome-glow glow-two"/>
      <div className="welcome-brand"><span><Code2 size={22}/></span><b>CodePath</b><em>AI 编程教练</em></div>
      <div className="welcome-copy"><span className="welcome-kicker"><Sparkles size={14}/> AI + 编程学习</span><h1>找到错误，<br/><i>也找到下一步。</i></h1><p>不只是告诉你答案。CodePath 会读懂代码的失败证据，诊断根因，并用分层提示引导你自己解决问题。</p></div>
      <div className="welcome-demo">
        <div className="demo-editor"><div className="demo-head"><span/><span/><span/><b>solution.py</b></div><pre><em>1</em> nums = list(map(int, input().split())){"\n"}<em>2</em> total = 0{"\n"}<em>3</em> <i>for</i> n <i>in</i> nums:{"\n"}<em>4</em>     total = n{"\n"}<em>5</em> print(total)</pre></div>
        <div className="demo-diagnosis"><span><Bot size={17}/></span><div><small>AI 根因诊断</small><b>循环中覆盖了累加结果</b><p>试着思考：每次遍历时，total 应该如何保留之前的值？</p></div></div>
      </div>
      <div className="welcome-features"><div><TerminalSquare size={16}/><span><b>隔离代码沙箱</b><small>真实运行证据</small></span></div><div><Bot size={16}/><span><b>错因诊断</b><small>不只判断对错</small></span></div><div><CheckCircle2 size={16}/><span><b>个性化路径</b><small>知识点掌握跟踪</small></span></div></div>
    </section>

    <section className="welcome-auth">
      <div className="auth-shell">
        <div className="auth-heading"><span>{mode==="login"?"欢迎回来":"开始学习"}</span><h2>{mode==="login"?"登录 CodePath":"创建你的账号"}</h2><p>{mode==="login"?"继续你的编程学习路径":"让 AI 教练记住你的进步与薄弱点"}</p></div>
        <div className="auth-mode"><button className={mode==="login"?"active":""} onClick={()=>switchMode("login")}>登录</button><button className={mode==="register"?"active":""} onClick={()=>switchMode("register")}>注册</button></div>
        <form className="welcome-form" onSubmit={submit}>
          {mode==="register"&&<div className="form-row"><label>昵称<input required value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})} placeholder="例如：小明"/></label><label>用户名<input required minLength={3} maxLength={64} value={form.username} onChange={e=>setForm({...form,username:e.target.value})} placeholder="3-64 个字符"/></label></div>}
          {mode==="register"?<label>邮箱<input required type="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} placeholder="name@example.com"/></label>:<label>账号<input autoFocus required value={form.account} onChange={e=>setForm({...form,account:e.target.value})} placeholder="用户名或邮箱"/></label>}
          <label>密码<div className="password-field"><input required type={showPassword?"text":"password"} minLength={8} maxLength={72} value={form.password} onChange={e=>setForm({...form,password:e.target.value})} placeholder="至少 8 位"/><button type="button" onClick={()=>setShowPassword(v=>!v)} title={showPassword?"隐藏密码":"显示密码"}>{showPassword?<EyeOff size={16}/>:<Eye size={16}/>}</button></div></label>
          {error&&<div className="welcome-error"><CircleAlert size={15}/>{error}</div>}
          <button className="welcome-submit" disabled={loading}>{loading?<LoaderCircle className="spin" size={17}/>:<ArrowRight size={17}/>}<span>{loading?"正在处理…":mode==="login"?"登录并继续":"注册并开始"}</span></button>
        </form>
        <div className="auth-security"><ShieldCheck size={14}/><span>密码使用 BCrypt 加密存储</span></div>
      </div>
    </section>
  </main>;
}
