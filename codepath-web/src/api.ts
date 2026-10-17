import type { AiGeneratedPractice, AiPracticeEvaluation, Assessment, CoachConversation, CoachMessage, Course, DiscussionPost, GeneratedQuestionSummary, HistoryItem, KnowledgeExplanation, LearningAssessment, LearningAssistance, LearningPlan, LearningTrack, Page, Problem, PublicExample, Submission, User, UserProfile } from "./types";

const BASE = import.meta.env.VITE_API_BASE_URL || "";
type Envelope<T> = { success: boolean; data: T; message?: string };
type AuthData = { accessToken: string; refreshToken: string; user: User };

export const AUTH_EXPIRED_EVENT = "codepath:auth-expired";

export function discussionSocketUrl(): string {
  const url = new URL("/ws/discussions", BASE || window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

export const authStore = {
  token: () => localStorage.getItem("codepath_access_token"),
  refreshToken: () => localStorage.getItem("codepath_refresh_token"),
  user: (): User | null => { try { return JSON.parse(localStorage.getItem("codepath_user") || "null"); } catch { return null; } },
  save: (data: AuthData) => {
    localStorage.setItem("codepath_access_token", data.accessToken);
    localStorage.setItem("codepath_refresh_token", data.refreshToken);
    localStorage.setItem("codepath_user", JSON.stringify(data.user));
  },
  clear: () => ["codepath_access_token", "codepath_refresh_token", "codepath_user"].forEach(k => localStorage.removeItem(k))
  ,updateUser: (user:User) => localStorage.setItem("codepath_user",JSON.stringify(user))
};

let refreshPromise: Promise<AuthData> | null = null;

function expireSession() {
  authStore.clear();
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}

function refreshSession(refreshToken: string): Promise<AuthData> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${BASE}/api/v1/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      });
      const body = await response.json().catch(() => ({ success: false, message: "服务器响应异常" })) as Envelope<AuthData>;
      if (!response.ok || !body.success || !body.data) {
        throw new Error(body.message || `刷新登录状态失败 (${response.status})`);
      }
      authStore.save(body.data);
      return body.data;
    })().finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

