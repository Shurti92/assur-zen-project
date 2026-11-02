import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
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

import { CampagneService } from '../../../core/campagnes/campagne.service';
import {
  CampagneCible,
  CampagneDestinataireResponse,
  CampagneMarketingRequest,
  CampagneMarketingResponse,
  CampagneStatut,
  CampagneType,
} from '../../../core/campagnes/models/campagne.model';
import { SegmentService } from '../../../core/segments/segment.service';
import { SegmentResponse } from '../../../core/segments/models/segment.model';
import { TemplateService } from '../../../core/templates/template.service';
import { CommunicationTemplateResponse } from '../../../core/templates/models/template.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type CampagnesMode = 'liste' | 'historique' | 'statistiques';
type StatutFilter = 'all' | CampagneStatut;
type PendingAction = 'lancer' | 'annuler' | 'supprimer' | null;

const TITLES: Record<CampagnesMode, string> = {
  liste: 'Mes campagnes',
  historique: 'Historique des campagnes',
  statistiques: 'Statistiques des campagnes',
};

const TYPE_LABEL: Record<CampagneType, string> = {
  SMS: 'SMS',
  EMAIL: 'Email',
  SMS_EMAIL: 'SMS + Email',
  WHATSAPP: 'WhatsApp',
  PUSH: 'Notification push',
  PDF: 'PDF',
};

const STATUT_LABEL: Record<CampagneStatut, string> = {
  BROUILLON: 'Brouillon',
  PLANIFIEE: 'Planifiée',
  EN_COURS: 'En cours',
  TERMINEE: 'Terminée',
  ANNULEE: 'Annulée',
};

const STATUT_VARIANT: Record<CampagneStatut, string> = {
  BROUILLON: 'neutral',
  PLANIFIEE: 'info',
  EN_COURS: 'warning',
  TERMINEE: 'success',
  ANNULEE: 'danger',
};

const STATUT_ORDER: CampagneStatut[] = ['BROUILLON', 'PLANIFIEE', 'EN_COURS', 'TERMINEE', 'ANNULEE'];

const CIBLE_LABEL: Record<CampagneCible, string> = {
  TOUS_CLIENTS: 'Tous les clients',
  CLIENTS_FIDELES: 'Clients fidèles',
  ECHEANCE_PROCHE: 'Échéance proche',
  NON_RENOUVELES: 'Non renouvelés',
  SEGMENT_PERSONNALISE: 'Segment personnalisé',
  SEGMENT_DYNAMIQUE: 'Segment dynamique',
};

const SEGMENT_CIBLES: CampagneCible[] = ['SEGMENT_PERSONNALISE', 'SEGMENT_DYNAMIQUE'];

const EMPTY_FORM = {
  nom: '',
  description: '',
  type: 'SMS' as CampagneType,
  cible: 'TOUS_CLIENTS' as CampagneCible,
  segmentId: '',
  templateId: '',
  messageContenu: '',
  sujetEmail: '',
  dateDebut: '',
  dateFin: '',
  budgetPrevu: '',
};

interface BreakdownRow {
  label: string;
  count: number;
  envoyes: number;
  destinataires: number;
}

