import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import { MobileEngagementService } from '../../core/mobile-admin/mobile-engagement.service';
import {
  CriterionAction,
  EngagementBadge,
  EngagementCriterion,
  EngagementSettings,
  EngagementStats,
  QuizQuestion,
} from '../../core/mobile-admin/models/mobile-engagement.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type Tab = 'settings' | 'criteria' | 'badges' | 'quiz';
type QuizFilter = 'all' | 'active' | 'inactive';

export const CRITERION_ACTIONS: { value: CriterionAction | ''; label: string }[] = [
  { value: '', label: 'Aucune' },
  { value: 'PROFILE', label: 'Ouvrir le profil' },
  { value: 'CONTACTS', label: 'Contacts' },
  { value: 'DOCUMENTS', label: 'Documents' },
  { value: 'RENEWAL', label: 'Renouvellement' },
  { value: 'SUPPORT', label: 'Support' },
];

const BADGE_ICON: Record<string, string> = {
  PROFILE_COMPLETE: '🧑‍💼',
  PHONE_VERIFIED: '📱',
  ATTESTATION_DOWNLOADED: '📄',
  PAID_BEFORE_DUE: '⏰',
  FEEDBACK_GIVEN: '💬',
  QUIZ_STREAK_7: '🔥',
  QUIZ_STREAK_30: '🏅',
};

interface CriterionDraft {
  code: string;
  label: string;
  hint: string;
  weight: number;
  action: CriterionAction | '';
  active: boolean;
}

interface BadgeDraft {
  code: string;
  label: string;
  description: string;
  target: number | null;
  displayOrder: number;
  active: boolean;
}

interface QuestionDraft {
  id: number | null;
  theme: string;
  question: string;
  explanation: string;
  choices: { label: string; correct: boolean }[];
  publishFrom: string;
  publishTo: string;
  active: boolean;
  /** Question déjà répondue : les choix ne peuvent plus changer. */
  locked: boolean;
}

// Limites du contrat de la gateway.
const MAX_THEME = 60;
const MAX_QUESTION = 300;
const MAX_EXPLANATION = 500;
const MAX_CHOICE = 120;

/** Menu « Mobile AssurZen » > Engagement : réglages et consentement, critères du score, badges, questions du quiz. */
@Component({
  selector: 'app-engagement-mobile',
  templateUrl: './engagement-mobile.component.html',
  styleUrl: './engagement-mobile.component.scss',
  imports: [AlertMessageComponent, CdkDrag, CdkDragHandle, CdkDropList, FormsModule, SpinnerComponent],
})
export class EngagementMobileComponent {
  private readonly service = inject(MobileEngagementService);

  readonly actions = CRITERION_ACTIONS;
  readonly max = { theme: MAX_THEME, question: MAX_QUESTION, explanation: MAX_EXPLANATION, choice: MAX_CHOICE };

  readonly tab = signal<Tab>('settings');
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  // ---------- Réglages ----------
  readonly stats = signal<EngagementStats | null>(null);
  readonly settingsLoading = signal(true);
  readonly settingsSaving = signal(false);
  readonly settingsDirty = signal(false);
  readonly consentVersion = signal<number | null>(null);
  settings: EngagementSettings = { enabled: false, features: { protectionScore: false, dailyQuiz: false, badges: false }, consentText: '' };

  // ---------- Critères ----------
  readonly criteria = signal<EngagementCriterion[]>([]);
  readonly criteriaLoading = signal(true);
  readonly criterionEdit = signal<CriterionDraft | null>(null);
  readonly modalError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly activeWeightTotal = computed(() => this.criteria().filter((c) => c.active && c.available).reduce((s, c) => s + c.weight, 0));
  readonly activeCriteria = computed(() => this.criteria().filter((c) => c.active).length);
  readonly unavailableCriteria = computed(() => this.criteria().filter((c) => !c.available).length);

  // ---------- Badges ----------
  readonly badges = signal<EngagementBadge[]>([]);
  readonly badgesLoading = signal(true);
  readonly badgeEdit = signal<BadgeDraft | null>(null);

  // ---------- Quiz ----------
  readonly questions = signal<QuizQuestion[]>([]);
  readonly quizLoading = signal(true);
  readonly quizFilter = signal<QuizFilter>('all');
  readonly themeFilter = signal('');
  readonly questionEdit = signal<QuestionDraft | null>(null);
  readonly deleteAsk = signal<number | null>(null);
  readonly themes = computed(() => [...new Set(this.questions().map((q) => q.theme))].sort());
  readonly counts = computed(() => ({
    all: this.questions().length,
    active: this.questions().filter((q) => q.active).length,
    inactive: this.questions().filter((q) => !q.active).length,
  }));
  readonly shownQuestions = computed(() => {
    const f = this.quizFilter();
    const t = this.themeFilter();
    return this.questions().filter((q) => (f === 'all' || (f === 'active') === q.active) && (!t || q.theme === t));
  });