async function request<T>(path: string, options: RequestInit = {}, canRefresh = true): Promise<T> {
  const token = authStore.token();
  const response = await fetch(`${BASE}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers }
  });
  const body = await response.json().catch(() => ({ success: false, message: "服务器响应异常" })) as Envelope<T>;
  if (!response.ok || !body.success) {
    if (response.status === 401 && canRefresh && !path.startsWith("/api/v1/auth/")) {
      const latestToken = authStore.token();
      if (token && latestToken && latestToken !== token) {
        return request<T>(path, options, false);
      }
      const refreshToken = authStore.refreshToken();
      if (refreshToken) {
        try {
          await refreshSession(refreshToken);
          return request<T>(path, options, false);
        } catch {
          expireSession();
          throw new Error("登录已过期，请重新登录");
        }
      }
      expireSession();
    } else if (response.status === 401 && !canRefresh) {
      expireSession();
    }
    throw new Error(body.message || `请求失败 (${response.status})`);
  }
  return body.data;
}

export const api = {
  courses: () => request<Course[]>("/api/v1/courses"),
  problems: (courseId: number) => request<Problem[]>(`/api/v1/problems?courseId=${courseId}`),
  problem: (id: number) => request<Problem>(`/api/v1/problems/${id}`),
  examples: (id:number) => request<PublicExample[]>(`/api/v1/problems/${id}/examples`),
  login: (account: string, password: string) => request<{ accessToken: string; refreshToken: string; user: User }>("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ account, password }) }),
  register: (email: string, username: string, password: string, displayName: string) => request<{ accessToken: string; refreshToken: string; user: User }>("/api/v1/auth/register", { method: "POST", body: JSON.stringify({ email, username, password, displayName }) }),
  userProfile: () => request<UserProfile>("/api/v1/users/me"),
  updateUserProfile: (profile:Pick<UserProfile,"displayName"|"gender"|"personality"|"country"|"school"|"familyAddress"|"major"|"habit"|"avatarDataUrl">) => request<UserProfile>("/api/v1/users/me",{method:"PUT",body:JSON.stringify(profile)}),
  learningAssessment: () => request<LearningAssessment|null>("/api/v1/users/me/learning-assessment"),
  generateLearningAssessment: () => request<LearningAssessment>("/api/v1/users/me/learning-assessment",{method:"POST"}),
  submit: (problemId: number, language: string, sourceCode: string) => request<{ submissionId: number; status: string }>(`/api/v1/problems/${problemId}/submissions`, { method: "POST", body: JSON.stringify({ language, sourceCode }) }),
  run: (problemId: number, language: string, sourceCode: string) => request<{ submissionId: number; runToken: string; status: string }>(`/api/v1/problems/${problemId}/runs`, { method: "POST", body: JSON.stringify({ language, sourceCode }) }),
  selfPractice: (language:string,sourceCode:string,stdin:string) => request<{submissionId:number;runToken:string;status:string}>("/api/v1/self-practice/runs",{method:"POST",body:JSON.stringify({language,sourceCode,stdin})}),
  runDetail: (id:number,runToken:string) => request<Submission>(`/api/v1/runs/${id}`,{headers:{"X-Run-Token":runToken}}),
  submission: (id: number) => request<Submission>(`/api/v1/submissions/${id}`),
  learningAssistance: (id:number) => request<LearningAssistance>(`/api/v1/submissions/${id}/learning-assistance`),
  runLearningAssistance: (id:number,runToken:string) => request<LearningAssistance>(`/api/v1/runs/${id}/learning-assistance`,{headers:{"X-Run-Token":runToken}}),
  generatePractice: (submissionId:number) => request<AiGeneratedPractice>(`/api/v1/submissions/${submissionId}/generated-practice`,{method:"POST"}),
  generatedPractice: (id:number) => request<AiGeneratedPractice>(`/api/v1/ai-practice/${id}`),
  submitAiPractice: (id:number,language:string,sourceCode:string) => request<{submissionId:number;status:string}>(`/api/v1/ai-practice/${id}/submissions`,{method:"POST",body:JSON.stringify({language,sourceCode})}),
  finalizeAiPractice: (id:number,submissionId:number) => request<AiPracticeEvaluation>(`/api/v1/ai-practice/${id}/submissions/${submissionId}/evaluation`,{method:"POST"}),
  history: (problemId?: number) => request<Page<HistoryItem>>(`/api/v1/submissions?size=20&sort=createdAt,desc${problemId ? `&problemId=${problemId}` : ""}`),
  hint: (submissionId: number, level: number) => request<{ content: string; modelName: string }>(`/api/v1/submissions/${submissionId}/hints`, { method: "POST", body: JSON.stringify({ level, question: "请根据我的错误给出当前层级的提示" }) })
  ,createCoachConversation: (submissionId?:number,title="AI 编程教练") => request<{id:number}>("/api/v1/coach/conversations",{method:"POST",body:JSON.stringify({submissionId:submissionId||null,title})})
  ,coachConversations: () => request<CoachConversation[]>("/api/v1/coach/conversations")
  ,coachMessages: (conversationId:number) => request<CoachMessage[]>(`/api/v1/coach/conversations/${conversationId}/messages`)
  ,sendCoachMessage: (conversationId:number,content:string) => request<{content:string;modelName:string}>(`/api/v1/coach/conversations/${conversationId}/messages`,{method:"POST",body:JSON.stringify({content})})
  ,discussions: (q="") => request<DiscussionPost[]>(`/api/v1/discussions?q=${encodeURIComponent(q)}`),
  discussion: (id:number,trackView=true) => request<DiscussionPost>(`/api/v1/discussions/${id}?trackView=${trackView}`),
  createDiscussion: (title:string,content:string,tags:string,problemId?:number) => request<{id:number}>("/api/v1/discussions",{method:"POST",body:JSON.stringify({title,content,tags,problemId})}),
  comment: (id:number,content:string) => request<{id:number}>(`/api/v1/discussions/${id}/comments`,{method:"POST",body:JSON.stringify({content})}),
  assessments: () => request<Assessment[]>("/api/v1/interview/assessments"),
  generatedQuestions: () => request<GeneratedQuestionSummary[]>("/api/v1/interview/generated-questions"),
  learningPlanTracks: () => request<LearningTrack[]>("/api/v1/learning-plans/tracks"),
  currentLearningPlan: (courseId:number) => request<LearningPlan|null>(`/api/v1/learning-plans/current?courseId=${courseId}`),
  activeLearningPlan: () => request<LearningPlan|null>("/api/v1/learning-plans/active"),
  generateLearningPlan: (input:{courseId:number;goal:string;currentLevel:string;daysPerWeek:number;minutesPerDay:number;durationWeeks:number}) => request<LearningPlan>("/api/v1/learning-plans/generate",{method:"POST",body:JSON.stringify(input)}),
  completeLearningPlanItem: (itemId:number,completed:boolean) => request<LearningPlan>(`/api/v1/learning-plans/items/${itemId}`,{method:"PUT",body:JSON.stringify({completed})}),
  generatePlanPractice: (itemId:number) => request<AiGeneratedPractice>(`/api/v1/learning-plans/items/${itemId}/generated-practice`,{method:"POST"}),
  generatePlanKnowledgeTest: (planId:number) => request<AiGeneratedPractice>(`/api/v1/learning-plans/${planId}/knowledge-test`,{method:"POST"}),
  explainPlanItem: (itemId:number) => request<KnowledgeExplanation>(`/api/v1/learning-plans/items/${itemId}/explain`,{method:"POST"})
};
