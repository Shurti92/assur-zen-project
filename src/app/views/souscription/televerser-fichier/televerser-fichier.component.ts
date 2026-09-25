import { Component, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';
import { forkJoin, from, of } from 'rxjs';
import { catchError, concatMap, map } from 'rxjs/operators';

import { SouscriptionService } from '../../../core/souscription/souscription.service';
import { InsuredRegistrationRequest, InsuredRegistrationResponse } from '../../../core/souscription/models/souscription.model';
import {
  ContractCommissionStatus,
  ContractStatus,
  ModePaiement,
  PeriodiciteEcheance,
} from '../../../core/contracts/models/contract.model';
import { TypeClient } from '../../../core/insured/models/insured.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { CompagnieService } from '../../../core/configuration/compagnie.service';
import { CompagnieResponse } from '../../../core/configuration/models/compagnie.model';
import { ProduitCommissionTauxService } from '../../../core/configuration/produit-commission-taux.service';
import { ProduitCommissionTauxResponse } from '../../../core/configuration/models/produit-commission-taux.model';
import { PartenaireService } from '../../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../../core/partenaire/models/partenaire.model';
import { configuredCommissionAmount, isPercentConfig, percentToApiRate } from '../../../core/contracts/commission-rate.util';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function computeEcheance(dateEffet: string, months: number): string {
  const date = new Date(dateEffet);
  if (Number.isNaN(date.getTime())) return today();
  date.setMonth(date.getMonth() + months);
  date.setDate(date.getDate() - 1);
  return date.toISOString().slice(0, 10);
}

function isValidYmd(y: number, m: number, d: number): boolean {
  if (m < 1 || m > 12 || d < 1 || d > 31) return false;
  const date = new Date(y, m - 1, d);
  return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d;
}

/** Accepte AAAA-MM-JJ (ISO) ou JJ/MM/AAAA / JJ-MM-AAAA (format courant pour des données historiques à rétrodater).
 * Aucune borne sur le passé : une date antérieure à aujourd'hui est une date valide (contrat rétroactif). */
/** Montant optionnel en FCFA entiers : null si la cellule est vide, 'invalid' si ce n'est pas
 * un entier positif (espaces de milliers tolérés). */
function parseWholeAmount(raw: string | undefined): number | null | 'invalid' {
  const value = (raw ?? '').replace(/[\s\u00a0\u202f]/g, '');
  if (!value) return null;
  return /^\d+$/.test(value) ? Number(value) : 'invalid';
}

function parseFlexibleDate(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;

  let m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(trimmed);
  if (m) {
    const [, y, mo, d] = m;
    return isValidYmd(+y, +mo, +d) ? `${y}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}` : null;
  }

  m = /^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/.exec(trimmed);
  if (m) {
    const [, d, mo, y] = m;
    return isValidYmd(+y, +mo, +d) ? `${y}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}` : null;
  }

  return null;
}

const TEMPLATE_COLUMNS = [
  'nom',
  'prenoms',
  'telephone',
  'ville',
  'email',
  'type_client',
  'produit',
  'compagnie',
  'partenaire',
  'prime',
  'duree_mois',
  'date_effet',
  'taux_commission',
  'numero_police',
  'mode_paiement',
  'nombre_echeances',
  'periodicite_echeances',
  'statut_contrat',
  'notes',
  'montant_commission',
  'part_partenaire',
];

const TEMPLATE_EXAMPLE_ROWS: string[][] = [
  [
    'Kone',
    'Awa',
    '0700000001',
    'Abidjan',
    'awa.kone@example.com',
    'PARTICULIER',
    'Assurance Auto',
    'AXA Cote d Ivoire',
    '',
    '150000',
    '12',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
  ],
  [
    'Kone',
    'Awa',
    '0700000001',
    'Abidjan',
    'awa.kone@example.com',
    'PARTICULIER',
    'Assurance Sante',
    'AXA Cote d Ivoire',
    '',
    '80000',
    '12',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
  ],
  [
    'Traore',
    'Ibrahim',
    '0700000002',
    'Bouake',
    '',
    'ENTREPRISE',
    'Assurance Habitation',
    'SanlamAllianz Cote d Ivoire',
    '',
    '200000',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
  ],
  [
    'Bamba',
    'Fatou',
    '0700000003',
    'Yamoussoukro',
    '',
    'PARTICULIER',
    'Assurance Moto',
    'Coris Assurances Cote d Ivoire',
    '',
    '50000',
    '12',
    '15/01/2025',
    '',
    '',
    '',
    '',
    '',
    '',
    'Exemple de contrat retroactif (date_effet anterieure a aujourdhui, format JJ/MM/AAAA)',
  ],
];

const VALID_TYPE_CLIENT = ['PARTICULIER', 'ENTREPRISE'];
const VALID_MODE_PAIEMENT = ['COMPTANT', 'FRACTIONNE'];
const VALID_PERIODICITES: PeriodiciteEcheance[] = ['MENSUELLE', 'TRIMESTRIELLE', 'SEMESTRIELLE', 'ANNUELLE'];
const VALID_STATUTS: ContractStatus[] = ['signed', 'pending', 'draft', 'cancelled'];

const STATUT_LABEL: Record<string, string> = {
  signed: 'Signé',
  pending: 'En attente',
  draft: 'Brouillon',
  cancelled: 'Annulé',
};

interface ParsedRow {
  line: number;
  nom: string;
  prenoms: string;
  clientPhone: string;
  clientCity: string;
  clientEmail: string;
  typeClient: TypeClient;
  produitNom: string;
  productId: number | null;
  compagnieNom: string;
  compagnieId: number | null;
  partenaireNom: string;
  partenaireId: number | null;
  premium: number;
  duree: string;
  dateEffet: string;
  dateEcheance: string;
  commissionRate: number;
  commissionAmount: number;
  /** Part reversée au partenaire (FCFA entiers) : colonne part_partenaire si remplie, sinon
   * undefined (commission configurée côté backend), 0 sans partenaire. */
  partnerShare: number | undefined;
  policyNumber: string;
  modePaiement: ModePaiement;
  nombreEcheances: number;
  periodiciteEcheances: PeriodiciteEcheance;
  status: ContractStatus;
  commissionStatus: ContractCommissionStatus;
  notes: string;
  errors: string[];
}

type GroupStatus = 'en_attente' | 'en_cours' | 'succes' | 'echec';

interface ClientGroup {
  key: string;
  nom: string;
  prenoms: string;
  clientPhone: string;
  rows: ParsedRow[];
  hasErrors: boolean;
  status: GroupStatus;
  resultMessage: string | null;
  policyNumbers: string[];
}

@Component({
  selector: 'app-televerser-fichier',
  templateUrl: './televerser-fichier.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, AlertMessageComponent],
})
export class TeleverserFichierComponent {
  private readonly souscriptionService = inject(SouscriptionService);
  private readonly produitService = inject(ProduitService);
  private readonly compagnieService = inject(CompagnieService);
  private readonly tauxService = inject(ProduitCommissionTauxService);
  private readonly partenaireService = inject(PartenaireService);

  readonly produits = signal<ProduitResponse[]>([]);
  readonly compagnies = signal<CompagnieResponse[]>([]);
  readonly commissionTaux = signal<ProduitCommissionTauxResponse[]>([]);
  readonly partenaires = signal<PartenaireResponse[]>([]);
  readonly referenceLoading = signal(true);
  readonly referenceError = signal<string | null>(null);

  readonly steps = ['FICHIER', 'APERÇU', 'RÉSULTAT'] as const;
  readonly currentStep = signal(0);

  readonly selectedFileName = signal<string | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly fileReading = signal(false);

  readonly clientGroups = signal<ClientGroup[]>([]);
  readonly totalRows = computed(() => this.clientGroups().reduce((sum, g) => sum + g.rows.length, 0));
  readonly rowsWithErrors = computed(() =>
    this.clientGroups().reduce((sum, g) => sum + g.rows.filter((r) => r.errors.length > 0).length, 0),
  );
  readonly validGroups = computed(() => this.clientGroups().filter((g) => !g.hasErrors));
  readonly invalidGroups = computed(() => this.clientGroups().filter((g) => g.hasErrors));

  readonly processing = signal(false);
  readonly processedCount = signal(0);
  readonly succeededCount = computed(() => this.clientGroups().filter((g) => g.status === 'succes').length);
  readonly failedCount = computed(() => this.clientGroups().filter((g) => g.status === 'echec').length);

  constructor() {
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
    }).subscribe({
      next: ({ produits, compagnies, taux, partenaires }) => {
        // Les produits de type Flotte se souscrivent depuis le module Gestion de flotte, pas ici.
        this.produits.set(produits.content.filter((p) => p.productTypeCode !== 'FLOTTE'));
        this.compagnies.set(compagnies.content);
        this.commissionTaux.set(taux);
        this.partenaires.set(partenaires.content);
        this.referenceLoading.set(false);
      },
      error: () => {
        this.referenceError.set(
          'Impossible de charger les données de référence (produits, compagnies, taux…).',
        );
        this.referenceLoading.set(false);
      },
    });
  }

  downloadTemplate(): void {
    const exampleRows = TEMPLATE_EXAMPLE_ROWS.map((row) => [...row, ...Array(TEMPLATE_COLUMNS.length - row.length).fill('')]);
    const lines = [TEMPLATE_COLUMNS, ...exampleRows].map((cols) => cols.join(';'));
    const csv = '﻿' + lines.join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'modele_souscription_masse.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.fileError.set(null);
    this.fileReading.set(true);
    this.selectedFileName.set(file.name);

    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result ?? '');
      const lines = text.split(/\r\n|\n|\r/).filter((l) => l.trim().length > 0);
      if (lines.length < 2) {
        this.fileError.set('Le fichier est vide ou ne contient aucune ligne de données.');
        this.fileReading.set(false);
        return;
      }
      const splitLine = (line: string) => line.split(';').map((c) => c.trim().replace(/^"|"$/g, ''));
      const header = splitLine(lines[0]).map((h) => h.toLowerCase());
      const missingColumns = ['nom', 'prenoms', 'telephone', 'ville', 'produit', 'compagnie', 'prime'].filter(
        (required) => !header.includes(required),
      );
      if (missingColumns.length > 0) {
        this.fileError.set(
          `Colonnes obligatoires manquantes dans l'en-tête : ${missingColumns.join(', ')}. Utilisez le modèle fourni.`,
        );
        this.fileReading.set(false);
        return;
      }

      const parsedRows: ParsedRow[] = lines.slice(1).map((line, index) => {
        const values = splitLine(line);
        const raw: Record<string, string> = {};
        header.forEach((col, i) => (raw[col] = values[i] ?? ''));
        return this.resolveRow(raw, index + 2);
      });

      this.clientGroups.set(this.groupRows(parsedRows));
      this.fileReading.set(false);
      this.currentStep.set(1);
    };
    reader.onerror = () => {
      this.fileError.set('Impossible de lire ce fichier.');
      this.fileReading.set(false);
    };
    reader.readAsText(file, 'UTF-8');
  }

  private resolveRow(raw: Record<string, string>, line: number): ParsedRow {
    const errors: string[] = [];

    const nom = (raw['nom'] ?? '').trim();
    const prenoms = (raw['prenoms'] ?? '').trim();
    const clientPhone = (raw['telephone'] ?? '').trim();
    const clientCity = (raw['ville'] ?? '').trim();
    const clientEmail = (raw['email'] ?? '').trim();
    if (!nom) errors.push('Nom manquant');
    if (!prenoms) errors.push('Prénoms manquant');
    if (!clientPhone) errors.push('Téléphone manquant');
    if (!clientCity) errors.push('Ville manquante');
    if (clientEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clientEmail)) {
      errors.push(`Email invalide « ${clientEmail} »`);
    }

    const typeClientRaw = (raw['type_client'] ?? '').trim().toUpperCase();
    const typeClient: TypeClient = typeClientRaw === 'ENTREPRISE' ? 'ENTREPRISE' : 'PARTICULIER';
    if (typeClientRaw && !VALID_TYPE_CLIENT.includes(typeClientRaw)) {
      errors.push(`Type de client invalide « ${raw['type_client']} »`);
    }

    const produitNom = (raw['produit'] ?? '').trim();
    const produit = this.produits().find((p) => p.nomProduit.toLowerCase() === produitNom.toLowerCase());
    if (!produitNom) errors.push('Produit manquant');
    else if (!produit) errors.push(`Produit inconnu « ${produitNom} »`);

    const compagnieNom = (raw['compagnie'] ?? '').trim();
    const compagnie = this.compagnies().find((c) => c.nom.toLowerCase() === compagnieNom.toLowerCase());
    if (!compagnieNom) errors.push('Compagnie manquante');
    else if (!compagnie) errors.push(`Compagnie inconnue « ${compagnieNom} »`);

    const partenaireNom = (raw['partenaire'] ?? '').trim();
    let partenaireId: number | null = null;
    if (partenaireNom) {
      const partenaire = this.partenaires().find(
        (p) =>
          `${p.nom} ${p.prenoms}`.toLowerCase() === partenaireNom.toLowerCase() ||
          p.nom.toLowerCase() === partenaireNom.toLowerCase(),
      );
      if (!partenaire) errors.push(`Partenaire inconnu « ${partenaireNom} »`);
      else partenaireId = partenaire.id;
    }

    const premiumRaw = (raw['prime'] ?? '').trim().replace(/\s/g, '').replace(',', '.');
    const premium = Number(premiumRaw);
    if (!premiumRaw) errors.push('Prime manquante');
    else if (!Number.isFinite(premium) || premium <= 0) errors.push(`Prime invalide « ${raw['prime']} »`);

    const dureeRaw = (raw['duree_mois'] ?? '').trim();
    const duree = dureeRaw || '12';
    if (!/^\d+$/.test(duree)) errors.push(`Durée invalide « ${dureeRaw} »`);

    const dateEffetRaw = (raw['date_effet'] ?? '').trim();
    let dateEffet = today();
    if (dateEffetRaw) {
      const parsed = parseFlexibleDate(dateEffetRaw);
      if (parsed) {
        dateEffet = parsed;
      } else {
        errors.push(`Date d'effet invalide « ${dateEffetRaw} » (formats acceptés : AAAA-MM-JJ ou JJ/MM/AAAA)`);
      }
    }
    // Les dates antérieures à aujourd'hui sont acceptées (contrats rétroactifs / reprise d'historique).
    const dateEcheance = computeEcheance(dateEffet, Number(duree) || 12);

    let commissionRate = 0;
    let tauxConfig: ProduitCommissionTauxResponse | null = null;
    const tauxRaw = (raw['taux_commission'] ?? '').trim().replace(',', '.');
    if (tauxRaw) {
      commissionRate = Number(tauxRaw);
      if (!Number.isFinite(commissionRate)) errors.push(`Taux de commission invalide « ${raw['taux_commission']} »`);
    } else if (produit) {
      const type = partenaireId ? (this.partenaires().find((p) => p.id === partenaireId)?.type ?? 'DIRECT') : 'DIRECT';
      const match = this.commissionTaux().find((t) => t.produitId === produit.id && t.typePartenaire === type && t.active);
      tauxConfig = match ?? null;
      commissionRate = match && isPercentConfig(match) ? match.tauxCommission : 0;
    }
    // montant_commission / part_partenaire : montants en FCFA entiers, prioritaires sur le calcul
    // à partir du taux (un montant saisi est toujours conservé tel quel).
    const montantCommission = parseWholeAmount(raw['montant_commission']);
    if (montantCommission === 'invalid') {
      errors.push(`Montant de commission invalide « ${raw['montant_commission']} » (montant entier en FCFA attendu)`);
    }
    let commissionAmount = !Number.isFinite(premium)
      ? 0
      : tauxConfig && !isPercentConfig(tauxConfig)
        ? configuredCommissionAmount(tauxConfig, premium)
        : Math.round((premium * commissionRate) / 100);
    if (tauxConfig && !isPercentConfig(tauxConfig) && premium > 0) {
      commissionRate = Math.round((commissionAmount / premium) * 10000) / 100;
    }
    if (typeof montantCommission === 'number') {
      commissionAmount = montantCommission;
      if (!tauxRaw && premium > 0) commissionRate = Math.round((montantCommission / premium) * 10000) / 100;
    }

    const partPartenaire = parseWholeAmount(raw['part_partenaire']);
    // Sans colonne part_partenaire, le backend applique la commission partenaire configurée
    // (/partner-commissions) — ou refuse la ligne en 400 s'il n'y en a pas.
    let partnerShare: number | undefined = partenaireId ? undefined : 0;
    if (partPartenaire === 'invalid') {
      errors.push(`Part partenaire invalide « ${raw['part_partenaire']} » (montant entier en FCFA attendu)`);
    } else if (typeof partPartenaire === 'number') {
      if (!partenaireNom) errors.push('Part partenaire renseignée sans partenaire');
      else if (Number.isFinite(premium) && partPartenaire > premium) errors.push('La part partenaire ne peut pas dépasser la prime');
      else partnerShare = partPartenaire;
    }

    const modePaiementRaw = (raw['mode_paiement'] ?? '').trim().toUpperCase();
    const modePaiement: ModePaiement = modePaiementRaw === 'FRACTIONNE' ? 'FRACTIONNE' : 'COMPTANT';
    if (modePaiementRaw && !VALID_MODE_PAIEMENT.includes(modePaiementRaw)) {
      errors.push(`Mode de paiement invalide « ${raw['mode_paiement']} »`);
    }
    const nombreEcheancesRaw = (raw['nombre_echeances'] ?? '').trim();
    const nombreEcheances = nombreEcheancesRaw ? Number(nombreEcheancesRaw) : 3;
    if (modePaiement === 'FRACTIONNE' && (!Number.isFinite(nombreEcheances) || nombreEcheances < 2)) {
      errors.push('Nombre d’échéances invalide pour un paiement fractionné (minimum 2)');
    }
    const periodiciteRaw = (raw['periodicite_echeances'] ?? '').trim().toUpperCase();
    const periodiciteEcheances = (VALID_PERIODICITES as string[]).includes(periodiciteRaw)
      ? (periodiciteRaw as PeriodiciteEcheance)
      : 'MENSUELLE';

    const statutRaw = (raw['statut_contrat'] ?? '').trim().toLowerCase();
    const status = (VALID_STATUTS as string[]).includes(statutRaw) ? (statutRaw as ContractStatus) : 'signed';
    if (statutRaw && !(VALID_STATUTS as string[]).includes(statutRaw)) {
      errors.push(`Statut de contrat invalide « ${raw['statut_contrat']} »`);
    }

    return {
      line,
      nom,
      prenoms,
      clientPhone,
      clientCity,
      clientEmail,
      typeClient,
      produitNom,
      productId: produit?.id ?? null,
      compagnieNom,
      compagnieId: compagnie?.id ?? null,
      partenaireNom,
      partenaireId,
      premium: Number.isFinite(premium) ? premium : 0,
      duree,
      dateEffet,
      dateEcheance,
      commissionRate,
      commissionAmount,
      partnerShare,
      policyNumber: (raw['numero_police'] ?? '').trim(),
      modePaiement,
      nombreEcheances,
      periodiciteEcheances,
      status,
      commissionStatus: 'pending',
      notes: (raw['notes'] ?? '').trim(),
      errors,
    };
  }

  private groupRows(rows: ParsedRow[]): ClientGroup[] {
    const map = new Map<string, ClientGroup>();
    for (const row of rows) {
      const key = row.clientPhone || `__ligne_${row.line}`;
      let group = map.get(key);
      if (!group) {
        group = {
          key,
          nom: row.nom,
          prenoms: row.prenoms,
          clientPhone: row.clientPhone,
          rows: [],
          hasErrors: false,
          status: 'en_attente',
          resultMessage: null,
          policyNumbers: [],
        };
        map.set(key, group);
      }
      group.rows.push(row);
      if (row.errors.length > 0) group.hasErrors = true;
    }
    return Array.from(map.values());
  }

  statutLabel(value: string): string {
    return STATUT_LABEL[value] ?? value;
  }

  sumPremium(group: ClientGroup): number {
    return group.rows.reduce((sum, row) => sum + row.premium, 0);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  backToFile(): void {
    this.currentStep.set(0);
  }

  startImport(): void {
    const valid = this.validGroups();
    if (valid.length === 0 || this.processing()) return;

    this.processing.set(true);
    this.processedCount.set(0);
    this.currentStep.set(2);

    from(valid)
      .pipe(
        concatMap((group) => {
          group.status = 'en_cours';
          this.clientGroups.set([...this.clientGroups()]);
          const firstRow = group.rows[0];
          const payload: InsuredRegistrationRequest = {
            insured: {
              nom: group.nom,
              prenoms: group.prenoms,
              clientPhone: group.clientPhone || undefined,
              clientCity: firstRow.clientCity || undefined,
              clientEmail: firstRow.clientEmail || undefined,
              typeClient: firstRow.typeClient,
            },
            contracts: group.rows.map((row) => ({
              policyNumber: row.policyNumber || undefined,
              dateEffet: row.dateEffet,
              duree: row.duree,
              dateEcheance: row.dateEcheance,
              productId: row.productId as number,
              partenaireId: row.partenaireId ?? undefined,
              compagnieId: row.compagnieId as number,
              premium: row.premium,
              commissionRate: percentToApiRate(row.commissionRate),
              commissionAmount: row.commissionAmount,
              partnerShare: row.partnerShare,
              status: row.status,
              commissionStatus: row.commissionStatus,
              modePaiement: row.modePaiement,
              nombreEcheances: row.modePaiement === 'FRACTIONNE' ? row.nombreEcheances : undefined,
              periodiciteEcheances: row.modePaiement === 'FRACTIONNE' ? row.periodiciteEcheances : undefined,
              notes: row.notes || undefined,
            })),
          };
          return this.souscriptionService.submit(payload).pipe(
            map((result) => ({ group, result, error: null as string | null })),
            catchError((err) =>
              of({
                group,
                result: null as InsuredRegistrationResponse | null,
                error: (err?.error?.message as string | undefined) ?? "Échec de l'enregistrement.",
              }),
            ),
          );
        }),
      )
      .subscribe({
        next: ({ group, result, error }) => {
          if (result) {
            group.status = 'succes';
            group.policyNumbers = result.contracts.map((c) => c.policyNumber);
            group.resultMessage = `${result.contracts.length} contrat(s) créé(s) — ${group.policyNumbers.join(', ')}.`;
          } else {
            group.status = 'echec';
            group.resultMessage = error;
          }
          this.processedCount.update((n) => n + 1);
          this.clientGroups.set([...this.clientGroups()]);
        },
        complete: () => {
          this.processing.set(false);
        },
      });
  }

  resetAll(): void {
    this.selectedFileName.set(null);
    this.fileError.set(null);
    this.clientGroups.set([]);
    this.processedCount.set(0);
    this.currentStep.set(0);
  }
}
