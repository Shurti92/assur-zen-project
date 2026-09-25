import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { PartnerCommissionService } from '../../../core/partner-commission/partner-commission.service';
import {
  CommissionBeneficiaire,
  PartnerCommissionRequest,
  PartnerCommissionResponse,
} from '../../../core/partner-commission/models/partner-commission.model';
import { TypePartenaire } from '../../../core/configuration/models/produit-commission-taux.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { PartenaireService } from '../../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../../core/partenaire/models/partenaire.model';
import { ProfileService } from '../../../core/profile/profile.service';
import { ProfileRecord } from '../../../core/profile/models/profile-details.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { ThousandSeparatorDirective } from '../../../shared/directives/thousand-separator.directive';

type BeneficiaireFilter = 'all' | CommissionBeneficiaire;
/** Cible d'une commission partenaire : un partenaire précis, ou tous les partenaires d'un type. */
type CiblePartenaire = 'PARTENAIRE' | 'TYPE';

const TYPE_LABEL: Record<TypePartenaire, string> = { DIRECT: 'Direct', AGENT: 'Agent', APPORTEUR: 'Apporteur' };

/** Commissions configurées (montant en FCFA par produit) pour un partenaire, un type de
 * partenaire ou un commercial — /partner-commissions, livré par le backend le 2026-09-25.
 * Elles préremplissent la commission partenaire / commerciale lors de la souscription ; le
 * backend les applique aussi quand aucune valeur n'est fournie. */
@Component({
  selector: 'app-commissions-configurees',
  templateUrl: './commissions-configurees.component.html',
  imports: [
    ButtonDirective,
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
  ],
})
export class CommissionsConfigureesComponent {
  private readonly service = inject(PartnerCommissionService);
  private readonly produitService = inject(ProduitService);
  private readonly partenaireService = inject(PartenaireService);
  private readonly profileService = inject(ProfileService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<PartnerCommissionResponse[]>([]);
  readonly produits = signal<ProduitResponse[]>([]);
  readonly partenaires = signal<PartenaireResponse[]>([]);
  readonly commerciaux = signal<ProfileRecord[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly beneficiaireFilter = signal<BeneficiaireFilter>('all');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<PartnerCommissionResponse | null>(null);

  readonly typesPartenaire: { value: TypePartenaire; label: string }[] = [
    { value: 'DIRECT', label: TYPE_LABEL.DIRECT },
    { value: 'AGENT', label: TYPE_LABEL.AGENT },
    { value: 'APPORTEUR', label: TYPE_LABEL.APPORTEUR },
  ];

  readonly form = this.fb.nonNullable.group({
    produitId: [0],
    beneficiaire: ['PARTENAIRE' as CommissionBeneficiaire],
    cible: ['PARTENAIRE' as CiblePartenaire],
    partenaireId: [''],
    typePartenaire: ['AGENT' as TypePartenaire],
    commercialId: [''],
    montant: [null as number | null],
    active: [true],
    dateDebut: [''],
    dateFin: [''],
  });

  /** Valeurs du formulaire en signaux, pour afficher les bons champs selon le bénéficiaire. */
  readonly beneficiaire = signal<CommissionBeneficiaire>('PARTENAIRE');
  readonly cible = signal<CiblePartenaire>('PARTENAIRE');

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.beneficiaireFilter();
    return this.items()
      .filter((item) => filter === 'all' || item.beneficiaire === filter)
      .filter((item) => !query || [item.produitName, this.cibleLabel(item)].some((v) => (v ?? '').toLowerCase().includes(query)));
  });

