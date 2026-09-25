import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  AccordionComponent,
  AccordionItemComponent,
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
  TemplateIdDirective,
} from '@coreui/angular';
import { Subscription, forkJoin, map, of, switchMap } from 'rxjs';

import { SouscriptionService } from '../../core/souscription/souscription.service';
import { PartnerCommissionService } from '../../core/partner-commission/partner-commission.service';
import { ProduitCommissionCommercialService } from '../../core/configuration/produit-commission-commercial.service';
import { ProduitCommissionCommercialResponse } from '../../core/configuration/models/produit-commission-commercial.model';
import {
  apiRateToPercent,
  configuredCommissionAmount,
  isPercentConfig,
  percentToApiRate,
} from '../../core/contracts/commission-rate.util';
import { InsuredRegistrationResponse } from '../../core/souscription/models/souscription.model';
import { InsuredService } from '../../core/insured/insured.service';
import { InsuredResponse, TypeClient } from '../../core/insured/models/insured.model';
import {
  ContractCommissionStatus,
  ContractResponse,
  ContractStatus,
  ModePaiement,
  PeriodiciteEcheance,
  StatutReversementPrime,
} from '../../core/contracts/models/contract.model';
import { ContractService } from '../../core/contracts/contract.service';
import { ProduitService } from '../../core/configuration/produit.service';
import { ProduitResponse } from '../../core/configuration/models/produit.model';
import { CompagnieService } from '../../core/configuration/compagnie.service';
import { CompagnieResponse } from '../../core/configuration/models/compagnie.model';
import { ProduitCommissionTauxService } from '../../core/configuration/produit-commission-taux.service';
import { ProduitCommissionTauxResponse } from '../../core/configuration/models/produit-commission-taux.model';
import { PartenaireService } from '../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../core/partenaire/models/partenaire.model';
import { ProfileService } from '../../core/profile/profile.service';
import { ProfileRecord, isCommercialRole } from '../../core/profile/models/profile-details.model';
import { VillePartenaireService } from '../../core/configuration/ville-partenaire.service';
import { VillePartenaireResponse } from '../../core/configuration/models/ville-partenaire.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ThousandSeparatorDirective } from '../../shared/directives/thousand-separator.directive';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Échéance = date d'effet + durée (mois) - 1 jour (période couverte inclusive, sans chevauchement). */
function computeEcheance(dateEffet: string, months: number): string {
  const date = new Date(dateEffet);
  date.setMonth(date.getMonth() + months);
  date.setDate(date.getDate() - 1);
  return date.toISOString().slice(0, 10);
}

function dayAfter(dateStr: string): string {
  const date = new Date(dateStr);
  date.setDate(date.getDate() + 1);
  return date.toISOString().slice(0, 10);
}

const STATUS_LABEL: Record<string, string> = {
  signed: 'Signé',
  pending: 'En attente',
  draft: 'Brouillon',
  cancelled: 'Annulé',
};

const MODE_PAIEMENT_LABEL: Record<string, string> = {
  COMPTANT: 'Comptant',
  FRACTIONNE: 'Fractionné',
};

const PERIODICITE_LABEL: Record<string, string> = {
  MENSUELLE: 'Mensuelle',
  TRIMESTRIELLE: 'Trimestrielle',
  SEMESTRIELLE: 'Semestrielle',
  ANNUELLE: 'Annuelle',
};

/** Miroir de PeriodiciteEcheance.getMoisEntreEcheances() côté backend — uniquement
 * pour l'aperçu de l'échéancier ci-dessous, tant que le calcul définitif reste
 * généré côté serveur (voir echeancesPreview()). */
const MOIS_ENTRE_ECHEANCES: Record<string, number> = {
  MENSUELLE: 1,
  TRIMESTRIELLE: 3,
  SEMESTRIELLE: 6,
  ANNUELLE: 12,
};

export interface EcheancePreview {
  numero: number;
  montant: number;
  date: string;
}

const TYPE_CLIENT_LABEL: Record<string, string> = {
  PARTICULIER: 'Particulier',
  ENTREPRISE: 'Entreprise',
};

interface RecapContract {
  index: number;
  productName: string;
  compagnieName: string;
  partenaireName: string;
  duree: string;
  dateEffet: string;
  dateEcheance: string;
  signedAt: string;
  modePaiementLabel: string;
  echeancesInfo: string | null;
  premium: number;
  commissionRate: number;
  commissionAmount: number;
  partnerShare: number;
  /** null quand le contrat n'a ni partenaire ni commercial (sans objet). */
  primeVerseeLabel: string | null;
  statusLabel: string;
  policyNumber: string;
  renouvellementLabel: string | null;
  notes: string;
}

