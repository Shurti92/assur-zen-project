import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { CrossSellService } from '../../../core/cross-sell/cross-sell.service';
import {
  CrossSellConditionChamp,
  CrossSellConditionOperateur,
  CrossSellHistoryResponse,
  CrossSellOpportunityResponse,
  CrossSellParameterResponse,
  CrossSellPriorite,
  CrossSellRuleConditionResponse,
  CrossSellRuleResponse,
  CrossSellStatut,
} from '../../../core/cross-sell/models/cross-sell.model';
import { InsuredService } from '../../../core/insured/insured.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { ContractService } from '../../../core/contracts/contract.service';
import { ContractResponse } from '../../../core/contracts/models/contract.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { ProfileService } from '../../../core/profile/profile.service';
import { ProfileRecord } from '../../../core/profile/models/profile-details.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import {
  CROSS_SELL_PRIORITE_LABEL,
  CROSS_SELL_PRIORITE_VARIANT,
  CROSS_SELL_STATUTS_TERMINAUX,
  CROSS_SELL_STATUT_LABEL,
  CROSS_SELL_STATUT_ORDER,
  CROSS_SELL_STATUT_VARIANT,
} from './cross-sell-status';

type View = 'opportunites' | 'regles' | 'parametres';
type StatutFilter = 'all' | CrossSellStatut;

const CONDITION_CHAMP_OPTIONS: CrossSellConditionChamp[] = [
  'ANCIENNETE_ANNEES',
  'PRIME_ANNUELLE',
  'NOMBRE_CONTRATS',
  'NOMBRE_PRODUITS_DISTINCTS',
  'ECHEANCES_IMPAYEES',
  'NPS',
  'RECLAMATIONS',
  'EST_VIP',
  'TYPE_CLIENT',
  'CANAL',
];

const CONDITION_OPERATEUR_OPTIONS: CrossSellConditionOperateur[] = [
  'EQUALS',
  'NOT_EQUALS',
  'GREATER_THAN',
  'GREATER_THAN_OR_EQUAL',
  'LESS_THAN',
  'LESS_THAN_OR_EQUAL',
  'CONTAINS',
  'IS_NULL',
  'IS_NOT_NULL',
  'EXPIRE_SOUS_N_JOURS',
];

const PRIORITE_OPTIONS: CrossSellPriorite[] = ['FAIBLE', 'NORMALE', 'HAUTE', 'CRITIQUE'];

