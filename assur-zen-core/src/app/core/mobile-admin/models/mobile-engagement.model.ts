/** Engagement de l'application mobile (score de protection, quiz du jour, badges) — /admin/mobile/engagement/** (ADMIN). */

export interface EngagementFeatures {
  protectionScore: boolean;
  dailyQuiz: boolean;
  badges: boolean;
}

export interface EngagementSettings {
  enabled: boolean;
  features: EngagementFeatures;
  consentText: string;
  /** Incrémentée par le serveur quand le texte de consentement change. */
  consentVersion?: number | null;
}

/** Agrégats anonymes (aucun client identifiable). */
export interface EngagementStats {
  consents: number;
  withdrawals: number;
  quizAnswers: number;
  badgesEarned: number;
}

export type CriterionAction = 'PROFILE' | 'CONTACTS' | 'DOCUMENTS' | 'RENEWAL' | 'SUPPORT';

export interface EngagementCriterion {
  code: string;
  label: string;
  hint?: string | null;
  weight: number;
  action?: CriterionAction | null;
  active: boolean;
  displayOrder: number;
  /** false : le serveur n'a pas la donnée, le critère ne peut pas être activé. */
  available: boolean;
}

export interface EngagementCriterionRequest {
  label: string;
  hint?: string;
  weight: number;
  action?: CriterionAction | '';
  active: boolean;
  displayOrder: number;
}

export interface EngagementBadge {
  code: string;
  label: string;
  description?: string | null;
  active: boolean;
  displayOrder: number;
  target?: number | null;
}

export interface EngagementBadgeRequest {
  label: string;
  description?: string;
  active: boolean;
  displayOrder: number;
  target?: number | null;
}

export interface QuizChoice {
  id?: number;
  label: string;
  correct: boolean;
}

export interface QuizQuestion {
  id: number;
  theme: string;
  question: string;
  explanation?: string | null;
  choices: QuizChoice[];
  publishFrom?: string | null;
  publishTo?: string | null;
  active: boolean;
  answerCount: number;
  correctCount: number;
}

export interface QuizQuestionRequest {
  theme: string;
  question: string;
  explanation?: string;
  choices: { label: string; correct: boolean }[];
  publishFrom?: string;
  publishTo?: string;
  active: boolean;
}