  constructor() {
    this.loadSettings();
    this.loadCriteria();
    this.loadBadges();
    this.loadQuestions();
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.info.set(null);
  }

  // ====================== Réglages ======================
  loadSettings(): void {
    this.settingsLoading.set(true);
    this.service.settings().subscribe({
      next: (s) => {
        this.applySettings(s);
        this.settingsLoading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les réglages.'));
        this.settingsLoading.set(false);
      },
    });
    this.service.stats().subscribe({ next: (s) => this.stats.set(s), error: () => undefined });
  }

  private applySettings(s: EngagementSettings): void {
    this.settings = {
      enabled: s.enabled,
      features: { protectionScore: s.features.protectionScore, dailyQuiz: s.features.dailyQuiz, badges: s.features.badges },
      consentText: s.consentText ?? '',
    };
    this.consentVersion.set(s.consentVersion ?? null);
    this.settingsDirty.set(false);
  }

  markDirty(): void {
    this.settingsDirty.set(true);
  }

  discardSettings(): void {
    this.loadSettings();
  }

  saveSettings(): void {
    if (this.settingsSaving()) return;
    this.settingsSaving.set(true);
    this.error.set(null);
    this.service.saveSettings(this.settings).subscribe({
      next: (s) => {
        this.applySettings(s);
        this.settingsSaving.set(false);
        this.info.set('Réglages enregistrés.');
      },
      error: (err) => {
        this.settingsSaving.set(false);
        this.error.set(this.messageOf(err, "Impossible d'enregistrer les réglages."));
      },
    });
  }

  // ====================== Critères ======================
  loadCriteria(): void {
    this.criteriaLoading.set(true);
    this.service.criteria().subscribe({
      next: (items) => {
        this.criteria.set([...items].sort((a, b) => a.displayOrder - b.displayOrder));
        this.criteriaLoading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les critères.'));
        this.criteriaLoading.set(false);
      },
    });
  }

  actionLabel(value?: string | null): string {
    return this.actions.find((a) => a.value === (value ?? ''))?.label ?? '—';
  }

  private criterionBody(c: EngagementCriterion, patch: Partial<EngagementCriterion> = {}) {
    const m = { ...c, ...patch };
    return { label: m.label, hint: m.hint ?? undefined, weight: m.weight, action: (m.action ?? '') as CriterionAction | '', active: m.active, displayOrder: m.displayOrder };
  }

  toggleCriterion(c: EngagementCriterion): void {
    if (!c.available) return;
    this.saveCriterion(c, { active: !c.active });
  }

  private saveCriterion(c: EngagementCriterion, patch: Partial<EngagementCriterion>, after?: () => void): void {
    this.error.set(null);
    this.service.saveCriterion(c.code, this.criterionBody(c, patch)).subscribe({
      next: (saved) => {
        this.criteria.update((list) => list.map((x) => (x.code === saved.code ? { ...x, ...saved } : x)));
        after?.();
      },
      error: (err) => this.error.set(this.messageOf(err, 'Impossible d’enregistrer le critère.')),
    });
  }

