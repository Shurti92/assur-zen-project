import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { FleetService } from '../../core/fleet/fleet.service';
import {
  AttachContractRequest,
  FleetContractResponse,
  FleetEcheanceResponse,
  FleetImportErrorResponse,
  FleetImportResponse,
  FleetImportStatus,
  FleetMovementResponse,
  FleetMovementType,
  FleetRequest,
  FleetResponse,
  FleetStatus,
  FleetType,
  FleetVehicleResponse,
  FleetVehicleStatus,
  VehiculeRequest,
  VehiculeResponse,
  VehiculeStatut,
  VehiculeType,
} from '../../core/fleet/models/fleet.model';
import { InsuredService } from '../../core/insured/insured.service';
import { InsuredResponse, TypeClient } from '../../core/insured/models/insured.model';
import { ProduitService } from '../../core/configuration/produit.service';
import { ProduitResponse } from '../../core/configuration/models/produit.model';
import { CompagnieService } from '../../core/configuration/compagnie.service';
import { CompagnieResponse } from '../../core/configuration/models/compagnie.model';
import { ContractService } from '../../core/contracts/contract.service';
import { ContractRequest, ContractResponse, ContractStatus } from '../../core/contracts/models/contract.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { EntityDocumentsComponent } from '../../shared/entity-documents/entity-documents.component';

type View = 'flottes' | 'vehicules';
type FleetDetailView = 'vehicules' | 'mouvements' | 'contrats' | 'echeances' | 'import' | 'documents';
const PAGE_SIZE = 20;

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Même calcul que souscription.component.ts (échéance = effet + durée - 1 jour), dupliqué
 * localement pour ne pas créer de dépendance croisée entre les modules Flotte et Souscription. */
function computeEcheance(dateEffet: string, months: number): string {
  const date = new Date(dateEffet);
  date.setMonth(date.getMonth() + months);
  date.setDate(date.getDate() - 1);
  return date.toISOString().slice(0, 10);
}

const FLEET_TYPE_LABEL: Record<FleetType, string> = { AUTO: 'Auto', MOTO: 'Moto', MIXED: 'Mixte' };

/** Options du sélecteur segmenté "Type de flotte" de la modale de création/modification. */
const FLEET_TYPE_OPTIONS: { value: FleetType; label: string; icon: string }[] = [
  { value: 'AUTO', label: 'Auto', icon: 'cilCarAlt' },
  { value: 'MOTO', label: 'Moto', icon: 'cilSpeedometer' },
  { value: 'MIXED', label: 'Mixte', icon: 'cilLayers' },
];

/** Icône d'identité de la fiche détail flotte — même mapping que FLEET_TYPE_OPTIONS. */
const FLEET_TYPE_ICON: Record<FleetType, string> = { AUTO: 'cilCarAlt', MOTO: 'cilSpeedometer', MIXED: 'cilLayers' };

/** Icônes des onglets de la fiche détail flotte — Véhicules reprend l'icône de type de la flotte. */
const FLEET_DETAIL_TAB_ICON: Record<FleetDetailView, string> = {
  vehicules: '',
  mouvements: 'cilHistory',
  contrats: 'cilDescription',
  echeances: 'clock',
  import: 'cilCloudUpload',
  documents: 'cilFile',
};

/** Même vocabulaire que ContratsMessagesComponent (views/contrats/messages) — dupliqué
 * volontairement pour ne pas créer de dépendance croisée entre les modules Flotte et Contrats. */
const NOTIF_TYPE_LABEL: Partial<Record<string, string>> = {
  bienvenue: 'Bienvenue',
  rappel_echeance: "Rappel d'échéance",
  avis_echeance: "Avis d'échéance",
  fin_contrat: 'Fin de contrat',
};

const NOTIF_STATUT_LABEL: Partial<Record<string, string>> = {
  en_attente: 'En attente',
  envoye: 'Envoyé',
  echoue: 'Échec',
  non_envoye: 'Échec',
  message_anti_date: 'Anti-daté',
};

const NOTIF_STATUT_VARIANT: Partial<Record<string, string>> = {
  en_attente: 'warning',
  envoye: 'success',
  echoue: 'danger',
  non_envoye: 'danger',
  message_anti_date: 'neutral',
};

const FLEET_STATUS_LABEL: Record<FleetStatus, string> = {
  DRAFT: 'Brouillon',
  ACTIVE: 'Active',
  SUSPENDED: 'Suspendue',
  CLOSED: 'Clôturée',
  EXPIRED: 'Expirée',
};

const FLEET_STATUS_VARIANT: Record<FleetStatus, string> = {
  DRAFT: 'neutral',
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  CLOSED: 'danger',
  EXPIRED: 'danger',
};

const VEHICULE_TYPE_LABEL: Record<VehiculeType, string> = { AUTO: 'Auto', MOTO: 'Moto' };

/** Options du sélecteur segmenté "Type de véhicule" de la modale Nouveau/Modifier véhicule —
 * "Véhicule" plutôt que "Auto" ici : à côté de "Moto" le libellé générique se lit mieux comme
 * un vrai choix binaire (deux-roues ou non), alors que la colonne "Type" des tableaux garde
 * VEHICULE_TYPE_LABEL ("Auto") tel quel. */
const VEHICULE_TYPE_TOGGLE_OPTIONS: { value: VehiculeType; label: string; icon: string }[] = [
  { value: 'AUTO', label: 'Véhicule', icon: 'cilCarAlt' },
  { value: 'MOTO', label: 'Moto', icon: 'cilSpeedometer' },
];

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
  }
  return fallback;
}

const VEHICULE_STATUT_LABEL: Record<VehiculeStatut, string> = {
  ACTIF: 'Actif',
  HORS_SERVICE: 'Hors service',
  VENDU: 'Vendu',
};

const VEHICULE_STATUT_VARIANT: Record<VehiculeStatut, string> = {
  ACTIF: 'success',
  HORS_SERVICE: 'warning',
  VENDU: 'neutral',
};

const FLEET_VEHICLE_STATUS_LABEL: Record<FleetVehicleStatus, string> = {
  ACTIVE: 'Actif dans la flotte',
  REMOVED: 'Retiré',
  TRANSFERRED: 'Transféré',
  REPLACED: 'Remplacé',
};

const FLEET_VEHICLE_STATUS_VARIANT: Record<FleetVehicleStatus, string> = {
  ACTIVE: 'success',
  REMOVED: 'neutral',
  TRANSFERRED: 'info',
  REPLACED: 'warning',
};

const FLEET_MOVEMENT_TYPE_LABEL: Record<FleetMovementType, string> = {
  ADD: 'Ajout',
  REMOVE: 'Retrait',
  REPLACE: 'Remplacement',
  TRANSFER: 'Transfert',
};

const FLEET_IMPORT_STATUS_LABEL: Record<FleetImportStatus, string> = {
  UPLOADED: 'Téléversé',
  VALIDATING: 'Validation en cours',
  READY: 'Prêt à confirmer',
  PROCESSING: 'Traitement en cours',
  COMPLETED: 'Terminé',
  PARTIALLY_COMPLETED: 'Partiellement terminé',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
};

const FLEET_IMPORT_STATUS_VARIANT: Record<FleetImportStatus, string> = {
  UPLOADED: 'neutral',
  VALIDATING: 'info',
  READY: 'info',
  PROCESSING: 'warning',
  COMPLETED: 'success',
  PARTIALLY_COMPLETED: 'warning',
  FAILED: 'danger',
  CANCELLED: 'neutral',
};

/** Reprend les 4 valeurs de ContractStatus (core/contracts) — dupliqué volontairement en local
 * pour ne pas créer de dépendance croisée entre les modules Flotte et Contrats. */
const CONTRACT_STATUS_LABEL: Partial<Record<string, string>> = {
  signed: 'Signé',
  pending: 'En attente',
  draft: 'Brouillon',
  cancelled: 'Annulé',
};

const CONTRACT_STATUS_VARIANT: Record<string, string> = {
  signed: 'success',
  pending: 'warning',
  draft: 'neutral',
  cancelled: 'danger',
};

@Component({
  selector: 'app-fleet',
  templateUrl: './fleet.component.html',
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
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
    EntityDocumentsComponent,
  ],
})
export class FleetComponent {
  private readonly fleetService = inject(FleetService);
  private readonly insuredService = inject(InsuredService);
  private readonly produitService = inject(ProduitService);
  private readonly compagnieService = inject(CompagnieService);
  private readonly contractService = inject(ContractService);
  private readonly fb = inject(FormBuilder);

