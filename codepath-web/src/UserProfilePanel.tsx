import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { Camera, CircleAlert, LoaderCircle, Save, ShieldCheck, Trash2, UserRound, X } from "lucide-react";
import { api } from "./api";
import type { User, UserProfile } from "./types";
import "./user-profile.css";

type EditableProfile = Pick<UserProfile,"displayName"|"gender"|"personality"|"country"|"school"|"familyAddress"|"major"|"habit"|"avatarDataUrl">;
const emptyProfile:EditableProfile={displayName:"",gender:"",personality:"",country:"",school:"",familyAddress:"",major:"",habit:"",avatarDataUrl:""};

export function UserProfilePanel({user,onClose,onSaved}:{user:User;onClose:()=>void;onSaved:(profile:UserProfile)=>void}){
  const [profile,setProfile]=useState<EditableProfile>({...emptyProfile,displayName:user.displayName});
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [imageLoading,setImageLoading]=useState(false);
  const [error,setError]=useState("");
  const fileRef=useRef<HTMLInputElement>(null);

  useEffect(()=>{
    let active=true;
    api.userProfile().then(data=>{if(active)setProfile(data);})
      .catch(e=>{if(active)setError(e instanceof Error?e.message:"个人资料加载失败");})
      .finally(()=>{if(active)setLoading(false);});
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

  return <div className="profile-backdrop" onMouseDown={onClose}>
    <section className="profile-panel" onMouseDown={event=>event.stopPropagation()} aria-label="用户信息">
      <header className="profile-head"><div><span><UserRound size={19}/></span><div><small>USER PROFILE</small><h2>用户信息</h2></div></div><button onClick={onClose} title="关闭"><X size={19}/></button></header>
      {loading?<div className="profile-loading"><LoaderCircle className="spin"/><span>正在读取个人资料…</span></div>:<form onSubmit={submit}>
        <div className="profile-identity">
          <div className="profile-avatar-large">{profile.avatarDataUrl?<img src={profile.avatarDataUrl} alt="用户头像"/>:<span>{profile.displayName.slice(0,1).toUpperCase()||<UserRound/>}</span>}{imageLoading&&<i><LoaderCircle className="spin" size={20}/></i>}</div>
          <div><b>{profile.displayName||user.username}</b><span>@{user.username}</span><small>{user.email}</small><div className="avatar-actions"><button type="button" onClick={()=>fileRef.current?.click()}><Camera size={14}/>{profile.avatarDataUrl?"更换头像":"上传头像"}</button>{profile.avatarDataUrl&&<button type="button" className="remove" onClick={()=>set("avatarDataUrl","")}><Trash2 size={13}/>移除</button>}</div><input ref={fileRef} hidden type="file" accept="image/png,image/jpeg,image/webp" onChange={selectAvatar}/></div>
        </div>

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
