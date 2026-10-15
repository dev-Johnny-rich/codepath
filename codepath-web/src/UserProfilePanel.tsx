import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { BrainCircuit, Camera, CircleAlert, LoaderCircle, RefreshCw, Save, ShieldCheck, Sparkles, Target, Trash2, TrendingUp, UserRound, X } from "lucide-react";
import { api } from "./api";
import { parseServerDate } from "./date";
import type { LearningAssessment, User, UserProfile } from "./types";
import "./user-profile.css";

type EditableProfile = Pick<UserProfile,"displayName"|"gender"|"personality"|"country"|"school"|"familyAddress"|"major"|"habit"|"avatarDataUrl">;
const emptyProfile:EditableProfile={displayName:"",gender:"",personality:"",country:"",school:"",familyAddress:"",major:"",habit:"",avatarDataUrl:""};

export function UserProfilePanel({user,onClose,onSaved}:{user:User;onClose:()=>void;onSaved:(profile:UserProfile)=>void}){
  const [profile,setProfile]=useState<EditableProfile>({...emptyProfile,displayName:user.displayName});
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [imageLoading,setImageLoading]=useState(false);
  const [error,setError]=useState("");
  const [assessment,setAssessment]=useState<LearningAssessment|null>(null);
  const [assessmentLoading,setAssessmentLoading]=useState(true);
  const [assessmentGenerating,setAssessmentGenerating]=useState(false);
  const [assessmentError,setAssessmentError]=useState("");
  const fileRef=useRef<HTMLInputElement>(null);

  useEffect(()=>{
    let active=true;
    api.userProfile().then(data=>{if(active)setProfile(data);})
      .catch(e=>{if(active)setError(e instanceof Error?e.message:"个人资料加载失败");})
      .finally(()=>{if(active)setLoading(false);});
    api.learningAssessment().then(data=>{if(active)setAssessment(data);})
      .catch(e=>{if(active)setAssessmentError(e instanceof Error?e.message:"学习评估加载失败");})
      .finally(()=>{if(active)setAssessmentLoading(false);});
    return()=>{active=false;};
  },[user.id]);

  function set<K extends keyof EditableProfile>(key:K,value:EditableProfile[K]){
    setProfile(current=>({...current,[key]:value}));
  }
  async function selectAvatar(event:ChangeEvent<HTMLInputElement>){
    const file=event.target.files?.[0];event.target.value="";
    if(!file)return;
    setError("");setImageLoading(true);
    try{set("avatarDataUrl",await compressAvatar(file));}
    catch(e){setError(e instanceof Error?e.message:"头像处理失败");}
    finally{setImageLoading(false);}
  }
  async function submit(event:FormEvent){
    event.preventDefault();setError("");setSaving(true);
    try{const saved=await api.updateUserProfile(profile);setProfile(saved);onSaved(saved);}
    catch(e){setError(e instanceof Error?e.message:"个人资料保存失败");}
    finally{setSaving(false);}
  }
  async function generateAssessment(){
    setAssessmentError("");setAssessmentGenerating(true);
    try{setAssessment(await api.generateLearningAssessment());}
    catch(e){setAssessmentError(e instanceof Error?e.message:"AI 学习评估生成失败");}
    finally{setAssessmentGenerating(false);}
  }

  return <div className="profile-backdrop" onMouseDown={onClose}>
    <section className="profile-panel" onMouseDown={event=>event.stopPropagation()} aria-label="用户信息">
      <header className="profile-head"><div><span><UserRound size={19}/></span><div><small>USER PROFILE</small><h2>用户信息</h2></div></div><button onClick={onClose} title="关闭"><X size={19}/></button></header>
      {loading?<div className="profile-loading"><LoaderCircle className="spin"/><span>正在读取个人资料…</span></div>:<form onSubmit={submit}>
        <div className="profile-identity">
          <div className="profile-avatar-large">{profile.avatarDataUrl?<img src={profile.avatarDataUrl} alt="用户头像"/>:<span>{profile.displayName.slice(0,1).toUpperCase()||<UserRound/>}</span>}{imageLoading&&<i><LoaderCircle className="spin" size={20}/></i>}</div>
          <div><b>{profile.displayName||user.username}</b><span>@{user.username}</span><small>{user.email}</small><div className="avatar-actions"><button type="button" onClick={()=>fileRef.current?.click()}><Camera size={14}/>{profile.avatarDataUrl?"更换头像":"上传头像"}</button>{profile.avatarDataUrl&&<button type="button" className="remove" onClick={()=>set("avatarDataUrl","")}><Trash2 size={13}/>移除</button>}</div><input ref={fileRef} hidden type="file" accept="image/png,image/jpeg,image/webp" onChange={selectAvatar}/></div>
        </div>

        <section className="learning-assessment" aria-label="我的学习情况">
          <div className="assessment-title"><div><span><BrainCircuit size={16}/></span><div><small>AI LEARNING INSIGHT</small><h3>我的学习情况</h3></div></div>{assessment&&<button type="button" onClick={generateAssessment} disabled={assessmentGenerating}><RefreshCw size={13} className={assessmentGenerating?"spin":""}/>{assessmentGenerating?"评估中…":"重新评估"}</button>}</div>
          {assessmentLoading?<div className="assessment-loading"><LoaderCircle className="spin" size={18}/>正在读取学习评估…</div>:assessment?<>
            <div className="assessment-overview"><div className="assessment-score"><strong>{assessment.score}</strong><span>/ 100</span></div><div><b>{assessment.overallLevel}</b><p>{assessment.summary}</p></div></div>
            <div className="assessment-stats"><span><b>{assessment.evidence.totalSubmissions}</b>30日练习</span><span><b>{Math.round(assessment.evidence.averagePassRate*100)}%</b>平均通过率</span><span><b>{assessment.evidence.activeDays}</b>活跃天数</span></div>
            <div className="assessment-columns"><div><h4><TrendingUp size={13}/>已表现优势</h4>{assessment.strengths.length?<ul>{assessment.strengths.map((item,index)=><li key={index}>{item}</li>)}</ul>:<p>继续完成练习后会识别优势。</p>}</div><div><h4><Target size={13}/>待提升</h4>{assessment.weaknesses.length?<ul>{assessment.weaknesses.map((item,index)=><li key={index}>{item}</li>)}</ul>:<p>暂无足够证据。</p>}</div></div>
            <div className="assessment-focus"><Sparkles size={15}/><div><small>下一步重点</small><b>{assessment.nextFocus}</b></div></div>
            {assessment.actions.length>0&&<ol className="assessment-actions">{assessment.actions.map((item,index)=><li key={index}>{item}</li>)}</ol>}
            <div className="assessment-meta">{assessment.modelName} · {parseServerDate(assessment.generatedAt).toLocaleString("zh-CN")} · 仅使用学习相关数据</div>
          </>:<div className="assessment-empty"><Sparkles size={23}/><b>让 AI 了解你的学习状态</b><p>综合分析提交记录、AI 练习评测、BKT 掌握度和学习计划，生成优势、薄弱点和下一步建议。</p><button type="button" onClick={generateAssessment} disabled={assessmentGenerating}>{assessmentGenerating?<LoaderCircle className="spin" size={14}/>:<BrainCircuit size={14}/>} {assessmentGenerating?"AI 正在评估…":"生成 AI 学习评估"}</button></div>}
          {assessmentError&&<div className="assessment-error"><CircleAlert size={14}/>{assessmentError}</div>}
        </section>

        <div className="profile-form-grid">
          <label><span>昵称</span><input required maxLength={100} value={profile.displayName} onChange={e=>set("displayName",e.target.value)} placeholder="你想如何被称呼"/></label>
          <label><span>性别</span><select value={profile.gender} onChange={e=>set("gender",e.target.value)}><option value="">未填写</option><option value="MALE">男</option><option value="FEMALE">女</option><option value="NON_BINARY">非二元</option><option value="PREFER_NOT_TO_SAY">不愿透露</option></select></label>
          <label><span>国家 / 地区</span><input maxLength={120} value={profile.country} onChange={e=>set("country",e.target.value)} placeholder="例如：中国"/></label>
          <label><span>学校</span><input maxLength={200} value={profile.school} onChange={e=>set("school",e.target.value)} placeholder="你所在的学校"/></label>
          <label><span>专业</span><input maxLength={200} value={profile.major} onChange={e=>set("major",e.target.value)} placeholder="例如：计算机科学"/></label>
          <label><span>家庭地址</span><input maxLength={300} value={profile.familyAddress} onChange={e=>set("familyAddress",e.target.value)} placeholder="选填，仅你可见"/></label>
          <label className="full"><span>个性签名</span><textarea maxLength={500} value={profile.personality} onChange={e=>set("personality",e.target.value)} placeholder="用一句话介绍你自己…"/></label>
          <label className="full"><span>兴趣爱好</span><textarea maxLength={500} value={profile.habit} onChange={e=>set("habit",e.target.value)} placeholder="例如：编程、阅读、篮球…"/></label>
        </div>
        {error&&<div className="profile-error"><CircleAlert size={15}/>{error}</div>}
        <div className="profile-privacy"><ShieldCheck size={14}/><span>个人资料只能由当前登录用户读取和修改。头像会在本地压缩后上传。</span></div>
        <footer><button type="button" className="profile-cancel" onClick={onClose}>取消</button><button className="profile-save" disabled={saving||imageLoading||!profile.displayName.trim()}>{saving?<LoaderCircle className="spin" size={15}/>:<Save size={15}/>} {saving?"正在保存…":"保存修改"}</button></footer>
      </form>}
    </section>
  </div>;
}

