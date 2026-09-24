import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
  ProgressComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { PartenaireService } from '../../core/partenaire/partenaire.service';
import {
  ContractAvenantSummaryResponse,
  FrequencePaiementCommission,
  HistoriqueContratResponse,
  PartenaireDocumentResponse,
  PartenaireDocumentType,
  PartenaireResponse,
  TypePartenaire,
} from '../../core/partenaire/models/partenaire.model';
import { ProfileService } from '../../core/profile/profile.service';
import { ProfileRecord } from '../../core/profile/models/profile-details.model';
import { UserService } from '../../core/user/user.service';
import { ExerciceComptableService } from '../../core/configuration/exercice-comptable.service';
import { ExerciceComptableResponse } from '../../core/configuration/models/exercice-comptable.model';
import { VillePartenaireService } from '../../core/configuration/ville-partenaire.service';
import { VillePartenaireResponse } from '../../core/configuration/models/ville-partenaire.model';
import { TypeProfileService } from '../../core/configuration/type-profile.service';
import { TypeProfileResponse } from '../../core/configuration/models/type-profile.model';
import { ContractResponse, ContractStatus } from '../../core/contracts/models/contract.model';
import { AVENANT_TYPE_LABEL } from '../../core/avenants/avenant-labels';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ThousandSeparatorDirective } from '../../shared/directives/thousand-separator.directive';

type ActiveFilter = 'all' | 'active' | 'inactive';
type TypeFilter = 'all' | TypePartenaire;
type DetailView = 'contrats' | 'primeNonReversee' | 'historique' | 'avenants' | 'documents';

const DOCUMENT_TYPE_LABEL: Record<PartenaireDocumentType, string> = {
  CONTRAT_APPORTEUR: "Contrat d'apporteur",
  CNI: 'Copie CNI',
  RIB: 'RIB',
  JUSTIFICATIF_DOMICILE: 'Justificatif de domicile',
  AUTRE: 'Autre',
};

const DETAIL_PAGE_SIZE = 10;