@Component({
  selector: 'app-souscription',
  templateUrl: './souscription.component.html',
  styleUrl: './souscription.component.scss',
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
    AccordionComponent,
    AccordionItemComponent,
    TemplateIdDirective,
    ThousandSeparatorDirective,
  ],
})
export class SouscriptionComponent {
  private readonly souscriptionService = inject(SouscriptionService);
  private readonly insuredService = inject(InsuredService);
  private readonly produitService = inject(ProduitService);
  private readonly compagnieService = inject(CompagnieService);
  private readonly tauxService = inject(ProduitCommissionTauxService);
  private readonly partnerCommissionService = inject(PartnerCommissionService);
  private readonly commissionCommercialService = inject(ProduitCommissionCommercialService);
  private readonly partenaireService = inject(PartenaireService);
  private readonly profileService = inject(ProfileService);
  private readonly villePartenaireService = inject(VillePartenaireService);
  private readonly contractService = inject(ContractService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  /** Contrat à renouveler, transmis par la page Contrats via ?renewContractId=… (bouton
   * "Renouveler") — pré-rempli une fois les données de référence chargées, voir
   * applyRenewalPrefill(). */
  private readonly renewContractId = Number(this.route.snapshot.queryParamMap.get('renewContractId')) || null;

  readonly produits = signal<ProduitResponse[]>([]);
  readonly compagnies = signal<CompagnieResponse[]>([]);
  readonly commissionTaux = signal<ProduitCommissionTauxResponse[]>([]);
  readonly partenaires = signal<PartenaireResponse[]>([]);
  readonly profiles = signal<ProfileRecord[]>([]);
  readonly villes = signal<VillePartenaireResponse[]>([]);
  /** Candidate contracts a new subscription can renew — used to populate contratPrecedentId. */
  readonly renewableContracts = signal<ContractResponse[]>([]);
  readonly referenceLoading = signal(true);
  readonly referenceError = signal<string | null>(null);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<InsuredRegistrationResponse | null>(null);
  readonly commissionWarning = signal<string | null>(null);

  readonly steps = ['ASSURÉ', 'PRODUIT', 'COUVERTURE', 'PAIEMENT', 'RÉCAPITULATIF'] as const;
  readonly currentStep = signal(0);
  /** The furthest step reached so far in this pass — governs which step headers are clickable. */
  readonly furthestStep = signal(0);

  /** État par ligne de contrat. Un champ de commission modifié par l'utilisateur n'est plus
   * jamais écrasé par un recalcul automatique, sauf via le bouton « Recalculer ». Les mises à
   * jour programmatiques passent par emitEvent: false, donc seuls les changements faits par
   * l'utilisateur déclenchent les abonnements ci-dessous. */
  private readonly commissionState = new WeakMap<object, CommissionRowState>();

  private createContractGroup() {
    const group = this.buildContractGroup();
    const state: CommissionRowState = {
      rateManual: false,
      amountManual: false,
      shareManual: false,
      commercialManual: false,
      tauxConfig: null,
      configuredShare: undefined,
      commercialConfig: undefined,
      shareRequest: null,
      commercialRequest: null,
    };
    this.commissionState.set(group, state);
    const c = group.controls;
    c.premium.valueChanges.subscribe(() => this.applyAutoCommission(group));
    c.commissionRate.valueChanges.subscribe(() => {
      state.rateManual = true;
      this.applyAutoCommission(group);
    });
    c.commissionAmount.valueChanges.subscribe(() => (state.amountManual = true));
    c.partnerShare.valueChanges.subscribe(() => (state.shareManual = true));
    c.commercialCommissionAmount.valueChanges.subscribe(() => (state.commercialManual = true));
    return group;
  }

  /** Public uniquement pour le type ContractGroup (ReturnType), pas pour le template. */
  buildContractGroup() {
    const dateEffet = today();
    const duree = '12';
    return this.fb.nonNullable.group({
      productId: [0],
      compagnieId: [0],
      compagnieQuery: [''],
      partenaireId: [''],
      duree: [duree],
      premium: [0],
      commissionRate: [0],
      commissionAmount: [0],
      partnerShare: [null as number | null],
      commercialCommissionAmount: [0],
      dateEffet: [dateEffet],
      signedAt: [dateEffet],
      dateEcheance: [computeEcheance(dateEffet, Number(duree))],
      policyNumber: [''],
      salesRepId: [''],
      status: ['signed'],
      commissionStatus: ['pending'],
      notes: [''],
      modePaiement: ['COMPTANT'],
      nombreEcheances: [3],
      periodiciteEcheances: ['MENSUELLE'],
      montantDejaVerse: [0],
      primeReversementStatus: ['non_reverse'],
      isRenouvellement: [false],
      contratPrecedentId: [''],
    });
  }

  readonly form = this.fb.nonNullable.group({
    insured: this.fb.nonNullable.group({
      insuredSearchQuery: [''],
      nom: [''],
      prenoms: [''],
      clientPhone: [''],
      clientPhone2: [''],
      clientCity: [''],
      clientEmail: [''],
      typeClient: ['PARTICULIER'],
    }),
    contracts: this.fb.array([this.createContractGroup()]),
  });

  readonly insuredSearchResults = signal<InsuredResponse[]>([]);
  readonly insuredSearchLoading = signal(false);
  readonly insuredSearchError = signal<string | null>(null);
  readonly insuredSearchOpen = signal(false);
  readonly selectedExistingInsured = signal<InsuredResponse | null>(null);
  private insuredSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  get contractsArray() {
    return this.form.controls.contracts;
  }

  readonly recapContracts = computed<RecapContract[]>(() => {
    // Re-read on every currentStep change so the récap reflects the latest edits.
    this.currentStep();
    return this.contractsArray.controls.map((group, index) => {
      const raw = group.getRawValue();
      const produit = this.produits().find((p) => p.id === Number(raw.productId));
      const compagnie = this.compagnies().find((c) => c.id === Number(raw.compagnieId));
      const partenaire = raw.partenaireId
        ? this.partenaires().find((p) => p.id === Number(raw.partenaireId))
        : null;
      const precedent = raw.contratPrecedentId
        ? this.renewableContracts().find((c) => c.id === Number(raw.contratPrecedentId))
        : null;
      return {
        index,
        productName: produit?.nomProduit ?? '—',
        compagnieName: compagnie?.nom ?? '—',
        partenaireName: partenaire ? `${partenaire.nom} ${partenaire.prenoms}` : '—',
        duree: `${raw.duree} mois`,
        dateEffet: raw.dateEffet,
        dateEcheance: raw.dateEcheance,
        signedAt: raw.signedAt,
        modePaiementLabel: MODE_PAIEMENT_LABEL[raw.modePaiement] ?? raw.modePaiement,
        echeancesInfo:
          raw.modePaiement === 'FRACTIONNE'
            ? `${raw.nombreEcheances} échéance(s) — ${PERIODICITE_LABEL[raw.periodiciteEcheances] ?? raw.periodiciteEcheances}`
            : null,
        premium: Number(raw.premium),
        commissionRate: Number(raw.commissionRate),
        commissionAmount: Number(raw.commissionAmount),
        partnerShare: Number(raw.partnerShare),
        primeVerseeLabel:
          this.hasIntermediaire(raw.partenaireId, raw.salesRepId)
            ? raw.primeReversementStatus === 'reverse'
              ? 'Versée à la compagnie'
              : 'Non versée'
            : null,
        statusLabel: STATUS_LABEL[raw.status] ?? raw.status,
        policyNumber: raw.policyNumber.trim() || 'Auto-généré',
        renouvellementLabel:
          raw.isRenouvellement && precedent
            ? `Renouvelle ${precedent.policyNumber}`
            : raw.isRenouvellement
              ? 'Oui'
              : null,
        notes: raw.notes.trim(),
      };
    });
  });

  readonly recapTotals = computed(() => {
    const contracts = this.recapContracts();
    return {
      count: contracts.length,
      premium: contracts.reduce((sum, c) => sum + c.premium, 0),
      commission: contracts.reduce((sum, c) => sum + c.commissionAmount, 0),
    };
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.insuredSearchDebounce) clearTimeout(this.insuredSearchDebounce);
    });
    this.fetchReferenceData();
  }

  private fetchReferenceData(): void {
    this.referenceLoading.set(true);
    this.referenceError.set(null);
    forkJoin({
      produits: this.produitService.list(0, 200),
      compagnies: this.compagnieService.list(0, 200),
      taux: this.tauxService.list(),
      partenaires: this.partenaireService.list(0, 200),
      profiles: this.profileService.list(),
      villes: this.villePartenaireService.list(),
      contracts: this.contractService.listAll(),
    }).subscribe({
      next: ({ produits, compagnies, taux, partenaires, profiles, villes, contracts }) => {
        // Les produits de type Flotte se souscrivent depuis le module Gestion de flotte, pas ici.
        this.produits.set(produits.content.filter((p) => p.productTypeCode !== 'FLOTTE'));
        this.compagnies.set(compagnies.content);
        this.commissionTaux.set(taux);
        this.partenaires.set(partenaires.content);
        this.profiles.set(profiles);
        this.villes.set(villes);
        // Un contrat déjà rattaché à un véhicule de flotte (vehiculeId renseigné, cf. le bouton
        // "Créer un contrat" de Gestion de flotte) suit son propre cycle de vie côté flotte —
        // on ne le propose pas comme "contrat précédent" à renouveler ici (2026-09-13).
        this.renewableContracts.set(contracts.content.filter((c) => c.vehiculeId == null));
        this.referenceLoading.set(false);

        if (this.renewContractId) {
          const contract = contracts.content.find((c) => c.id === this.renewContractId);
          // Le bouton "Renouveler" de la page Contrats est déjà masqué pour un contrat de
          // flotte (vehiculeId renseigné) — cette garde couvre une arrivée directe par URL.
          if (contract && contract.vehiculeId == null) this.applyRenewalPrefill(contract);
        }
      },
      error: () => {
        this.referenceError.set(
          'Impossible de charger les données de référence (produits, compagnies, taux…).',
        );
        this.referenceLoading.set(false);
      },
    });
  }

  addContractRow(): void {
    this.contractsArray.push(this.createContractGroup());
  }

  removeContractRow(index: number): void {
    this.contractsArray.removeAt(index);
  }

  /** Changement de produit ou de partenaire (force=false), ou bouton « Recalculer » (force=true,
   * qui abandonne les saisies manuelles de la ligne). Recharge les trois commissions proposées :
   * - commission AssurZen : taux configuré (produits-commission-taux, produit + type partenaire),
   *   pourcentage de la prime si libTauxCommission = "%", montant fixe sinon ;
   * - commission partenaire : /partner-commissions (partenaire précis, sinon son type) ;
   * - commission du commercial (vente directe uniquement) : voir loadCommercialCommission(). */
  recomputeCommission(index: number, force = false): void {
    const group = this.contractsArray.at(index);
    const state = this.commissionState.get(group)!;
    if (force) {
      state.rateManual = false;
      state.amountManual = false;
      state.shareManual = false;
      state.commercialManual = false;
    }
    const productId = Number(group.controls.productId.value);
    const partenaireIdRaw = group.controls.partenaireId.value;
    const partenaireId = partenaireIdRaw ? Number(partenaireIdRaw) : null;

    const partner = partenaireId ? this.partenaires().find((p) => p.id === partenaireId) : null;
    const type = partner?.type ?? 'DIRECT';
    const match = this.commissionTaux().find(
      (t) => t.produitId === productId && t.typePartenaire === type && t.active,
    );
    state.tauxConfig = match ?? null;

    if (!match) {
      // No configured rate for this produit + type de partenaire combination — reset
      // rather than leaving whatever the previous selection computed, which would look
      // like a valid automatic result even though it doesn't apply to this combination.
      // Les montants saisis à la main sont conservés.
      if (!state.rateManual) group.controls.commissionRate.setValue(0, { emitEvent: false });
      if (!state.amountManual) group.controls.commissionAmount.setValue(0, { emitEvent: false });
      const produit = this.produits().find((p) => p.id === productId);
      this.commissionWarning.set(
        produit
          ? type === 'DIRECT'
            ? `Aucun taux de commission configuré pour "${produit.nomProduit}" en vente directe (sans partenaire). La commission reste obligatoire : saisissez le taux et le montant manuellement.`
            : `Aucun taux de commission configuré pour "${produit.nomProduit}" avec un partenaire de type ${this.typePartenaireLabel(type)}. Configurez-le dans Configuration → Taux de commission, ou saisissez le taux manuellement.`
          : null,
      );
    } else {
      this.commissionWarning.set(null);
    }
    this.applyAutoCommission(group);
    this.loadPartnerCommission(group);
    this.loadCommercialCommission(group);
  }

  /** Sélection d'un commercial : recharge sa commission configurée. */
  onCommercialChange(index: number): void {
    this.loadCommercialCommission(this.contractsArray.at(index));
  }

  /** Recalcule les champs NON modifiés à la main à partir de la configuration chargée. */
  private applyAutoCommission(group: ContractGroup): void {
    const state = this.commissionState.get(group)!;
    const c = group.controls;
    const premium = Number(c.premium.value) || 0;
    if (!state.rateManual && state.tauxConfig) {
      const rate = isPercentConfig(state.tauxConfig)
        ? state.tauxConfig.tauxCommission
        : premium > 0
          ? Math.round((state.tauxConfig.tauxCommission / premium) * 10000) / 100
          : 0;
      c.commissionRate.setValue(rate, { emitEvent: false });
    }
    if (!state.amountManual) {
      c.commissionAmount.setValue(this.autoCommissionAmount(group), { emitEvent: false });
    }
    if (!state.shareManual) {
      c.partnerShare.setValue(this.autoPartnerShare(group), { emitEvent: false });
    }
    if (!state.commercialManual) {
      c.commercialCommissionAmount.setValue(this.autoCommercialCommission(group), { emitEvent: false });
    }
  }

  private autoCommissionAmount(group: ContractGroup): number {
    const state = this.commissionState.get(group)!;
    const premium = Number(group.controls.premium.value) || 0;
    if (state.tauxConfig && !state.rateManual) return configuredCommissionAmount(state.tauxConfig, premium);
    const rate = Number(group.controls.commissionRate.value) || 0;
    return Math.round((premium * rate) / 100);
  }

  /** Commission partenaire configurée ; null = aucune configuration (saisie obligatoire),
   * 0 en vente directe. */
  private autoPartnerShare(group: ContractGroup): number | null {
    if (!group.controls.partenaireId.value) return 0;
    return this.commissionState.get(group)!.configuredShare ?? null;
  }

  private autoCommercialCommission(group: ContractGroup): number {
    const config = this.commissionState.get(group)!.commercialConfig;
    if (!config || !this.isCommercialCommissionApplicable(group)) return 0;
    if (config.kind === 'fixed') return config.amount;
    return configuredCommissionAmount(config.config, Number(group.controls.premium.value) || 0);
  }

  /** La prime n'est encaissée par un intermédiaire (qui doit ensuite la verser à la compagnie)
   * que si le contrat passe par un partenaire ou par un vendeur ayant le rôle COMMERCIAL. */
  isPrimeVerseeVisible(index: number): boolean {
    const c = this.contractsArray.at(index).controls;
    return this.hasIntermediaire(c.partenaireId.value, c.salesRepId.value);
  }

  private hasIntermediaire(partenaireId: string, salesRepId: string): boolean {
    if (partenaireId) return true;
    if (!salesRepId) return false;
    return isCommercialRole(this.profiles().find((p) => p.userId === Number(salesRepId))?.role);
  }

  private isCommercialCommissionApplicable(group: ContractGroup): boolean {
    return !!group.controls.salesRepId.value && !group.controls.partenaireId.value;
  }

  /** La commission du commercial n'existe qu'en vente directe (règle du 2026-09-08) : avec un
   * partenaire, la commission revient au partenaire et le backend refuse un montant non nul. */
  isCommercialCommissionVisible(index: number): boolean {
    return this.isCommercialCommissionApplicable(this.contractsArray.at(index));
  }

  private loadPartnerCommission(group: ContractGroup): void {
    const state = this.commissionState.get(group)!;
    state.shareRequest?.unsubscribe();
    state.shareRequest = null;
    const productId = Number(group.controls.productId.value);
    const partenaireId = Number(group.controls.partenaireId.value) || null;
    if (!productId || !partenaireId) {
      state.configuredShare = undefined;
      this.applyAutoCommission(group);
      return;
    }
    state.configuredShare = undefined;
    state.shareRequest = this.partnerCommissionService
      .configuredForPartner(productId, partenaireId, group.controls.dateEffet.value || undefined)
      .subscribe({
        next: (amount) => {
          state.configuredShare = amount;
          this.applyAutoCommission(group);
        },
        // Erreur réseau : on se comporte comme sans configuration, la saisie reste possible.
        error: () => {
          state.configuredShare = null;
          this.applyAutoCommission(group);
        },
      });
  }

  /** Commission configurée du commercial : sa configuration propre (/partner-commissions),
   * sinon la commission du produit (produits-commission-commercial), sinon 0. */
  private loadCommercialCommission(group: ContractGroup): void {
    const state = this.commissionState.get(group)!;
    state.commercialRequest?.unsubscribe();
    state.commercialRequest = null;
    const productId = Number(group.controls.productId.value);
    const commercialId = Number(group.controls.salesRepId.value) || null;
    state.commercialConfig = undefined;
    if (!productId || !commercialId || !this.isCommercialCommissionApplicable(group)) {
      this.applyAutoCommission(group);
      return;
    }
    state.commercialRequest = this.partnerCommissionService
      .configuredForCommercial(productId, commercialId, group.controls.dateEffet.value || undefined)
      .pipe(
        switchMap((amount) =>
          amount !== null
            ? of<CommercialCommissionConfig | null>({ kind: 'fixed', amount })
            : this.commissionCommercialService.list(productId).pipe(
                map((configs) => {
                  const config = configs.find((cfg) => cfg.produitId === productId && cfg.active);
                  return config ? ({ kind: 'product', config } as CommercialCommissionConfig) : null;
                }),
              ),
        ),
      )
      .subscribe({
        next: (config) => {
          state.commercialConfig = config;
          this.applyAutoCommission(group);
        },
        error: () => {
          state.commercialConfig = null;
          this.applyAutoCommission(group);
        },
      });
  }

  /** Valeurs proposées automatiquement, affichées sous les champs pour distinguer la valeur
   * configurée de la valeur appliquée (celle réellement envoyée à l'API). */
  commissionHint(index: number): CommissionHint {
    const group = this.contractsArray.at(index);
    const state = this.commissionState.get(group)!;
    const c = group.controls;
    const amount = this.autoCommissionAmount(group);
    const share = this.autoPartnerShare(group);
    const commercial = this.autoCommercialCommission(group);
    return {
      amount,
      amountModified: state.amountManual && Number(c.commissionAmount.value) !== amount,
      shareLoading: !!c.partenaireId.value && !!Number(c.productId.value) && state.configuredShare === undefined,
      share,
      shareModified: state.shareManual && share !== null && Number(c.partnerShare.value) !== share,
      commercialLoading: this.isCommercialCommissionApplicable(group) && !!Number(c.productId.value) && state.commercialConfig === undefined,
      commercial,
      commercialModified: state.commercialManual && Number(c.commercialCommissionAmount.value) !== commercial,
    };
  }

  /** Vrai quand aucun partenaire n'est sélectionné sur la ligne — vente directe. */
  isDirectAt(index: number): boolean {
    return !this.contractsArray.at(index).controls.partenaireId.value;
  }

  /** Search-as-you-type for an existing insured — mirrors the debounce pattern used
   * in assures.component.ts/contrats.component.ts. Selecting a result pre-fills the
   * form fields below AND links the submission to that insured record via insuredId
   * (see submit() below) — the backend then rattaches contracts to it instead of
   * creating a duplicate, and updates its nom/prenoms/etc from whatever is in the
   * form at submit time (so editing a pre-filled field here does apply). */
  onInsuredSearchInput(value: string): void {
    this.selectedExistingInsured.set(null);
    if (this.insuredSearchDebounce) clearTimeout(this.insuredSearchDebounce);

    const query = value.trim();
    if (query.length < 2) {
      this.insuredSearchResults.set([]);
      this.insuredSearchError.set(null);
      this.insuredSearchOpen.set(query.length > 0);
      return;
    }

    this.insuredSearchOpen.set(true);
    this.insuredSearchDebounce = setTimeout(() => this.searchInsureds(query), 400);
  }

  private searchInsureds(query: string): void {
    this.insuredSearchLoading.set(true);
    this.insuredSearchError.set(null);
    this.insuredService.list(0, 6, query).subscribe({
      next: (page) => {
        this.insuredSearchResults.set(page.content);
        this.insuredSearchLoading.set(false);
      },
      error: () => {
        this.insuredSearchResults.set([]);
        this.insuredSearchError.set('Recherche indisponible pour le moment.');
        this.insuredSearchLoading.set(false);
      },
    });
  }

  selectExistingInsured(insured: InsuredResponse): void {
    this.form.controls.insured.patchValue({
      insuredSearchQuery: `${insured.nom} ${insured.prenoms}`,
      nom: insured.nom,
      prenoms: insured.prenoms,
      clientPhone: insured.clientPhone ?? '',
      clientPhone2: insured.clientPhone2 ?? '',
      clientCity: insured.clientCity ?? '',
      clientEmail: insured.clientEmail ?? '',
      typeClient: insured.typeClient ?? 'PARTICULIER',
    });
    this.selectedExistingInsured.set(insured);
    this.insuredSearchOpen.set(false);
    this.insuredSearchResults.set([]);
  }

  /** Pré-remplit assuré + première ligne produit à partir du contrat passé par le bouton
   * "Renouveler" de la page Contrats. La date d'effet reprend le lendemain de l'ancienne
   * échéance (pas de trou de couverture) ; tout reste modifiable avant validation. */
  private applyRenewalPrefill(contract: ContractResponse): void {
    this.selectExistingInsured({
      id: contract.insuredId,
      nom: contract.clientLastName ?? '',
      prenoms: contract.clientFirstName ?? '',
      clientPhone: contract.clientPhone ?? '',
      clientCity: contract.clientCity ?? '',
    });

    const compagnie = this.compagnies().find((c) => c.id === contract.compagnieId);
    const dateEffet = contract.dateEcheance ? dayAfter(contract.dateEcheance) : today();
    const duree = contract.duree;

    const renewedGroup = this.contractsArray.at(0);
    const renewedState = this.commissionState.get(renewedGroup)!;
    renewedState.rateManual = true;
    renewedState.amountManual = true;
    renewedState.shareManual = true;
    renewedState.commercialManual = true;
    renewedGroup.patchValue({
      productId: contract.productId,
      compagnieId: contract.compagnieId,
      compagnieQuery: compagnie?.nom ?? '',
      partenaireId: contract.partenaireId ? String(contract.partenaireId) : '',
      duree,
      premium: contract.premium,
      commissionRate: apiRateToPercent(contract.commissionRate),
      commissionAmount: contract.commissionAmount,
      partnerShare: contract.partnerShare,
      commercialCommissionAmount: contract.commercialCommissionAmount ?? 0,
      dateEffet,
      signedAt: dateEffet,
      dateEcheance: computeEcheance(dateEffet, Number(duree)),
      policyNumber: '',
      modePaiement: contract.modePaiement,
      nombreEcheances: contract.nombreEcheances ?? 3,
      periodiciteEcheances: contract.periodiciteEcheances ?? 'MENSUELLE',
      isRenouvellement: true,
      contratPrecedentId: String(contract.id),
    }, { emitEvent: false });
  }

  clearExistingInsuredSelection(): void {
    this.form.controls.insured.patchValue({
      insuredSearchQuery: '',
      nom: '',
      prenoms: '',
      clientPhone: '',
      clientPhone2: '',
      clientCity: '',
      clientEmail: '',
      typeClient: 'PARTICULIER',
    });
    this.selectedExistingInsured.set(null);
    this.insuredSearchResults.set([]);
    this.insuredSearchOpen.set(false);
    this.insuredSearchError.set(null);
  }

  onInsuredSearchFocus(): void {
    if (this.insuredSearchResults().length > 0) this.insuredSearchOpen.set(true);
  }

  /** Delayed close so a (mousedown) on a result item fires before the input's blur
   * would otherwise collapse the dropdown out from under the click. */
  onInsuredSearchBlur(): void {
    setTimeout(() => this.insuredSearchOpen.set(false), 150);
  }

  initials(nom: string, prenoms: string): string {
    return `${nom.trim()[0] ?? ''}${prenoms.trim()[0] ?? ''}`.toUpperCase();
  }

  onCompagnieQueryChange(index: number): void {
    const group = this.contractsArray.at(index);
    const query = group.controls.compagnieQuery.value.trim().toLowerCase();
    const match = this.compagnies().find((c) => c.nom.toLowerCase() === query);
    group.controls.compagnieId.setValue(match ? match.id : 0);
  }

  private typePartenaireLabel(type: string): string {
    if (type === 'DIRECT') return 'Direct';
    if (type === 'AGENT') return 'Agent';
    if (type === 'APPORTEUR') return 'Apporteur';
    return type;
  }

  recomputeEcheance(index: number): void {
    const group = this.contractsArray.at(index);
    const dateEffet = group.controls.dateEffet.value;
    const months = Number(group.controls.duree.value);
    if (!dateEffet || !Number.isFinite(months) || months <= 0) return;
    group.patchValue({ dateEcheance: computeEcheance(dateEffet, months) });
  }

  // Wizard navigation -------------------------------------------------------

  nextStep(): void {
    this.error.set(null);
    if (this.currentStep() === 0 && !this.validateInsured()) return;
    if (this.currentStep() === 1 && !this.validateProduitStep()) return;
    if (this.currentStep() === 2 && !this.validateCouvertureStep()) return;
    if (this.currentStep() === 3 && !this.validatePaiementStep()) return;
    const next = Math.min(this.currentStep() + 1, this.steps.length - 1);
    this.currentStep.set(next);
    this.furthestStep.set(Math.max(this.furthestStep(), next));
  }

  prevStep(): void {
    this.error.set(null);
    this.currentStep.set(Math.max(this.currentStep() - 1, 0));
  }

  goToStep(index: number): void {
    if (index > this.furthestStep()) return;
    this.error.set(null);
    this.currentStep.set(index);
  }

  private validateInsured(): boolean {
    const raw = this.form.getRawValue();
    if (
      !raw.insured.nom.trim() ||
      !raw.insured.prenoms.trim() ||
      !raw.insured.clientPhone.trim() ||
      !raw.insured.clientCity.trim()
    ) {
      this.error.set('Tous les champs de l’assuré (nom, prénoms, téléphone, ville) sont obligatoires.');
      return false;
    }
    if (raw.insured.clientEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw.insured.clientEmail.trim())) {
      this.error.set("L'adresse email de l'assuré n'est pas valide.");
      return false;
    }
    return true;
  }

  private validateProduitStep(): boolean {
    const raw = this.form.getRawValue();
    if (raw.contracts.length === 0) {
      this.error.set('Ajoutez au moins un produit à souscrire.');
      return false;
    }
    for (const contract of raw.contracts) {
      if (!contract.productId || !contract.compagnieId) {
        this.error.set('Sélectionnez un produit et une compagnie pour chaque ligne.');
        return false;
      }
      if (contract.isRenouvellement && !contract.contratPrecedentId) {
        this.error.set('Sélectionnez le contrat précédent pour chaque renouvellement.');
        return false;
      }
    }
    return true;
  }

  private validateCouvertureStep(): boolean {
    const raw = this.form.getRawValue();
    for (const contract of raw.contracts) {
      if (!contract.dateEffet || !contract.dateEcheance) {
        this.error.set("La date d'effet et la date d'échéance sont obligatoires pour chaque produit.");
        return false;
      }
    }
    return true;
  }

  private validatePaiementStep(): boolean {
    const raw = this.form.getRawValue();
    for (const contract of raw.contracts) {
      // La commission reste obligatoire même sans partenaire (vente directe) : elle
      // revient entièrement à l'entreprise. La part partenaire, elle, n'a de sens que
      // lorsqu'un partenaire est sélectionné.
      const isDirect = !contract.partenaireId;
      const premium = Number(contract.premium) || 0;
      if (!premium || !contract.commissionAmount) {
        this.error.set(
          'Renseignez la prime et les informations de commission pour chaque produit (utilisez « Recalculer » si besoin, ou saisissez-les manuellement pour un produit en vente directe).',
        );
        return false;
      }
      if (Number(contract.commissionAmount) > premium) {
        this.error.set('Le montant de la commission ne peut pas dépasser la prime.');
        return false;
      }
      if (!isDirect && (contract.partnerShare === null || contract.partnerShare === undefined)) {
        this.error.set(
          'Aucune commission partenaire n\'est configurée pour ce produit et ce partenaire : saisissez la part reversée au partenaire.',
        );
        return false;
      }
      // La part partenaire est prélevée sur la commission (règle du 2026-09-25), donc aussi ≤ prime.
      if (!isDirect && Number(contract.partnerShare) > Number(contract.commissionAmount)) {
        this.error.set('La part reversée au partenaire ne peut pas dépasser le montant de la commission.');
        return false;
      }
      if (isDirect && contract.salesRepId && Number(contract.commercialCommissionAmount) > premium) {
        this.error.set('La commission du commercial ne peut pas dépasser la prime.');
        return false;
      }
      if (
        contract.modePaiement === 'FRACTIONNE' &&
        (!contract.nombreEcheances || contract.nombreEcheances < 2)
      ) {
        this.error.set('Un paiement fractionné doit comporter au moins 2 échéances.');
        return false;
      }
    }
    return true;
  }

  submit(): void {
    if (!this.validateInsured()) {
      this.currentStep.set(0);
      return;
    }
    if (!this.validateProduitStep()) {
      this.currentStep.set(1);
      return;
    }
    if (!this.validateCouvertureStep()) {
      this.currentStep.set(2);
      return;
    }
    if (!this.validatePaiementStep()) {
      this.currentStep.set(3);
      return;
    }

    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);

    this.souscriptionService
      .submit({
        insured: {
          insuredId: this.selectedExistingInsured()?.id,
          nom: raw.insured.nom,
          prenoms: raw.insured.prenoms,
          clientPhone: raw.insured.clientPhone.trim() || undefined,
          clientPhone2: raw.insured.clientPhone2.trim() || undefined,
          clientCity: raw.insured.clientCity.trim() || undefined,
          clientEmail: raw.insured.clientEmail.trim() || undefined,
          typeClient: raw.insured.typeClient as TypeClient,
        },
        contracts: raw.contracts.map((contract) => ({
          policyNumber: contract.policyNumber.trim() || undefined,
          dateEffet: contract.dateEffet || undefined,
          duree: contract.duree,
          dateEcheance: contract.dateEcheance || undefined,
          productId: Number(contract.productId),
          partenaireId: contract.partenaireId ? Number(contract.partenaireId) : undefined,
          compagnieId: Number(contract.compagnieId),
          premium: Number(contract.premium),
          commissionRate: percentToApiRate(
            Number(contract.commissionRate) ||
              (Number(contract.commissionAmount) / Number(contract.premium)) * 100,
          ),
          commissionAmount: Number(contract.commissionAmount),
          partnerShare: contract.partenaireId ? Number(contract.partnerShare) : 0,
          commercialCommissionAmount:
            contract.salesRepId && !contract.partenaireId ? Number(contract.commercialCommissionAmount) || 0 : undefined,
          status: contract.status as ContractStatus,
          commissionStatus: contract.commissionStatus as ContractCommissionStatus,
          signedAt: contract.signedAt || undefined,
          salesRepId: contract.salesRepId ? Number(contract.salesRepId) : undefined,
          notes: contract.notes.trim() || undefined,
          modePaiement: contract.modePaiement as ModePaiement,
          nombreEcheances:
            contract.modePaiement === 'FRACTIONNE' ? Number(contract.nombreEcheances) : undefined,
          periodiciteEcheances:
            contract.modePaiement === 'FRACTIONNE'
              ? (contract.periodiciteEcheances as PeriodiciteEcheance)
              : undefined,
          montantDejaVerse:
            contract.modePaiement === 'FRACTIONNE' && Number(contract.montantDejaVerse) > 0
              ? Number(contract.montantDejaVerse)
              : undefined,
          contratPrecedentId:
            contract.isRenouvellement && contract.contratPrecedentId
              ? Number(contract.contratPrecedentId)
              : undefined,
          primeReversementStatus:
            this.hasIntermediaire(contract.partenaireId, contract.salesRepId)
              ? (contract.primeReversementStatus as StatutReversementPrime)
              : undefined,
        })),
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.success.set(result);
          this.resetForm();
        },
        error: (err) => {
          this.saving.set(false);
          // 400 métier (ex. part partenaire > commission) : le motif du backend est affiché tel quel.
          const backendMessage = (err?.error?.message ?? err?.error?.reason) as string | undefined;
          this.error.set(backendMessage || "Impossible d'enregistrer la souscription. Réessayez.");
        },
      });
  }

  private resetForm(): void {
    this.form.controls.insured.reset({
      insuredSearchQuery: '',
      nom: '',
      prenoms: '',
      clientPhone: '',
      clientPhone2: '',
      clientCity: '',
      clientEmail: '',
      typeClient: 'PARTICULIER',
    });
    this.selectedExistingInsured.set(null);
    this.insuredSearchResults.set([]);
    this.insuredSearchOpen.set(false);
    this.insuredSearchError.set(null);
    while (this.contractsArray.length > 0) {
      this.contractsArray.removeAt(0);
    }
    this.contractsArray.push(this.createContractGroup());
    this.currentStep.set(0);
    this.furthestStep.set(0);
  }

  productNameAt(index: number): string {
    const id = Number(this.contractsArray.at(index).controls.productId.value);
    return this.produits().find((p) => p.id === id)?.nomProduit ?? '';
  }

  /** Aperçu indicatif de l'échéancier restant après déduction du montant déjà versé —
   * répartition à parts égales, le reliquat de la division entière portée par la
   * dernière échéance, exactement comme EcheancierService.genererEcheancier() côté
   * backend. Reste un aperçu frontend : le montant déjà versé n'est pas encore
   * transmis à l'API de souscription (voir docs/api-contracts/souscription-montant-deja-verse.md). */
  echeancesPreview(index: number): EcheancePreview[] {
    const raw = this.contractsArray.at(index).getRawValue();
    if (raw.modePaiement !== 'FRACTIONNE') return [];

    const n = Number(raw.nombreEcheances) || 0;
    if (n < 1) return [];

    const premium = Number(raw.premium) || 0;
    const dejaVerse = Math.max(0, Number(raw.montantDejaVerse) || 0);
    const restant = Math.max(0, premium - dejaVerse);

    const base = Math.floor(restant / n);
    const remainder = restant - base * n;
    const moisEntreEcheances = MOIS_ENTRE_ECHEANCES[raw.periodiciteEcheances] ?? 1;
    const depart = new Date(raw.dateEffet || today());

    const echeances: EcheancePreview[] = [];
    for (let i = 1; i <= n; i++) {
      const date = new Date(depart);
      date.setMonth(date.getMonth() + moisEntreEcheances * i);
      echeances.push({
        numero: i,
        montant: base + (i === n ? remainder : 0),
        date: date.toISOString().slice(0, 10),
      });
    }
    return echeances;
  }

  montantRestantAFinancer(index: number): number {
    const raw = this.contractsArray.at(index).getRawValue();
    const premium = Number(raw.premium) || 0;
    const dejaVerse = Math.max(0, Number(raw.montantDejaVerse) || 0);
    return Math.max(0, premium - dejaVerse);
  }

  contractLabel(contract: ContractResponse): string {
    return `${contract.policyNumber} — ${contract.insuredName} — ${contract.productName}`;
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  typeClientLabel(value: string): string {
    return TYPE_CLIENT_LABEL[value] ?? value;
  }

  successSummary(result: InsuredRegistrationResponse): string {
    const numbers = result.contracts.map((c) => c.policyNumber).join(', ');
    const sms = result.welcomeSmsSent ? 'SMS de bienvenue envoyé.' : 'SMS de bienvenue non envoyé.';
    const reminders =
      result.reminderSmsScheduled > 0
        ? `${result.reminderSmsScheduled} rappel(s) d'échéance programmé(s).`
        : '';
    return `Souscription enregistrée pour ${result.insured.nom} ${result.insured.prenoms} — ${result.contracts.length} contrat(s) créé(s) (${numbers}). ${sms} ${reminders}`;
  }
}

interface CommissionRowState {
  /** Champs saisis à la main — plus recalculés automatiquement (sauf « Recalculer »). */
  rateManual: boolean;
  amountManual: boolean;
  shareManual: boolean;
  commercialManual: boolean;
  /** Taux configuré (produit + type partenaire) appliqué à la ligne. */
  tauxConfig: ProduitCommissionTauxResponse | null;
  /** Commission partenaire configurée : undefined = pas encore chargée (ou sans partenaire),
   * null = aucune configuration. */
  configuredShare: number | null | undefined;
  /** Commission configurée du commercial : undefined = pas encore chargée, null = aucune. */
  commercialConfig: CommercialCommissionConfig | null | undefined;
  shareRequest: Subscription | null;
  commercialRequest: Subscription | null;
}

type CommercialCommissionConfig =
  | { kind: 'fixed'; amount: number }
  | { kind: 'product'; config: ProduitCommissionCommercialResponse };

interface CommissionHint {
  amount: number;
  amountModified: boolean;
  shareLoading: boolean;
  share: number | null;
  shareModified: boolean;
  commercialLoading: boolean;
  commercial: number;
  commercialModified: boolean;
}

type ContractGroup = ReturnType<SouscriptionComponent['buildContractGroup']>;
