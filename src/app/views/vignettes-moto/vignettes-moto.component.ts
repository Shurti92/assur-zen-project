import { Component, computed, inject, signal } from '@angular/core';
import { LowerCasePipe, NgTemplateOutlet } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  ButtonDirective,
  FormControlDirective,
  FormCheckInputDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { VignetteMotoService } from '../../core/vignettes-moto/vignette-moto.service';
import { VignetteTarifService } from '../../core/vignettes-moto/vignette-tarif.service';
import { VignetteLivraisonService } from '../../core/vignettes-moto/vignette-livraison.service';
import { ALL_STATUTS, STATUT_VARIANT } from '../../core/vignettes-moto/vignette-moto-statut.util';
import {
  ChangerStatutDemandeVignetteMotoRequest,
  DemandeVignetteMotoRequest,
  DemandeVignetteMotoResponse,
  HistoriqueDemandeVignetteMotoResponse,
  LienSuiviVignetteResponse,
  RejeterDemandeVignetteMotoRequest,
  RevoquerLienSuiviRequest,
  SimulationTarifVignetteMotoResponse,
  VignetteCommissionResponse,
  VignetteCommissionStatut,
  VignetteMotoCategorieAge,
  VignetteMotoCategorieCylindree,
  VignetteMotoDocumentTypeCode,
  VignetteMotoStatut,
  VignetteMotoTypeDemande,
} from '../../core/vignettes-moto/models/vignette-moto.model';
import { TarifVignetteMotoRequest, TarifVignetteMotoResponse } from '../../core/vignettes-moto/models/vignette-tarif.model';
import { FraisLivraisonZoneRequest, FraisLivraisonZoneResponse } from '../../core/vignettes-moto/models/vignette-livraison.model';
import { AuthService } from '../../core/auth/auth.service';
import { GedDocumentResponse } from '../../core/ged/models/ged-document.model';
import { PartenaireService } from '../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../core/partenaire/models/partenaire.model';
import { CommercialService } from '../../core/commercial/commercial.service';
import { CommercialResponse } from '../../core/commercial/models/commercial.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

type View = 'demandes' | 'tarifs' | 'livraisons' | 'commissions';
const PAGE_SIZE = 20;

/** Suivi de statut simplifié (section 11 du besoin) — dérivé du statut courant, pas d'un
 * historique réel : aucun endpoint /historique n'existe côté backend pour ce module (contrairement
 * à Sorties de fonds/GED). ANNULEE/REJETEE sont des sorties de parcours, pas une étape de la
 * progression linéaire — voir statutMilestoneIndex().
 *
 * computeStatutMilestoneIndex renvoie l'index **1-based** de l'étape active (jamais 0) : un
 * template `@if (expr; as x)` traite 0 comme faux, donc un index 0-based casserait l'affichage
 * de BROUILLON (première étape) en le confondant avec "aucune étape" (ANNULEE/REJETEE, qui
 * renvoient null). Le gabarit retranche 1 pour retrouver l'index réel dans STATUT_MILESTONES. */
const STATUT_MILESTONES = [
  'vignettesMoto.milestone.creee',
  'vignettesMoto.milestone.paiement',
  'vignettesMoto.milestone.traitement',
  'vignettesMoto.milestone.edition',
  'vignettesMoto.milestone.livraison',
  'vignettesMoto.milestone.livree',
] as const;

/** POST /{id}/statut (ADMIN) ne fait progresser que vers PAYEE, EN_TRAITEMENT, EN_EDITION,
 * EDITEE, EN_LIVRAISON, LIVREE, ou REJETEE (à n'importe quelle étape) — voir le besoin livré le
 * 2026-09-17. BROUILLON/EN_ATTENTE_PAIEMENT/ANNULEE restent gérés par soumettre()/annuler(), pas
 * cette matrice. Purement indicatif côté UI (filtre les options du select) : le backend reste seul
 * juge via 409 si la transition demandée n'est pas autorisée.
 * REJETEE n'apparaît pas ici en tant que clé : LIVREE et REJETEE sont des sorties de parcours. */
const STATUT_TRANSITIONS_ADMIN: Partial<Record<VignetteMotoStatut, VignetteMotoStatut[]>> = {
  EN_ATTENTE_PAIEMENT: ['PAYEE', 'REJETEE'],
  PAYEE: ['EN_TRAITEMENT', 'REJETEE'],
  EN_TRAITEMENT: ['EN_EDITION', 'REJETEE'],
  EN_EDITION: ['EDITEE', 'REJETEE'],
  EDITEE: ['EN_LIVRAISON', 'REJETEE'],
  EN_LIVRAISON: ['LIVREE', 'REJETEE'],
};

function computeStatutMilestoneIndex(statut: VignetteMotoStatut): number | null {
  switch (statut) {
    case 'BROUILLON':
      return 1;
    case 'EN_ATTENTE_PAIEMENT':
      return 2;
    case 'PAYEE':
    case 'EN_TRAITEMENT':
      return 3;
    case 'EN_EDITION':
      return 4;
    case 'EDITEE':
    case 'EN_LIVRAISON':
      return 5;
    case 'LIVREE':
      return 6;
    default:
      return null;
  }
}

/** Un slot par document attendu — liste fixe (2 ou 3 selon le type de demande), pas une liste
 * dynamique : voir buildDocumentSlots(). */
interface DocSlot {
  typeCode: VignetteMotoDocumentTypeCode;
  file: File | null;
  uploading: boolean;
  uploaded: GedDocumentResponse | null;
  error: string | null;
}

/** "Documents" n'est atteint qu'après la création réussie de la demande (étape programmatique,
 * jamais via "Suivant") — voir confirmerDemande(). Le parcours ne peut pas le proposer plus tôt :
 * POST .../documents exige un id de demande déjà existant. */
const WIZARD_STEPS = [
  'vignettesMoto.wizard.step.type',
  'vignettesMoto.wizard.step.assure',
  'vignettesMoto.wizard.step.moto',
  'vignettesMoto.wizard.step.origine',
  'vignettesMoto.wizard.step.tarification',
  'vignettesMoto.wizard.step.recapitulatif',
  'vignettesMoto.wizard.step.documents',
] as const;

const STEP_TYPE = 0;
const STEP_ASSURE = 1;
const STEP_MOTO = 2;
const STEP_ORIGINE = 3;
const STEP_TARIFICATION = 4;
const STEP_RECAP = 5;
const STEP_DOCUMENTS = 6;

/** Origine de l'affaire (2026-09-19) — au plus un apporteur, partenaire OU commercial, jamais les
 * deux (voir vignette_commission_apporteur en mémoire projet). "aucun" = pas d'apporteur. */
type OrigineApporteurType = 'aucun' | 'partenaire' | 'commercial';

