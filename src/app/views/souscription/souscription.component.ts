import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { FormDirective, SpinnerComponent } from '@coreui/angular';
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

function addDays(dateStr: string, days: number): string {
  const date = new Date(dateStr);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

/** 1er jour du mois suivant (raccourci « 1er octobre » de l'étape Couverture). */
function firstOfNextMonth(): string {
  const now = new Date();
  return new Date(Date.UTC(now.getFullYear(), now.getMonth() + 1, 1)).toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return Math.round((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000);
}

/** Pastille d'un produit : 1re lettre du nom sans le préfixe « Assurance » (Moto → M). */
function productInitial(name: string): string {
  return (name.replace(/^Assurance\s+/i, '')[0] ?? '?').toUpperCase();
}

function initialsOf(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
}

/** Chiffres d'un numéro ivoirien sans l'indicatif (+225 / 00225). */
function phoneDigits(value: string): string {
  const digits = value.replace(/\D/g, '');
  if (digits.length === 13 && digits.startsWith('225')) return digits.slice(3);
  if (digits.length === 15 && digits.startsWith('00225')) return digits.slice(5);
  return digits;
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
  productInitial: string;
  compagnieName: string;
  partenaireName: string;
  /** Partenaire, sinon « Vente directe ». */
  canalLabel: string;
  commercialName: string;
  status: string;
  commissionPercent: number;
  commissionStatusLabel: string;
  commissionPaid: boolean;
  /** Part de la prime qui revient à la compagnie, en % (barre du récapitulatif). */
  compagnieNetPercent: number;
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
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    AlertMessageComponent,
    ThousandSeparatorDirective,
    RouterLink,
    NgTemplateOutlet,
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
  /** Tous les contrats (flotte comprise) : nombre de contrats par assuré, produits les plus
   * souscrits, compagnie habituelle d'un produit, villes les plus fréquentes. */
  readonly allContracts = signal<ContractResponse[]>([]);
  readonly referenceLoading = signal(true);
  readonly referenceError = signal<string | null>(null);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<InsuredRegistrationResponse | null>(null);
  readonly commissionWarning = signal<string | null>(null);

  readonly steps = ['Assuré', 'Produit', 'Couverture', 'Paiement', 'Récapitulatif'] as const;
  readonly currentStep = signal(0);
  /** The furthest step reached so far in this pass — governs which step headers are clickable. */
  readonly furthestStep = signal(0);

  /** État par ligne de contrat. Un champ de commission modifié par l'utilisateur n'est plus
   * jamais écrasé par un recalcul automatique, sauf via le bouton « Recalculer ». Les mises à
   * jour programmatiques passent par emitEvent: false, donc seuls les changements faits par
   * l'utilisateur déclenchent les abonnements ci-dessous. */
  private readonly commissionState = new WeakMap<object, CommissionRowState>();
  /** Étape Paiement : unité de saisie de la commission (FCFA ou %) et texte brut du champ %
   * pendant la frappe (« 10, » ne doit pas être réécrit en « 10 »). */
  private readonly commissionUnit = new WeakMap<object, 'fcfa' | 'pct'>();
  private readonly commissionPercentText = new WeakMap<object, string>();
  readonly nombreEcheancesOptions = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

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
      const commercial = raw.salesRepId ? this.profiles().find((p) => p.userId === Number(raw.salesRepId)) : null;
      const premium = Number(raw.premium) || 0;
      const commissionAmount = Number(raw.commissionAmount) || 0;
      const productName = produit?.nomProduit ?? '—';
      return {
        index,
        productName,
        productInitial: productInitial(productName),
        compagnieName: compagnie?.nom ?? '—',
        partenaireName: partenaire ? `${partenaire.nom} ${partenaire.prenoms}` : '—',
        canalLabel: partenaire ? `${partenaire.nom} ${partenaire.prenoms}`.trim() : 'Vente directe',
        commercialName: commercial ? `${commercial.firstname} ${commercial.lastname}`.trim() : '—',
        status: raw.status,
        commissionPercent: premium > 0 ? (commissionAmount / premium) * 100 : 0,
        commissionStatusLabel: raw.commissionStatus === 'paid' ? 'Payée' : 'En attente',
        commissionPaid: raw.commissionStatus === 'paid',
        compagnieNetPercent: premium > 0 ? Math.max(0, Math.min(100, ((premium - commissionAmount) / premium) * 100)) : 100,
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
    const premium = contracts.reduce((sum, c) => sum + c.premium, 0);
    const commission = contracts.reduce((sum, c) => sum + c.commissionAmount, 0);
    return {
      count: contracts.length,
      premium,
      commission,
      net: Math.max(premium - commission, 0),
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
        this.allContracts.set(contracts.content);
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

  // Étape Paiement (maquette validée le 2026-09-25) -------------------------

  setModePaiement(index: number, mode: ModePaiement): void {
    this.contractsArray.at(index).controls.modePaiement.setValue(mode);
  }

  setCommissionStatus(index: number, status: ContractCommissionStatus): void {
    this.contractsArray.at(index).controls.commissionStatus.setValue(status);
  }

  setPrimeReversementStatus(index: number, status: StatutReversementPrime): void {
    this.contractsArray.at(index).controls.primeReversementStatus.setValue(status);
  }

  commissionUnitAt(index: number): 'fcfa' | 'pct' {
    return this.commissionUnit.get(this.contractsArray.at(index)) ?? 'fcfa';
  }

  setCommissionUnit(index: number, unit: 'fcfa' | 'pct'): void {
    const group = this.contractsArray.at(index);
    this.commissionUnit.set(group, unit);
    if (unit === 'pct') {
      const pct = this.commissionPercentOf(group);
      this.commissionPercentText.set(group, String(Math.round(pct * 100) / 100).replace('.', ','));
    }
  }

  commissionPercentInputValue(index: number): string {
    return this.commissionPercentText.get(this.contractsArray.at(index)) ?? '0';
  }

  /** Saisie en % : le montant (seule valeur envoyée, le taux en est dérivé) suit le taux. */
  onCommissionPercentInput(index: number, value: string): void {
    const group = this.contractsArray.at(index);
    const state = this.commissionState.get(group)!;
    this.commissionPercentText.set(group, value);
    const pct = Number(value.replace(/\s/g, '').replace(',', '.')) || 0;
    const premium = Number(group.controls.premium.value) || 0;
    state.rateManual = true;
    state.amountManual = true;
    group.controls.commissionRate.setValue(pct, { emitEvent: false });
    group.controls.commissionAmount.setValue(Math.round((premium * pct) / 100), { emitEvent: false });
  }

  private commissionPercentOf(group: ContractGroup): number {
    const premium = Number(group.controls.premium.value) || 0;
    const amount = Number(group.controls.commissionAmount.value) || 0;
    return premium > 0 ? (amount / premium) * 100 : 0;
  }

  formatPercent(value: number): string {
    return `${value.toFixed(1).replace('.', ',')} %`;
  }

  formatShortDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
  }

  /** Tout ce qu'affiche une carte produit de l'étape Paiement : répartition de la prime,
   * intermédiaire, contrôles. Les règles restent celles de validatePaiementStep(). */
  paiementView(index: number): PaiementView {
    const group = this.contractsArray.at(index);
    const state = this.commissionState.get(group)!;
    const c = group.controls;
    const produit = this.produits().find((p) => p.id === Number(c.productId.value));
    const compagnie = this.compagnies().find((co) => co.id === Number(c.compagnieId.value));
    const partner = c.partenaireId.value ? this.partenaires().find((p) => p.id === Number(c.partenaireId.value)) : null;
    const commercial = this.isCommercialCommissionApplicable(group)
      ? this.profiles().find((p) => p.userId === Number(c.salesRepId.value))
      : null;

    const premium = Number(c.premium.value) || 0;
    const commission = Number(c.commissionAmount.value) || 0;
    const shareKind: PaiementView['shareKind'] = c.partenaireId.value ? 'partner' : commercial ? 'commercial' : null;
    const shareRaw: number | null =
      shareKind === 'partner' ? c.partnerShare.value : shareKind === 'commercial' ? c.commercialCommissionAmount.value : 0;
    const shareMissing = shareKind === 'partner' && (shareRaw === null || shareRaw === undefined);
    const share = Number(shareRaw) || 0;
    const compagnieNet = Math.max(premium - commission, 0);
    const cabinet = Math.max(commission - share, 0);
    const commissionBad = commission > premium;
    // Part partenaire : prélevée sur la commission (règle du 2026-09-25). Commission du
    // commercial : bornée par la prime (règle backend).
    const shareBad = shareKind === 'partner' ? share > commission : shareKind === 'commercial' ? share > premium : false;

    const intermediaireName = partner
      ? `${partner.nom} ${partner.prenoms}`.trim()
      : commercial
        ? `${commercial.firstname} ${commercial.lastname}`.trim()
        : '';
    const initials = initialsOf(intermediaireName);
    const compagnieName = compagnie?.nom ?? '—';
    const subtitle = partner
      ? `${compagnieName} · via ${intermediaireName}`
      : commercial
        ? `${compagnieName} · vendu par ${intermediaireName}`
        : `${compagnieName} · vente directe`;

    const checks: PaiementCheck[] = [
      { ok: premium > 0, label: 'Prime renseignée' },
      { ok: commission > 0 && !commissionBad, label: 'Commission valide' },
    ];
    if (shareKind) checks.push({ ok: !shareMissing && !shareBad, label: 'Part dans la commission' });

    const configured = state.tauxConfig ? configuredCommissionAmount(state.tauxConfig, premium) : 0;
    const width = (x: number) => (premium > 0 ? Math.min((x / premium) * 100, 100) : 0);
    const productName = produit?.nomProduit ?? '—';

    return {
      productName,
      productInitial: productInitial(productName),
      subtitle,
      premium,
      commission,
      commissionPercent: premium > 0 ? (commission / premium) * 100 : 0,
      commissionBad,
      configuredCommission: configured,
      commissionModified: state.amountManual && commission !== configured,
      noConfig: !!produit && !state.tauxConfig,
      noConfigType: partner ? this.typePartenaireLabel(partner.type) : 'Vente directe',
      shareKind,
      shareLabel: shareKind === 'partner' ? 'Part reversée au partenaire' : 'Commission du commercial',
      legendLabel: shareKind === 'partner' ? 'Partenaire' : shareKind === 'commercial' ? 'Commercial' : 'Intermédiaire',
      share,
      shareMissing,
      shareBad,
      shareOverCommission: shareKind === 'commercial' && !shareBad && share > commission,
      compagnieNet,
      cabinet,
      widthCompagnie: width(compagnieNet),
      widthShare: width(share),
      widthCabinet: shareBad ? 0 : width(cabinet),
      primeVerseeVisible: this.isPrimeVerseeVisible(index),
      intermediaireName,
      intermediaireKind: partner ? 'partenaire' : 'commercial',
      initials,
      checks,
      complete: checks.every((ch) => ch.ok),
    };
  }

  /** Panneau « Total de la souscription » de l'étape Paiement, tous produits confondus. */
  paiementSummary(): PaiementSummary {
    const views = this.contractsArray.controls.map((_, i) => this.paiementView(i));
    const raws = this.contractsArray.controls.map((g) => g.getRawValue());
    const single = views.length === 1;
    const kinds = new Set(views.map((v) => v.shareKind).filter((k) => k !== null));
    const fractionnes = raws.filter((r) => r.modePaiement === 'FRACTIONNE');
    return {
      premium: views.reduce((s, v) => s + v.premium, 0),
      commission: views.reduce((s, v) => s + v.commission, 0),
      share: kinds.size ? views.reduce((s, v) => s + v.share, 0) : null,
      shareLabel:
        kinds.size === 1 ? (kinds.has('partner') ? 'Part partenaire' : 'Part commercial') : single ? 'Part intermédiaire' : 'Part intermédiaires',
      mode: single
        ? raws[0].modePaiement === 'FRACTIONNE'
          ? `${raws[0].nombreEcheances} échéances`
          : 'Comptant'
        : fractionnes.length
          ? `${fractionnes.length} fractionné${fractionnes.length > 1 ? 's' : ''}`
          : 'Comptant',
      net: views.reduce((s, v) => s + v.compagnieNet, 0),
      checks: views.flatMap((v, i) =>
        v.checks.map((ch) => ({ ok: ch.ok, label: single ? ch.label : `Produit ${i + 1} · ${ch.label}` })),
      ),
      complete: views.every((v) => v.complete),
    };
  }

  // Étape Assuré (maquette validée le 2026-09-27) ----------------------------

  /** Résultat surligné dans la liste de recherche (↑ ↓ puis Entrée). */
  readonly insuredActiveIndex = signal(0);
  /** Un téléphone n'est signalé invalide qu'une fois le champ quitté (ou à « Suivant »). */
  readonly phoneTouched = signal(false);
  /** 2e téléphone (optionnel) : reçoit aussi les SMS et le cycle de vie du contrat. */
  readonly phone2Touched = signal(false);

  onInsuredSearchKeydown(event: KeyboardEvent): void {
    const results = this.insuredSearchResults();
    if (event.key === 'Escape') {
      this.insuredSearchOpen.set(false);
      return;
    }
    if (!this.insuredSearchOpen() || results.length === 0) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      this.insuredActiveIndex.set((this.insuredActiveIndex() + step + results.length) % results.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.selectExistingInsured(results[this.insuredActiveIndex()] ?? results[0]);
    }
  }

  /** « Aucun ne correspond » : ferme la recherche et place le curseur sur le nom. */
  startNewInsured(): void {
    this.insuredSearchOpen.set(false);
    setTimeout(() => document.getElementById('ins-nom')?.focus());
  }

  insuredContractCount(insuredId: number): number {
    return this.allContracts().filter((c) => c.insuredId === insuredId).length;
  }

  setTypeClient(type: TypeClient): void {
    this.form.controls.insured.controls.typeClient.setValue(type);
  }

  setCity(name: string): void {
    this.form.controls.insured.controls.clientCity.setValue(name);
  }

  /** Raccourcis de ville : les 5 villes les plus fréquentes parmi les contrats existants,
   * complétées par les villes configurées. */
  readonly topCities = computed(() => {
    const counts = new Map<string, number>();
    for (const c of this.allContracts()) {
      const city = c.clientCity?.trim();
      if (city) counts.set(city, (counts.get(city) ?? 0) + 1);
    }
    const ranked = [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([city]) => city);
    for (const v of this.villes()) {
      if (!ranked.some((c) => c.toLowerCase() === v.nomVille.toLowerCase())) ranked.push(v.nomVille);
    }
    return ranked.slice(0, 5);
  });

  isPhoneValid(value: string): boolean {
    return phoneDigits(value).length === 10;
  }

  assureView(): AssureView {
    const ins = this.form.controls.insured.getRawValue();
    const nom = ins.nom.trim();
    const prenoms = ins.prenoms.trim();
    const phone = ins.clientPhone.trim();
    const phone2 = ins.clientPhone2.trim();
    const email = ins.clientEmail.trim();
    const city = ins.clientCity.trim();
    const emailValid = !email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    const checks: StepCheck[] = [
      { ok: !!nom && !!prenoms, label: 'Nom et prénoms' },
      { ok: this.isPhoneValid(phone), label: 'Téléphone valide' },
      { ok: !!city, label: 'Ville' },
    ];
    if (phone2) checks.push({ ok: this.isPhoneValid(phone2), label: '2e téléphone valide' });
    if (email) checks.push({ ok: emailValid, label: 'Email valide' });
    return {
      initials: `${nom[0] ?? ''}${prenoms[0] ?? ''}`.toUpperCase() || '?',
      fullName: `${nom} ${prenoms}`.trim(),
      typeLabel: this.typeClientLabel(ins.typeClient),
      existing: !!this.selectedExistingInsured(),
      phone,
      email,
      city,
      phoneError: !!phone && this.phoneTouched() && !this.isPhoneValid(phone),
      phone2,
      phone2Error: !!phone2 && this.phone2Touched() && !this.isPhoneValid(phone2),
      checks,
      complete: checks.every((c) => c.ok),
    };
  }

  // Étape Produit (maquette validée le 2026-09-27) ---------------------------

  /** Liste complète des produits dépliée (« + N autres produits »), par ligne. */
  private readonly allProductsOpen = signal<ReadonlySet<object>>(new Set());
  /** Notes dépliées (« + Ajouter une note interne »), par ligne. */
  private readonly notesOpen = signal<ReadonlySet<object>>(new Set());
  /** Sélecteur de personne ouvert (partenaire ou commercial) et sa recherche. */
  readonly personPicker = signal<{ group: object; kind: 'partner' | 'commercial' } | null>(null);
  readonly personQuery = signal('');

  /** Produits classés du plus souscrit au moins souscrit. */
  private readonly rankedProducts = computed(() => {
    const counts = new Map<number, number>();
    for (const c of this.allContracts()) counts.set(c.productId, (counts.get(c.productId) ?? 0) + 1);
    return [...this.produits()].sort((a, b) => (counts.get(b.id) ?? 0) - (counts.get(a.id) ?? 0));
  });

  /** Les 3 tuiles de la ligne : les produits les plus souscrits, le produit choisi toujours visible. */
  productTiles(index: number): ProduitResponse[] {
    const ranked = this.rankedProducts();
    const tiles = ranked.slice(0, 3);
    const selected = ranked.find((p) => p.id === Number(this.contractsArray.at(index).controls.productId.value));
    if (selected && !tiles.includes(selected)) tiles[tiles.length - 1] = selected;
    return tiles;
  }

  otherProducts(index: number): ProduitResponse[] {
    const tiles = this.productTiles(index);
    return this.rankedProducts().filter((p) => !tiles.includes(p));
  }

  isAllProductsOpen(index: number): boolean {
    return this.allProductsOpen().has(this.contractsArray.at(index));
  }

  toggleAllProducts(index: number): void {
    this.allProductsOpen.set(this.toggled(this.allProductsOpen(), this.contractsArray.at(index)));
  }

  productInitialOf(name: string): string {
    return productInitial(name);
  }

  selectProduct(index: number, productId: number): void {
    const group = this.contractsArray.at(index);
    group.controls.productId.setValue(productId);
    // Compagnie vide : on propose la compagnie habituelle du produit.
    if (!Number(group.controls.compagnieId.value)) {
      const habituelle = this.compagnies().find((c) => c.id === this.habitualCompagnieId(productId));
      if (habituelle) group.patchValue({ compagnieId: habituelle.id, compagnieQuery: habituelle.nom });
    }
    const open = new Set(this.allProductsOpen());
    open.delete(group);
    this.allProductsOpen.set(open);
    this.recomputeCommission(index);
  }

  /** Compagnie la plus souvent associée au produit dans les contrats existants. */
  private habitualCompagnieId(productId: number): number | null {
    const counts = new Map<number, number>();
    for (const c of this.allContracts()) {
      if (c.productId === productId) counts.set(c.compagnieId, (counts.get(c.compagnieId) ?? 0) + 1);
    }
    let best: number | null = null;
    let bestCount = 0;
    counts.forEach((count, id) => {
      if (count > bestCount) {
        best = id;
        bestCount = count;
      }
    });
    return best;
  }

  isNotesOpen(index: number): boolean {
    const group = this.contractsArray.at(index);
    return this.notesOpen().has(group) || !!group.controls.notes.value.trim();
  }

  openNotes(index: number): void {
    this.notesOpen.set(new Set([...this.notesOpen(), this.contractsArray.at(index)]));
  }

  setStatus(index: number, status: ContractStatus): void {
    this.contractsArray.at(index).controls.status.setValue(status);
  }

  setSignedAt(index: number, preset: 'today' | 'yesterday'): void {
    this.contractsArray.at(index).controls.signedAt.setValue(preset === 'today' ? today() : addDays(today(), -1));
  }

  isSignedAtPreset(index: number, preset: 'today' | 'yesterday'): boolean {
    const value = this.contractsArray.at(index).controls.signedAt.value;
    return value === (preset === 'today' ? today() : addDays(today(), -1));
  }

  toggleRenouvellement(index: number): void {
    const control = this.contractsArray.at(index).controls.isRenouvellement;
    control.setValue(!control.value);
  }

  openPersonPicker(index: number, kind: 'partner' | 'commercial'): void {
    const group = this.contractsArray.at(index);
    const current = this.personPicker();
    this.personQuery.set('');
    this.personPicker.set(current?.group === group && current.kind === kind ? null : { group, kind });
  }

  isPersonPickerOpen(index: number, kind: 'partner' | 'commercial'): boolean {
    const current = this.personPicker();
    return current?.group === this.contractsArray.at(index) && current.kind === kind;
  }

  personOptions(kind: 'partner' | 'commercial'): PersonOption[] {
    const query = this.personQuery().trim().toLowerCase();
    const options: PersonOption[] =
      kind === 'partner'
        ? this.partenaires().map((p) => ({
            id: String(p.id),
            name: `${p.nom} ${p.prenoms}`.trim(),
            detail: [this.typePartenaireLabel(p.type), p.villePartenaireNom].filter(Boolean).join(' · '),
          }))
        : this.profiles().map((p) => ({
            id: String(p.userId),
            name: `${p.firstname} ${p.lastname}`.trim(),
            detail: this.profileDetail(p),
          }));
    return options
      .filter((o) => !query || o.name.toLowerCase().includes(query) || o.detail.toLowerCase().includes(query))
      .map((o) => ({ ...o, initials: initialsOf(o.name) }));
  }

  selectPerson(index: number, kind: 'partner' | 'commercial', id: string): void {
    const group = this.contractsArray.at(index);
    if (kind === 'partner') {
      group.controls.partenaireId.setValue(id);
      this.recomputeCommission(index);
    } else {
      group.controls.salesRepId.setValue(id);
      this.onCommercialChange(index);
    }
    this.personPicker.set(null);
  }

  private profileDetail(profile: ProfileRecord): string {
    const role = isCommercialRole(profile.role) ? 'Commercial' : this.roleLabel(profile.role);
    const ville = this.villes().find((v) => v.id === profile.villePartenaireId)?.nomVille;
    return [role, ville].filter(Boolean).join(' · ');
  }

  private roleLabel(role: string | null | undefined): string {
    if (!role) return '';
    const clean = role.replace(/^ROLE_/, '').replace(/_/g, ' ').toLowerCase();
    return clean.charAt(0).toUpperCase() + clean.slice(1);
  }

  produitView(index: number): ProduitView {
    const group = this.contractsArray.at(index);
    const c = group.controls;
    const produit = this.produits().find((p) => p.id === Number(c.productId.value));
    const compagnie = this.compagnies().find((co) => co.id === Number(c.compagnieId.value));
    const partner = c.partenaireId.value ? this.partenaires().find((p) => p.id === Number(c.partenaireId.value)) : null;
    const commercial = c.salesRepId.value ? this.profiles().find((p) => p.userId === Number(c.salesRepId.value)) : null;
    const partnerName = partner ? `${partner.nom} ${partner.prenoms}`.trim() : '';
    const commercialName = commercial ? `${commercial.firstname} ${commercial.lastname}`.trim() : '';
    const productName = produit?.nomProduit ?? '';
    return {
      productName,
      productInitial: productName ? productInitial(productName) : String(index + 1),
      compagnieName: compagnie?.nom ?? '',
      compagnieHabituelle: !!produit && !!compagnie && this.habitualCompagnieId(produit.id) === compagnie.id,
      partnerName,
      partnerDetail: partner ? [this.typePartenaireLabel(partner.type), partner.villePartenaireNom].filter(Boolean).join(' · ') : '',
      partnerInitials: initialsOf(partnerName),
      commercialName,
      commercialDetail: commercial ? this.profileDetail(commercial) : '',
      commercialInitials: initialsOf(commercialName),
      complete: !!produit && !!compagnie && (!c.isRenouvellement.value || !!c.contratPrecedentId.value),
    };
  }

  // Étape Couverture (maquette validée le 2026-09-27) ------------------------

  readonly dureeOptions = [1, 3, 6, 12];
  /** « Même période pour tous les produits » (plusieurs produits uniquement). */
  readonly samePeriod = signal(true);

  /** Période commune active : seule la 1re carte est affichée et recopiée sur les autres. */
  isSharedPeriod(): boolean {
    return this.samePeriod() && this.contractsArray.length > 1;
  }

  toggleSamePeriod(): void {
    this.samePeriod.set(!this.samePeriod());
    this.syncPeriod(0);
  }

  setDuree(index: number, months: number): void {
    this.contractsArray.at(index).controls.duree.setValue(String(months));
    this.recomputeEcheance(index);
    this.syncPeriod(index);
  }

  setDateEffet(index: number, preset: 'today' | 'tomorrow' | 'nextMonth'): void {
    const value = preset === 'today' ? today() : preset === 'tomorrow' ? addDays(today(), 1) : firstOfNextMonth();
    this.contractsArray.at(index).controls.dateEffet.setValue(value);
    this.recomputeEcheance(index);
    this.syncPeriod(index);
  }

  onDateEffetChange(index: number): void {
    this.recomputeEcheance(index);
    this.syncPeriod(index);
  }

  /** Recopie durée et dates de la ligne sur les autres quand la période est commune. */
  syncPeriod(index: number): void {
    if (!this.isSharedPeriod()) return;
    const { duree, dateEffet, dateEcheance } = this.contractsArray.at(index).getRawValue();
    this.contractsArray.controls.forEach((group, i) => {
      if (i !== index) group.patchValue({ duree, dateEffet, dateEcheance });
    });
  }

  /** Sous-titre de la carte « Tous les produits » : les noms des produits concernés. */
  sharedProductNames(): string {
    return this.contractsArray.controls.map((_, i) => this.productNameAt(i) || `Produit ${i + 1}`).join(' · ');
  }

  dateEffetPresets(): { key: 'today' | 'tomorrow' | 'nextMonth'; label: string; value: string }[] {
    const nextMonth = firstOfNextMonth();
    return [
      { key: 'today', label: "Aujourd'hui", value: today() },
      { key: 'tomorrow', label: 'Demain', value: addDays(today(), 1) },
      {
        key: 'nextMonth',
        label: `1er ${new Date(nextMonth).toLocaleDateString('fr-FR', { month: 'long', timeZone: 'UTC' })}`,
        value: nextMonth,
      },
    ];
  }

  couvertureView(index: number): CouvertureView {
    const raw = this.contractsArray.at(index).getRawValue();
    const months = Number(raw.duree) || 0;
    const valid = !!raw.dateEffet && !!raw.dateEcheance;
    const days = valid ? daysBetween(raw.dateEffet, raw.dateEcheance) + 1 : 0;
    const fromToday = valid ? daysBetween(raw.dateEffet, today()) : 0;
    const produit = this.produits().find((p) => p.id === Number(raw.productId));
    const compagnie = this.compagnies().find((c) => c.id === Number(raw.compagnieId));
    const productName = produit?.nomProduit ?? '—';
    return {
      productName,
      productInitial: productInitial(productName),
      compagnieName: compagnie?.nom ?? '—',
      startLabel: valid ? this.formatLongDate(raw.dateEffet) : '—',
      endLabel: valid ? this.formatLongDate(raw.dateEcheance) : '—',
      days,
      ticks: Array.from({ length: Math.min(Math.max(months, 1), 24) + 1 }, (_, i) => i),
      todayPercent: valid && days > 0 ? Math.max(0, Math.min(100, (fromToday / days) * 100)) : 0,
      effetState: !valid ? 'none' : fromToday === 0 ? 'today' : fromToday < 0 ? 'future' : 'past',
      daysUntilStart: -fromToday,
      echeanceComputed: !!raw.dateEffet && months > 0 && raw.dateEcheance === computeEcheance(raw.dateEffet, months),
      complete: valid && daysBetween(raw.dateEffet, raw.dateEcheance) >= 0,
    };
  }

  isDateEffetPreset(index: number, value: string): boolean {
    return this.contractsArray.at(index).controls.dateEffet.value === value;
  }

  /** « Dim. 27 sept. 2026 ». */
  formatLongDate(value: string): string {
    const label = new Date(value).toLocaleDateString('fr-FR', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  // Colonne de droite « Votre souscription » (étapes Produit et Couverture) ---

  stepChecks(): StepCheckState[] {
    const produitsOk = this.contractsArray.controls.every((_, i) => this.produitView(i).complete);
    const couvertureOk = this.contractsArray.controls.every((_, i) => this.couvertureView(i).complete);
    const step = this.currentStep();
    const state = (ok: boolean, stepIndex: number): 'ok' | 'ko' | 'todo' =>
      ok && step >= stepIndex ? 'ok' : step >= stepIndex ? 'ko' : 'todo';
    return [
      { state: this.assureView().complete ? 'ok' : 'ko', label: 'Assuré renseigné' },
      { state: state(produitsOk, 1), label: state(produitsOk, 1) === 'todo' ? 'Produit à choisir' : 'Produit et compagnie choisis' },
      { state: state(couvertureOk, 2), label: step >= 2 ? 'Couverture définie' : 'Couverture à définir' },
      { state: 'todo', label: 'Paiement à définir' },
    ];
  }

  /** Canal de la souscription : vente directe, ou le(s) partenaire(s). */
  canalLabel(): string {
    const partners = new Set(this.contractsArray.controls.map((g) => g.controls.partenaireId.value).filter(Boolean));
    if (partners.size === 0) return 'Vente directe';
    if (partners.size > 1 || partners.size < this.contractsArray.length) return 'Mixte';
    const partner = this.partenaires().find((p) => p.id === Number([...partners][0]));
    return partner ? `${partner.nom} ${partner.prenoms}`.trim() : 'Partenaire';
  }

  // Récapitulatif (maquette validée le 2026-09-27) ---------------------------

  recapStatusClass(status: string): string {
    return status === 'signed' ? 'ok' : status === 'cancelled' ? 'ko' : status === 'pending' ? 'warn' : 'muted';
  }

  statusLabel(status: string): string {
    return STATUS_LABEL[status] ?? status;
  }

  private toggled(set: ReadonlySet<object>, item: object): ReadonlySet<object> {
    const next = new Set(set);
    if (next.has(item)) next.delete(item);
    else next.add(item);
    return next;
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
    this.insuredActiveIndex.set(0);
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
    this.scrollToTop();
  }

  prevStep(): void {
    this.error.set(null);
    this.currentStep.set(Math.max(this.currentStep() - 1, 0));
    this.scrollToTop();
  }

  goToStep(index: number): void {
    if (index > this.furthestStep()) return;
    this.error.set(null);
    this.currentStep.set(index);
    this.scrollToTop();
  }

  /** Chaque étape s'ouvre en haut de page (sinon on arrive au milieu de la nouvelle étape). */
  private scrollToTop(): void {
    this.personPicker.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
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
    if (!this.isPhoneValid(raw.insured.clientPhone)) {
      this.phoneTouched.set(true);
      this.error.set('Le numéro de téléphone doit comporter 10 chiffres.');
      return false;
    }
    if (raw.insured.clientPhone2.trim() && !this.isPhoneValid(raw.insured.clientPhone2)) {
      this.phone2Touched.set(true);
      this.error.set('Le 2e numéro de téléphone doit comporter 10 chiffres, ou laissez-le vide.');
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
      if (contract.dateEcheance < contract.dateEffet) {
        this.error.set("La date d'échéance doit être postérieure à la date d'effet.");
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
          // Dérivé du montant : seule valeur saisie à l'étape Paiement (en FCFA ou en %).
          commissionRate: percentToApiRate((Number(contract.commissionAmount) / Number(contract.premium)) * 100),
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
    this.phoneTouched.set(false);
    this.phone2Touched.set(false);
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

  formatNumber(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(Math.round(value));
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

interface PaiementCheck {
  ok: boolean;
  label: string;
}

interface PaiementView {
  productName: string;
  productInitial: string;
  subtitle: string;
  premium: number;
  commission: number;
  commissionPercent: number;
  commissionBad: boolean;
  configuredCommission: number;
  commissionModified: boolean;
  noConfig: boolean;
  noConfigType: string;
  shareKind: 'partner' | 'commercial' | null;
  shareLabel: string;
  legendLabel: string;
  share: number;
  shareMissing: boolean;
  shareBad: boolean;
  shareOverCommission: boolean;
  compagnieNet: number;
  cabinet: number;
  widthCompagnie: number;
  widthShare: number;
  widthCabinet: number;
  primeVerseeVisible: boolean;
  intermediaireName: string;
  intermediaireKind: 'partenaire' | 'commercial';
  initials: string;
  checks: PaiementCheck[];
  complete: boolean;
}

interface PaiementSummary {
  premium: number;
  commission: number;
  share: number | null;
  shareLabel: string;
  mode: string;
  net: number;
  checks: PaiementCheck[];
  complete: boolean;
}

interface StepCheck {
  ok: boolean;
  label: string;
}

interface StepCheckState {
  state: 'ok' | 'ko' | 'todo';
  label: string;
}

interface AssureView {
  initials: string;
  fullName: string;
  typeLabel: string;
  existing: boolean;
  phone: string;
  email: string;
  city: string;
  phoneError: boolean;
  phone2: string;
  phone2Error: boolean;
  checks: StepCheck[];
  complete: boolean;
}

interface PersonOption {
  id: string;
  name: string;
  detail: string;
  initials?: string;
}

interface ProduitView {
  productName: string;
  productInitial: string;
  compagnieName: string;
  compagnieHabituelle: boolean;
  partnerName: string;
  partnerDetail: string;
  partnerInitials: string;
  commercialName: string;
  commercialDetail: string;
  commercialInitials: string;
  complete: boolean;
}

interface CouvertureView {
  productName: string;
  productInitial: string;
  compagnieName: string;
  startLabel: string;
  endLabel: string;
  days: number;
  ticks: number[];
  todayPercent: number;
  effetState: 'none' | 'today' | 'future' | 'past';
  daysUntilStart: number;
  echeanceComputed: boolean;
  complete: boolean;
}