function compressAvatar(file:File):Promise<string>{
  if(!["image/png","image/jpeg","image/webp"].includes(file.type))return Promise.reject(new Error("仅支持 PNG、JPEG 和 WebP 图片"));
  if(file.size>5*1024*1024)return Promise.reject(new Error("原始图片不能超过 5 MB"));
  return new Promise((resolve,reject)=>{
    const url=URL.createObjectURL(file);const image=new Image();
    image.onload=()=>{
      try{
        const max=512;const scale=Math.min(1,max/Math.max(image.width,image.height));
        const canvas=document.createElement("canvas");canvas.width=Math.max(1,Math.round(image.width*scale));canvas.height=Math.max(1,Math.round(image.height*scale));
        const context=canvas.getContext("2d");if(!context)throw new Error("浏览器无法处理该图片");
        context.drawImage(image,0,0,canvas.width,canvas.height);
        const data=canvas.toDataURL("image/jpeg",.84);URL.revokeObjectURL(url);
        if(data.length>700000)throw new Error("头像压缩后仍过大，请更换图片");
        resolve(data);
      }catch(error){URL.revokeObjectURL(url);reject(error);}
    };
    image.onerror=()=>{URL.revokeObjectURL(url);reject(new Error("无法读取该图片"));};image.src=url;
  });
}