/** Un seul composant gère les 3 sous-vues (demandes/tarifs/livraisons) via `view`, comme
 * GedComponent/FundOutflowsComponent — pas de routes séparées. Le parcours de création est une
 * modale avec un stepper (même pattern que SouscriptionComponent), pas une page dédiée. */
@Component({
  selector: 'app-vignettes-moto',
  templateUrl: './vignettes-moto.component.html',
  imports: [
    ButtonDirective,
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
    LowerCasePipe,
    NgTemplateOutlet,
    TranslocoPipe,
  ],
})
export class VignettesMotoComponent {
  private readonly vignetteMotoService = inject(VignetteMotoService);
  private readonly tarifService = inject(VignetteTarifService);
  private readonly livraisonService = inject(VignetteLivraisonService);
  private readonly authService = inject(AuthService);
  private readonly partenaireService = inject(PartenaireService);
  private readonly commercialService = inject(CommercialService);
  private readonly fb = inject(FormBuilder);

  /** Tarifs/Livraisons sont réservés ADMIN côté backend (403 sinon, voir le contrat OpenAPI) —
   * ce n'est qu'un masquage d'affichage, pas une garde de route (le seul rempart réel reste le
   * 403 backend). Demandes reste ouvert à tout compte authentifié, scopé au créateur côté backend
   * depuis le durcissement IDOR du 2026-09-12 (voir docs/api-contracts/securisation-idor-2026-09-12.md,
   * section "Correction du 2026-09-12" — owner-scoping, pas ADMIN-only, comme pour les Assurés).
   * `role` vient tel quel de AuthUser (vérifié en direct : "ADMIN" sur ce compte) — ne pas confondre
   * avec NavPermissionsService.canAccessAdminMenus(), qui répond à une question différente
   * (masquage Paramétrage pour un Type Profil partenaire, pas ce rôle applicatif). */
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  readonly view = signal<View>('demandes');
  readonly statutMilestones = STATUT_MILESTONES;
  readonly allStatuts = ALL_STATUTS;
  readonly today = new Date().toISOString().slice(0, 10);

  typeDemandeLabelKey(typeDemande: VignetteMotoTypeDemande): string {
    return `vignettesMoto.typeDemande.${typeDemande}`;
  }

  statutLabelKey(statut: VignetteMotoStatut): string {
    return `vignettesMoto.statut.${statut}`;
  }

  categorieAgeLabelKey(categorie: VignetteMotoCategorieAge): string {
    return `vignettesMoto.categorieAge.${categorie}`;
  }

  categorieCylindreeLabelKey(categorie: VignetteMotoCategorieCylindree): string {
    return `vignettesMoto.categorieCylindree.${categorie}`;
  }

  documentTypeLabelKey(typeCode: VignetteMotoDocumentTypeCode): string {
    return `vignettesMoto.documentType.${typeCode}`;
  }

  statutVariant(statut: VignetteMotoStatut): string {
    return STATUT_VARIANT[statut];
  }