@Component({
  selector: 'app-campagnes',
  templateUrl: './campagnes.component.html',
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
    ConfirmDialogComponent,
  ],
})
export class CampagnesComponent {
  private readonly campagneService = inject(CampagneService);
  private readonly segmentService = inject(SegmentService);
  private readonly templateService = inject(TemplateService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly segments = signal<SegmentResponse[]>([]);
  readonly templates = signal<CommunicationTemplateResponse[]>([]);

  readonly mode = (this.route.snapshot.data['mode'] as CampagnesMode) ?? 'liste';
  readonly title = TITLES[this.mode];
  readonly isStatsMode = this.mode === 'statistiques';
  readonly isHistoriqueMode = this.mode === 'historique';
  readonly statutOrder = STATUT_ORDER;

  readonly pageSize = 20;

  readonly allCampagnes = signal<CampagneMarketingResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statutFilter = signal<StatutFilter>('all');
  readonly pageIndex = signal(0);

  readonly formVisible = signal(false);
  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({ ...EMPTY_FORM });
  private readonly typeValue = toSignal(this.form.controls.type.valueChanges, { initialValue: this.form.controls.type.value });
  readonly showSujetEmail = computed(() => this.typeValue() !== 'SMS');
  private readonly cibleValue = toSignal(this.form.controls.cible.valueChanges, { initialValue: this.form.controls.cible.value });
  readonly showSegmentSelect = computed(() => SEGMENT_CIBLES.includes(this.cibleValue()));

  readonly detailVisible = signal(false);
  readonly selectedCampagne = signal<CampagneMarketingResponse | null>(null);
  readonly destinataires = signal<CampagneDestinataireResponse[]>([]);
  readonly destinatairesLoading = signal(false);

  readonly pendingAction = signal<PendingAction>(null);
  readonly actionSaving = signal(false);

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  readonly scopedCampagnes = computed(() => {
    const all = this.allCampagnes();
    if (this.mode === 'historique') return all.filter((c) => c.statut === 'TERMINEE' || c.statut === 'ANNULEE');
    if (this.mode === 'statistiques') return all;
    const filter = this.statutFilter();
    return filter === 'all' ? all : all.filter((c) => c.statut === filter);
  });

  readonly filteredCampagnes = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedCampagnes();
    if (!query) return scoped;
    return scoped.filter((c) => `${c.code} ${c.nom} ${c.description ?? ''}`.toLowerCase().includes(query));
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredCampagnes().length / this.pageSize)));

  readonly pagedCampagnes = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredCampagnes().slice(start, start + this.pageSize);
  });

  readonly statTotal = computed(() => this.filteredCampagnes().length);

  readonly statDestinatairesTouches = computed(() => this.filteredCampagnes().reduce((sum, c) => sum + c.nbEnvoyes, 0));

  readonly statTauxSucces = computed(() => {
    const campagnes = this.filteredCampagnes().filter((c) => c.nbDestinataires > 0);
    const total = campagnes.reduce((sum, c) => sum + c.nbDestinataires, 0);
    const envoyes = campagnes.reduce((sum, c) => sum + c.nbEnvoyes, 0);
    return total === 0 ? 0 : Math.round((envoyes / total) * 100);
  });

  readonly statFlagLabel = computed(() => (this.isHistoriqueMode ? 'Annulées' : 'En cours'));

  readonly statFlagValue = computed(() => {
    const target: CampagneStatut = this.isHistoriqueMode ? 'ANNULEE' : 'EN_COURS';
    return this.filteredCampagnes().filter((c) => c.statut === target).length;
  });

  readonly breakdownByStatut = computed(() => this.breakdown((c) => STATUT_LABEL[c.statut]));
  readonly breakdownByType = computed(() => this.breakdown((c) => TYPE_LABEL[c.type]));
  readonly breakdownByCible = computed(() => this.breakdown((c) => CIBLE_LABEL[c.cible]));

  readonly pendingActionTitle = computed(() => {
    const action = this.pendingAction();
    if (action === 'lancer') return 'Lancer la campagne';
    if (action === 'annuler') return 'Annuler la campagne';
    if (action === 'supprimer') return 'Supprimer la campagne';
    return '';
  });

  readonly pendingActionMessage = computed(() => {
    const campagne = this.selectedCampagne();
    const action = this.pendingAction();
    if (!campagne || !action) return '';
    if (action === 'lancer') return `Lancer l'envoi de « ${campagne.nom} » aux destinataires ciblés ?`;
    if (action === 'annuler') return `Voulez-vous vraiment annuler la campagne « ${campagne.nom} » ?`;
    return `Voulez-vous vraiment supprimer la campagne « ${campagne.nom} » ? Cette action est irréversible.`;
  });

  readonly pendingActionConfirmLabel = computed(() => {
    const action = this.pendingAction();
    if (action === 'lancer') return 'Lancer';
    if (action === 'annuler') return 'Annuler la campagne';
    return 'Supprimer';
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
    this.segmentService.listAll().subscribe({ next: (page) => this.segments.set(page.content) });
    this.templateService.listAll().subscribe({ next: (page) => this.templates.set(page.content.filter((t) => t.statut === 'ACTIF')) });
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);
    this.campagneService.listAll().subscribe({
      next: (page) => {
        this.allCampagnes.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les campagnes.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.pageIndex.set(0), 400);
  }

  setStatutFilter(filter: StatutFilter): void {
    this.statutFilter.set(filter);
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  openCreate(): void {
    this.formMode.set('create');
    this.editingId.set(null);
    this.form.reset({ ...EMPTY_FORM });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  openEdit(campagne: CampagneMarketingResponse): void {
    this.formMode.set('edit');
    this.editingId.set(campagne.id);
    this.form.reset({
      nom: campagne.nom,
      description: campagne.description ?? '',
      type: campagne.type,
      cible: campagne.cible,
      segmentId: campagne.segmentId != null ? String(campagne.segmentId) : '',
      templateId: campagne.templateId != null ? String(campagne.templateId) : '',
      messageContenu: campagne.messageContenu,
      sujetEmail: campagne.sujetEmail ?? '',
      dateDebut: campagne.dateDebut,
      dateFin: campagne.dateFin,
      budgetPrevu: campagne.budgetPrevu != null ? String(campagne.budgetPrevu) : '',
    });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  closeForm(): void {
    this.formVisible.set(false);
  }

  onTemplateSelected(templateId: string): void {
    const template = this.templates().find((t) => t.id === Number(templateId));
    if (!template) return;
    if (!this.form.controls.messageContenu.value.trim()) {
      this.form.controls.messageContenu.setValue(template.contenu);
    }
    if (template.objet && !this.form.controls.sujetEmail.value.trim()) {
      this.form.controls.sujetEmail.setValue(template.objet);
    }
  }

  submitForm(): void {
    const raw = this.form.getRawValue();
    if (!raw.nom.trim()) {
      this.formError.set('Le nom est requis.');
      return;
    }
    if (!raw.messageContenu.trim()) {
      this.formError.set('Le contenu du message est requis.');
      return;
    }
    if (!raw.dateDebut || !raw.dateFin) {
      this.formError.set('Les dates de début et de fin sont requises.');
      return;
    }
    if (this.showSegmentSelect() && !raw.segmentId) {
      this.formError.set('Sélectionnez un segment pour cette cible.');
      return;
    }

    const payload: CampagneMarketingRequest = {
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      type: raw.type,
      cible: raw.cible,
      segmentId: this.showSegmentSelect() && raw.segmentId ? Number(raw.segmentId) : undefined,
      templateId: raw.templateId ? Number(raw.templateId) : undefined,
      messageContenu: raw.messageContenu.trim(),
      sujetEmail: raw.sujetEmail.trim() || undefined,
      dateDebut: raw.dateDebut,
      dateFin: raw.dateFin,
      budgetPrevu: raw.budgetPrevu ? Number(raw.budgetPrevu) : undefined,
    };

    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id ? this.campagneService.update(id, payload) : this.campagneService.create(payload);
    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer la campagne.");
      },
    });
  }

  openDetail(campagne: CampagneMarketingResponse): void {
    this.selectedCampagne.set(campagne);
    this.detailVisible.set(true);
    this.fetchDestinataires(campagne.id);
  }

  private fetchDestinataires(id: number): void {
    this.destinatairesLoading.set(true);
    this.destinataires.set([]);
    this.campagneService.listDestinataires(id).subscribe({
      next: (list) => {
        this.destinataires.set(list);
        this.destinatairesLoading.set(false);
      },
      error: () => {
        this.destinatairesLoading.set(false);
      },
    });
  }

  askLancer(campagne: CampagneMarketingResponse): void {
    this.selectedCampagne.set(campagne);
    this.pendingAction.set('lancer');
  }

  askAnnuler(campagne: CampagneMarketingResponse): void {
    this.selectedCampagne.set(campagne);
    this.pendingAction.set('annuler');
  }

  askSupprimer(campagne: CampagneMarketingResponse): void {
    this.selectedCampagne.set(campagne);
    this.pendingAction.set('supprimer');
  }

  confirmPendingAction(): void {
    const campagne = this.selectedCampagne();
    const action = this.pendingAction();
    if (!campagne || !action) return;

    this.actionSaving.set(true);
    const request$: Observable<unknown> =
      action === 'lancer'
        ? this.campagneService.lancer(campagne.id)
        : action === 'annuler'
          ? this.campagneService.annuler(campagne.id)
          : this.campagneService.delete(campagne.id);

    request$.subscribe({
      next: () => {
        this.actionSaving.set(false);
        this.pendingAction.set(null);
        this.detailVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.actionSaving.set(false);
        this.pendingAction.set(null);
        this.error.set("Impossible d'effectuer cette action sur la campagne.");
      },
    });
  }

  cancelPendingAction(): void {
    this.pendingAction.set(null);
  }

  private breakdown(keyFn: (c: CampagneMarketingResponse) => string): BreakdownRow[] {
    const map = new Map<string, BreakdownRow>();
    for (const c of this.filteredCampagnes()) {
      const label = keyFn(c);
      const row = map.get(label) ?? { label, count: 0, envoyes: 0, destinataires: 0 };
      row.count += 1;
      row.envoyes += c.nbEnvoyes;
      row.destinataires += c.nbDestinataires;
      map.set(label, row);
    }
    return Array.from(map.values()).sort((a, b) => b.count - a.count);
  }

  canEdit(c: CampagneMarketingResponse): boolean {
    return c.statut === 'BROUILLON';
  }

  canLancer(c: CampagneMarketingResponse): boolean {
    return c.statut === 'BROUILLON' || c.statut === 'PLANIFIEE';
  }

  canAnnuler(c: CampagneMarketingResponse): boolean {
    return c.statut === 'PLANIFIEE' || c.statut === 'EN_COURS';
  }

  canDelete(c: CampagneMarketingResponse): boolean {
    return c.statut === 'BROUILLON';
  }

  typeLabel(type: CampagneType): string {
    return TYPE_LABEL[type];
  }

  statutLabel(statut: CampagneStatut): string {
    return STATUT_LABEL[statut];
  }

  statutVariant(statut: CampagneStatut): string {
    return STATUT_VARIANT[statut];
  }

  cibleLabel(cible: CampagneCible): string {
    return CIBLE_LABEL[cible];
  }

  taux(envoyes: number, destinataires: number): number {
    return destinataires === 0 ? 0 : Math.round((envoyes / destinataires) * 100);
  }

  formatAmount(value: number | null): string {
    return value == null ? '—' : `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
