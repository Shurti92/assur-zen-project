import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { SatisfactionService } from '../../../core/satisfaction/satisfaction.service';
import { EnqueteContexte, EnqueteSatisfactionResponse } from '../../../core/satisfaction/models/satisfaction.model';
import { InsuredService } from '../../../core/insured/insured.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type SatisfactionMode = 'enquetes' | 'reponses' | 'nps';

const TITLES: Record<SatisfactionMode, string> = {
  enquetes: 'Enquêtes de satisfaction',
  reponses: 'Réponses aux enquêtes',
  nps: 'Score NPS',
};

const CONTEXTE_LABEL: Record<EnqueteContexte, string> = {
  POST_SOUSCRIPTION: 'Post-souscription',
  POST_SINISTRE: 'Post-sinistre',
  PERIODIQUE: 'Périodique',
  POST_RESILIATION: 'Post-résiliation',
};

const NPS_CATEGORY_LABEL = { promoteur: 'Promoteur', passif: 'Passif', detracteur: 'Détracteur' } as const;

function npsCategory(score: number): keyof typeof NPS_CATEGORY_LABEL {
  if (score >= 9) return 'promoteur';
  if (score >= 7) return 'passif';
  return 'detracteur';
}

interface ContexteBreakdownRow {
  label: string;
  total: number;
  repondues: number;
  scoreMoyen: number | null;
}