@Component({
  selector: 'app-partenaire-affaire',
  templateUrl: './partenaire-affaire.component.html',
  styleUrl: './partenaire-affaire.component.scss',
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
    FormCheckInputDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
    ThousandSeparatorDirective,
    ProgressComponent,
  ],
})
export class PartenaireAffaireComponent {
  private readonly partenaireService = inject(PartenaireService);
  private readonly profileService = inject(ProfileService);
  private readonly userService = inject(UserService);
  private readonly exerciceService = inject(ExerciceComptableService);
  private readonly villePartenaireService = inject(VillePartenaireService);
  private readonly typeProfileService = inject(TypeProfileService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly pageSize = 20;

  readonly items = signal<PartenaireResponse[]>([]);
  /** Noms d'utilisateur (= téléphone) des comptes de connexion réellement créés — permet de
   * distinguer un partenaire qui a juste un numéro renseigné d'un partenaire qui a vraiment
   * un compte (le badge "Compte actif" affichait auparavant les deux de la même façon). */
  readonly accountUsernames = signal<Set<string>>(new Set());
  /** username (= téléphone) → userId, alimenté par le même appel que accountUsernames : évite une
   * seconde recherche GET /users?q= au moment du renvoi des accès. */
  private readonly accountIdsByUsername = new Map<string, number>();
  readonly profiles = signal<ProfileRecord[]>([]);
  readonly exercices = signal<ExerciceComptableResponse[]>([]);
  readonly villes = signal<VillePartenaireResponse[]>([]);
  readonly typeProfiles = signal<TypeProfileResponse[]>([]);
  /** Résultat de la création automatique de compte (voir compteCree/compteCreationErreur sur
   * PartenaireResponse) — affiché après sauvegarde, sans quitter le formulaire en cas d'erreur
   * pour permettre de corriger le téléphone et relancer. */
  readonly accountNotice = signal<{ type: 'success' | 'error'; message: string } | null>(null);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  readonly typeFilter = signal<TypeFilter>('all');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<PartenaireResponse | null>(null);
  readonly deleting = signal(false);

  readonly resendAccessTarget = signal<PartenaireResponse | null>(null);
  readonly resendingAccess = signal(false);
  /** Pas de mot de passe ici (contrairement à avant) : le backend le génère et l'envoie par SMS
   * lui-même, sans jamais le renvoyer dans la réponse HTTP — voir UserService.resendAccess(). */
  readonly resendAccessResult = signal<
    | { status: 'sending'; username: string }
    | { status: 'done'; username: string; notifie: boolean }
    | { status: 'error'; username: string; message: string }
    | null
  >(null);

  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  // ================= Modale de détail =================

  readonly detailVisible = signal(false);
  readonly selectedPartenaire = signal<PartenaireResponse | null>(null);
  readonly detailView = signal<DetailView>('contrats');

  // -- Contrats --
  readonly detailContracts = signal<ContractResponse[]>([]);
  readonly detailContractsLoading = signal(false);
  readonly detailContractsError = signal<string | null>(null);
  readonly detailContractsPage = signal(0);
  readonly detailContractsTotalPages = signal(1);
  readonly detailContractsTotalElements = signal(0);
  readonly detailContractsSearch = signal('');
  readonly detailContractsStatus = signal<ContractStatus | ''>('');
  readonly detailContractsIncludeOld = signal(false);
  private detailContractsSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  // -- Primes non reversées --
  readonly primeNonReverseeItems = signal<ContractResponse[]>([]);
  readonly primeNonReverseeLoading = signal(false);
  readonly primeNonReverseeError = signal<string | null>(null);
  readonly primeNonReverseePage = signal(0);
  readonly primeNonReverseeTotalPages = signal(1);
  readonly primeNonReverseeTotalElements = signal(0);

  // -- Historique --
  readonly historiqueItems = signal<HistoriqueContratResponse[]>([]);
  readonly historiqueLoading = signal(false);
  readonly historiqueError = signal<string | null>(null);
  readonly historiquePage = signal(0);
  readonly historiqueTotalPages = signal(1);
  readonly historiqueTotalElements = signal(0);

  // -- Avenants --
  readonly avenantsItems = signal<ContractAvenantSummaryResponse[]>([]);
  readonly avenantsLoading = signal(false);
  readonly avenantsError = signal<string | null>(null);
  readonly avenantsPage = signal(0);
  readonly avenantsTotalPages = signal(1);
  readonly avenantsTotalElements = signal(0);

  // -- Documents --
  readonly documentsItems = signal<PartenaireDocumentResponse[]>([]);
  readonly documentsLoading = signal(false);
  readonly documentsError = signal<string | null>(null);
  readonly documentUploadType = signal<PartenaireDocumentType>('CONTRAT_APPORTEUR');
  readonly documentUploading = signal(false);
  readonly viewingDocumentId = signal<number | null>(null);
  readonly deletingDocumentId = signal<number | null>(null);
  readonly documentDeleteTarget = signal<PartenaireDocumentResponse | null>(null);
  readonly documentTypeOptions: PartenaireDocumentType[] = ['CONTRAT_APPORTEUR', 'CNI', 'RIB', 'JUSTIFICATIF_DOMICILE', 'AUTRE'];

  readonly form = this.fb.nonNullable.group({
    codePartenaire: [''],
    type: ['DIRECT'],
    nom: [''],
    prenoms: [''],
    objectifProduction: [0],
    profileId: [''],
    exerciceComptableId: [''],
    villePartenaireId: [''],
    active: [true],
    typeProfileId: [''],
    telephone: [''],
    numeroWaveCommission: [''],
    frequencePaiementCommission: this.fb.nonNullable.control<FrequencePaiementCommission | ''>(''),
    accesActiviteCommerciale: [false],
    primeObjectifActivee: [false],
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
      if (this.detailContractsSearchDebounce) clearTimeout(this.detailContractsSearchDebounce);
    });
    this.fetch();
    this.fetchProfiles();
    this.fetchExercices();
    this.fetchVilles();
    this.fetchTypeProfiles();
    this.fetchAccountUsernames();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const active = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    const typeFilter = this.typeFilter();
    const type = typeFilter === 'all' ? undefined : typeFilter;
    this.partenaireService.list(this.pageIndex(), this.pageSize, this.searchQuery() || undefined, active, type).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les partenaires.');
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.pageIndex.set(0);
      this.fetch();
    }, 400);
  }

  setActiveFilter(filter: ActiveFilter): void {
    this.activeFilter.set(filter);
    this.pageIndex.set(0);
    this.fetch();
  }

  setTypeFilter(filter: TypeFilter): void {
    this.typeFilter.set(filter);
    this.pageIndex.set(0);
    this.fetch();
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.accountNotice.set(null);
    this.form.reset({
      codePartenaire: '',
      type: 'DIRECT',
      nom: '',
      prenoms: '',
      objectifProduction: 0,
      profileId: '',
      exerciceComptableId: '',
      villePartenaireId: '',
      active: true,
      typeProfileId: '',
      telephone: '',
      numeroWaveCommission: '',
      frequencePaiementCommission: '',
      accesActiviteCommerciale: false,
      primeObjectifActivee: false,
    });
    this.form.controls.codePartenaire.enable();
    this.formVisible.set(true);
  }

  openEdit(item: PartenaireResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.accountNotice.set(null);
    this.form.reset({
      codePartenaire: item.codePartenaire ?? '',
      type: item.type,
      nom: item.nom,
      prenoms: item.prenoms,
      objectifProduction: item.objectifProduction,
      profileId: item.profileId ? String(item.profileId) : '',
      exerciceComptableId: item.exerciceComptableId ? String(item.exerciceComptableId) : '',
      villePartenaireId: item.villePartenaireId ? String(item.villePartenaireId) : '',
      active: item.active,
      typeProfileId: item.typeProfileId ? String(item.typeProfileId) : '',
      telephone: item.telephone ?? '',
      numeroWaveCommission: item.numeroWaveCommission ?? '',
      frequencePaiementCommission: item.frequencePaiementCommission ?? '',
      accesActiviteCommerciale: item.accesActiviteCommerciale ?? false,
      primeObjectifActivee: item.primeObjectifActivee ?? false,
    });
    // Backend-generated — visible for reference during edit, but never modifiable.
    this.form.controls.codePartenaire.disable();
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    // Profil associé n'est obligatoire que si on ne compte pas laisser le backend créer
    // automatiquement un nouveau profil + compte (voir accesActiviteCommerciale ci-dessous).
    const profileRequired = !raw.accesActiviteCommerciale;
    // Type Profil (seule valeur possible : REPRESENTANT) n'a de sens que pour déclencher la
    // création automatique de compte — inversement de profileRequired : obligatoire seulement
    // si le toggle est activé, sinon un partenaire "normal" (Direct/Agent/Apporteur sans app
    // mobile) ne doit pas être forcé à porter cette étiquette.
    const typeProfileRequired = raw.accesActiviteCommerciale;
    if (
      !raw.nom.trim() ||
      !raw.prenoms.trim() ||
      !Number(raw.objectifProduction) ||
      (profileRequired && !raw.profileId) ||
      !raw.exerciceComptableId ||
      !raw.villePartenaireId ||
      (typeProfileRequired && !raw.typeProfileId) ||
      !raw.telephone.trim() ||
      !raw.numeroWaveCommission.trim() ||
      !raw.frequencePaiementCommission
    ) {
      this.formError.set('Tous les champs sont obligatoires.');
      return;
    }
    const payload = {
      codePartenaire: raw.codePartenaire.trim() || undefined,
      type: raw.type as TypePartenaire,
      nom: raw.nom,
      prenoms: raw.prenoms,
      objectifProduction: Number(raw.objectifProduction),
      profileId: raw.profileId ? Number(raw.profileId) : undefined,
      exerciceComptableId: raw.exerciceComptableId ? Number(raw.exerciceComptableId) : undefined,
      villePartenaireId: raw.villePartenaireId ? Number(raw.villePartenaireId) : undefined,
      active: raw.active,
      typeProfileId: raw.typeProfileId ? Number(raw.typeProfileId) : undefined,
      telephone: raw.telephone.trim() || undefined,
      numeroWaveCommission: raw.numeroWaveCommission.trim() || undefined,
      frequencePaiementCommission: raw.frequencePaiementCommission as FrequencePaiementCommission,
      accesActiviteCommerciale: raw.accesActiviteCommerciale,
      primeObjectifActivee: raw.primeObjectifActivee,
    };
    this.formSaving.set(true);
    this.formError.set(null);
    this.accountNotice.set(null);
    const id = this.editingId();
    const request = id ? this.partenaireService.update(id, payload) : this.partenaireService.create(payload);
    request.subscribe({
      next: (response) => {
        this.formSaving.set(false);
        this.fetch();
        if (response.compteCreationErreur) {
          // Le partenaire est bien enregistré côté backend, mais le compte de connexion n'a pas
          // pu être créé (ex. téléphone déjà utilisé) — on garde le formulaire ouvert pour que
          // l'admin corrige le téléphone et resauvegarde, plutôt que de fermer sur un échec silencieux.
          this.editingId.set(response.id);
          this.accountNotice.set({ type: 'error', message: response.compteCreationErreur });
          return;
        }
        this.formVisible.set(false);
        if (response.compteCree) {
          this.accountNotice.set({
            type: 'success',
            message: `Compte de connexion créé pour ${response.nom} ${response.prenoms} — SMS envoyé avec les accès.`,
          });
          this.fetchAccountUsernames();
        }
      },
      error: (err: unknown) => {
        // PUT /partenaires/{id} a été vu renvoyer 500 puis 403 alors que la mise à jour est
        // réellement appliquée en base (voir docs/api-contracts/partenaire-conversion-representant-500.md)
        // — plutôt que d'inviter l'admin à ressaisir (ce qui a déjà causé un doublon de compte),
        // on revérifie l'état réel du partenaire avant d'afficher un message d'échec.
        if (id != null && err instanceof HttpErrorResponse && (err.status === 500 || err.status === 403)) {
          this.partenaireService.get(id).subscribe({
            next: (current) => {
              this.formSaving.set(false);
              const applied =
                (current.typeProfileId ?? null) === (payload.typeProfileId ?? null) &&
                (current.accesActiviteCommerciale ?? false) === (payload.accesActiviteCommerciale ?? false) &&
                (current.telephone ?? null) === (payload.telephone ?? null);
              if (applied) {
                this.fetch();
                this.formVisible.set(false);
                this.accountNotice.set({
                  type: 'success',
                  message:
                    'Le partenaire a bien été mis à jour (confirmé en relisant sa fiche), malgré une erreur technique renvoyée par le serveur. Aucune action supplémentaire nécessaire.',
                });
                if (current.compteCree) {
                  this.fetchAccountUsernames();
                }
              } else {
                this.formError.set("Impossible d'enregistrer le partenaire. Réessayez.");
              }
            },
            error: () => {
              this.formSaving.set(false);
              this.formError.set("Impossible d'enregistrer le partenaire. Réessayez.");
            },
          });
          return;
        }
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer le partenaire. Réessayez.");
      },
    });
  }

  viewCommissions(item: PartenaireResponse): void {
    this.router.navigate(['/commissions'], { queryParams: { partenaireId: item.id } });
  }

  viewLoginHistory(item: PartenaireResponse): void {
    this.router.navigate(['/administration/historique-connexions'], { queryParams: { partenaireId: item.id } });
  }

  confirmDelete(item: PartenaireResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.partenaireService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer ce partenaire.');
        this.deleteTarget.set(null);
      },
    });
  }

  refreshAll(): void {
    this.fetch();
  }

  // ================= Ajustement rapide de l'objectif (PATCH .../objectif) =================

  readonly objectifTarget = signal<PartenaireResponse | null>(null);
  readonly objectifValue = signal('');
  readonly objectifSaving = signal(false);
  readonly objectifError = signal<string | null>(null);

  openObjectifModal(item: PartenaireResponse): void {
    this.objectifTarget.set(item);
    this.objectifValue.set(String(item.objectifProduction));
    this.objectifError.set(null);
  }

  closeObjectifModal(): void {
    this.objectifTarget.set(null);
  }

  saveObjectif(): void {
    const item = this.objectifTarget();
    if (!item) return;
    const value = Number(this.objectifValue().replace(/\s/g, ''));
    if (!value || value < 0) {
      this.objectifError.set('Saisissez un objectif de production valide.');
      return;
    }
    this.objectifSaving.set(true);
    this.objectifError.set(null);
    this.partenaireService.setObjectif(item.id, value).subscribe({
      next: () => {
        this.objectifSaving.set(false);
        this.objectifTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.objectifSaving.set(false);
        this.objectifError.set("Impossible de mettre à jour l'objectif.");
      },
    });
  }

  askResendAccess(item: PartenaireResponse): void {
    this.resendAccessTarget.set(item);
  }

  cancelResendAccess(): void {
    this.resendAccessTarget.set(null);
  }

  /** Retrouve le compte de connexion auto-créé pour ce partenaire (username = téléphone,
   * voir PartenaireRequest.telephone) puis lui renvoie ses accès par SMS via
   * POST /users/{id}/resend-access (voir docs/api-contracts/partenaire-renvoi-acces-sms.md).
   * Le SMS doit impérativement partir vers le bon compte : on exige donc une correspondance
   * exacte username === telephone plutôt qu'un simple "premier résultat de la recherche" —
   * un fallback approximatif enverrait silencieusement le SMS au mauvais destinataire si la
   * recherche `q` renvoyait plusieurs comptes ou un ordre différent, ce qui, vu du partenaire
   * concerné, ressemble exactement à « le SMS ne part jamais ». */
  confirmResendAccess(): void {
    const item = this.resendAccessTarget();
    this.resendAccessTarget.set(null);
    if (!item?.telephone) return;
    const username = item.telephone;
    // Tout le déroulé (envoi, succès, échec) s'affiche dans la modale : les erreurs posées dans
    // accountNotice apparaissaient en haut de la liste, souvent hors écran — « rien ne se passe ».
    this.resendingAccess.set(true);
    this.resendAccessResult.set({ status: 'sending', username });
    const knownId = this.accountIdsByUsername.get(username);
    if (knownId !== undefined) {
      this.sendAccess(username, knownId);
      return;
    }
    this.userService.list(0, 20, { q: username }).subscribe({
      next: (page) => {
        const user = page.content.find((u) => u.username === username);
        if (!user) {
          this.failResendAccess(
            username,
            `Aucun compte de connexion avec l'identifiant exact « ${username} » n'a été retrouvé — vérifiez que le téléphone du partenaire correspond bien à l'identifiant du compte avant de réessayer.`,
          );
          return;
        }
        this.sendAccess(username, user.userId);
      },
      error: () => this.failResendAccess(username, 'Impossible de rechercher le compte associé à ce partenaire.'),
    });
  }

  private sendAccess(username: string, userId: number): void {
    this.userService.resendAccess(userId).subscribe({
      next: (response) => {
        this.resendingAccess.set(false);
        this.resendAccessResult.set({ status: 'done', username, notifie: response.notificationEnvoyee ?? false });
      },
      error: (err: HttpErrorResponse) =>
        this.failResendAccess(username, err.error?.message ?? 'Impossible de renvoyer les accès pour ce compte.'),
    });
  }

  private failResendAccess(username: string, message: string): void {
    this.resendingAccess.set(false);
    this.resendAccessResult.set({ status: 'error', username, message });
  }

  private fetchProfiles(): void {
    this.profileService.list().subscribe({
      next: (profiles) => this.profiles.set(profiles),
      error: () => {
        // Silent: the profile dropdown will just stay empty if this fails.
      },
    });
  }

  private fetchExercices(): void {
    this.exerciceService.listAvailable().subscribe({
      next: (exercices) => this.exercices.set(exercices),
      error: () => {
        // Silent: the exercice dropdown will just stay empty if this fails.
      },
    });
  }

  private fetchVilles(): void {
    this.villePartenaireService.list().subscribe({
      next: (villes) => this.villes.set(villes),
      error: () => {
        // Silent: the ville autocomplete just stays empty if this fails.
      },
    });
  }

  /** Seul REPRESENTANT est proposable sur un partenaire — ADMIN est réservé aux comptes
   * back-office créés directement via Administration → Utilisateurs. */
  readonly assignableTypeProfiles = computed(() => this.typeProfiles().filter((tp) => tp.libelle === 'REPRESENTANT'));

  private fetchTypeProfiles(): void {
    this.typeProfileService.list().subscribe({
      next: (typeProfiles) => this.typeProfiles.set(typeProfiles),
      error: () => {
        // Silent: the Type Profil dropdown just stays empty if this fails.
      },
    });
  }

  private fetchAccountUsernames(): void {
    this.userService.list(0, 1000, {}).subscribe({
      next: (page) => {
        this.accountIdsByUsername.clear();
        page.content.forEach((u) => this.accountIdsByUsername.set(u.username, u.userId));
        this.accountUsernames.set(new Set(page.content.map((u) => u.username)));
      },
      error: () => {
        // Silent: le badge "Compte actif" reste simplement absent si ça échoue.
      },
    });
  }

  hasAccount(item: PartenaireResponse): boolean {
    return !!item.telephone && this.accountUsernames().has(item.telephone);
  }

  tauxVariant(taux: number | null): string {
    if (taux === null) return 'neutral';
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  /** Couleur CoreUI pour <c-progress> — mêmes seuils que tauxVariant(), mais 'secondary'
   * plutôt que 'neutral' (nom de variante propre à nos chips .dl-chip--*, pas un couleur CoreUI valide). */
  tauxProgressColor(taux: number | null): string {
    if (taux === null) return 'secondary';
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  typeLabel(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'Direct';
    if (type === 'AGENT') return 'Agent';
    return 'Apporteur';
  }

  typeVariant(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'info';
    if (type === 'AGENT') return 'neutral';
    return 'warning';
  }

  formatDate(value: string): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }

  contractStatusLabel(status: string): string {
    return { signed: 'Signé', pending: 'En attente', draft: 'Brouillon', cancelled: 'Annulé' }[status] ?? status;
  }

  contractStatusVariant(status: string): string {
    if (status === 'signed') return 'success';
    if (status === 'pending') return 'warning';
    if (status === 'cancelled') return 'danger';
    return 'neutral';
  }

  primeReversementLabel(status?: string | null): string {
    return status === 'reverse' ? 'Reversée' : 'Non reversée';
  }

  avenantTypeLabel(type: string): string {
    return AVENANT_TYPE_LABEL[type as keyof typeof AVENANT_TYPE_LABEL] ?? type;
  }

  // ================= Export CSV de la liste =================

  exportList(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    this.partenaireService.export().subscribe({
      next: (blob) => {
        this.downloadBlob(blob, `assurzen-partenaires-${this.exportTimestamp()}.csv`);
        this.exporting.set(false);
      },
      error: () => {
        this.exportError.set("Impossible d'exporter les partenaires.");
        this.exporting.set(false);
      },
    });
  }

  private exportTimestamp(): string {
    return new Date().toISOString().replace(/[:.]/g, '-');
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ================= Modale de détail =================

  openDetail(item: PartenaireResponse): void {
    this.selectedPartenaire.set(item);
    this.detailVisible.set(true);
    this.detailView.set('contrats');

    // Reset des états des 4 onglets à chaque ouverture pour éviter d'afficher des
    // données d'un autre partenaire pendant le chargement.
    this.detailContracts.set([]);
    this.detailContractsPage.set(0);
    this.detailContractsSearch.set('');
    this.detailContractsStatus.set('');
    this.detailContractsIncludeOld.set(false);
    this.primeNonReverseeItems.set([]);
    this.primeNonReverseePage.set(0);
    this.historiqueItems.set([]);
    this.historiquePage.set(0);
    this.avenantsItems.set([]);
    this.avenantsPage.set(0);
    this.documentsItems.set([]);
    this.documentsError.set(null);
    this.documentDeleteTarget.set(null);

    this.fetchDetailContracts();
  }

  setDetailView(view: DetailView): void {
    this.detailView.set(view);
    if (view === 'contrats' && this.detailContracts().length === 0 && !this.detailContractsLoading()) {
      this.fetchDetailContracts();
    }
    if (view === 'primeNonReversee' && this.primeNonReverseeItems().length === 0 && !this.primeNonReverseeLoading()) {
      this.fetchPrimeNonReversee();
    }
    if (view === 'historique' && this.historiqueItems().length === 0 && !this.historiqueLoading()) {
      this.fetchHistorique();
    }
    if (view === 'avenants' && this.avenantsItems().length === 0 && !this.avenantsLoading()) {
      this.fetchAvenants();
    }
    if (view === 'documents' && this.documentsItems().length === 0 && !this.documentsLoading()) {
      this.fetchDocuments();
    }
  }

  // -- Contrats --

  fetchDetailContracts(): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.detailContractsLoading.set(true);
    this.detailContractsError.set(null);
    this.partenaireService
      .getContracts(
        partenaire.id,
        this.detailContractsPage(),
        DETAIL_PAGE_SIZE,
        this.detailContractsSearch() || undefined,
        this.detailContractsStatus() || undefined,
        this.detailContractsIncludeOld(),
      )
      .subscribe({
        next: (page) => {
          this.detailContracts.set(page.content);
          this.detailContractsTotalPages.set(Math.max(1, page.totalPages));
          this.detailContractsTotalElements.set(page.totalElements);
          this.detailContractsLoading.set(false);
        },
        error: () => {
          this.detailContractsError.set('Impossible de charger les contrats de ce partenaire.');
          this.detailContractsLoading.set(false);
        },
      });
  }

  onDetailContractsSearchInput(value: string): void {
    this.detailContractsSearch.set(value);
    if (this.detailContractsSearchDebounce) clearTimeout(this.detailContractsSearchDebounce);
    this.detailContractsSearchDebounce = setTimeout(() => {
      this.detailContractsPage.set(0);
      this.fetchDetailContracts();
    }, 400);
  }

  setDetailContractsStatus(value: string): void {
    this.detailContractsStatus.set((value as ContractStatus) || '');
    this.detailContractsPage.set(0);
    this.fetchDetailContracts();
  }

  toggleDetailContractsIncludeOld(): void {
    this.detailContractsIncludeOld.update((v) => !v);
    this.detailContractsPage.set(0);
    this.fetchDetailContracts();
  }

  goToDetailContractsPage(index: number): void {
    if (index < 0 || index >= this.detailContractsTotalPages()) return;
    this.detailContractsPage.set(index);
    this.fetchDetailContracts();
  }

  // -- Primes non reversées --

  fetchPrimeNonReversee(): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.primeNonReverseeLoading.set(true);
    this.primeNonReverseeError.set(null);
    this.partenaireService.getPrimeNonReversee(partenaire.id, this.primeNonReverseePage(), DETAIL_PAGE_SIZE).subscribe({
      next: (page) => {
        this.primeNonReverseeItems.set(page.content);
        this.primeNonReverseeTotalPages.set(Math.max(1, page.totalPages));
        this.primeNonReverseeTotalElements.set(page.totalElements);
        this.primeNonReverseeLoading.set(false);
      },
      error: () => {
        this.primeNonReverseeError.set('Impossible de charger les primes non reversées.');
        this.primeNonReverseeLoading.set(false);
      },
    });
  }

  goToPrimeNonReverseePage(index: number): void {
    if (index < 0 || index >= this.primeNonReverseeTotalPages()) return;
    this.primeNonReverseePage.set(index);
    this.fetchPrimeNonReversee();
  }

  // -- Historique --

  fetchHistorique(): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.historiqueLoading.set(true);
    this.historiqueError.set(null);
    this.partenaireService.getHistorique(partenaire.id, this.historiquePage(), DETAIL_PAGE_SIZE).subscribe({
      next: (page) => {
        this.historiqueItems.set(page.content);
        this.historiqueTotalPages.set(Math.max(1, page.totalPages));
        this.historiqueTotalElements.set(page.totalElements);
        this.historiqueLoading.set(false);
      },
      error: () => {
        this.historiqueError.set("Impossible de charger l'historique de ce partenaire.");
        this.historiqueLoading.set(false);
      },
    });
  }

  goToHistoriquePage(index: number): void {
    if (index < 0 || index >= this.historiqueTotalPages()) return;
    this.historiquePage.set(index);
    this.fetchHistorique();
  }

  // -- Avenants --

  fetchAvenants(): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.avenantsLoading.set(true);
    this.avenantsError.set(null);
    this.partenaireService.getAvenants(partenaire.id, this.avenantsPage(), DETAIL_PAGE_SIZE).subscribe({
      next: (page) => {
        this.avenantsItems.set(page.content);
        this.avenantsTotalPages.set(Math.max(1, page.totalPages));
        this.avenantsTotalElements.set(page.totalElements);
        this.avenantsLoading.set(false);
      },
      error: () => {
        this.avenantsError.set('Impossible de charger les avenants de ce partenaire.');
        this.avenantsLoading.set(false);
      },
    });
  }

  goToAvenantsPage(index: number): void {
    if (index < 0 || index >= this.avenantsTotalPages()) return;
    this.avenantsPage.set(index);
    this.fetchAvenants();
  }

  // -- Documents --

  fetchDocuments(): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.documentsLoading.set(true);
    this.documentsError.set(null);
    this.partenaireService.listDocuments(partenaire.id).subscribe({
      next: (documents) => {
        this.documentsItems.set(documents);
        this.documentsLoading.set(false);
      },
      error: () => {
        this.documentsError.set('Impossible de charger les documents de ce partenaire.');
        this.documentsLoading.set(false);
      },
    });
  }

  onDocumentSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const partenaire = this.selectedPartenaire();
    if (!file || !partenaire) return;

    this.documentUploading.set(true);
    this.documentsError.set(null);
    this.partenaireService.uploadDocument(partenaire.id, this.documentUploadType(), file).subscribe({
      next: () => {
        this.documentUploading.set(false);
        input.value = '';
        this.fetchDocuments();
      },
      error: () => {
        this.documentUploading.set(false);
        input.value = '';
        this.documentsError.set("Impossible de joindre ce document. Réessayez.");
      },
    });
  }

  viewDocument(doc: PartenaireDocumentResponse): void {
    const partenaire = this.selectedPartenaire();
    if (!partenaire) return;
    this.viewingDocumentId.set(doc.id);
    this.documentsError.set(null);
    this.partenaireService.downloadDocument(partenaire.id, doc.id).subscribe({
      next: (blob) => {
        this.viewingDocumentId.set(null);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.viewingDocumentId.set(null);
        this.documentsError.set("Impossible d'afficher ce document.");
      },
    });
  }

  askDeleteDocument(doc: PartenaireDocumentResponse): void {
    this.documentDeleteTarget.set(doc);
  }

  cancelDeleteDocument(): void {
    this.documentDeleteTarget.set(null);
  }

  confirmDeleteDocument(): void {
    const partenaire = this.selectedPartenaire();
    const doc = this.documentDeleteTarget();
    this.documentDeleteTarget.set(null);
    if (!partenaire || !doc) return;
    this.deletingDocumentId.set(doc.id);
    this.documentsError.set(null);
    this.partenaireService.deleteDocument(partenaire.id, doc.id).subscribe({
      next: () => {
        this.deletingDocumentId.set(null);
        this.documentsItems.set(this.documentsItems().filter((d) => d.id !== doc.id));
      },
      error: () => {
        this.deletingDocumentId.set(null);
        this.documentsError.set('Impossible de supprimer ce document pour le moment.');
      },
    });
  }

  documentTypeLabel(type: PartenaireDocumentType): string {
    return DOCUMENT_TYPE_LABEL[type] ?? type;
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }
}
