export type User = { id: number; email: string; username: string; displayName: string; role: string };
export type UserProfile = User & {
  gender:string; personality:string; country:string; school:string;
  familyAddress:string; major:string; habit:string; avatarDataUrl:string;
};
export type Course = { id: number; code: string; name: string; description?: string };
export type Problem = {
  id: number; courseId: number; title: string; description: string; inputDescription?: string;
  outputDescription?: string; starterCode?: string; language: string; difficulty: number;
  timeLimitMs: number; memoryLimitMb: number;
};
export type PublicExample = { id:number; input:string; expectedOutput:string };
export type Diagnosis = {
  errorType: string; rootCause: string; explanation: string; confidence?: number;
  hintLevel: number; nextAction?: string; modelName?: string;
};
export type TestResult = {
  id: number; testCaseId: number; status: string; actualOutput?: string; stderr?: string;
  runtimeMs?: number; memoryKb?: number;
};
export type Submission = {
  id: number; problemId: number; language: string; status: string; passedCount: number;
  totalCount: number; runtimeMs?: number; memoryKb?: number; errorMessage?: string;
  createdAt: string; testResults: TestResult[]; diagnosis?: Diagnosis;
};
export type KnowledgeGuidance = {
  id:number; code:string; name:string; description?:string;
  masteryBefore:number; masteryAfter:number; delta:number; reason:string;
};
export type LearningAssistance = {
  submissionId:number; errorType:string; rootCause:string; explanation:string;
  nextStep:string; modelName:string; knowledge:KnowledgeGuidance[];
};
export type AiPracticeKnowledge = { id:number; code:string; name:string };
export type AiGeneratedPractice = {
  id:number; title:string; description:string; inputDescription:string; outputDescription:string;
  starterCode:string; stdin:string; language:string; difficulty:number;
  knowledgePoints:AiPracticeKnowledge[]; modelName:string;
};
export type MasteryChange = {
  knowledgePointId:number; knowledgePointName:string; masteryBefore:number;
  masteryAfter:number; delta:number; modelName:string;
};
export type AiPracticeEvaluation = {
  correct:boolean; score:number; feedback:string; errorType:string; nextStep:string;
  modelName:string; masteryChanges:MasteryChange[];
};
export type LearningPlanItem = {
  id:number; taskType:"LEARN"|"PRACTICE"|"REVIEW"|"ASSESSMENT"; title:string;
  description:string; knowledgePointId?:number; knowledgePointName?:string;
  problemId?:number; problemTitle?:string; estimatedMinutes:number;
  targetMastery:number; status:"PENDING"|"COMPLETED";
};
export type LearningPlanStage = {
  id:number; weekNumber:number; title:string; objective:string; items:LearningPlanItem[];
};
export type LearningPlan = {
  id:number; courseId:number; courseName:string; title:string; summary:string; goal:string;
  currentLevel:string; daysPerWeek:number; minutesPerDay:number; durationWeeks:number;
  totalItems:number; completedItems:number; totalMinutes:number; completedMinutes:number;
  planProgress:number; abilityMastery:number; nextRegenerationAt?:string; canRegenerate:boolean;
  modelName:string; stages:LearningPlanStage[];
};
export type LearningTrack = {id:number;name:string;language:string;description?:string};
export type KnowledgeExplanationSection = {title:string;content:string;code?:string};
export type KnowledgeExplanation = {
  title:string;overview:string;sections:KnowledgeExplanationSection[];
  keyPoints:string[];checkQuestion:string;modelName:string;
};
export type CoachConversation = {
  id:number;title:string;submissionId?:number;createdAt:string;updatedAt:string;
  messageCount:number;preview:string;
};
export type CoachMessage = {
  id:number;role:"user"|"assistant";content:string;modelName?:string;createdAt:string;
};
export type HistoryItem = Omit<Submission, "testResults" | "diagnosis" | "errorMessage" | "memoryKb">;
export type Page<T> = { content: T[]; totalElements: number; totalPages: number; number: number };
export type DiscussionPost = {
  id:number; title:string; content:string; tags?:string; problem_id?:number; problem_title?:string;
  view_count:number; comment_count?:number; created_at:string; author_id:number; author_name:string;
  author_role:string; comments?:DiscussionComment[];
};
export type DiscussionComment = { id:number; content:string; created_at:string; author_id:number; author_name:string; author_role:string };
export type Assessment = {
  id:number; problem_id:number; problem_title:string; difficulty:number; language:string; status:string;
  passed_count:number; total_count:number; runtime_ms?:number; memory_kb?:number; error_message?:string;
  created_at:string; error_type?:string; root_cause?:string; confidence?:number; next_action?:string; model_name?:string;
};