  readonly view = signal<View>('flottes');
  readonly fleetTypeLabel = FLEET_TYPE_LABEL;
  readonly fleetTypeOptions = FLEET_TYPE_OPTIONS;
  readonly fleetStatusLabel = FLEET_STATUS_LABEL;
  readonly vehiculeTypeLabel = VEHICULE_TYPE_LABEL;
  readonly vehiculeTypeToggleOptions = VEHICULE_TYPE_TOGGLE_OPTIONS;
  readonly fleetTypeIcon = FLEET_TYPE_ICON;
  readonly fleetDetailTabIcon = FLEET_DETAIL_TAB_ICON;
  readonly vehiculeStatutLabel = VEHICULE_STATUT_LABEL;
  readonly fleetVehicleStatusLabel = FLEET_VEHICLE_STATUS_LABEL;
  readonly fleetMovementTypeLabel = FLEET_MOVEMENT_TYPE_LABEL;
  readonly fleetImportStatusLabel = FLEET_IMPORT_STATUS_LABEL;
  readonly contractStatusLabel = CONTRACT_STATUS_LABEL;

  readonly insureds = signal<InsuredResponse[]>([]);
  /** Produits de type "Flotte" (référentiel Configuration > Type de produit, code FLOTTE) —
   * seuls proposés au choix à la création/modification d'une flotte, et réutilisés pour le
   * produit d'un contrat créé depuis l'onglet Véhicules d'une flotte. */
  readonly fleetProducts = signal<ProduitResponse[]>([]);
  readonly compagnies = signal<CompagnieResponse[]>([]);

  setView(view: View): void {
    this.view.set(view);
  }

  constructor() {
    this.fetchFleets();
    this.fetchVehicules();
    this.insuredService.list(0, 500).subscribe({
      next: (page) => this.insureds.set(page.content),
      error: () => undefined,
    });
    this.produitService.list(0, 100, undefined, true, 'FLOTTE').subscribe({
      next: (page) => this.fleetProducts.set(page.content),
      error: () => undefined,
    });
    this.compagnieService.list(0, 200).subscribe({
      next: (page) => this.compagnies.set(page.content),
      error: () => undefined,
    });
  }

  /** Immatriculation à afficher pour un véhicule de flotte — retombe sur son n° de châssis
   * (résolu depuis le registre véhicules déjà chargé) quand la plaque n'est pas encore
   * attribuée (moto neuve, cf. l'API du 2026-09-13) : FleetVehicleResponse ne porte pas
   * lui-même le n° de châssis, d'où cette petite résolution croisée plutôt qu'un champ vide. */
  fleetVehicleLabel(fv: FleetVehicleResponse | null | undefined): string {
    if (!fv) return '';
    return this.resolveVehicleLabel(fv.vehiculeId, fv.immatriculation);
  }

  /** Même repli que fleetVehicleLabel(), pour FleetContractResponse qui porte les mêmes deux
   * champs (vehiculeId/immatriculation, nullable si le véhicule a été supprimé) sans être du
   * même type — voir l'onglet Contrats de la fiche détail flotte. */
  fleetContractVehicleLabel(c: FleetContractResponse): string {
    return this.resolveVehicleLabel(c.vehiculeId, c.immatriculation);
  }

  private resolveVehicleLabel(vehiculeId: number | null, immatriculation: string | null): string {
    if (immatriculation) return immatriculation;
    if (vehiculeId == null) return 'Véhicule supprimé';
    const vehicule = this.vehicules().find((v) => v.id === vehiculeId);
    return vehicule?.numeroChassis || '—';
  }

  insuredName(id: number): string {
    const insured = this.insureds().find((i) => i.id === id);
    return insured ? `${insured.nom} ${insured.prenoms}` : `#${id}`;
  }

  /** Initiales pour l'avatar de la fiche "client résolu" (ex. "Konan Marie" → "KM"). */
  insuredInitials(insured: InsuredResponse): string {
    return `${insured.nom.charAt(0)}${insured.prenoms.charAt(0)}`.toUpperCase();
  }

  /** Initiales pour l'avatar client de l'en-tête détail flotte — fleet.clientName est une
   * chaîne "à plat" (pas un InsuredResponse), d'où ce petit helper séparé d'insuredInitials(). */
  clientInitials(name: string): string {
    const [first = '', second = ''] = name.trim().split(/\s+/);
    return `${first.charAt(0)}${second.charAt(0)}`.toUpperCase();
  }

  /** Taux d'occupation (véhicules actifs / total) affiché en barre de progression dans l'en-tête
   * détail flotte — 0 par défaut plutôt que NaN quand la flotte est vide. */
  occupancyPercent(fleet: FleetResponse): number {
    return fleet.totalVehicles > 0 ? Math.round((fleet.activeVehicles / fleet.totalVehicles) * 100) : 0;
  }

  /** Filtrage client-side — this.insureds() est déjà entièrement chargé (jusqu'à 500 assurés)
   * au constructeur, pas besoin d'un aller-retour serveur comme dans souscription. */
  private searchInsuredsLocally(query: string): InsuredResponse[] {
    const q = query.trim().toLowerCase();
    if (q.length < 2) return [];
    return this.insureds()
      .filter((i) => `${i.nom} ${i.prenoms} ${i.clientPhone ?? ''}`.toLowerCase().includes(q))
      .slice(0, 8);
  }

  // -- création d'un assuré à la volée, depuis le champ d'autocomplétion (client ou propriétaire) --

  readonly createInsuredVisible = signal(false);
  readonly createInsuredSaving = signal(false);
  readonly createInsuredError = signal<string | null>(null);
  private createInsuredTarget: 'client' | 'owner' | null = null;

  readonly createInsuredForm = this.fb.nonNullable.group({
    nom: [''],
    prenoms: [''],
    clientPhone: [''],
    clientCity: [''],
    clientEmail: [''],
    typeClient: ['PARTICULIER' as TypeClient],
  });

  /** Pré-remplit nom/prénoms à partir de ce que l'utilisateur a déjà tapé dans le champ de recherche. */
  openCreateInsured(target: 'client' | 'owner'): void {
    this.createInsuredTarget = target;
    this.createInsuredError.set(null);
    const typed = (target === 'client' ? this.clientSearchQuery() : this.ownerSearchQuery()).trim();
    const [nom = '', ...rest] = typed.split(/\s+/);
    this.createInsuredForm.reset({
      nom,
      prenoms: rest.join(' '),
      clientPhone: '',
      clientCity: '',
      clientEmail: '',
      typeClient: 'PARTICULIER',
    });
    if (target === 'client') this.clientSearchOpen.set(false);
    else this.ownerSearchOpen.set(false);
    this.createInsuredVisible.set(true);
  }

  submitCreateInsured(): void {
    const raw = this.createInsuredForm.getRawValue();
    if (!raw.nom.trim() || !raw.prenoms.trim()) {
      this.createInsuredError.set('Le nom et les prénoms sont requis.');
      return;
    }
    this.createInsuredSaving.set(true);
    this.createInsuredError.set(null);
    this.insuredService
      .create({
        nom: raw.nom.trim(),
        prenoms: raw.prenoms.trim(),
        clientPhone: raw.clientPhone.trim() || undefined,
        clientCity: raw.clientCity.trim() || undefined,
        clientEmail: raw.clientEmail.trim() || undefined,
        typeClient: raw.typeClient,
      })
      .subscribe({
        next: (insured) => {
          this.createInsuredSaving.set(false);
          this.createInsuredVisible.set(false);
          this.insureds.set([...this.insureds(), insured]);
          if (this.createInsuredTarget === 'client') this.selectClient(insured);
          else if (this.createInsuredTarget === 'owner') this.selectOwner(insured);
        },
        error: () => {
          this.createInsuredSaving.set(false);
          this.createInsuredError.set('Impossible de créer cet assuré.');
        },
      });
  }

  fleetStatusVariant(status: FleetStatus): string {
    return FLEET_STATUS_VARIANT[status];
  }

  vehiculeStatutVariant(statut: VehiculeStatut): string {
    return VEHICULE_STATUT_VARIANT[statut];
  }