  constructor() {
    this.form.controls.beneficiaire.valueChanges.subscribe((value) => this.beneficiaire.set(value));
    this.form.controls.cible.valueChanges.subscribe((value) => this.cible.set(value));
    this.fetch();
    this.fetchReferenceData();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les commissions configurées.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setBeneficiaireFilter(filter: BeneficiaireFilter): void {
    this.beneficiaireFilter.set(filter);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({
      produitId: 0,
      beneficiaire: this.beneficiaireFilter() === 'COMMERCIAL' ? 'COMMERCIAL' : 'PARTENAIRE',
      cible: 'PARTENAIRE',
      partenaireId: '',
      typePartenaire: 'AGENT',
      commercialId: '',
      montant: null,
      active: true,
      dateDebut: '',
      dateFin: '',
    });
    this.formVisible.set(true);
  }

  openEdit(item: PartnerCommissionResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      produitId: item.produitId,
      beneficiaire: item.beneficiaire,
      cible: item.partenaireId !== null ? 'PARTENAIRE' : 'TYPE',
      partenaireId: item.partenaireId !== null ? String(item.partenaireId) : '',
      typePartenaire: item.typePartenaire ?? 'AGENT',
      commercialId: item.commercialId !== null ? String(item.commercialId) : '',
      montant: item.montant,
      active: item.active,
      dateDebut: item.dateDebut ?? '',
      dateFin: item.dateFin ?? '',
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.produitId) {
      this.formError.set('Sélectionnez un produit.');
      return;
    }
    const isPartenaire = raw.beneficiaire === 'PARTENAIRE';
    if (isPartenaire && raw.cible === 'PARTENAIRE' && !raw.partenaireId) {
      this.formError.set('Sélectionnez un partenaire.');
      return;
    }
    if (!isPartenaire && !raw.commercialId) {
      this.formError.set('Sélectionnez un commercial.');
      return;
    }
    if (raw.montant === null || raw.montant === undefined || raw.montant < 0) {
      this.formError.set('Saisissez un montant (FCFA, 0 ou plus).');
      return;
    }
    if (raw.dateDebut && raw.dateFin && raw.dateFin < raw.dateDebut) {
      this.formError.set('La date de fin doit être postérieure à la date de début.');
      return;
    }
    // Le backend refuse (400) les champs qui ne correspondent pas au bénéficiaire : on n'envoie
    // que ceux de la cible choisie.
    const payload: PartnerCommissionRequest = {
      produitId: Number(raw.produitId),
      beneficiaire: raw.beneficiaire,
      partenaireId: isPartenaire && raw.cible === 'PARTENAIRE' ? Number(raw.partenaireId) : null,
      typePartenaire: isPartenaire && raw.cible === 'TYPE' ? raw.typePartenaire : null,
      commercialId: isPartenaire ? null : Number(raw.commercialId),
      montant: Number(raw.montant),
      active: raw.active,
      dateDebut: raw.dateDebut || null,
      dateFin: raw.dateFin || null,
    };
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.service.update(id, payload) : this.service.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: (err: HttpErrorResponse) => {
        this.formSaving.set(false);
        this.formError.set(
          err.status === 409
            ? (err.error?.message ?? 'Une configuration active existe déjà pour cette cible sur cette période.')
            : (err.error?.message ?? "Impossible d'enregistrer la commission configurée. Réessayez."),
        );
      },
    });
  }

  /** Action principale : désactiver (ou réactiver) garde l'historique — DELETE est un hard
   * delete, réservé aux erreurs de saisie (conseil du backend, 2026-09-25). Une configuration
   * inactive n'est plus appliquée et n'entre pas dans le contrôle de chevauchement. */
  toggleActive(item: PartnerCommissionResponse): void {
    this.error.set(null);
    this.service
      .update(item.id, {
        produitId: item.produitId,
        beneficiaire: item.beneficiaire,
        partenaireId: item.partenaireId,
        typePartenaire: item.typePartenaire,
        commercialId: item.commercialId,
        montant: item.montant,
        active: !item.active,
        dateDebut: item.dateDebut,
        dateFin: item.dateFin,
      })
      .subscribe({
        next: () => this.fetch(),
        error: (err: HttpErrorResponse) =>
          this.error.set(err.error?.message ?? 'Impossible de modifier le statut de cette commission configurée.'),
      });
  }

  confirmDelete(item: PartnerCommissionResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleteTarget.set(null);
    this.service.delete(item.id).subscribe({
      next: () => this.fetch(),
      error: () => this.error.set('Impossible de supprimer cette commission configurée.'),
    });
  }

  /** « Partenaire XYZ », « Tous les agents » ou le nom du commercial. */
  cibleLabel(item: PartnerCommissionResponse): string {
    if (item.beneficiaire === 'COMMERCIAL') {
      const profile = this.commerciaux().find((p) => p.userId === item.commercialId);
      return profile ? `${profile.firstname} ${profile.lastname}` : (item.commercialUsername ?? `Commercial #${item.commercialId}`);
    }
    if (item.partenaireId !== null) return item.partenaireNom ?? `Partenaire #${item.partenaireId}`;
    return item.typePartenaire ? `Tous les partenaires ${TYPE_LABEL[item.typePartenaire].toLowerCase()}` : '—';
  }

  periodeLabel(item: PartnerCommissionResponse): string {
    if (!item.dateDebut && !item.dateFin) return 'Permanente';
    if (item.dateDebut && item.dateFin) return `Du ${this.formatDate(item.dateDebut)} au ${this.formatDate(item.dateFin)}`;
    return item.dateDebut ? `À partir du ${this.formatDate(item.dateDebut)}` : `Jusqu'au ${this.formatDate(item.dateFin!)}`;
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  private fetchReferenceData(): void {
    this.produitService.list(0, 200).subscribe({
      next: (page) => this.produits.set(page.content),
      error: () => {
        // Silent: the produit dropdown will just stay empty if this fails.
      },
    });
    this.partenaireService.list(0, 200).subscribe({
      next: (page) => this.partenaires.set(page.content),
      error: () => {
        // Silent: idem.
      },
    });
    this.profileService.list().subscribe({
      next: (profiles) => this.commerciaux.set(profiles),
      error: () => {
        // Silent: idem.
      },
    });
  }
}