  statutMilestoneIndex(statut: VignetteMotoStatut): number | null {
    return computeStatutMilestoneIndex(statut);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ================================================================ LISTE DES DEMANDES ================================================================

  readonly demandes = signal<DemandeVignetteMotoResponse[]>([]);
  readonly demandesLoading = signal(true);
  readonly demandesError = signal<string | null>(null);
  readonly demandesPage = signal(0);
  readonly demandesTotalPages = signal(1);
  readonly demandesTotalElements = signal(0);
  readonly statutFilter = signal<'all' | VignetteMotoStatut>('all');

  readonly tarifs = signal<TarifVignetteMotoResponse[]>([]);
  readonly livraisons = signal<FraisLivraisonZoneResponse[]>([]);

  setView(view: View): void {
    if ((view === 'tarifs' || view === 'livraisons' || view === 'commissions') && !this.isAdmin()) return;
    this.view.set(view);
    if (view === 'tarifs' && this.tarifs().length === 0) this.fetchTarifs();
    if (view === 'livraisons' && this.livraisons().length === 0) this.fetchLivraisons();
    if (view === 'commissions') {
      this.commissionPaymentResult.set(null);
      this.fetchCommissions();
    }
  }

  constructor() {
    this.fetchDemandes();
  }

  fetchDemandes(): void {
    this.demandesLoading.set(true);
    this.demandesError.set(null);
    const statut = this.statutFilter();
    this.vignetteMotoService.search(this.demandesPage(), PAGE_SIZE, statut === 'all' ? undefined : statut).subscribe({
      next: (page) => {
        this.demandes.set(page.content);
        this.demandesTotalPages.set(Math.max(1, page.totalPages));
        this.demandesTotalElements.set(page.totalElements);
        this.demandesLoading.set(false);
      },
      error: () => {
        this.demandesError.set('vignettesMoto.errors.chargementDemandes');
        this.demandesLoading.set(false);
      },
    });
  }

  setStatutFilter(value: string): void {
    this.statutFilter.set(value as 'all' | VignetteMotoStatut);
    this.demandesPage.set(0);
    this.fetchDemandes();
  }

  goToDemandesPage(index: number): void {
    if (index < 0 || index >= this.demandesTotalPages()) return;
    this.demandesPage.set(index);
    this.fetchDemandes();
  }

  private fetchTarifs(): void {
    this.tarifService.list().subscribe({ next: (list) => this.tarifs.set(list), error: () => undefined });
  }

  private fetchLivraisons(): void {
    this.livraisonService.list().subscribe({ next: (list) => this.livraisons.set(list), error: () => undefined });
  }

  // ================================================================ COMMISSIONS APPORTEUR (ADMIN, livré 2026-09-19) ================================================================

  /** Registre `VignetteCommission` — un enregistrement par demande avec apporteur, montant fixé par
   * le backend (500 FCFA Abidjan / 1000 hors Abidjan). Toute la vue est ADMIN-only (401/403 sinon) ;
   * payer() marque payé ET envoie un SMS à l'apporteur, donc irréversible côté notification. */
  readonly commissions = signal<VignetteCommissionResponse[]>([]);
  readonly commissionsLoading = signal(false);
  readonly commissionsError = signal<string | null>(null);
  readonly commissionsPage = signal(0);
  readonly commissionsTotalPages = signal(1);
  readonly commissionsTotalElements = signal(0);
  readonly commissionStatutFilter = signal<'all' | VignetteCommissionStatut>('pending');
  readonly selectedCommissionIds = signal<ReadonlySet<number>>(new Set());
  readonly commissionPaymentResult = signal<{ count: number; notificationsEchouees: number } | null>(null);

  readonly pendingCommissions = computed(() => this.commissions().filter((c) => c.statut === 'pending'));
  readonly allPendingCommissionsSelected = computed(
    () => this.pendingCommissions().length > 0 && this.pendingCommissions().every((c) => this.selectedCommissionIds().has(c.id)),
  );
  readonly selectedCommissionsTotal = computed(() =>
    this.commissions().filter((c) => this.selectedCommissionIds().has(c.id)).reduce((sum, c) => sum + c.montant, 0),
  );

  commissionStatutLabelKey(statut: VignetteCommissionStatut): string {
    return `vignettesMoto.commissionStatut.${statut}`;
  }

  commissionStatutVariant(statut: VignetteCommissionStatut): string {
    return statut === 'paid' ? 'success' : 'warning';
  }

  fetchCommissions(): void {
    this.commissionsLoading.set(true);
    this.commissionsError.set(null);
    const statut = this.commissionStatutFilter();
    this.vignetteMotoService.listCommissions(this.commissionsPage(), PAGE_SIZE, statut === 'all' ? undefined : statut).subscribe({
      next: (page) => {
        this.commissions.set(page.content);
        this.commissionsTotalPages.set(Math.max(1, page.totalPages));
        this.commissionsTotalElements.set(page.totalElements);
        this.commissionsLoading.set(false);
      },
      error: () => {
        this.commissionsError.set('vignettesMoto.errors.chargementCommissions');
        this.commissionsLoading.set(false);
      },
    });
  }

  setCommissionStatutFilter(value: string): void {
    this.commissionStatutFilter.set(value as 'all' | VignetteCommissionStatut);
    this.commissionsPage.set(0);
    this.selectedCommissionIds.set(new Set());
    this.commissionPaymentResult.set(null);
    this.fetchCommissions();
  }

  goToCommissionsPage(index: number): void {
    if (index < 0 || index >= this.commissionsTotalPages()) return;
    this.commissionsPage.set(index);
    this.selectedCommissionIds.set(new Set());
    this.fetchCommissions();
  }

  toggleCommission(id: number): void {
    const next = new Set(this.selectedCommissionIds());
    if (!next.delete(id)) next.add(id);
    this.selectedCommissionIds.set(next);
  }

  toggleAllPendingCommissions(): void {
    this.selectedCommissionIds.set(
      this.allPendingCommissionsSelected() ? new Set() : new Set(this.pendingCommissions().map((c) => c.id)),
    );
  }

  // -- paiement par lot (référence de paiement obligatoire) --

  readonly payCommissionsModalVisible = signal(false);
  readonly payCommissionsForm = this.fb.nonNullable.group({ reference: [''] });
  readonly payCommissionsSaving = signal(false);
  readonly payCommissionsError = signal<string | null>(null);

  askPayCommissions(): void {
    if (this.selectedCommissionIds().size === 0) return;
    this.payCommissionsError.set(null);
    this.payCommissionsForm.reset({ reference: '' });
    this.payCommissionsModalVisible.set(true);
  }

  submitPayCommissions(): void {
    const reference = this.payCommissionsForm.getRawValue().reference.trim();
    if (!reference) {
      this.payCommissionsError.set('vignettesMoto.errors.referencePaiementObligatoire');
      return;
    }
    const commissionIds = [...this.selectedCommissionIds()];
    if (commissionIds.length === 0) return;
    this.payCommissionsSaving.set(true);
    this.payCommissionsError.set(null);
    this.vignetteMotoService.payerCommissions({ commissionIds, reference }).subscribe({
      next: (updated) => {
        this.payCommissionsSaving.set(false);
        this.payCommissionsModalVisible.set(false);
        this.selectedCommissionIds.set(new Set());
        this.commissionPaymentResult.set({
          count: updated.length,
          notificationsEchouees: updated.filter((c) => c.notificationEnvoyee === false).length,
        });
        this.fetchCommissions();
      },
      error: () => {
        this.payCommissionsSaving.set(false);
        this.payCommissionsError.set('vignettesMoto.errors.paiementCommissions');
      },
    });
  }

  // ================================================================ PARCOURS DE CRÉATION ================================================================

  /** Titre + icône de l'en-tête du parcours pour chaque étape (même ordre que WIZARD_STEPS), d'après
   * la maquette validée. Les icônes sont des noms CoreUI présents dans `iconSubset`. */
  readonly stepMeta = [
    { titleKey: 'vignettesMoto.wizard.titre', icon: 'cilCarAlt' },
    { titleKey: 'vignettesMoto.wizard.step2.titre', icon: 'cilUser' },
    { titleKey: 'vignettesMoto.wizard.step3.titre', icon: 'cilCarAlt' },
    { titleKey: 'vignettesMoto.wizard.step4b.titre', icon: 'cilPeople' },
    { titleKey: 'vignettesMoto.wizard.step4.titre', icon: 'cilCalculator' },
    { titleKey: 'vignettesMoto.wizard.step5.titre', icon: 'cilTask' },
    { titleKey: 'vignettesMoto.wizard.step6.titre', icon: 'cilFile' },
  ] as const;

  /** Icône par type de document pour les lignes de l'étape Documents. */
  private static readonly DOCUMENT_ICONS: Record<VignetteMotoDocumentTypeCode, string> = {
    CNI_ASSURE: 'cilUser',
    RECU_ACHAT_MOTO: 'cilDescription',
    ANCIENNE_VIGNETTE: 'cilTags',
    PHOTO_MOTO: 'cilCarAlt',
    PHOTO_CHASSIS: 'cilCarAlt',
    ASSURANCE: 'cilLockLocked',
  };

  /** Pièces introduites le 2026-09-19 — badge « Nouveau » et texte d'aide dans la maquette. */
  private static readonly NEW_DOCUMENT_TYPES: readonly VignetteMotoDocumentTypeCode[] = ['PHOTO_MOTO', 'PHOTO_CHASSIS', 'ASSURANCE'];

  documentIcon(typeCode: VignetteMotoDocumentTypeCode): string {
    return VignettesMotoComponent.DOCUMENT_ICONS[typeCode];
  }

  isNewDocumentType(typeCode: VignetteMotoDocumentTypeCode): boolean {
    return VignettesMotoComponent.NEW_DOCUMENT_TYPES.includes(typeCode);
  }

  readonly documentsDeposes = computed(() => this.documentSlots().filter((s) => !!s.uploaded).length);
  readonly dragOverTypeCode = signal<VignetteMotoDocumentTypeCode | null>(null);

  readonly createFormVisible = signal(false);
  readonly steps = WIZARD_STEPS;
  readonly currentStep = signal(0);
  readonly furthestStep = signal(0);
  readonly wizardError = signal<string | null>(null);

  readonly demandeForm = this.fb.nonNullable.group({
    typeDemande: ['NOUVELLE_DEMANDE' as VignetteMotoTypeDemande],
    nomAssure: [''],
    prenomsAssure: [''],
    telephoneAssure: [''],
    telephoneAssureSecondaire: [''],
    villeLivraison: [''],
    adresseLivraison: [''],
    zoneLivraison: [''],
    marqueMoto: [''],
    modeleMoto: [''],
    numeroChassisMoto: [''],
    cylindreeMoto: [''],
    datePremiereMiseCirculation: [''],
  });

  readonly simulating = signal(false);
  readonly simulationResult = signal<SimulationTarifVignetteMotoResponse | null>(null);
  readonly simulationError = signal<string | null>(null);

  readonly creating = signal(false);
  readonly createdDemande = signal<DemandeVignetteMotoResponse | null>(null);

  readonly documentSlots = signal<DocSlot[]>([]);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly submitted = signal(false);

  // -- Origine de l'affaire (apporteur partenaire/commercial, confirmé 2026-09-19) --

  readonly origineType = signal<OrigineApporteurType>('aucun');
  readonly origineSearch = signal('');
  readonly partenaires = signal<PartenaireResponse[]>([]);
  readonly partenairesLoading = signal(false);
  readonly commerciaux = signal<CommercialResponse[]>([]);
  readonly commerciauxLoading = signal(false);
  readonly selectedPartenaireId = signal<number | null>(null);
  readonly selectedCommercialId = signal<number | null>(null);

  readonly filteredPartenaires = computed(() => {
    const q = this.origineSearch().trim().toLowerCase();
    if (!q) return this.partenaires();
    return this.partenaires().filter((p) => `${p.nom} ${p.prenoms}`.toLowerCase().includes(q));
  });

  readonly filteredCommerciaux = computed(() => {
    const q = this.origineSearch().trim().toLowerCase();
    if (!q) return this.commerciaux();
    return this.commerciaux().filter((c) => `${c.firstname} ${c.lastname}`.toLowerCase().includes(q));
  });

  readonly selectedApporteurNom = computed(() => {
    if (this.origineType() === 'partenaire') {
      const p = this.partenaires().find((x) => x.id === this.selectedPartenaireId());
      return p ? `${p.nom} ${p.prenoms}` : null;
    }
    if (this.origineType() === 'commercial') {
      const c = this.commerciaux().find((x) => x.id === this.selectedCommercialId());
      return c ? `${c.firstname} ${c.lastname}` : null;
    }
    return null;
  });

  setOrigineType(type: OrigineApporteurType): void {
    this.wizardError.set(null);
    this.origineType.set(type);
    this.selectedPartenaireId.set(null);
    this.selectedCommercialId.set(null);
    this.origineSearch.set('');
    this.simulationResult.set(null);
    this.simulationError.set(null);
  }

  selectPartenaireApporteur(id: number): void {
    this.wizardError.set(null);
    this.selectedPartenaireId.set(id);
    this.simulationResult.set(null);
  }

  selectCommercialApporteur(id: number): void {
    this.wizardError.set(null);
    this.selectedCommercialId.set(id);
    this.simulationResult.set(null);
  }

  private fetchPartenaires(): void {
    this.partenairesLoading.set(true);
    this.partenaireService.listAll().subscribe({
      next: (page) => {
        this.partenaires.set(page.content.filter((p) => p.active));
        this.partenairesLoading.set(false);
      },
      error: () => this.partenairesLoading.set(false),
    });
  }

  private fetchCommerciaux(): void {
    this.commerciauxLoading.set(true);
    this.commercialService.list(0, 500).subscribe({
      next: (page) => {
        this.commerciaux.set(page.content);
        this.commerciauxLoading.set(false);
      },
      error: () => this.commerciauxLoading.set(false),
    });
  }

  private validateOrigineStep(): boolean {
    const type = this.origineType();
    if (type === 'partenaire' && !this.selectedPartenaireId()) {
      this.wizardError.set('vignettesMoto.errors.apporteurPartenaireObligatoire');
      return false;
    }
    if (type === 'commercial' && !this.selectedCommercialId()) {
      this.wizardError.set('vignettesMoto.errors.apporteurCommercialObligatoire');
      return false;
    }
    return true;
  }

  private apporteurPayload(): { partenaireApporteurId?: number; commercialApporteurId?: number } {
    if (this.origineType() === 'partenaire' && this.selectedPartenaireId()) {
      return { partenaireApporteurId: this.selectedPartenaireId()! };
    }
    if (this.origineType() === 'commercial' && this.selectedCommercialId()) {
      return { commercialApporteurId: this.selectedCommercialId()! };
    }
    return {};
  }

  openCreateDemande(): void {
    this.wizardError.set(null);
    this.currentStep.set(STEP_TYPE);
    this.furthestStep.set(STEP_TYPE);
    this.demandeForm.reset({
      typeDemande: 'NOUVELLE_DEMANDE',
      nomAssure: '',
      prenomsAssure: '',
      telephoneAssure: '',
      telephoneAssureSecondaire: '',
      villeLivraison: '',
      adresseLivraison: '',
      zoneLivraison: '',
      marqueMoto: '',
      modeleMoto: '',
      numeroChassisMoto: '',
      cylindreeMoto: '',
      datePremiereMiseCirculation: '',
    });
    this.simulationResult.set(null);
    this.simulationError.set(null);
    this.creating.set(false);
    this.createdDemande.set(null);
    this.documentSlots.set([]);
    this.submitError.set(null);
    this.submitted.set(false);
    this.origineType.set('aucun');
    this.origineSearch.set('');
    this.selectedPartenaireId.set(null);
    this.selectedCommercialId.set(null);
    if (this.livraisons().length === 0) this.fetchLivraisons();
    if (this.partenaires().length === 0) this.fetchPartenaires();
    if (this.commerciaux().length === 0) this.fetchCommerciaux();
    this.createFormVisible.set(true);
  }

  /** Une demande déjà créée (BROUILLON) reste en base même si la modale se ferme avant la
   * soumission — l'utilisateur pourra la reprendre depuis la liste (écran à venir). On
   * rafraîchit donc la liste dans ce cas plutôt que de laisser croire que rien ne s'est passé. */
  closeCreateDemande(): void {
    this.createFormVisible.set(false);
    if (this.createdDemande()) this.fetchDemandes();
  }

  /** Purement informatif pendant la saisie — jamais envoyé au backend. Le seul âge qui compte
   * est ageMotoCalcule, renvoyé par le backend à la simulation/création (voir section 6 : la
   * tarification appartient exclusivement au backend). */
  ageApproximatif(): number | null {
    const value = this.demandeForm.controls.datePremiereMiseCirculation.value;
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    const now = new Date();
    let age = now.getFullYear() - date.getFullYear();
    const beforeAnniversary =
      now.getMonth() < date.getMonth() || (now.getMonth() === date.getMonth() && now.getDate() < date.getDate());
    if (beforeAnniversary) age -= 1;
    return Math.max(age, 0);
  }

  nextStep(): void {
    this.wizardError.set(null);
    if (this.currentStep() === STEP_ASSURE && !this.validateAssureStep()) return;
    if (this.currentStep() === STEP_MOTO && !this.validateMotoStep()) return;
    if (this.currentStep() === STEP_ORIGINE && !this.validateOrigineStep()) return;
    if (this.currentStep() === STEP_TARIFICATION && !this.simulationResult()) {
      this.wizardError.set('vignettesMoto.errors.calculerTarifAvant');
      return;
    }
    const next = Math.min(this.currentStep() + 1, this.steps.length - 2);
    this.currentStep.set(next);
    this.furthestStep.set(Math.max(this.furthestStep(), next));
  }

  prevStep(): void {
    if (this.createdDemande()) return;
    this.wizardError.set(null);
    this.currentStep.set(Math.max(this.currentStep() - 1, 0));
  }

  goToStep(index: number): void {
    if (this.createdDemande()) return;
    if (index > this.furthestStep()) return;
    this.wizardError.set(null);
    this.currentStep.set(index);
  }

  private validateAssureStep(): boolean {
    const raw = this.demandeForm.getRawValue();
    if (
      !raw.nomAssure.trim() ||
      !raw.prenomsAssure.trim() ||
      !raw.telephoneAssure.trim() ||
      !raw.villeLivraison.trim() ||
      !raw.adresseLivraison.trim() ||
      !raw.zoneLivraison
    ) {
      this.wizardError.set('vignettesMoto.errors.champsAssureObligatoires');
      return false;
    }
    if (raw.telephoneAssure.trim().length > 20) {
      this.wizardError.set('vignettesMoto.errors.telephoneTropLong');
      return false;
    }
    if (raw.telephoneAssureSecondaire.trim().length > 20) {
      this.wizardError.set('vignettesMoto.errors.telephoneTropLong');
      return false;
    }
    return true;
  }

  private validateMotoStep(): boolean {
    const raw = this.demandeForm.getRawValue();
    if (!raw.marqueMoto.trim() || !raw.modeleMoto.trim() || !raw.numeroChassisMoto.trim()) {
      this.wizardError.set('vignettesMoto.errors.champsMotoObligatoires');
      return false;
    }
    const cylindree = Number(raw.cylindreeMoto);
    if (!raw.cylindreeMoto || !Number.isFinite(cylindree) || cylindree <= 0) {
      this.wizardError.set('vignettesMoto.errors.cylindreeInvalide');
      return false;
    }
    if (!raw.datePremiereMiseCirculation) {
      this.wizardError.set('vignettesMoto.errors.dateCirculationObligatoire');
      return false;
    }
    if (raw.datePremiereMiseCirculation > this.today) {
      this.wizardError.set('vignettesMoto.errors.dateCirculationFutur');
      return false;
    }
    return true;
  }

  /** Aucun tarif codé côté Angular — on envoie les données, on affiche ce que le backend renvoie.
   * L'apporteur (si renseigné à l'étape Origine) est déjà validé ici : un id inexistant renvoie un
   * 404 dès la simulation (voir vignette_commission_apporteur en mémoire projet). */
  simulerTarif(): void {
    if (!this.validateMotoStep() || !this.validateOrigineStep()) return;
    const raw = this.demandeForm.getRawValue();
    this.simulating.set(true);
    this.simulationError.set(null);
    this.simulationResult.set(null);
    this.vignetteMotoService
      .simulerTarification({
        datePremiereMiseCirculation: raw.datePremiereMiseCirculation,
        cylindree: Number(raw.cylindreeMoto),
        zoneLivraison: raw.zoneLivraison,
        ...this.apporteurPayload(),
      })
      .subscribe({
        next: (result) => {
          this.simulating.set(false);
          this.simulationResult.set(result);
        },
        error: (err) => {
          this.simulating.set(false);
          this.simulationError.set(
            err?.status === 404 ? err?.error?.message || 'vignettesMoto.errors.calculTarifIntrouvable' : 'vignettesMoto.errors.calculTarif',
          );
        },
      });
  }

  confirmerDemande(): void {
    const raw = this.demandeForm.getRawValue();
    const telephoneAssureSecondaire = raw.telephoneAssureSecondaire.trim();
    const payload: DemandeVignetteMotoRequest = {
      typeDemande: raw.typeDemande,
      nomAssure: raw.nomAssure.trim(),
      prenomsAssure: raw.prenomsAssure.trim(),
      telephoneAssure: raw.telephoneAssure.trim(),
      ...(telephoneAssureSecondaire ? { telephoneAssureSecondaire } : {}),
      adresseLivraison: raw.adresseLivraison.trim(),
      villeLivraison: raw.villeLivraison.trim(),
      zoneLivraison: raw.zoneLivraison,
      marqueMoto: raw.marqueMoto.trim(),
      modeleMoto: raw.modeleMoto.trim(),
      numeroChassisMoto: raw.numeroChassisMoto.trim(),
      cylindreeMoto: Number(raw.cylindreeMoto),
      datePremiereMiseCirculation: raw.datePremiereMiseCirculation,
      ...this.apporteurPayload(),
    };
    this.creating.set(true);
    this.wizardError.set(null);
    this.vignetteMotoService.create(payload).subscribe({
      next: (demande) => {
        this.creating.set(false);
        this.createdDemande.set(demande);
        this.documentSlots.set(this.buildDocumentSlots(demande.typeDemande));
        this.currentStep.set(STEP_DOCUMENTS);
        this.furthestStep.set(STEP_DOCUMENTS);
      },
      error: (err) => {
        this.creating.set(false);
        this.wizardError.set(err?.status === 404 ? 'vignettesMoto.errors.tarifIntrouvable' : 'vignettesMoto.errors.creationDemande');
      },
    });
  }

  /** Nouvelle demande : 5 documents requis (CNI, reçu d'achat, photo moto, photo châssis,
   * attestation d'assurance — 3 nouveaux types confirmés 2026-09-19). Renouvellement : inchangé,
   * toujours 3 (les 2 précédents + copie de l'ancienne vignette). */
  private buildDocumentSlots(typeDemande: VignetteMotoTypeDemande): DocSlot[] {
    if (typeDemande === 'RENOUVELLEMENT') {
      return [
        { typeCode: 'CNI_ASSURE', file: null, uploading: false, uploaded: null, error: null },
        { typeCode: 'RECU_ACHAT_MOTO', file: null, uploading: false, uploaded: null, error: null },
        { typeCode: 'ANCIENNE_VIGNETTE', file: null, uploading: false, uploaded: null, error: null },
      ];
    }
    return [
      { typeCode: 'CNI_ASSURE', file: null, uploading: false, uploaded: null, error: null },
      { typeCode: 'RECU_ACHAT_MOTO', file: null, uploading: false, uploaded: null, error: null },
      { typeCode: 'PHOTO_MOTO', file: null, uploading: false, uploaded: null, error: null },
      { typeCode: 'PHOTO_CHASSIS', file: null, uploading: false, uploaded: null, error: null },
      { typeCode: 'ASSURANCE', file: null, uploading: false, uploaded: null, error: null },
    ];
  }

  onDocumentSelected(typeCode: VignetteMotoDocumentTypeCode, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadDocument(typeCode, file);
    input.value = '';
  }

  onDocumentDropped(typeCode: VignetteMotoDocumentTypeCode, event: DragEvent): void {
    event.preventDefault();
    this.dragOverTypeCode.set(null);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.uploadDocument(typeCode, file);
  }

  onDocumentDragOver(event: DragEvent, typeCode?: VignetteMotoDocumentTypeCode): void {
    event.preventDefault();
    if (typeCode) this.dragOverTypeCode.set(typeCode);
  }

  private uploadDocument(typeCode: VignetteMotoDocumentTypeCode, file: File): void {
    const demande = this.createdDemande();
    if (!demande) return;
    this.updateSlot(typeCode, { file, uploading: true, uploaded: null, error: null });
    this.vignetteMotoService.uploadDocument(demande.id, typeCode, file).subscribe({
      next: (doc) => this.updateSlot(typeCode, { uploading: false, uploaded: doc }),
      error: (err) => {
        this.updateSlot(typeCode, {
          uploading: false,
          error: err?.status === 415 ? 'vignettesMoto.errors.formatNonAccepte' : 'vignettesMoto.errors.depotDocument',
        });
      },
    });
  }

  clearDocument(typeCode: VignetteMotoDocumentTypeCode): void {
    this.updateSlot(typeCode, { file: null, uploaded: null, error: null });
  }

  private updateSlot(typeCode: VignetteMotoDocumentTypeCode, patch: Partial<DocSlot>): void {
    this.documentSlots.set(this.documentSlots().map((slot) => (slot.typeCode === typeCode ? { ...slot, ...patch } : slot)));
  }

  /** 409 si un document obligatoire manque — le message backend liste déjà précisément les
   * typeCode manquants, on l'affiche tel quel plutôt que de le reformuler. */
  soumettreDemande(): void {
    const demande = this.createdDemande();
    if (!demande) return;
    this.submitting.set(true);
    this.submitError.set(null);
    this.vignetteMotoService.soumettre(demande.id).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.createdDemande.set(updated);
        this.submitted.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.submitError.set(err?.error?.message || 'vignettesMoto.errors.soumissionDemande');
      },
    });
  }

  // ================================================================ DÉTAIL D'UNE DEMANDE ================================================================

  readonly detailVisible = signal(false);
  readonly selectedDemande = signal<DemandeVignetteMotoResponse | null>(null);
  readonly detailDocuments = signal<GedDocumentResponse[]>([]);
  readonly detailDocumentsLoading = signal(false);
  readonly detailActionPending = signal(false);
  readonly detailActionError = signal<string | null>(null);

  openDetail(demande: DemandeVignetteMotoResponse): void {
    this.selectedDemande.set(demande);
    this.detailActionError.set(null);
    this.detailDocuments.set([]);
    this.newDocError.set(null);
    this.historique.set([]);
    this.historiqueExpanded.set(false);
    this.historiqueError.set(null);
    this.lienRegenerated.set(null);
    this.lienRegenerateError.set(null);
    this.detailVisible.set(true);
    this.fetchDetailDocuments(demande.id);
    if (this.isAdmin()) this.fetchHistorique(demande.id);
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.selectedDemande.set(null);
  }

  private fetchDetailDocuments(id: number): void {
    this.detailDocumentsLoading.set(true);
    this.vignetteMotoService.listDocuments(id, 0, 50).subscribe({
      next: (page) => {
        this.detailDocuments.set(page.content);
        this.detailDocumentsLoading.set(false);
      },
      error: () => this.detailDocumentsLoading.set(false),
    });
  }

  downloadDetailDocument(doc: GedDocumentResponse): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    this.vignetteMotoService.downloadDocument(demande.id, doc.id).subscribe({
      next: (blob) => this.downloadBlob(blob, doc.title || doc.documentNumber),
      error: () => this.detailActionError.set('vignettesMoto.errors.telechargementDocument'),
    });
  }

  private applyDemandeUpdate(updated: DemandeVignetteMotoResponse): void {
    this.selectedDemande.set(updated);
    this.demandes.set(this.demandes().map((d) => (d.id === updated.id ? updated : d)));
  }

  canSubmitDemande(statut: VignetteMotoStatut): boolean {
    return statut === 'BROUILLON';
  }

  canCancelDemande(statut: VignetteMotoStatut): boolean {
    return statut !== 'LIVREE' && statut !== 'ANNULEE' && statut !== 'REJETEE';
  }

  /** Réservé ADMIN (2026-09-12) : rejeter sa propre demande n'a pas de sens pour un créateur —
   * décision confirmée côté métier suite à la question ouverte du backend (voir
   * docs/api-contracts/securisation-idor-2026-09-12.md, section "Correction du 2026-09-12"). Le
   * backend doit encore restreindre PATCH /vignettes-moto/demandes/{id}/rejeter en conséquence ;
   * masquage d'affichage en attendant, comme partout ailleurs dans ce composant. */
  canRejectDemande(statut: VignetteMotoStatut): boolean {
    return this.isAdmin() && (statut === 'BROUILLON' || statut === 'EN_ATTENTE_PAIEMENT');
  }

  doSoumettreDetail(): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    this.detailActionPending.set(true);
    this.detailActionError.set(null);
    this.vignetteMotoService.soumettre(demande.id).subscribe({
      next: (updated) => {
        this.detailActionPending.set(false);
        this.applyDemandeUpdate(updated);
      },
      error: (err) => {
        this.detailActionPending.set(false);
        this.detailActionError.set(err?.error?.message || 'vignettesMoto.errors.soumissionDemandeDetail');
      },
    });
  }

  // -- ajouter un document manquant (uniquement tant que la demande est en Brouillon) --

  readonly newDocTypeCode = signal<VignetteMotoDocumentTypeCode>('CNI_ASSURE');
  readonly newDocUploading = signal(false);
  readonly newDocError = signal<string | null>(null);

  onDetailDocSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadDetailDocument(file);
    input.value = '';
  }

  onDetailDocDropped(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) this.uploadDetailDocument(file);
  }

  onDetailDocDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  private uploadDetailDocument(file: File): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    this.newDocUploading.set(true);
    this.newDocError.set(null);
    this.vignetteMotoService.uploadDocument(demande.id, this.newDocTypeCode(), file).subscribe({
      next: () => {
        this.newDocUploading.set(false);
        this.fetchDetailDocuments(demande.id);
      },
      error: (err) => {
        this.newDocUploading.set(false);
        this.newDocError.set(err?.status === 415 ? 'vignettesMoto.errors.formatNonAccepte' : 'vignettesMoto.errors.depotDocument');
      },
    });
  }

  // -- annuler (aucun motif requis côté API) --

  readonly cancelTarget = signal<DemandeVignetteMotoResponse | null>(null);
  readonly cancelling = signal(false);

  askCancelDemande(): void {
    this.cancelTarget.set(this.selectedDemande());
  }

  confirmCancelDemande(): void {
    const demande = this.cancelTarget();
    if (!demande) return;
    this.cancelling.set(true);
    this.vignetteMotoService.annuler(demande.id).subscribe({
      next: (updated) => {
        this.cancelling.set(false);
        this.cancelTarget.set(null);
        this.applyDemandeUpdate(updated);
      },
      error: () => {
        this.cancelling.set(false);
        this.cancelTarget.set(null);
        this.detailActionError.set('vignettesMoto.errors.annulationDemande');
      },
    });
  }

  // -- rejeter (motif obligatoire) --

  readonly rejectModalVisible = signal(false);
  readonly rejectForm = this.fb.nonNullable.group({ motif: [''] });
  readonly rejectSaving = signal(false);
  readonly rejectError = signal<string | null>(null);

  askRejectDemande(): void {
    this.rejectError.set(null);
    this.rejectForm.reset({ motif: '' });
    this.rejectModalVisible.set(true);
  }

  submitRejectDemande(): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    const motif = this.rejectForm.getRawValue().motif.trim();
    if (!motif) {
      this.rejectError.set('vignettesMoto.errors.motifObligatoire');
      return;
    }
    this.rejectSaving.set(true);
    this.rejectError.set(null);
    const payload: RejeterDemandeVignetteMotoRequest = { motif };
    this.vignetteMotoService.rejeter(demande.id, payload).subscribe({
      next: (updated) => {
        this.rejectSaving.set(false);
        this.rejectModalVisible.set(false);
        this.applyDemandeUpdate(updated);
      },
      error: () => {
        this.rejectSaving.set(false);
        this.rejectError.set('vignettesMoto.errors.rejetDemande');
      },
    });
  }

  // ================================================================ SUIVI D'ÉVOLUTION + LIEN SMS (livré 2026-09-17, ADMIN) ================================================================

  readonly historique = signal<HistoriqueDemandeVignetteMotoResponse[]>([]);
  readonly historiqueLoading = signal(false);
  readonly historiqueError = signal<string | null>(null);

  /** Timeline du détail : le plus récent d'abord, aperçu limité à 3 entrées + « N étapes précédentes »
   * (maquette « Détail admin »). */
  private static readonly HISTORIQUE_APERCU = 3;
  readonly historiqueExpanded = signal(false);
  readonly historiqueTriee = computed(() => [...this.historique()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
  readonly historiqueVisible = computed(() =>
    this.historiqueExpanded() ? this.historiqueTriee() : this.historiqueTriee().slice(0, VignettesMotoComponent.HISTORIQUE_APERCU),
  );
  readonly historiqueMasquees = computed(() => Math.max(0, this.historiqueTriee().length - VignettesMotoComponent.HISTORIQUE_APERCU));

  toggleHistoriqueExpanded(): void {
    this.historiqueExpanded.update((v) => !v);
  }

  /** Variante de la pastille de statut d'un document GED : rejeté/supprimé = rouge, archivé = orange,
   * déposé/validé = vert (comme dans la maquette). */
  documentPillVariant(status: GedDocumentResponse['status']): '' | 'warning' | 'danger' {
    if (status === 'REJECTED' || status === 'DELETED') return 'danger';
    if (status === 'ARCHIVED') return 'warning';
    return '';
  }

  private fetchHistorique(id: number): void {
    this.historiqueLoading.set(true);
    this.historiqueError.set(null);
    this.vignetteMotoService.historique(id).subscribe({
      next: (list) => {
        this.historique.set(list);
        this.historiqueLoading.set(false);
      },
      error: () => {
        this.historiqueError.set('vignettesMoto.errors.chargementHistorique');
        this.historiqueLoading.set(false);
      },
    });
  }

  /** Options affichées dans le select "Changer le statut" — purement indicatif, le backend reste
   * seul juge de la transition (409 si non autorisée par sa matrice de statuts). */
  statutsCiblesDisponibles(statut: VignetteMotoStatut): VignetteMotoStatut[] {
    return STATUT_TRANSITIONS_ADMIN[statut] ?? [];
  }

  readonly statutChangeModalVisible = signal(false);
  readonly statutChangeForm = this.fb.nonNullable.group({
    statut: ['' as VignetteMotoStatut | ''],
    motif: [''],
    referenceOperation: [''],
  });
  readonly statutChangeSaving = signal(false);
  readonly statutChangeError = signal<string | null>(null);

  askChangerStatut(): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    const options = this.statutsCiblesDisponibles(demande.statut);
    this.statutChangeError.set(null);
    this.statutChangeForm.reset({ statut: options[0] ?? '', motif: '', referenceOperation: '' });
    this.statutChangeModalVisible.set(true);
  }

  submitChangerStatut(): void {
    const demande = this.selectedDemande();
    const raw = this.statutChangeForm.getRawValue();
    if (!demande || !raw.statut) return;
    this.statutChangeSaving.set(true);
    this.statutChangeError.set(null);
    const payload: ChangerStatutDemandeVignetteMotoRequest = {
      statut: raw.statut,
      motif: raw.motif.trim() || undefined,
      referenceOperation: raw.referenceOperation.trim() || undefined,
    };
    this.vignetteMotoService.changerStatut(demande.id, payload).subscribe({
      next: (updated) => {
        this.statutChangeSaving.set(false);
        this.statutChangeModalVisible.set(false);
        this.applyDemandeUpdate(updated);
        this.fetchHistorique(updated.id);
      },
      error: (err) => {
        this.statutChangeSaving.set(false);
        this.statutChangeError.set(
          err?.status === 409 ? err?.error?.message || 'vignettesMoto.errors.transitionStatutRefusee' : 'vignettesMoto.errors.changementStatut',
        );
      },
    });
  }

  // -- lien de suivi public (régénération avec envoi SMS / révocation) --

  readonly lienRegenerating = signal(false);
  readonly lienRegenerated = signal<LienSuiviVignetteResponse | null>(null);
  readonly lienRegenerateError = signal<string | null>(null);

  regenererLienSuivi(): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    this.lienRegenerating.set(true);
    this.lienRegenerateError.set(null);
    this.lienRegenerated.set(null);
    this.vignetteMotoService.regenererLienSuivi(demande.id).subscribe({
      next: (result) => {
        this.lienRegenerating.set(false);
        this.lienRegenerated.set(result);
      },
      error: () => {
        this.lienRegenerating.set(false);
        this.lienRegenerateError.set('vignettesMoto.errors.regenerationLien');
      },
    });
  }

  readonly revokeLienModalVisible = signal(false);
  readonly revokeLienForm = this.fb.nonNullable.group({ motif: [''] });
  readonly revokeLienSaving = signal(false);
  readonly revokeLienError = signal<string | null>(null);

  askRevoquerLien(): void {
    this.revokeLienError.set(null);
    this.revokeLienForm.reset({ motif: '' });
    this.revokeLienModalVisible.set(true);
  }

  submitRevoquerLien(): void {
    const demande = this.selectedDemande();
    if (!demande) return;
    const motif = this.revokeLienForm.getRawValue().motif.trim();
    if (!motif) {
      this.revokeLienError.set('vignettesMoto.errors.motifObligatoire');
      return;
    }
    this.revokeLienSaving.set(true);
    this.revokeLienError.set(null);
    const payload: RevoquerLienSuiviRequest = { motif };
    this.vignetteMotoService.revoquerLienSuivi(demande.id, payload).subscribe({
      next: () => {
        this.revokeLienSaving.set(false);
        this.revokeLienModalVisible.set(false);
      },
      error: () => {
        this.revokeLienSaving.set(false);
        this.revokeLienError.set('vignettesMoto.errors.revocationLien');
      },
    });
  }

  // ================================================================ ADMINISTRATION : TARIFS ================================================================

  readonly tarifFormVisible = signal(false);
  readonly tarifFormSaving = signal(false);
  readonly tarifFormError = signal<string | null>(null);
  readonly editingTarifId = signal<number | null>(null);
  readonly tarifForm = this.fb.nonNullable.group({
    categorieAge: ['1_4_ANS' as VignetteMotoCategorieAge],
    categorieCylindree: ['MOINS_125_CM3' as VignetteMotoCategorieCylindree],
    montantVignette: [0],
    fraisOperation: [0],
    dateDebutValidite: [this.today],
    dateFinValidite: [''],
  });

  openCreateTarif(): void {
    this.editingTarifId.set(null);
    this.tarifFormError.set(null);
    this.tarifForm.reset({
      categorieAge: '1_4_ANS',
      categorieCylindree: 'MOINS_125_CM3',
      montantVignette: 0,
      fraisOperation: 0,
      dateDebutValidite: this.today,
      dateFinValidite: '',
    });
    this.tarifFormVisible.set(true);
  }

  openEditTarif(item: TarifVignetteMotoResponse): void {
    this.editingTarifId.set(item.id);
    this.tarifFormError.set(null);
    this.tarifForm.reset({
      categorieAge: item.categorieAge,
      categorieCylindree: item.categorieCylindree,
      montantVignette: item.montantVignette,
      fraisOperation: item.fraisOperation,
      dateDebutValidite: item.dateDebutValidite,
      dateFinValidite: item.dateFinValidite ?? '',
    });
    this.tarifFormVisible.set(true);
  }

  saveTarif(): void {
    const raw = this.tarifForm.getRawValue();
    if (!raw.montantVignette || raw.montantVignette <= 0 || raw.fraisOperation == null || raw.fraisOperation < 0 || !raw.dateDebutValidite) {
      this.tarifFormError.set('vignettesMoto.errors.champsTarifObligatoires');
      return;
    }
    this.tarifFormSaving.set(true);
    this.tarifFormError.set(null);
    const payload: TarifVignetteMotoRequest = {
      categorieAge: raw.categorieAge,
      categorieCylindree: raw.categorieCylindree,
      montantVignette: Number(raw.montantVignette),
      fraisOperation: Number(raw.fraisOperation),
      dateDebutValidite: raw.dateDebutValidite,
      dateFinValidite: raw.dateFinValidite || undefined,
    };
    const id = this.editingTarifId();
    const request$ = id ? this.tarifService.update(id, payload) : this.tarifService.create(payload);
    request$.subscribe({
      next: () => {
        this.tarifFormSaving.set(false);
        this.tarifFormVisible.set(false);
        this.fetchTarifs();
      },
      error: (err) => {
        this.tarifFormSaving.set(false);
        this.tarifFormError.set(
          err?.status === 409 ? 'vignettesMoto.errors.tarifChevauchement' : 'vignettesMoto.errors.enregistrementTarif',
        );
      },
    });
  }

  /** Aucune réactivation possible côté backend pour l'instant — n'affecte jamais les demandes déjà créées. */
  desactiverTarif(item: TarifVignetteMotoResponse): void {
    this.tarifService.desactiver(item.id).subscribe({ next: () => this.fetchTarifs(), error: () => undefined });
  }

  // ================================================================ ADMINISTRATION : LIVRAISONS ================================================================

  readonly livraisonFormVisible = signal(false);
  readonly livraisonFormSaving = signal(false);
  readonly livraisonFormError = signal<string | null>(null);
  readonly editingLivraisonId = signal<number | null>(null);
  readonly livraisonForm = this.fb.nonNullable.group({
    code: [''],
    libelle: [''],
    montant: [0],
  });

  openCreateLivraison(): void {
    this.editingLivraisonId.set(null);
    this.livraisonFormError.set(null);
    this.livraisonForm.reset({ code: '', libelle: '', montant: 0 });
    this.livraisonFormVisible.set(true);
  }

  openEditLivraison(item: FraisLivraisonZoneResponse): void {
    this.editingLivraisonId.set(item.id);
    this.livraisonFormError.set(null);
    this.livraisonForm.reset({ code: item.code, libelle: item.libelle, montant: item.montant });
    this.livraisonFormVisible.set(true);
  }

  saveLivraison(): void {
    const raw = this.livraisonForm.getRawValue();
    if (!raw.code.trim() || !raw.libelle.trim() || raw.montant == null || raw.montant < 0) {
      this.livraisonFormError.set('vignettesMoto.errors.champsLivraisonObligatoires');
      return;
    }
    this.livraisonFormSaving.set(true);
    this.livraisonFormError.set(null);
    const payload: FraisLivraisonZoneRequest = {
      code: raw.code.trim(),
      libelle: raw.libelle.trim(),
      montant: Number(raw.montant),
    };
    const id = this.editingLivraisonId();
    const request$ = id ? this.livraisonService.update(id, payload) : this.livraisonService.create(payload);
    request$.subscribe({
      next: () => {
        this.livraisonFormSaving.set(false);
        this.livraisonFormVisible.set(false);
        this.fetchLivraisons();
      },
      error: () => {
        this.livraisonFormSaving.set(false);
        this.livraisonFormError.set('vignettesMoto.errors.enregistrementLivraison');
      },
    });
  }

  desactiverLivraison(item: FraisLivraisonZoneResponse): void {
    this.livraisonService.desactiver(item.id).subscribe({ next: () => this.fetchLivraisons(), error: () => undefined });
  }
}