  fleetVehicleStatusVariant(status: FleetVehicleStatus): string {
    return FLEET_VEHICLE_STATUS_VARIANT[status];
  }

  fleetImportStatusVariant(status: FleetImportStatus): string {
    return FLEET_IMPORT_STATUS_VARIANT[status];
  }

  contractStatusVariant(status: string): string {
    return CONTRACT_STATUS_VARIANT[status] ?? 'neutral';
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  /** Délai du rappel avant l'échéance du contrat (« J-45 », « J0 »…), pour la frise de l'onglet
   * Échéances — plus lisible que de répéter la même heure fixe (minuit) sur chaque rappel. */
  reminderOffsetLabel(dateEnvoi: string | null, dateEcheance: string | null): string {
    if (!dateEnvoi || !dateEcheance) return '—';
    const days = Math.round((new Date(dateEcheance).getTime() - new Date(dateEnvoi).getTime()) / 86_400_000);
    return days > 0 ? `J-${days}` : 'J0';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  // ================================================================ FLOTTES ================================================================

  readonly fleets = signal<FleetResponse[]>([]);
  readonly fleetsLoading = signal(true);
  readonly fleetsError = signal<string | null>(null);
  readonly fleetSearch = signal('');
  readonly fleetTypeFilter = signal<'all' | FleetType>('all');
  readonly fleetStatusFilter = signal<'all' | FleetStatus>('all');
  readonly fleetsPage = signal(0);
  private fleetSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  readonly filteredFleets = computed(() => {
    const query = this.fleetSearch().trim().toLowerCase();
    const type = this.fleetTypeFilter();
    const status = this.fleetStatusFilter();
    return this.fleets().filter((f) => {
      if (type !== 'all' && f.fleetType !== type) return false;
      if (status !== 'all' && f.status !== status) return false;
      if (query && !`${f.fleetNumber} ${f.name} ${f.clientName}`.toLowerCase().includes(query)) return false;
      return true;
    });
  });

  readonly fleetsTotalPages = computed(() => Math.max(1, Math.ceil(this.filteredFleets().length / PAGE_SIZE)));
  readonly pagedFleets = computed(() => {
    const start = this.fleetsPage() * PAGE_SIZE;
    return this.filteredFleets().slice(start, start + PAGE_SIZE);
  });

  /** Autres flottes ACTIVE du même client — cibles possibles d'un transfert. */
  activeFleetsForClient(clientId: number, excludeFleetId: number): FleetResponse[] {
    return this.fleets().filter((f) => f.clientId === clientId && f.id !== excludeFleetId && f.status === 'ACTIVE');
  }

  fetchFleets(): void {
    this.fleetsLoading.set(true);
    this.fleetsError.set(null);
    this.fleetService.listAllFleets().subscribe({
      next: (page) => {
        this.fleets.set(page.content);
        this.fleetsLoading.set(false);
        // Le modal de détail garde sa propre copie (selectedFleet) — la resynchroniser ici
        // évite que les compteurs de véhicules y restent figés après un ajout/retrait.
        const selected = this.selectedFleet();
        if (selected) {
          const updated = page.content.find((f) => f.id === selected.id);
          if (updated) this.selectedFleet.set(updated);
        }
      },
      error: () => {
        this.fleetsError.set('Impossible de charger les flottes.');
        this.fleetsLoading.set(false);
      },
    });
  }

  onFleetSearchInput(value: string): void {
    this.fleetSearch.set(value);
    if (this.fleetSearchDebounce) clearTimeout(this.fleetSearchDebounce);
    this.fleetSearchDebounce = setTimeout(() => this.fleetsPage.set(0), 300);
  }

  setFleetTypeFilter(value: string): void {
    this.fleetTypeFilter.set(value as 'all' | FleetType);
    this.fleetsPage.set(0);
  }

  setFleetStatusFilter(value: string): void {
    this.fleetStatusFilter.set(value as 'all' | FleetStatus);
    this.fleetsPage.set(0);
  }

  goToFleetsPage(index: number): void {
    if (index < 0 || index >= this.fleetsTotalPages()) return;
    this.fleetsPage.set(index);
  }

  // -- création / modification --

  readonly fleetFormVisible = signal(false);
  readonly fleetFormSaving = signal(false);
  readonly fleetFormError = signal<string | null>(null);
  readonly editingFleetId = signal<number | null>(null);

  readonly fleetForm = this.fb.nonNullable.group({
    fleetNumber: [''],
    clientId: [''],
    name: [''],
    description: [''],
    fleetType: ['AUTO' as FleetType],
    produitId: [''],
    startDate: [''],
    endDate: [''],
    managerName: [''],
    managerPhone: [''],
    managerEmail: [''],
  });

  // -- autocomplétion du client (assuré) --

  readonly clientSearchQuery = signal('');
  readonly clientSearchOpen = signal(false);
  readonly clientSearchResults = computed(() => this.searchInsuredsLocally(this.clientSearchQuery()));

  /** Fiche du client sélectionné — affichée à la place du champ de recherche une fois résolue. */
  selectedClientInsured(): InsuredResponse | undefined {
    const id = this.fleetForm.controls.clientId.value;
    return id ? this.insureds().find((i) => String(i.id) === id) : undefined;
  }

  onClientSearchInput(value: string): void {
    this.clientSearchQuery.set(value);
    this.fleetForm.controls.clientId.setValue('');
    this.clientSearchOpen.set(value.trim().length > 0);
  }

  onClientSearchFocus(): void {
    if (this.clientSearchQuery().trim().length >= 2) this.clientSearchOpen.set(true);
  }

  /** Fermeture différée pour laisser le (mousedown) d'un résultat s'exécuter avant le blur. */
  onClientSearchBlur(): void {
    setTimeout(() => this.clientSearchOpen.set(false), 150);
  }

  selectClient(insured: InsuredResponse): void {
    this.fleetForm.controls.clientId.setValue(String(insured.id));
    this.clientSearchQuery.set(`${insured.nom} ${insured.prenoms}`);
    this.clientSearchOpen.set(false);
  }

  clearClientSelection(): void {
    this.fleetForm.controls.clientId.setValue('');
    this.clientSearchQuery.set('');
    this.clientSearchOpen.set(false);
  }

  openCreateFleet(): void {
    this.editingFleetId.set(null);
    this.fleetFormError.set(null);
    this.fleetForm.reset({
      fleetNumber: '',
      clientId: '',
      name: '',
      description: '',
      fleetType: 'AUTO',
      produitId: '',
      startDate: '',
      endDate: '',
      managerName: '',
      managerPhone: '',
      managerEmail: '',
    });
    this.clientSearchQuery.set('');
    this.clientSearchOpen.set(false);
    this.fleetFormVisible.set(true);
  }

  openEditFleet(fleet: FleetResponse): void {
    this.editingFleetId.set(fleet.id);
    this.fleetFormError.set(null);
    this.fleetForm.reset({
      fleetNumber: fleet.fleetNumber ?? '',
      clientId: String(fleet.clientId),
      name: fleet.name,
      description: fleet.description ?? '',
      fleetType: fleet.fleetType,
      produitId: fleet.produitId != null ? String(fleet.produitId) : '',
      startDate: fleet.startDate ?? '',
      endDate: fleet.endDate ?? '',
      managerName: fleet.managerName ?? '',
      managerPhone: fleet.managerPhone ?? '',
      managerEmail: fleet.managerEmail ?? '',
    });
    this.clientSearchQuery.set(this.insuredName(fleet.clientId));
    this.clientSearchOpen.set(false);
    this.fleetFormVisible.set(true);
  }

  saveFleet(): void {
    const raw = this.fleetForm.getRawValue();
    if (!raw.clientId || !raw.name.trim()) {
      this.fleetFormError.set('Le client et le nom de la flotte sont requis.');
      return;
    }
    this.fleetFormSaving.set(true);
    this.fleetFormError.set(null);
    const payload: FleetRequest = {
      fleetNumber: raw.fleetNumber.trim() || undefined,
      clientId: Number(raw.clientId),
      name: raw.name.trim(),
      description: raw.description.trim() || undefined,
      fleetType: raw.fleetType,
      produitId: raw.produitId ? Number(raw.produitId) : undefined,
      startDate: raw.startDate || undefined,
      endDate: raw.endDate || undefined,
      managerName: raw.managerName.trim() || undefined,
      managerPhone: raw.managerPhone.trim() || undefined,
      managerEmail: raw.managerEmail.trim() || undefined,
    };
    const id = this.editingFleetId();
    const request = id ? this.fleetService.updateFleet(id, payload) : this.fleetService.createFleet(payload);
    request.subscribe({
      next: (fleet) => {
        this.fleetFormSaving.set(false);
        this.fleetFormVisible.set(false);
        this.fetchFleets();
        if (this.selectedFleet()?.id === fleet.id) this.selectedFleet.set(fleet);
      },
      error: () => {
        this.fleetFormSaving.set(false);
        this.fleetFormError.set("Impossible d'enregistrer cette flotte.");
      },
    });
  }

  // -- transitions de statut --

  readonly fleetActionPendingId = signal<number | null>(null);
  readonly closeFleetTarget = signal<FleetResponse | null>(null);

  activateFleet(fleet: FleetResponse): void {
    this.fleetActionPendingId.set(fleet.id);
    this.fleetsError.set(null);
    this.fleetService.activateFleet(fleet.id).subscribe({
      next: (updated) => {
        this.fleetActionPendingId.set(null);
        this.applyFleetUpdate(updated);
      },
      error: () => {
        this.fleetActionPendingId.set(null);
        this.fleetsError.set('Impossible d’activer cette flotte.');
      },
    });
  }

  suspendFleet(fleet: FleetResponse): void {
    this.fleetActionPendingId.set(fleet.id);
    this.fleetsError.set(null);
    this.fleetService.suspendFleet(fleet.id).subscribe({
      next: (updated) => {
        this.fleetActionPendingId.set(null);
        this.applyFleetUpdate(updated);
      },
      error: () => {
        this.fleetActionPendingId.set(null);
        this.fleetsError.set('Impossible de suspendre cette flotte.');
      },
    });
  }

  askCloseFleet(fleet: FleetResponse): void {
    this.closeFleetTarget.set(fleet);
  }

  confirmCloseFleet(): void {
    const fleet = this.closeFleetTarget();
    this.closeFleetTarget.set(null);
    if (!fleet) return;
    this.fleetActionPendingId.set(fleet.id);
    this.fleetsError.set(null);
    this.fleetService.closeFleet(fleet.id).subscribe({
      next: (updated) => {
        this.fleetActionPendingId.set(null);
        this.applyFleetUpdate(updated);
      },
      error: () => {
        this.fleetActionPendingId.set(null);
        this.fleetsError.set('Impossible de clôturer cette flotte.');
      },
    });
  }

  private applyFleetUpdate(updated: FleetResponse): void {
    this.fleets.set(this.fleets().map((f) => (f.id === updated.id ? updated : f)));
    if (this.selectedFleet()?.id === updated.id) this.selectedFleet.set(updated);
  }

  // ================================================================ DÉTAIL D'UNE FLOTTE ================================================================

  readonly fleetDetailVisible = signal(false);
  readonly selectedFleet = signal<FleetResponse | null>(null);
  readonly fleetDetailView = signal<FleetDetailView>('vehicules');

  openFleetDetail(fleet: FleetResponse): void {
    this.selectedFleet.set(fleet);
    this.fleetDetailView.set('vehicules');
    this.fleetVehicles.set([]);
    this.fleetMovements.set([]);
    this.fleetContracts.set([]);
    this.fleetEcheances.set([]);
    this.fleetImports.set([]);
    this.fleetDetailVisible.set(true);
    this.fetchFleetVehicles();
  }

  closeFleetDetail(): void {
    this.fleetDetailVisible.set(false);
    this.selectedFleet.set(null);
  }

  setFleetDetailView(view: FleetDetailView): void {
    this.fleetDetailView.set(view);
    if (view === 'vehicules' && this.fleetVehicles().length === 0 && !this.fleetVehiclesLoading()) this.fetchFleetVehicles();
    if (view === 'mouvements' && this.fleetMovements().length === 0 && !this.fleetMovementsLoading()) this.fetchFleetMovements();
    if (view === 'contrats' && this.fleetContracts().length === 0 && !this.fleetContractsLoading()) this.fetchFleetContracts();
    if (view === 'echeances' && this.fleetEcheances().length === 0 && !this.fleetEcheancesLoading()) this.fetchFleetEcheances();
    if (view === 'import' && this.fleetImports().length === 0 && !this.fleetImportsLoading()) this.fetchFleetImports();
  }

  // -- Véhicules de la flotte --

  readonly fleetVehicles = signal<FleetVehicleResponse[]>([]);
  readonly fleetVehiclesLoading = signal(false);
  readonly fleetVehiclesError = signal<string | null>(null);
  readonly fleetVehiclesStatusFilter = signal<'all' | FleetVehicleStatus>('all');

  fetchFleetVehicles(): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetVehiclesLoading.set(true);
    this.fleetVehiclesError.set(null);
    const status = this.fleetVehiclesStatusFilter();
    this.fleetService.listFleetVehicles(fleet.id, 0, 200, status === 'all' ? undefined : { status }).subscribe({
      next: (page) => {
        this.fleetVehicles.set(page.content);
        this.fleetVehiclesLoading.set(false);
      },
      error: () => {
        this.fleetVehiclesError.set('Impossible de charger les véhicules de cette flotte.');
        this.fleetVehiclesLoading.set(false);
      },
    });
  }

  setFleetVehiclesStatusFilter(value: string): void {
    this.fleetVehiclesStatusFilter.set(value as 'all' | FleetVehicleStatus);
    this.fetchFleetVehicles();
  }

  /** Véhicules pas déjà rattachés à une flotte et d'un type compatible avec celui de la flotte —
   * candidats à l'ajout/remplacement. Filtre proactif côté frontend depuis le 2026-09-13 (une
   * flotte AUTO/MOTO n'accepte que son propre type, MIXED accepte les deux ; le backend renvoie
   * 409 sinon). Le propriétaire du véhicule n'a PAS à être le client de la flotte (règle métier
   * du 2026-09-24) : le nom du propriétaire est affiché dans la liste pour les distinguer. */
  readonly availableVehiculesForSelectedFleet = computed(() => {
    const fleet = this.selectedFleet();
    if (!fleet) return [];
    return this.vehicules().filter(
      (v) =>
        !v.currentFleetId &&
        (fleet.fleetType === 'MIXED' || v.type === fleet.fleetType),
    );
  });

  // -- ajouter --

  readonly addVehicleFormVisible = signal(false);
  readonly addVehicleSaving = signal(false);
  readonly addVehicleError = signal<string | null>(null);
  readonly addVehicleForm = this.fb.nonNullable.group({ vehiculeId: [''], reason: [''] });

  openAddVehicle(): void {
    this.addVehicleError.set(null);
    this.addVehicleForm.reset({ vehiculeId: '', reason: '' });
    this.addVehicleFormVisible.set(true);
  }

  submitAddVehicle(): void {
    const fleet = this.selectedFleet();
    const raw = this.addVehicleForm.getRawValue();
    if (!fleet) return;
    if (!raw.vehiculeId) {
      this.addVehicleError.set('Sélectionnez un véhicule.');
      return;
    }
    this.addVehicleSaving.set(true);
    this.addVehicleError.set(null);
    this.fleetService
      .addFleetVehicle(fleet.id, { vehiculeId: Number(raw.vehiculeId), reason: raw.reason.trim() || undefined })
      .subscribe({
        next: () => {
          this.addVehicleSaving.set(false);
          this.addVehicleFormVisible.set(false);
          this.fetchFleetVehicles();
          this.fetchFleets();
          this.fetchVehicules();
        },
        error: (err) => {
          this.addVehicleSaving.set(false);
          this.addVehicleError.set(extractErrorMessage(err, "Impossible d'ajouter ce véhicule à la flotte."));
        },
      });
  }

  // -- créer un contrat pour ce véhicule --
  // POST /contracts est l'endpoint générique standard (pas de route dédiée flotte) — confirmé
  // par le backend le 2026-09-13 : vehiculeId est un champ optionnel de ContractRequest, à
  // renseigner directement ici. Vente directe uniquement (pas de partenaire) : le formulaire
  // exige donc un taux ou un montant de commission, comme le ferait la sélection "vente
  // directe" du wizard Souscription (voir souscription.component.ts, recomputeCommission()).

  readonly createContractTarget = signal<FleetVehicleResponse | null>(null);
  readonly createContractSaving = signal(false);
  readonly createContractError = signal<string | null>(null);
  readonly createContractForm = this.fb.nonNullable.group({
    productId: [0],
    compagnieId: [0],
    duree: ['12'],
    dateEffet: [today()],
    policyNumber: [''],
    premium: [0],
    commissionRate: [0],
    commissionAmount: [0],
    status: ['signed' as ContractStatus],
  });

  /** Véhicule complet (registre) derrière la ligne de flotte ciblée — pour afficher son
   * identité dans la modale et résoudre son propriétaire (insuredId du contrat). */
  createContractVehicule(): VehiculeResponse | undefined {
    const target = this.createContractTarget();
    if (!target) return undefined;
    return this.vehicules().find((v) => v.id === target.vehiculeId);
  }

  askCreateContract(fv: FleetVehicleResponse): void {
    this.createContractError.set(null);
    const dateEffet = today();
    this.createContractForm.reset({
      productId: 0,
      compagnieId: 0,
      duree: '12',
      dateEffet,
      policyNumber: '',
      premium: 0,
      commissionRate: 0,
      commissionAmount: 0,
      status: 'signed',
    });
    this.productSearchQuery.set('');
    this.productSearchOpen.set(false);
    this.compagnieSearchQuery.set('');
    this.compagnieSearchOpen.set(false);
    this.createContractTarget.set(fv);
  }

  // -- autocomplétion produit & compagnie (modale "Créer un contrat") — filtrage 100% local,
  // les deux listes sont déjà entièrement chargées au constructeur (cf. fleetProducts/compagnies). --

  readonly productSearchQuery = signal('');
  readonly productSearchOpen = signal(false);
  readonly productSearchResults = computed(() => {
    const q = this.productSearchQuery().trim().toLowerCase();
    const list = this.fleetProducts();
    return q ? list.filter((p) => p.nomProduit.toLowerCase().includes(q)) : list;
  });

  selectedProduct(): ProduitResponse | undefined {
    const id = this.createContractForm.controls.productId.value;
    return id ? this.fleetProducts().find((p) => p.id === id) : undefined;
  }

  onProductSearchInput(value: string): void {
    this.productSearchQuery.set(value);
    this.createContractForm.controls.productId.setValue(0);
    this.productSearchOpen.set(true);
  }

  onProductSearchFocus(): void {
    this.productSearchOpen.set(true);
  }

  /** Fermeture différée pour laisser le (mousedown) d'un résultat s'exécuter avant le blur. */
  onProductSearchBlur(): void {
    setTimeout(() => this.productSearchOpen.set(false), 150);
  }

  selectProduct(produit: ProduitResponse): void {
    this.createContractForm.controls.productId.setValue(produit.id);
    this.productSearchQuery.set(produit.nomProduit);
    this.productSearchOpen.set(false);
  }

  clearProductSelection(): void {
    this.createContractForm.controls.productId.setValue(0);
    this.productSearchQuery.set('');
    this.productSearchOpen.set(false);
  }

  readonly compagnieSearchQuery = signal('');
  readonly compagnieSearchOpen = signal(false);
  readonly compagnieSearchResults = computed(() => {
    const q = this.compagnieSearchQuery().trim().toLowerCase();
    const list = this.compagnies();
    return q ? list.filter((c) => c.nom.toLowerCase().includes(q)) : list;
  });

  selectedCompagnie(): CompagnieResponse | undefined {
    const id = this.createContractForm.controls.compagnieId.value;
    return id ? this.compagnies().find((c) => c.id === id) : undefined;
  }

  onCompagnieSearchInput(value: string): void {
    this.compagnieSearchQuery.set(value);
    this.createContractForm.controls.compagnieId.setValue(0);
    this.compagnieSearchOpen.set(true);
  }

  onCompagnieSearchFocus(): void {
    this.compagnieSearchOpen.set(true);
  }

  /** Fermeture différée pour laisser le (mousedown) d'un résultat s'exécuter avant le blur. */
  onCompagnieSearchBlur(): void {
    setTimeout(() => this.compagnieSearchOpen.set(false), 150);
  }

  selectCompagnie(compagnie: CompagnieResponse): void {
    this.createContractForm.controls.compagnieId.setValue(compagnie.id);
    this.compagnieSearchQuery.set(compagnie.nom);
    this.compagnieSearchOpen.set(false);
  }

  clearCompagnieSelection(): void {
    this.createContractForm.controls.compagnieId.setValue(0);
    this.compagnieSearchQuery.set('');
    this.compagnieSearchOpen.set(false);
  }

  submitCreateContract(): void {
    const vehicule = this.createContractVehicule();
    if (!vehicule) return;
    const raw = this.createContractForm.getRawValue();
    if (!raw.productId || !raw.compagnieId) {
      this.createContractError.set('Sélectionnez un produit et une compagnie.');
      return;
    }
    if (!raw.premium) {
      this.createContractError.set('Renseignez la prime.');
      return;
    }
    if (!raw.commissionRate && !raw.commissionAmount) {
      this.createContractError.set(
        'Vente directe (sans partenaire) : renseignez le taux ou le montant de la commission.',
      );
      return;
    }
    this.createContractSaving.set(true);
    this.createContractError.set(null);
    const dateEffet = raw.dateEffet || today();
    const duree = Number(raw.duree) || 12;
    const payload: ContractRequest = {
      productId: Number(raw.productId),
      insuredId: vehicule.proprietaireId,
      compagnieId: Number(raw.compagnieId),
      vehiculeId: vehicule.id,
      duree: raw.duree,
      dateEffet,
      dateEcheance: computeEcheance(dateEffet, duree),
      policyNumber: raw.policyNumber.trim() || undefined,
      premium: Number(raw.premium),
      partnerShare: 0,
      status: raw.status,
      signedAt: raw.status === 'signed' ? dateEffet : undefined,
      commissionRate: raw.commissionRate || undefined,
      commissionAmount: raw.commissionAmount || undefined,
    };
    this.contractService.create(payload).subscribe({
      next: () => {
        this.createContractSaving.set(false);
        this.createContractTarget.set(null);
        this.fleetDetailView.set('contrats');
        this.fetchFleetContracts();
        this.fetchFleetEcheances();
      },
      error: (err) => {
        this.createContractSaving.set(false);
        this.createContractError.set(extractErrorMessage(err, 'Impossible de créer ce contrat.'));
      },
    });
  }

  /** Traduit en direct le taux/montant de commission saisis, pour que l'utilisateur voie le
   * résultat au lieu de deviner lequel des deux champs se déduit de l'autre. */
  createContractCommissionPreview(): string {
    const raw = this.createContractForm.getRawValue();
    const premium = Number(raw.premium) || 0;
    const rate = Number(raw.commissionRate) || 0;
    const amount = Number(raw.commissionAmount) || 0;
    if (rate > 0) {
      const computed = Math.round((premium * rate) / 100);
      return `≈ ${this.formatAmount(computed)} de commission (${rate} % de ${this.formatAmount(premium)})`;
    }
    if (amount > 0 && premium > 0) {
      return `Taux équivalent ≈ ${((amount / premium) * 100).toFixed(2)} % de la prime`;
    }
    return 'Renseignez le taux ou le montant de commission — l’autre se déduit automatiquement.';
  }

  // -- rattacher un contrat mono existant --
  // POST /fleets/{fleetId}/contracts/attach (livré et confirmé en E2E live par le backend le
  // 2026-09-14) : rattache un contrat mono déjà souscrit hors flotte (sans véhicule) à un
  // véhicule de cette flotte. Ajoute le véhicule à la flotte au passage s'il n'y est pas encore.
  // Le backend refuse (409) si le contrat a déjà un véhicule, si le véhicule est déjà lié à un
  // autre contrat non annulé, si le propriétaire du véhicule diffère de l'assuré du contrat, ou
  // si le véhicule est déjà actif dans une autre flotte.

  readonly attachContractVisible = signal(false);
  readonly attachContractSaving = signal(false);
  readonly attachContractError = signal<string | null>(null);
  readonly attachContractSearchQuery = signal('');
  readonly attachContractSearchOpen = signal(false);
  /** Contrats mono candidats, chargés une fois à l'ouverture de la modale — la recherche qui
   * suit est un filtrage 100% local (même pattern que productSearchResults/compagnieSearchResults
   * plus bas), pour filtrer en direct à chaque frappe sans dépendre du paramètre q du backend. */
  readonly attachContractAll = signal<ContractResponse[]>([]);
  readonly attachContractLoading = signal(false);
  readonly attachContractLoadError = signal<string | null>(null);
  readonly attachContractSelected = signal<ContractResponse | null>(null);
  readonly attachContractForm = this.fb.nonNullable.group({ vehiculeId: [''], reason: [''] });

  /** Contrats mono (sans véhicule rattaché) correspondant à la recherche en cours — un contrat
   * déjà lié à un véhicule passe par un autre flux côté backend (remplacement, pas rattachement). */
  readonly attachContractSearchResults = computed(() => {
    const q = this.attachContractSearchQuery().trim().toLowerCase();
    const mono = this.attachContractAll().filter((c) => c.vehiculeId == null);
    const filtered = q ? mono.filter((c) => `${c.policyNumber} ${c.insuredName}`.toLowerCase().includes(q)) : mono;
    return filtered.slice(0, 20);
  });

  openAttachContract(): void {
    this.attachContractError.set(null);
    this.attachContractSearchQuery.set('');
    this.attachContractSearchOpen.set(false);
    this.attachContractSelected.set(null);
    this.attachContractForm.reset({ vehiculeId: '', reason: '' });
    this.attachContractVisible.set(true);
    this.attachContractLoading.set(true);
    this.attachContractLoadError.set(null);
    this.contractService.listAll().subscribe({
      next: (page) => {
        this.attachContractLoading.set(false);
        this.attachContractAll.set(page.content);
      },
      error: () => {
        this.attachContractLoading.set(false);
        this.attachContractLoadError.set('Impossible de charger les contrats.');
      },
    });
  }

  /** Véhicules du propriétaire du contrat sélectionné, compatibles avec le type de flotte
   * (déjà membres de cette flotte, ou pas encore rattachés à une flotte — le backend refuse
   * les deux autres cas : véhicule lié à un autre contrat, véhicule actif ailleurs). */
  readonly attachContractVehiculeOptions = computed(() => {
    const contract = this.attachContractSelected();
    const fleet = this.selectedFleet();
    if (!contract || !fleet) return [];
    return this.vehicules().filter(
      (v) =>
        v.proprietaireId === contract.insuredId &&
        (fleet.fleetType === 'MIXED' || v.type === fleet.fleetType) &&
        (v.currentFleetId == null || v.currentFleetId === fleet.id),
    );
  });

  onAttachContractSearchInput(value: string): void {
    this.attachContractSearchQuery.set(value);
    this.attachContractSearchOpen.set(true);
  }

  onAttachContractSearchFocus(): void {
    this.attachContractSearchOpen.set(true);
  }

  /** Fermeture différée pour laisser le (mousedown) d'un résultat s'exécuter avant le blur. */
  onAttachContractSearchBlur(): void {
    setTimeout(() => this.attachContractSearchOpen.set(false), 150);
  }

  selectAttachContract(contract: ContractResponse): void {
    this.attachContractSelected.set(contract);
    this.attachContractSearchOpen.set(false);
    this.attachContractForm.controls.vehiculeId.setValue('');
  }

  clearAttachContractSelection(): void {
    this.attachContractSelected.set(null);
    this.attachContractSearchQuery.set('');
    this.attachContractForm.controls.vehiculeId.setValue('');
  }

  submitAttachContract(): void {
    const fleet = this.selectedFleet();
    const contract = this.attachContractSelected();
    const raw = this.attachContractForm.getRawValue();
    if (!fleet || !contract) return;
    if (!raw.vehiculeId) {
      this.attachContractError.set('Sélectionnez un véhicule/Moto du même assuré.');
      return;
    }
    this.attachContractSaving.set(true);
    this.attachContractError.set(null);
    const payload: AttachContractRequest = {
      contractId: contract.id,
      vehiculeId: Number(raw.vehiculeId),
      reason: raw.reason.trim() || undefined,
    };
    this.fleetService.attachFleetContract(fleet.id, payload).subscribe({
      next: () => {
        this.attachContractSaving.set(false);
        this.attachContractVisible.set(false);
        this.fleetDetailView.set('contrats');
        this.fetchFleetContracts();
        this.fetchFleetVehicles();
        this.fetchFleets();
        this.fetchVehicules();
      },
      error: (err) => {
        this.attachContractSaving.set(false);
        this.attachContractError.set(extractErrorMessage(err, 'Impossible de rattacher ce contrat à la flotte.'));
      },
    });
  }

  // -- retirer --

  readonly removeVehicleTarget = signal<FleetVehicleResponse | null>(null);
  readonly removeVehicleSaving = signal(false);
  readonly removeVehicleError = signal<string | null>(null);
  readonly removeVehicleForm = this.fb.nonNullable.group({ reason: [''] });

  askRemoveVehicle(fv: FleetVehicleResponse): void {
    this.removeVehicleError.set(null);
    this.removeVehicleForm.reset({ reason: '' });
    this.removeVehicleTarget.set(fv);
  }

  submitRemoveVehicle(): void {
    const fleet = this.selectedFleet();
    const target = this.removeVehicleTarget();
    if (!fleet || !target || target.vehiculeId == null) return;
    const raw = this.removeVehicleForm.getRawValue();
    this.removeVehicleSaving.set(true);
    this.removeVehicleError.set(null);
    this.fleetService
      .removeFleetVehicle(fleet.id, { vehiculeId: target.vehiculeId, reason: raw.reason.trim() || undefined })
      .subscribe({
        next: () => {
          this.removeVehicleSaving.set(false);
          this.removeVehicleTarget.set(null);
          this.fetchFleetVehicles();
          this.fetchFleets();
          this.fetchVehicules();
        },
        error: () => {
          this.removeVehicleSaving.set(false);
          this.removeVehicleError.set('Impossible de retirer ce véhicule.');
        },
      });
  }

  // -- remplacer --

  readonly replaceVehicleTarget = signal<FleetVehicleResponse | null>(null);
  readonly replaceVehicleSaving = signal(false);
  readonly replaceVehicleError = signal<string | null>(null);
  readonly replaceVehicleForm = this.fb.nonNullable.group({ newVehiculeId: [''], reason: [''] });

  askReplaceVehicle(fv: FleetVehicleResponse): void {
    this.replaceVehicleError.set(null);
    this.replaceVehicleForm.reset({ newVehiculeId: '', reason: '' });
    this.replaceVehicleTarget.set(fv);
  }

  submitReplaceVehicle(): void {
    const fleet = this.selectedFleet();
    const target = this.replaceVehicleTarget();
    if (!fleet || !target || target.vehiculeId == null) return;
    const raw = this.replaceVehicleForm.getRawValue();
    if (!raw.newVehiculeId) {
      this.replaceVehicleError.set('Sélectionnez le véhicule de remplacement.');
      return;
    }
    this.replaceVehicleSaving.set(true);
    this.replaceVehicleError.set(null);
    this.fleetService
      .replaceFleetVehicle(fleet.id, {
        oldVehiculeId: target.vehiculeId,
        newVehiculeId: Number(raw.newVehiculeId),
        reason: raw.reason.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.replaceVehicleSaving.set(false);
          this.replaceVehicleTarget.set(null);
          this.fetchFleetVehicles();
          this.fetchFleets();
          this.fetchVehicules();
        },
        error: () => {
          this.replaceVehicleSaving.set(false);
          this.replaceVehicleError.set('Impossible de remplacer ce véhicule.');
        },
      });
  }