@Component({
  selector: 'app-vente-croisee',
  templateUrl: './vente-croisee.component.html',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class VenteCroiseeComponent {
  private readonly crossSellService = inject(CrossSellService);
  private readonly insuredService = inject(InsuredService);
  private readonly contractService = inject(ContractService);
  private readonly produitService = inject(ProduitService);
  private readonly profileService = inject(ProfileService);
  private readonly fb = inject(FormBuilder);

  readonly view = signal<View>('opportunites');
  readonly statutOrder = CROSS_SELL_STATUT_ORDER;
  readonly statutLabel = CROSS_SELL_STATUT_LABEL;
  readonly prioriteLabel = CROSS_SELL_PRIORITE_LABEL;
  readonly prioriteOptions = PRIORITE_OPTIONS;
  readonly conditionChamps = CONDITION_CHAMP_OPTIONS;
  readonly conditionOperateurs = CONDITION_OPERATEUR_OPTIONS;

  readonly produits = signal<ProduitResponse[]>([]);
  readonly insureds = signal<InsuredResponse[]>([]);
  readonly commerciaux = signal<ProfileRecord[]>([]);
  readonly contractsForInsured = signal<ContractResponse[]>([]);

  setView(view: View): void {
    this.view.set(view);
  }

  constructor() {
    this.fetchOpportunites();
    this.fetchRegles();
    this.fetchParametres();
    this.produitService.list(0, 200, undefined, true).subscribe({
      next: (page) => this.produits.set(page.content),
      error: () => undefined,
    });
    this.insuredService.list(0, 500).subscribe({
      next: (page) => this.insureds.set(page.content),
      error: () => undefined,
    });
    this.profileService.list().subscribe({
      next: (profiles) => this.commerciaux.set(profiles),
      error: () => undefined,
    });
  }

  produitLabel(id: number | null): string {
    if (!id) return '—';
    return this.produits().find((p) => p.id === id)?.nomProduit ?? `#${id}`;
  }

  commercialName(profile: ProfileRecord): string {
    return `${profile.firstname} ${profile.lastname}`.trim();
  }

  // ================= OPPORTUNITÉS =================

  readonly opportunites = signal<CrossSellOpportunityResponse[]>([]);
  readonly opportunitesLoading = signal(true);
  readonly opportunitesError = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statutFilter = signal<StatutFilter>('all');
  readonly generating = signal(false);
  readonly pageIndex = signal(0);
  readonly pageSize = 20;

  readonly statTotal = computed(() => this.opportunites().length);
  readonly statOuvertes = computed(() => this.opportunites().filter((o) => !CROSS_SELL_STATUTS_TERMINAUX.includes(o.statut)).length);
  readonly statConverties = computed(() => this.opportunites().filter((o) => o.statut === 'CONVERTIE').length);
  readonly statScoreMoyen = computed(() => {
    const items = this.opportunites();
    if (items.length === 0) return 0;
    return Math.round(items.reduce((sum, o) => sum + o.score, 0) / items.length);
  });

  readonly filteredOpportunites = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const statut = this.statutFilter();
    let items = this.opportunites();
    if (statut !== 'all') items = items.filter((o) => o.statut === statut);
    if (query) {
      items = items.filter((o) =>
        `${o.code} ${o.insuredName} ${o.produitRecommandeNom}`.toLowerCase().includes(query),
      );
    }
    return [...items].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredOpportunites().length / this.pageSize)));
  readonly pagedOpportunites = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredOpportunites().slice(start, start + this.pageSize);
  });

  fetchOpportunites(): void {
    this.opportunitesLoading.set(true);
    this.opportunitesError.set(null);
    this.crossSellService.listAllOpportunites().subscribe({
      next: (page) => {
        this.opportunites.set(page.content);
        this.opportunitesLoading.set(false);
      },
      error: () => {
        this.opportunitesError.set('Impossible de charger les opportunités.');
        this.opportunitesLoading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    this.pageIndex.set(0);
  }

  setStatutFilter(value: string): void {
    this.statutFilter.set(value as StatutFilter);
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  genererOpportunites(): void {
    this.generating.set(true);
    this.opportunitesError.set(null);
    this.crossSellService.genererOpportunites().subscribe({
      next: () => {
        this.generating.set(false);
        this.fetchOpportunites();
      },
      error: () => {
        this.generating.set(false);
        this.opportunitesError.set("Impossible de générer des opportunités — vérifiez qu'au moins une règle active existe.");
      },
    });
  }

  // -- création --

  readonly createVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createForm = this.fb.nonNullable.group({
    insuredId: 0,
    contratId: 0,
    produitActuelId: 0,
    produitRecommandeId: 0,
    score: 50,
    priorite: 'NORMALE' as CrossSellPriorite,
    commentaire: '',
  });

  openCreate(): void {
    this.createForm.reset({
      insuredId: 0,
      contratId: 0,
      produitActuelId: 0,
      produitRecommandeId: 0,
      score: 50,
      priorite: 'NORMALE',
      commentaire: '',
    });
    this.contractsForInsured.set([]);
    this.createError.set(null);
    this.createVisible.set(true);
  }

  onCreateInsuredChange(value: string): void {
    const insuredId = Number(value);
    this.createForm.patchValue({ insuredId, contratId: 0 });
    this.contractsForInsured.set([]);
    if (!insuredId) return;
    this.contractService.list(0, 200, { insuredId }).subscribe({
      next: (page) => this.contractsForInsured.set(page.content),
      error: () => undefined,
    });
  }

  submitCreate(): void {
    const raw = this.createForm.getRawValue();
    if (!raw.insuredId) {
      this.createError.set("Sélectionnez l'assuré concerné.");
      return;
    }
    if (!raw.produitRecommandeId) {
      this.createError.set('Sélectionnez le produit recommandé.');
      return;
    }
    this.createSaving.set(true);
    this.createError.set(null);
    this.crossSellService
      .createOpportunite({
        insuredId: Number(raw.insuredId),
        contratId: raw.contratId ? Number(raw.contratId) : undefined,
        produitActuelId: raw.produitActuelId ? Number(raw.produitActuelId) : undefined,
        produitRecommandeId: Number(raw.produitRecommandeId),
        score: Number(raw.score),
        priorite: raw.priorite,
        commentaire: raw.commentaire.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.createSaving.set(false);
          this.createVisible.set(false);
          this.fetchOpportunites();
        },
        error: () => {
          this.createSaving.set(false);
          this.createError.set("Impossible de créer cette opportunité.");
        },
      });
  }

  // -- détail --

  readonly detailVisible = signal(false);
  readonly selectedOpportunite = signal<CrossSellOpportunityResponse | null>(null);
  readonly historique = signal<CrossSellHistoryResponse[]>([]);
  readonly historiqueLoading = signal(true);
  readonly historiqueError = signal<string | null>(null);

  readonly isTerminal = computed(() => {
    const o = this.selectedOpportunite();
    return !!o && CROSS_SELL_STATUTS_TERMINAUX.includes(o.statut);
  });

  readonly statutForm = this.fb.nonNullable.group({ statut: 'NOUVELLE' as CrossSellStatut, motif: '' });
  readonly statutSaving = signal(false);
  readonly statutError = signal<string | null>(null);

  readonly affectationForm = this.fb.nonNullable.group({ commercialId: 0 });
  readonly affectationSaving = signal(false);
  readonly affectationError = signal<string | null>(null);

  openDetail(o: CrossSellOpportunityResponse): void {
    this.selectedOpportunite.set(o);
    this.statutForm.reset({ statut: o.statut, motif: '' });
    this.statutError.set(null);
    this.affectationForm.reset({ commercialId: o.commercialId ?? 0 });
    this.affectationError.set(null);
    this.detailVisible.set(true);
    this.fetchHistorique(o.id);
  }

  private fetchHistorique(id: number): void {
    this.historiqueLoading.set(true);
    this.historiqueError.set(null);
    this.crossSellService.getHistorique(id).subscribe({
      next: (list) => {
        this.historique.set(list);
        this.historiqueLoading.set(false);
      },
      error: () => {
        this.historiqueError.set("Impossible de charger l'historique.");
        this.historiqueLoading.set(false);
      },
    });
  }

  submitStatut(): void {
    const o = this.selectedOpportunite();
    if (!o) return;
    const raw = this.statutForm.getRawValue();
    this.statutSaving.set(true);
    this.statutError.set(null);
    this.crossSellService.changerStatut(o.id, { statut: raw.statut, motif: raw.motif.trim() || undefined }).subscribe({
      next: (updated) => {
        this.statutSaving.set(false);
        this.selectedOpportunite.set(updated);
        this.applyOpportuniteUpdate(updated);
        this.fetchHistorique(o.id);
      },
      error: () => {
        this.statutSaving.set(false);
        this.statutError.set('Impossible de mettre à jour le statut.');
      },
    });
  }

  submitAffectation(): void {
    const o = this.selectedOpportunite();
    const raw = this.affectationForm.getRawValue();
    if (!o) return;
    if (!raw.commercialId) {
      this.affectationError.set('Sélectionnez un commercial.');
      return;
    }
    this.affectationSaving.set(true);
    this.affectationError.set(null);
    this.crossSellService.affecter(o.id, { commercialId: Number(raw.commercialId) }).subscribe({
      next: (updated) => {
        this.affectationSaving.set(false);
        this.selectedOpportunite.set(updated);
        this.applyOpportuniteUpdate(updated);
        this.fetchHistorique(o.id);
      },
      error: () => {
        this.affectationSaving.set(false);
        this.affectationError.set("Impossible d'affecter cette opportunité.");
      },
    });
  }

  private applyOpportuniteUpdate(updated: CrossSellOpportunityResponse): void {
    this.opportunites.set(this.opportunites().map((o) => (o.id === updated.id ? updated : o)));
  }

  statutVariant(statut: CrossSellStatut): string {
    return CROSS_SELL_STATUT_VARIANT[statut];
  }

  prioriteVariant(priorite: CrossSellPriorite): string {
    return CROSS_SELL_PRIORITE_VARIANT[priorite];
  }

  // ================= RÈGLES =================

  readonly regles = signal<CrossSellRuleResponse[]>([]);
  readonly reglesLoading = signal(true);
  readonly reglesError = signal<string | null>(null);

  readonly regleFormVisible = signal(false);
  readonly regleFormSaving = signal(false);
  readonly regleFormError = signal<string | null>(null);
  readonly editingRegleId = signal<number | null>(null);

  readonly regleForm = this.fb.nonNullable.group({
    code: '',
    nom: '',
    description: '',
    produitDeclencheurId: 0,
    produitRecommandeId: 0,
    priorite: 'NORMALE' as CrossSellPriorite,
    poidsScore: 10,
    ordre: 0,
    dateDebut: '',
    dateFin: '',
    actif: true,
  });

  readonly deleteRegleTarget = signal<CrossSellRuleResponse | null>(null);
  readonly deletingRegle = signal(false);
  readonly duplicatingId = signal<number | null>(null);

  fetchRegles(): void {
    this.reglesLoading.set(true);
    this.reglesError.set(null);
    this.crossSellService.listRegles().subscribe({
      next: (list) => {
        this.regles.set(list);
        this.reglesLoading.set(false);
      },
      error: () => {
        this.reglesError.set('Impossible de charger les règles.');
        this.reglesLoading.set(false);
      },
    });
  }

  openCreateRegle(): void {
    this.editingRegleId.set(null);
    this.regleFormError.set(null);
    this.regleForm.reset({
      code: '',
      nom: '',
      description: '',
      produitDeclencheurId: 0,
      produitRecommandeId: 0,
      priorite: 'NORMALE',
      poidsScore: 10,
      ordre: 0,
      dateDebut: '',
      dateFin: '',
      actif: true,
    });
    this.regleFormVisible.set(true);
  }

  openEditRegle(regle: CrossSellRuleResponse): void {
    this.editingRegleId.set(regle.id);
    this.regleFormError.set(null);
    this.regleForm.reset({
      code: regle.code,
      nom: regle.nom,
      description: regle.description ?? '',
      produitDeclencheurId: regle.produitDeclencheurId ?? 0,
      produitRecommandeId: regle.produitRecommandeId,
      priorite: regle.priorite,
      poidsScore: regle.poidsScore,
      ordre: regle.ordre ?? 0,
      dateDebut: regle.dateDebut ?? '',
      dateFin: regle.dateFin ?? '',
      actif: regle.actif,
    });
    this.regleFormVisible.set(true);
  }

  saveRegle(): void {
    const raw = this.regleForm.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim() || !raw.produitRecommandeId) {
      this.regleFormError.set('Le code, le nom et le produit recommandé sont requis.');
      return;
    }
    this.regleFormSaving.set(true);
    this.regleFormError.set(null);
    const payload = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      produitDeclencheurId: raw.produitDeclencheurId ? Number(raw.produitDeclencheurId) : undefined,
      produitRecommandeId: Number(raw.produitRecommandeId),
      priorite: raw.priorite,
      poidsScore: Number(raw.poidsScore),
      ordre: raw.ordre ? Number(raw.ordre) : undefined,
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
      actif: raw.actif,
    };
    const id = this.editingRegleId();
    const request = id ? this.crossSellService.updateRegle(id, payload) : this.crossSellService.createRegle(payload);
    request.subscribe({
      next: () => {
        this.regleFormSaving.set(false);
        this.regleFormVisible.set(false);
        this.fetchRegles();
      },
      error: () => {
        this.regleFormSaving.set(false);
        this.regleFormError.set("Impossible d'enregistrer cette règle.");
      },
    });
  }

  dupliquerRegle(regle: CrossSellRuleResponse): void {
    this.duplicatingId.set(regle.id);
    this.crossSellService.dupliquerRegle(regle.id).subscribe({
      next: () => {
        this.duplicatingId.set(null);
        this.fetchRegles();
      },
      error: () => {
        this.duplicatingId.set(null);
        this.reglesError.set('Impossible de dupliquer cette règle.');
      },
    });
  }

  confirmDeleteRegle(regle: CrossSellRuleResponse): void {
    this.deleteRegleTarget.set(regle);
  }

  performDeleteRegle(): void {
    const regle = this.deleteRegleTarget();
    if (!regle) return;
    this.deletingRegle.set(true);
    this.crossSellService.deleteRegle(regle.id).subscribe({
      next: () => {
        this.deleteRegleTarget.set(null);
        // Ce endpoint a déjà été vu renvoyer 204 sans rien changer du tout (voir
        // docs/api-contracts/recap-points-ouverts-2026-08-27.md §1) — on revérifie l'état réel
        // plutôt que de faire confiance au 204 pour annoncer une suppression.
        this.crossSellService.listRegles().subscribe({
          next: (list) => {
            this.deletingRegle.set(false);
            this.regles.set(list);
            const stillActive = list.find((r) => r.id === regle.id && r.actif);
            this.reglesError.set(
              stillActive
                ? `Le serveur a confirmé la suppression de « ${regle.nom} », mais elle est toujours active dans la liste — rien n'a réellement été supprimé. Contactez le support technique.`
                : null
            );
          },
          error: () => {
            this.deletingRegle.set(false);
          },
        });
      },
      error: () => {
        this.deletingRegle.set(false);
        this.deleteRegleTarget.set(null);
        this.reglesError.set('Impossible de supprimer cette règle.');
      },
    });
  }

  // -- conditions --

  readonly conditionsVisible = signal(false);
  readonly conditionsRegle = signal<CrossSellRuleResponse | null>(null);
  readonly conditions = signal<CrossSellRuleConditionResponse[]>([]);
  readonly conditionsLoading = signal(true);
  readonly conditionsError = signal<string | null>(null);
  readonly conditionSaving = signal(false);

  readonly conditionForm = this.fb.nonNullable.group({
    champ: 'ANCIENNETE_ANNEES' as CrossSellConditionChamp,
    operateur: 'GREATER_THAN' as CrossSellConditionOperateur,
    valeur: '',
    ordre: 0,
  });

  openConditions(regle: CrossSellRuleResponse): void {
    this.conditionsRegle.set(regle);
    this.conditionForm.reset({ champ: 'ANCIENNETE_ANNEES', operateur: 'GREATER_THAN', valeur: '', ordre: 0 });
    this.conditionsError.set(null);
    this.conditionsVisible.set(true);
    this.fetchConditions(regle.id);
  }

  private fetchConditions(regleId: number): void {
    this.conditionsLoading.set(true);
    this.conditionsError.set(null);
    this.crossSellService.listConditions(regleId).subscribe({
      next: (list) => {
        this.conditions.set(list);
        this.conditionsLoading.set(false);
      },
      error: () => {
        this.conditionsError.set('Impossible de charger les conditions.');
        this.conditionsLoading.set(false);
      },
    });
  }

  addCondition(): void {
    const regle = this.conditionsRegle();
    const raw = this.conditionForm.getRawValue();
    if (!regle) return;
    if (!raw.valeur.trim()) {
      this.conditionsError.set('La valeur de la condition est requise.');
      return;
    }
    this.conditionSaving.set(true);
    this.conditionsError.set(null);
    this.crossSellService
      .addCondition(regle.id, {
        champ: raw.champ,
        operateur: raw.operateur,
        valeur: raw.valeur.trim(),
        ordre: raw.ordre ? Number(raw.ordre) : undefined,
      })
      .subscribe({
        next: () => {
          this.conditionSaving.set(false);
          this.conditionForm.patchValue({ valeur: '' });
          this.fetchConditions(regle.id);
        },
        error: () => {
          this.conditionSaving.set(false);
          this.conditionsError.set("Impossible d'ajouter cette condition.");
        },
      });
  }

  // ================= PARAMÈTRES =================

  readonly parametres = signal<CrossSellParameterResponse[]>([]);
  readonly parametresLoading = signal(true);
  readonly parametresError = signal<string | null>(null);

  readonly parametreFormVisible = signal(false);
  readonly parametreFormSaving = signal(false);
  readonly parametreFormError = signal<string | null>(null);
  readonly editingParametre = signal<CrossSellParameterResponse | null>(null);

  readonly parametreForm = this.fb.nonNullable.group({ valeur: '', description: '' });

  fetchParametres(): void {
    this.parametresLoading.set(true);
    this.parametresError.set(null);
    this.crossSellService.listParametres().subscribe({
      next: (list) => {
        this.parametres.set(list);
        this.parametresLoading.set(false);
      },
      error: () => {
        this.parametresError.set('Impossible de charger les paramètres.');
        this.parametresLoading.set(false);
      },
    });
  }

  openEditParametre(param: CrossSellParameterResponse): void {
    this.editingParametre.set(param);
    this.parametreFormError.set(null);
    this.parametreForm.reset({ valeur: param.valeur, description: param.description ?? '' });
    this.parametreFormVisible.set(true);
  }

  saveParametre(): void {
    const param = this.editingParametre();
    const raw = this.parametreForm.getRawValue();
    if (!param) return;
    if (!raw.valeur.trim()) {
      this.parametreFormError.set('La valeur est requise.');
      return;
    }
    this.parametreFormSaving.set(true);
    this.parametreFormError.set(null);
    this.crossSellService
      .updateParametre({ cle: param.cle, valeur: raw.valeur.trim(), description: raw.description.trim() || undefined })
      .subscribe({
        next: () => {
          this.parametreFormSaving.set(false);
          this.parametreFormVisible.set(false);
          this.fetchParametres();
        },
        error: () => {
          this.parametreFormSaving.set(false);
          this.parametreFormError.set("Impossible d'enregistrer ce paramètre.");
        },
      });
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