  dropCriterion(event: CdkDragDrop<EngagementCriterion[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const previous = new Map(this.criteria().map((c) => [c.code, c.displayOrder]));
    const list = [...this.criteria()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    const renumbered = list.map((c, i) => ({ ...c, displayOrder: i + 1 }));
    this.criteria.set(renumbered);
    // Un PUT par critère dont l'ordre a changé.
    renumbered
      .filter((c) => previous.get(c.code) !== c.displayOrder)
      .forEach((c) => this.service.saveCriterion(c.code, this.criterionBody(c)).subscribe({ error: (err) => this.error.set(this.messageOf(err, "Impossible d'enregistrer l'ordre.")) }));
  }

  editCriterion(c: EngagementCriterion): void {
    this.modalError.set(null);
    this.criterionEdit.set({ code: c.code, label: c.label, hint: c.hint ?? '', weight: c.weight, action: (c.action ?? '') as CriterionAction | '', active: c.active });
  }

  submitCriterion(): void {
    const d = this.criterionEdit();
    const c = d && this.criteria().find((x) => x.code === d.code);
    if (!d || !c || this.saving()) return;
    if (!d.label.trim()) {
      this.modalError.set('Le nom affiché est obligatoire.');
      return;
    }
    if (!(d.weight >= 0 && d.weight <= 100)) {
      this.modalError.set('Le poids doit être compris entre 0 et 100.');
      return;
    }
    this.saving.set(true);
    this.modalError.set(null);
    this.service.saveCriterion(c.code, { label: d.label.trim(), hint: d.hint.trim() || undefined, weight: Number(d.weight), action: d.action, active: d.active, displayOrder: c.displayOrder }).subscribe({
      next: (saved) => {
        this.criteria.update((list) => list.map((x) => (x.code === saved.code ? { ...x, ...saved } : x)));
        this.saving.set(false);
        this.criterionEdit.set(null);
      },
      error: (err) => {
        this.saving.set(false);
        this.modalError.set(this.messageOf(err, "Impossible d'enregistrer le critère."));
      },
    });
  }

  // ====================== Badges ======================
  loadBadges(): void {
    this.badgesLoading.set(true);
    this.service.badges().subscribe({
      next: (items) => {
        this.badges.set([...items].sort((a, b) => a.displayOrder - b.displayOrder));
        this.badgesLoading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les badges.'));
        this.badgesLoading.set(false);
      },
    });
  }

  badgeIcon(code: string): string {
    return BADGE_ICON[code] ?? '🏷️';
  }

  isStreak(code: string): boolean {
    return code.startsWith('QUIZ_STREAK_');
  }

  private badgeBody(b: EngagementBadge, patch: Partial<EngagementBadge> = {}) {
    const m = { ...b, ...patch };
    return { label: m.label, description: m.description ?? undefined, active: m.active, displayOrder: m.displayOrder, target: this.isStreak(m.code) ? (m.target ?? undefined) : undefined };
  }

  toggleBadge(b: EngagementBadge): void {
    this.error.set(null);
    this.service.saveBadge(b.code, this.badgeBody(b, { active: !b.active })).subscribe({
      next: (saved) => this.badges.update((list) => list.map((x) => (x.code === saved.code ? { ...x, ...saved } : x))),
      error: (err) => this.error.set(this.messageOf(err, 'Impossible d’enregistrer le badge.')),
    });
  }

  dropBadge(event: CdkDragDrop<EngagementBadge[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const previous = new Map(this.badges().map((b) => [b.code, b.displayOrder]));
    const list = [...this.badges()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    const renumbered = list.map((b, i) => ({ ...b, displayOrder: i + 1 }));
    this.badges.set(renumbered);
    renumbered
      .filter((b) => previous.get(b.code) !== b.displayOrder)
      .forEach((b) => this.service.saveBadge(b.code, this.badgeBody(b)).subscribe({ error: (err) => this.error.set(this.messageOf(err, "Impossible d'enregistrer l'ordre.")) }));
  }

  editBadge(b: EngagementBadge): void {
    this.modalError.set(null);
    this.badgeEdit.set({ code: b.code, label: b.label, description: b.description ?? '', target: b.target ?? null, displayOrder: b.displayOrder, active: b.active });
  }

  submitBadge(): void {
    const d = this.badgeEdit();
    if (!d || this.saving()) return;
    if (!d.label.trim()) {
      this.modalError.set('Le nom affiché est obligatoire.');
      return;
    }
    if (this.isStreak(d.code) && !(d.target && d.target >= 1 && d.target <= 365)) {
      this.modalError.set("L'objectif doit être compris entre 1 et 365 jours.");
      return;
    }
    this.saving.set(true);
    this.modalError.set(null);
    this.service
      .saveBadge(d.code, { label: d.label.trim(), description: d.description.trim() || undefined, active: d.active, displayOrder: Number(d.displayOrder), target: this.isStreak(d.code) ? Number(d.target) : undefined })
      .subscribe({
        next: (saved) => {
          this.badges.update((list) => [...list.map((x) => (x.code === saved.code ? { ...x, ...saved } : x))].sort((a, b) => a.displayOrder - b.displayOrder));
          this.saving.set(false);
          this.badgeEdit.set(null);
        },
        error: (err) => {
          this.saving.set(false);
          this.modalError.set(this.messageOf(err, "Impossible d'enregistrer le badge."));
        },
      });
  }

  // ====================== Quiz ======================
  loadQuestions(): void {
    this.quizLoading.set(true);
    this.service.questions().subscribe({
      next: (items) => {
        this.questions.set(items);
        this.quizLoading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les questions.'));
        this.quizLoading.set(false);
      },
    });
  }

  rate(q: QuizQuestion): number | null {
    return q.answerCount > 0 ? Math.round((q.correctCount / q.answerCount) * 100) : null;
  }

  goodChoice(q: QuizQuestion): string {
    return q.choices.find((c) => c.correct)?.label ?? '—';
  }

  publication(q: QuizQuestion): string {
    const f = (d?: string | null) => (d ? new Date(d).toLocaleDateString('fr-FR') : '');
    if (q.publishFrom && q.publishTo) return `${f(q.publishFrom)} → ${f(q.publishTo)}`;
    if (q.publishFrom) return `Depuis le ${f(q.publishFrom)}`;
    if (q.publishTo) return `Jusqu'au ${f(q.publishTo)}`;
    return '—';
  }

  newQuestion(): void {
    this.modalError.set(null);
    this.questionEdit.set({
      id: null,
      theme: '',
      question: '',
      explanation: '',
      choices: [
        { label: '', correct: true },
        { label: '', correct: false },
      ],
      publishFrom: '',
      publishTo: '',
      active: true,
      locked: false,
    });
  }

  editQuestion(q: QuizQuestion): void {
    this.modalError.set(null);
    this.questionEdit.set({
      id: q.id,
      theme: q.theme,
      question: q.question,
      explanation: q.explanation ?? '',
      choices: q.choices.map((c) => ({ label: c.label, correct: c.correct })),
      publishFrom: q.publishFrom ?? '',
      publishTo: q.publishTo ?? '',
      active: q.active,
      locked: q.answerCount > 0,
    });
  }

  addChoice(d: QuestionDraft): void {
    if (d.choices.length < 4) d.choices.push({ label: '', correct: false });
  }

  removeChoice(d: QuestionDraft, index: number): void {
    if (d.choices.length <= 2) return;
    const wasGood = d.choices[index].correct;
    d.choices.splice(index, 1);
    if (wasGood) d.choices[0].correct = true;
  }

  pickGood(d: QuestionDraft, index: number): void {
    if (d.locked) return;
    d.choices.forEach((c, i) => (c.correct = i === index));
  }

  submitQuestion(): void {
    const d = this.questionEdit();
    if (!d || this.saving()) return;
    const choices = d.choices.map((c) => ({ label: c.label.trim(), correct: c.correct }));
    if (!d.theme.trim() || !d.question.trim()) {
      this.modalError.set('Le thème et la question sont obligatoires.');
      return;
    }
    if (choices.length < 2 || choices.length > 4 || choices.some((c) => !c.label) || choices.filter((c) => c.correct).length !== 1) {
      this.modalError.set('Il faut exactement une bonne réponse parmi 2 à 4 choix, tous renseignés.');
      return;
    }
    if (d.publishFrom && d.publishTo && d.publishTo < d.publishFrom) {
      this.modalError.set('La date de fin doit suivre la date de début.');
      return;
    }
    const body = {
      theme: d.theme.trim(),
      question: d.question.trim(),
      explanation: d.explanation.trim() || undefined,
      choices,
      publishFrom: d.publishFrom || undefined,
      publishTo: d.publishTo || undefined,
      active: d.active,
    };
    this.saving.set(true);
    this.modalError.set(null);
    const call = d.id === null ? this.service.createQuestion(body) : this.service.updateQuestion(d.id, body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.questionEdit.set(null);
        this.info.set('Question enregistrée.');
        this.loadQuestions();
      },
      error: (err) => {
        this.saving.set(false);
        this.modalError.set(this.messageOf(err, "Impossible d'enregistrer la question."));
      },
    });
  }

  toggleQuestion(q: QuizQuestion): void {
    this.error.set(null);
    this.service.setQuestionActive(q.id, !q.active).subscribe({
      next: () => this.loadQuestions(),
      error: (err) => this.error.set(this.messageOf(err, 'Impossible de changer le statut de la question.')),
    });
  }

  deleteQuestion(q: QuizQuestion): void {
    if (this.deleteAsk() !== q.id) {
      this.deleteAsk.set(q.id);
      return;
    }
    this.deleteAsk.set(null);
    this.error.set(null);
    this.service.deleteQuestion(q.id).subscribe({
      next: () => this.loadQuestions(),
      error: (err) => this.error.set(this.messageOf(err, 'Impossible de supprimer la question.')),
    });
  }

  // ====================== Erreurs ======================
  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code === 'QUIZ_QUESTION_ANSWERED') return 'Cette question a déjà reçu des réponses : ses choix ne peuvent plus changer et elle ne peut pas être supprimée. Désactivez-la à la place.';
      if (err.status === 404) return 'Élément introuvable : il a peut-être été supprimé.';
      if (err.status === 403) return 'Accès non autorisé.';
      if (code === 'VALIDATION_FAILED') {
        const field = err.error?.field ? ` (${err.error.field})` : '';
        return `${typeof err.error?.message === 'string' && err.error.message ? err.error.message : 'Valeurs invalides.'}${field}`;
      }
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