  // -- transférer --

  readonly transferVehicleTarget = signal<FleetVehicleResponse | null>(null);
  readonly transferVehicleSaving = signal(false);
  readonly transferVehicleError = signal<string | null>(null);
  readonly transferVehicleForm = this.fb.nonNullable.group({ destinationFleetId: [''], reason: [''] });

  askTransferVehicle(fv: FleetVehicleResponse): void {
    this.transferVehicleError.set(null);
    this.transferVehicleForm.reset({ destinationFleetId: '', reason: '' });
    this.transferVehicleTarget.set(fv);
  }

  submitTransferVehicle(): void {
    const fleet = this.selectedFleet();
    const target = this.transferVehicleTarget();
    if (!fleet || !target || target.vehiculeId == null) return;
    const raw = this.transferVehicleForm.getRawValue();
    if (!raw.destinationFleetId) {
      this.transferVehicleError.set('Sélectionnez la flotte de destination.');
      return;
    }
    this.transferVehicleSaving.set(true);
    this.transferVehicleError.set(null);
    this.fleetService
      .transferFleetVehicle(fleet.id, target.vehiculeId, {
        destinationFleetId: Number(raw.destinationFleetId),
        reason: raw.reason.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.transferVehicleSaving.set(false);
          this.transferVehicleTarget.set(null);
          this.fetchFleetVehicles();
          this.fetchFleets();
          this.fetchVehicules();
        },
        error: () => {
          this.transferVehicleSaving.set(false);
          this.transferVehicleError.set('Impossible de transférer ce véhicule.');
        },
      });
  }

  // -- Mouvements --

  readonly fleetMovements = signal<FleetMovementResponse[]>([]);
  readonly fleetMovementsLoading = signal(false);
  readonly fleetMovementsError = signal<string | null>(null);
  readonly fleetMovementsTypeFilter = signal<'all' | FleetMovementType>('all');

  fetchFleetMovements(): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetMovementsLoading.set(true);
    this.fleetMovementsError.set(null);
    const movementType = this.fleetMovementsTypeFilter();
    this.fleetService
      .listFleetMovements(fleet.id, 0, 200, movementType === 'all' ? undefined : { movementType })
      .subscribe({
        next: (page) => {
          this.fleetMovements.set(page.content);
          this.fleetMovementsLoading.set(false);
        },
        error: () => {
          this.fleetMovementsError.set("Impossible de charger l'historique des mouvements.");
          this.fleetMovementsLoading.set(false);
        },
      });
  }

  setFleetMovementsTypeFilter(value: string): void {
    this.fleetMovementsTypeFilter.set(value as 'all' | FleetMovementType);
    this.fetchFleetMovements();
  }

  // -- Contrats --

  readonly fleetContracts = signal<FleetContractResponse[]>([]);
  readonly fleetContractsLoading = signal(false);
  readonly fleetContractsError = signal<string | null>(null);

  fetchFleetContracts(): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetContractsLoading.set(true);
    this.fleetContractsError.set(null);
    this.fleetService.getFleetContracts(fleet.id).subscribe({
      next: (list) => {
        this.fleetContracts.set(list);
        this.fleetContractsLoading.set(false);
      },
      error: () => {
        this.fleetContractsError.set('Impossible de charger les contrats de cette flotte.');
        this.fleetContractsLoading.set(false);
      },
    });
  }

  // -- Échéances --

  readonly fleetEcheances = signal<FleetEcheanceResponse[]>([]);
  readonly fleetEcheancesLoading = signal(false);
  readonly fleetEcheancesError = signal<string | null>(null);
  readonly notifTypeLabel = NOTIF_TYPE_LABEL;
  readonly notifStatutLabel = NOTIF_STATUT_LABEL;
  readonly notifStatutVariant = NOTIF_STATUT_VARIANT;

  fetchFleetEcheances(): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetEcheancesLoading.set(true);
    this.fleetEcheancesError.set(null);
    this.fleetService.getFleetEcheances(fleet.id).subscribe({
      next: (list) => {
        this.fleetEcheances.set(list);
        this.fleetEcheancesLoading.set(false);
      },
      error: () => {
        this.fleetEcheancesError.set('Impossible de charger les échéances de cette flotte.');
        this.fleetEcheancesLoading.set(false);
      },
    });
  }

  // -- Import CSV/Excel --

  readonly fleetImports = signal<FleetImportResponse[]>([]);
  readonly fleetImportsLoading = signal(false);
  readonly fleetImportsError = signal<string | null>(null);

  readonly importPreview = signal<FleetImportResponse | null>(null);
  readonly importPreviewing = signal(false);
  readonly importConfirming = signal(false);

  readonly importDetailVisible = signal(false);
  readonly selectedImport = signal<FleetImportResponse | null>(null);
  readonly importErrors = signal<FleetImportErrorResponse[]>([]);
  readonly importErrorsLoading = signal(false);

  fetchFleetImports(): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetImportsLoading.set(true);
    this.fleetImportsError.set(null);
    this.fleetService.listImports(fleet.id, 0, 100).subscribe({
      next: (page) => {
        this.fleetImports.set(page.content);
        this.fleetImportsLoading.set(false);
      },
      error: () => {
        this.fleetImportsError.set("Impossible de charger l'historique des imports.");
        this.fleetImportsLoading.set(false);
      },
    });
  }

  downloadTemplate(format: 'csv' | 'xlsx'): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.fleetService.getImportTemplate(fleet.id, format).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `modele-import-vehicules.${format}`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.fleetImportsError.set('Impossible de télécharger le modèle.');
      },
    });
  }

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const fleet = this.selectedFleet();
    if (!file || !fleet) return;
    this.importPreviewing.set(true);
    this.fleetImportsError.set(null);
    this.importPreview.set(null);
    this.fleetService.previewImport(fleet.id, file).subscribe({
      next: (result) => {
        this.importPreviewing.set(false);
        input.value = '';
        this.importPreview.set(result);
      },
      error: () => {
        this.importPreviewing.set(false);
        input.value = '';
        this.fleetImportsError.set('Impossible de valider ce fichier.');
      },
    });
  }

  cancelImportPreview(): void {
    this.importPreview.set(null);
  }

  confirmImportPreview(): void {
    const fleet = this.selectedFleet();
    const preview = this.importPreview();
    if (!fleet || !preview) return;
    this.importConfirming.set(true);
    this.fleetImportsError.set(null);
    this.fleetService.confirmImport(fleet.id, preview.id).subscribe({
      next: () => {
        this.importConfirming.set(false);
        this.importPreview.set(null);
        this.fetchFleetImports();
        this.fetchFleetVehicles();
        this.fetchFleets();
        this.fetchVehicules();
      },
      error: () => {
        this.importConfirming.set(false);
        this.fleetImportsError.set("Impossible de confirmer l'import.");
      },
    });
  }

  openImportDetail(imp: FleetImportResponse): void {
    this.selectedImport.set(imp);
    this.importErrors.set([]);
    this.importDetailVisible.set(true);
    if ((imp.errorCount ?? 0) > 0 || (imp.invalidRows ?? 0) > 0 || (imp.duplicateRows ?? 0) > 0) {
      this.fetchImportErrors(imp.id);
    }
  }

  private fetchImportErrors(importId: number): void {
    const fleet = this.selectedFleet();
    if (!fleet) return;
    this.importErrorsLoading.set(true);
    this.fleetService.listImportErrors(fleet.id, importId, 0, 200).subscribe({
      next: (page) => {
        this.importErrors.set(page.content);
        this.importErrorsLoading.set(false);
      },
      error: () => {
        this.importErrorsLoading.set(false);
      },
    });
  }

  // ================================================================ VÉHICULES ================================================================

  readonly vehicules = signal<VehiculeResponse[]>([]);
  readonly vehiculesLoading = signal(true);
  readonly vehiculesError = signal<string | null>(null);
  readonly vehiculeSearch = signal('');
  readonly vehiculeTypeFilter = signal<'all' | VehiculeType>('all');
  readonly vehiculeStatutFilter = signal<'all' | VehiculeStatut>('all');
  readonly vehiculesPage = signal(0);
  private vehiculeSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  readonly filteredVehicules = computed(() => {
    const query = this.vehiculeSearch().trim().toLowerCase();
    const type = this.vehiculeTypeFilter();
    const statut = this.vehiculeStatutFilter();
    return this.vehicules().filter((v) => {
      if (type !== 'all' && v.type !== type) return false;
      if (statut !== 'all' && v.statut !== statut) return false;
      if (query && !`${v.immatriculation ?? ''} ${v.numeroChassis ?? ''} ${v.marque} ${v.modele} ${v.proprietaireName}`.toLowerCase().includes(query)) return false;
      return true;
    });
  });

  readonly vehiculesTotalPages = computed(() => Math.max(1, Math.ceil(this.filteredVehicules().length / PAGE_SIZE)));
  readonly pagedVehicules = computed(() => {
    const start = this.vehiculesPage() * PAGE_SIZE;
    return this.filteredVehicules().slice(start, start + PAGE_SIZE);
  });

  fetchVehicules(): void {
    this.vehiculesLoading.set(true);
    this.vehiculesError.set(null);
    this.fleetService.listVehicules(0, 1000).subscribe({
      next: (page) => {
        this.vehicules.set(page.content);
        this.vehiculesLoading.set(false);
      },
      error: () => {
        this.vehiculesError.set('Impossible de charger les véhicules.');
        this.vehiculesLoading.set(false);
      },
    });
  }

  onVehiculeSearchInput(value: string): void {
    this.vehiculeSearch.set(value);
    if (this.vehiculeSearchDebounce) clearTimeout(this.vehiculeSearchDebounce);
    this.vehiculeSearchDebounce = setTimeout(() => this.vehiculesPage.set(0), 300);
  }

  setVehiculeTypeFilter(value: string): void {
    this.vehiculeTypeFilter.set(value as 'all' | VehiculeType);
    this.vehiculesPage.set(0);
  }

  setVehiculeStatutFilter(value: string): void {
    this.vehiculeStatutFilter.set(value as 'all' | VehiculeStatut);
    this.vehiculesPage.set(0);
  }

  goToVehiculesPage(index: number): void {
    if (index < 0 || index >= this.vehiculesTotalPages()) return;
    this.vehiculesPage.set(index);
  }

  readonly vehiculeFormVisible = signal(false);
  readonly vehiculeFormSaving = signal(false);
  readonly vehiculeFormError = signal<string | null>(null);
  readonly editingVehiculeId = signal<number | null>(null);

  readonly vehiculeForm = this.fb.nonNullable.group({
    immatriculation: [''],
    type: ['AUTO' as VehiculeType],
    marque: [''],
    modele: [''],
    numeroChassis: [''],
    dateMiseEnCirculation: [''],
    puissanceFiscale: [''],
    cylindree: [''],
    couleur: [''],
    proprietaireId: [''],
    statut: ['ACTIF' as VehiculeStatut],
  });

  // -- autocomplétion du propriétaire (assuré) --

  readonly ownerSearchQuery = signal('');
  readonly ownerSearchOpen = signal(false);
  readonly ownerSearchResults = computed(() => this.searchInsuredsLocally(this.ownerSearchQuery()));

  /** Fiche du propriétaire sélectionné — même rôle que selectedClientInsured() pour la
   * modale Nouvelle flotte, affichée à la place du champ de recherche une fois résolue. */
  selectedOwnerInsured(): InsuredResponse | undefined {
    const id = this.vehiculeForm.controls.proprietaireId.value;
    return id ? this.insureds().find((i) => String(i.id) === id) : undefined;
  }

  onOwnerSearchInput(value: string): void {
    this.ownerSearchQuery.set(value);
    this.vehiculeForm.controls.proprietaireId.setValue('');
    this.ownerSearchOpen.set(value.trim().length > 0);
  }

  onOwnerSearchFocus(): void {
    if (this.ownerSearchQuery().trim().length >= 2) this.ownerSearchOpen.set(true);
  }

  /** Fermeture différée pour laisser le (mousedown) d'un résultat s'exécuter avant le blur. */
  onOwnerSearchBlur(): void {
    setTimeout(() => this.ownerSearchOpen.set(false), 150);
  }

  selectOwner(insured: InsuredResponse): void {
    this.vehiculeForm.controls.proprietaireId.setValue(String(insured.id));
    this.ownerSearchQuery.set(`${insured.nom} ${insured.prenoms}`);
    this.ownerSearchOpen.set(false);
  }

  clearOwnerSelection(): void {
    this.vehiculeForm.controls.proprietaireId.setValue('');
    this.ownerSearchQuery.set('');
    this.ownerSearchOpen.set(false);
  }

  openCreateVehicule(): void {
    this.editingVehiculeId.set(null);
    this.vehiculeFormError.set(null);
    this.vehiculeForm.reset({
      immatriculation: '',
      type: 'AUTO',
      marque: '',
      modele: '',
      numeroChassis: '',
      dateMiseEnCirculation: '',
      puissanceFiscale: '',
      cylindree: '',
      couleur: '',
      proprietaireId: '',
      statut: 'ACTIF',
    });
    this.ownerSearchQuery.set('');
    this.ownerSearchOpen.set(false);
    this.vehiculeFormVisible.set(true);
  }

  openEditVehicule(vehicule: VehiculeResponse): void {
    this.editingVehiculeId.set(vehicule.id);
    this.vehiculeFormError.set(null);
    this.vehiculeForm.reset({
      immatriculation: vehicule.immatriculation ?? '',
      type: vehicule.type,
      marque: vehicule.marque,
      modele: vehicule.modele,
      numeroChassis: vehicule.numeroChassis ?? '',
      dateMiseEnCirculation: vehicule.dateMiseEnCirculation ?? '',
      puissanceFiscale: vehicule.puissanceFiscale != null ? String(vehicule.puissanceFiscale) : '',
      cylindree: vehicule.cylindree != null ? String(vehicule.cylindree) : '',
      couleur: vehicule.couleur ?? '',
      proprietaireId: String(vehicule.proprietaireId),
      statut: vehicule.statut,
    });
    this.ownerSearchQuery.set(this.insuredName(vehicule.proprietaireId));
    this.ownerSearchOpen.set(false);
    this.vehiculeFormVisible.set(true);
  }

  saveVehicule(): void {
    const raw = this.vehiculeForm.getRawValue();
    const immatriculation = raw.immatriculation.trim();
    const numeroChassis = raw.numeroChassis.trim();
    if (!raw.marque.trim() || !raw.modele.trim() || !raw.proprietaireId) {
      this.vehiculeFormError.set('La marque, le modèle et le propriétaire sont requis.');
      return;
    }
    if (!immatriculation && !numeroChassis) {
      this.vehiculeFormError.set(
        "Renseignez une immatriculation, ou à défaut un n° de châssis (cas d'une moto sans plaque encore attribuée).",
      );
      return;
    }
    this.vehiculeFormSaving.set(true);
    this.vehiculeFormError.set(null);
    const payload: VehiculeRequest = {
      immatriculation: immatriculation || undefined,
      type: raw.type,
      marque: raw.marque.trim(),
      modele: raw.modele.trim(),
      numeroChassis: numeroChassis || undefined,
      dateMiseEnCirculation: raw.dateMiseEnCirculation || undefined,
      puissanceFiscale: raw.puissanceFiscale ? Number(raw.puissanceFiscale) : undefined,
      cylindree: raw.cylindree ? Number(raw.cylindree) : undefined,
      couleur: raw.couleur.trim() || undefined,
      proprietaireId: Number(raw.proprietaireId),
      statut: raw.statut,
    };
    const id = this.editingVehiculeId();
    const request = id ? this.fleetService.updateVehicule(id, payload) : this.fleetService.createVehicule(payload);
    request.subscribe({
      next: () => {
        this.vehiculeFormSaving.set(false);
        this.vehiculeFormVisible.set(false);
        this.fetchVehicules();
      },
      error: (err) => {
        this.vehiculeFormSaving.set(false);
        this.vehiculeFormError.set(extractErrorMessage(err, 'Impossible d’enregistrer ce véhicule.'));
      },
    });
  }

  // -- supprimer --

  readonly deleteVehiculeTarget = signal<VehiculeResponse | null>(null);
  readonly deleteVehiculeSaving = signal(false);

  askDeleteVehicule(vehicule: VehiculeResponse): void {
    this.vehiculesError.set(null);
    this.deleteVehiculeTarget.set(vehicule);
  }

  /** Erreur remontée via vehiculesError() (même convention que fleetsError() pour
   * activateFleet()/confirmCloseFleet()) plutôt qu'un signal dédié — pas de bannière
   * séparée dans ce composant pour une erreur d'action sur une liste déjà chargée. */
  confirmDeleteVehicule(): void {
    const vehicule = this.deleteVehiculeTarget();
    if (!vehicule) return;
    this.deleteVehiculeSaving.set(true);
    this.fleetService.deleteVehicule(vehicule.id).subscribe({
      next: () => {
        this.deleteVehiculeSaving.set(false);
        this.deleteVehiculeTarget.set(null);
        this.fetchVehicules();
      },
      error: (err) => {
        this.deleteVehiculeSaving.set(false);
        this.deleteVehiculeTarget.set(null);
        this.vehiculesError.set(extractErrorMessage(err, 'Impossible de supprimer ce véhicule.'));
      },
    });
  }
}