@Component({
  selector: 'app-satisfaction',
  templateUrl: './satisfaction.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
  ],
})
export class SatisfactionComponent {
  private readonly satisfactionService = inject(SatisfactionService);
  private readonly insuredService = inject(InsuredService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly mode = (this.route.snapshot.data['mode'] as SatisfactionMode) ?? 'enquetes';
  readonly title = TITLES[this.mode];
  readonly isEnquetesMode = this.mode === 'enquetes';
  readonly isReponsesMode = this.mode === 'reponses';
  readonly isNpsMode = this.mode === 'nps';
  readonly contexteLabels = CONTEXTE_LABEL;

  readonly pageSize = 20;

  readonly allEnquetes = signal<EnqueteSatisfactionResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly pageIndex = signal(0);

  readonly insureds = signal<InsuredResponse[]>([]);

  readonly createVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createForm = this.fb.nonNullable.group({
    contexte: 'PERIODIQUE' as EnqueteContexte,
    insuredId: 0,
  });

  readonly reponseVisible = signal(false);
  readonly reponseSaving = signal(false);
  readonly reponseError = signal<string | null>(null);
  readonly reponseTarget = signal<EnqueteSatisfactionResponse | null>(null);
  readonly reponseForm = this.fb.nonNullable.group({
    scoreNps: 10,
    commentaire: '',
  });

  readonly scopedEnquetes = computed(() => {
    const all = this.allEnquetes();
    if (this.isReponsesMode || this.isNpsMode) return all.filter((e) => e.statut === 'REPONDUE');
    return all;
  });

  readonly filteredEnquetes = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedEnquetes();
    if (!query) return scoped;
    return scoped.filter((e) =>
      `${e.code} ${e.insuredName} ${e.contratPolicyNumber ?? ''} ${e.commentaire ?? ''}`.toLowerCase().includes(query),
    );
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredEnquetes().length / this.pageSize)));

  readonly pagedEnquetes = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredEnquetes().slice(start, start + this.pageSize);
  });

  /** In reponses/nps modes, scopedEnquetes is already filtered to REPONDUE, so this
   * equals filteredEnquetes().length there — but in enquetes mode (all statuses shown)
   * it must re-filter to count only the answered ones. */
  readonly answeredCount = computed(() => this.filteredEnquetes().filter((e) => e.statut === 'REPONDUE').length);

  // NPS aggregate — reponduesCount is filteredEnquetes().length, valid only when scoped to REPONDUE (reponses/nps modes).
  readonly reponduesCount = computed(() => this.filteredEnquetes().length);

  readonly npsCounts = computed(() => {
    const counts = { promoteur: 0, passif: 0, detracteur: 0 };
    for (const e of this.filteredEnquetes()) {
      if (e.scoreNps == null) continue;
      counts[npsCategory(e.scoreNps)] += 1;
    }
    return counts;
  });

  readonly npsScore = computed(() => {
    const total = this.reponduesCount();
    if (total === 0) return 0;
    const { promoteur, detracteur } = this.npsCounts();
    return Math.round(((promoteur - detracteur) / total) * 100);
  });

  readonly npsScoreMoyen = computed(() => {
    const scores = this.filteredEnquetes().map((e) => e.scoreNps).filter((s): s is number => s != null);
    if (scores.length === 0) return null;
    return Math.round((scores.reduce((sum, s) => sum + s, 0) / scores.length) * 10) / 10;
  });

  readonly breakdownByContexte = computed<ContexteBreakdownRow[]>(() => {
    const map = new Map<string, { total: number; repondues: number; scoreSum: number; scoreCount: number }>();
    for (const e of this.allEnquetes()) {
      const label = CONTEXTE_LABEL[e.contexte];
      const row = map.get(label) ?? { total: 0, repondues: 0, scoreSum: 0, scoreCount: 0 };
      row.total += 1;
      if (e.statut === 'REPONDUE') row.repondues += 1;
      if (e.scoreNps != null) {
        row.scoreSum += e.scoreNps;
        row.scoreCount += 1;
      }
      map.set(label, row);
    }
    return Array.from(map.entries())
      .map(([label, v]) => ({
        label,
        total: v.total,
        repondues: v.repondues,
        scoreMoyen: v.scoreCount === 0 ? null : Math.round((v.scoreSum / v.scoreCount) * 10) / 10,
      }))
      .sort((a, b) => b.total - a.total);
  });

  constructor() {
    this.fetch();
    if (this.isEnquetesMode) this.fetchInsureds();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);
    this.satisfactionService.listAll().subscribe({
      next: (page) => {
        this.allEnquetes.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les enquêtes de satisfaction.');
        this.loading.set(false);
      },
    });
  }

  private fetchInsureds(): void {
    this.insuredService.list(0, 500).subscribe({
      next: (page) => this.insureds.set(page.content),
      error: () => undefined,
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  openCreate(): void {
    this.createForm.reset({ contexte: 'PERIODIQUE', insuredId: 0 });
    this.createError.set(null);
    this.createVisible.set(true);
  }

  closeCreate(): void {
    this.createVisible.set(false);
  }

  submitCreate(): void {
    const raw = this.createForm.getRawValue();
    if (!raw.insuredId) {
      this.createError.set("Sélectionnez l'assuré concerné.");
      return;
    }
    this.createSaving.set(true);
    this.createError.set(null);
    this.satisfactionService.envoyer({ contexte: raw.contexte, insuredId: Number(raw.insuredId) }).subscribe({
      next: () => {
        this.createSaving.set(false);
        this.createVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.createSaving.set(false);
        this.createError.set("Impossible d'envoyer cette enquête.");
      },
    });
  }

  openReponse(enquete: EnqueteSatisfactionResponse): void {
    this.reponseTarget.set(enquete);
    this.reponseForm.reset({ scoreNps: 10, commentaire: '' });
    this.reponseError.set(null);
    this.reponseVisible.set(true);
  }

  closeReponse(): void {
    this.reponseVisible.set(false);
  }

  submitReponse(): void {
    const enquete = this.reponseTarget();
    if (!enquete) return;
    const raw = this.reponseForm.getRawValue();
    this.reponseSaving.set(true);
    this.reponseError.set(null);
    this.satisfactionService
      .repondre(enquete.id, { scoreNps: Number(raw.scoreNps), commentaire: raw.commentaire.trim() || undefined })
      .subscribe({
        next: () => {
          this.reponseSaving.set(false);
          this.reponseVisible.set(false);
          this.fetch();
        },
        error: () => {
          this.reponseSaving.set(false);
          this.reponseError.set("Impossible d'enregistrer cette réponse.");
        },
      });
  }

  contexteLabel(contexte: EnqueteContexte): string {
    return CONTEXTE_LABEL[contexte];
  }

  statutVariant(statut: EnqueteSatisfactionResponse['statut']): string {
    if (statut === 'REPONDUE') return 'success';
    if (statut === 'EXPIREE' || statut === 'ECHEC_ENVOI') return 'danger';
    return 'warning';
  }

  scoreVariant(score: number | null): string {
    if (score == null) return 'neutral';
    const cat = npsCategory(score);
    return cat === 'promoteur' ? 'success' : cat === 'passif' ? 'warning' : 'danger';
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
