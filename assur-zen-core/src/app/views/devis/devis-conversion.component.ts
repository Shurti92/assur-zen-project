import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../core/auth/auth.service';
import {
  QuoteContract,
  QuoteConvertRequest,
  QuoteDocument,
  QuoteDocumentType,
  QuoteInsuredCandidate,
  QuoteLookup,
  QuoteView,
} from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

export const DOCUMENT_TYPE_LABEL: Record<QuoteDocumentType, string> = {
  ATTESTATION: "Attestation d'assurance",
  CONDITIONS_PARTICULIERES: 'Conditions particulières',
  CARTE_GRISE: 'Carte grise',
  AUTRE: 'Autre document',
};

const MAX_FILE_BYTES = 5 * 1024 * 1024;

const SOURCE_LABEL: Record<string, string> = {
  LIE_AU_COMPTE: 'Lié au compte',
  TELEPHONE_DU_COMPTE: 'Même téléphone',
  RECHERCHE: 'Recherche',
};

type InsuredMode = 'auto' | 'choose' | 'create';

/** Devis payé (finalisation) ou converti (contrat créé) : paiement, documents, formulaire « Créer le contrat ». */
@Component({
  selector: 'app-devis-conversion',
  templateUrl: './devis-conversion.component.html',
  styleUrl: './devis-conversion.component.scss',
  imports: [AlertMessageComponent, RouterLink, SpinnerComponent],
})
export class DevisConversionComponent implements OnInit {
  private readonly service = inject(QuoteService);
  private readonly auth = inject(AuthService);

  readonly quote = input.required<QuoteView>();
  /** Émis avec le devis converti (la page se met à jour). */
  readonly converted = output<QuoteView>();

  readonly typeLabel = DOCUMENT_TYPE_LABEL;
  readonly types = Object.keys(DOCUMENT_TYPE_LABEL) as QuoteDocumentType[];
  readonly isAdmin = this.auth.currentUser()?.role === 'ADMIN';

  readonly isConverted = computed(() => this.quote().status === 'CONVERTI');

  // Documents
  readonly documents = signal<QuoteDocument[]>([]);
  readonly docsLoading = signal(true);
  readonly docType = signal<QuoteDocumentType>('ATTESTATION');
  readonly file = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly docError = signal<string | null>(null);
  readonly hasAttestation = computed(() => this.documents().some((d) => d.type === 'ATTESTATION'));

  // Formulaire
  readonly companies = signal<QuoteLookup[]>([]);
  readonly partners = signal<QuoteLookup[]>([]);
  readonly products = signal<QuoteLookup[]>([]);
  readonly policyNumber = signal('');
  readonly companyId = signal<number | null>(null);
  readonly partnerId = signal<number | null>(null);
  readonly commissionRate = signal('0');
  readonly productId = signal<number | null>(null);
  readonly insuredMode = signal<InsuredMode>('auto');
  readonly insuredId = signal<number | null>(null);
  readonly searchText = signal('');
  readonly candidates = signal<QuoteInsuredCandidate[]>([]);
  readonly candidatesLoading = signal(false);

  /** Contrat créé (devis converti) : compagnie, partenaire, commission. */
  readonly contract = signal<QuoteContract | null>(null);

  readonly converting = signal(false);
  readonly error = signal<string | null>(null);
  /** Candidats renvoyés par INSURED_AMBIGUOUS : fenêtre de choix. */
  readonly ambiguous = signal<QuoteInsuredCandidate[] | null>(null);
  readonly ambiguousPick = signal<number | 'new' | null>(null);

  readonly canConvert = computed(
    () =>
      !this.converting() &&
      this.hasAttestation() &&
      this.companyId() !== null &&
      (this.insuredMode() !== 'choose' || this.insuredId() !== null),
  );

  ngOnInit(): void {
    this.loadDocuments();
    if (this.isConverted()) {
      this.service.contract(this.quote().reference).subscribe({ next: (c) => this.contract.set(c), error: () => undefined });
      return;
    }
    this.service.lookup('companies').subscribe({ next: (l) => this.companies.set(l), error: () => this.error.set('Impossible de charger les compagnies.') });
    this.service.lookup('partners').subscribe({ next: (l) => this.partners.set(l), error: () => this.error.set('Impossible de charger les partenaires.') });
    this.service.lookup('products').subscribe({ next: (l) => this.products.set(l), error: () => undefined });
  }

  loadDocuments(): void {
    this.docsLoading.set(true);
    this.service.documents(this.quote().reference).subscribe({
      next: (items) => {
        this.documents.set(items);
        this.docsLoading.set(false);
      },
      error: () => {
        this.docError.set('Impossible de charger les documents.');
        this.docsLoading.set(false);
      },
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    this.docError.set(null);
    if (f && f.size > MAX_FILE_BYTES) {
      this.docError.set('Fichier trop volumineux (5 Mo maximum).');
      input.value = '';
      this.file.set(null);
      return;
    }
    if (f && !/\.(pdf|jpe?g|png)$/i.test(f.name)) {
      this.docError.set('Type de fichier non autorisé (PDF, JPG ou PNG).');
      input.value = '';
      this.file.set(null);
      return;
    }
    this.file.set(f);
  }

  upload(fileInput: HTMLInputElement): void {
    const f = this.file();
    if (!f || this.uploading()) return;
    this.uploading.set(true);
    this.docError.set(null);
    this.service.uploadDocument(this.quote().reference, this.docType(), f).subscribe({
      next: () => {
        this.uploading.set(false);
        this.file.set(null);
        fileInput.value = '';
        this.loadDocuments();
      },
      error: (err) => {
        this.uploading.set(false);
        this.docError.set(this.docMessage(err));
      },
    });
  }

  preview(d: QuoteDocument): void {
    this.service.documentBlob(this.quote().reference, d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.docError.set("Impossible d'ouvrir le document."),
    });
  }

  remove(d: QuoteDocument): void {
    this.docError.set(null);
    this.service.deleteDocument(this.quote().reference, d.id).subscribe({
      next: () => this.loadDocuments(),
      error: (err) => this.docError.set(this.docMessage(err)),
    });
  }

  setMode(mode: InsuredMode): void {
    this.insuredMode.set(mode);
    if (mode !== 'choose') this.insuredId.set(null);
    if (mode === 'choose' && this.candidates().length === 0) this.searchCandidates('');
  }

  searchCandidates(q: string): void {
    this.searchText.set(q);
    if (q.trim() && q.trim().length < 3) return;
    this.candidatesLoading.set(true);
    this.service.insuredCandidates(this.quote().reference, q).subscribe({
      next: (items) => {
        this.candidates.set(items);
        this.candidatesLoading.set(false);
      },
      error: () => {
        this.candidatesLoading.set(false);
        this.error.set('Impossible de charger les assurés.');
      },
    });
  }

  sourceLabel(c: QuoteInsuredCandidate): string {
    return c.source ? (SOURCE_LABEL[c.source] ?? '') : '';
  }

  convert(forced?: { insuredId?: number; createInsured?: boolean }): void {
    if (!forced && !this.canConvert()) return;
    const body: QuoteConvertRequest = { companyId: this.companyId()! };
    if (this.policyNumber().trim()) body.policyNumber = this.policyNumber().trim();
    if (this.partnerId() !== null) body.partnerId = this.partnerId()!;
    const rate = Number(this.commissionRate().replace(',', '.'));
    if (this.isAdmin && rate > 0) body.commissionRate = rate;
    if (this.productId() !== null) body.productId = this.productId()!;
    if (forced?.insuredId) body.insuredId = forced.insuredId;
    else if (forced?.createInsured) body.createInsured = true;
    else if (this.insuredMode() === 'choose') body.insuredId = this.insuredId()!;
    else if (this.insuredMode() === 'create') body.createInsured = true;

    this.converting.set(true);
    this.error.set(null);
    this.service.convert(this.quote().reference, body).subscribe({
      next: (q) => {
        this.converting.set(false);
        this.ambiguous.set(null);
        this.converted.emit(q);
      },
      error: (err) => {
        this.converting.set(false);
        this.handleConvertError(err);
      },
    });
  }

  confirmAmbiguous(): void {
    const pick = this.ambiguousPick();
    if (pick === null) return;
    this.convert(pick === 'new' ? { createInsured: true } : { insuredId: pick });
  }

  private handleConvertError(err: unknown): void {
    this.ambiguous.set(null);
    if (!(err instanceof HttpErrorResponse)) {
      this.error.set('Impossible de créer le contrat.');
      return;
    }
    const code = err.error?.code as string | undefined;
    if (code === 'INSURED_AMBIGUOUS') {
      const list = (err.error?.details?.candidates ?? []) as QuoteInsuredCandidate[];
      this.ambiguousPick.set(null);
      this.ambiguous.set(list);
      return;
    }
    const byCode: Record<string, string> = {
      ATTESTATION_REQUIRED: "Attestation manquante. Joignez d'abord l'attestation d'assurance, puis relancez la finalisation.",
      POLICY_NUMBER_EXISTS: 'Ce numéro de police existe déjà. Saisissez-en un autre ou laissez vide pour un numéro provisoire.',
      VEHICLE_CONFLICT: "Ce véhicule est déjà rattaché à un autre assuré. Vérifiez l'immatriculation et le châssis.",
      QUOTE_NOT_PAID: "Ce devis n'est pas encore payé.",
      QUOTE_ALREADY_CONVERTED: 'Ce devis est déjà converti en contrat.',
    };
    if (code && byCode[code]) this.error.set(byCode[code]);
    else if (err.status === 403) this.error.set('Seul un administrateur peut fixer une commission non nulle.');
    else this.error.set(typeof err.error?.message === 'string' && err.error.message ? err.error.message : 'Impossible de créer le contrat.');
  }

  private docMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code === 'FICHIER_TROP_VOLUMINEUX') return 'Fichier trop volumineux (5 Mo maximum).';
      if (code === 'DOCUMENT_TYPE_NON_AUTORISE') return 'Type de fichier non autorisé (PDF, JPG ou PNG).';
      if (code === 'QUOTE_ALREADY_CONVERTED') return 'Le contrat est déjà créé : les documents ne peuvent plus être modifiés.';
      if (code === 'QUOTE_NOT_PAID') return "Ce devis n'est pas encore payé.";
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return 'Opération impossible sur le document.';
  }

  formatDateTime(value?: string | null): string {
    if (!value) return '—';
    const d = new Date(value);
    return `${d.toLocaleDateString('fr-FR')} à ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  formatSize(bytes: number): string {
    return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} Mo` : `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  }

  ext(d: QuoteDocument): string {
    return (d.fileName.split('.').pop() ?? '').slice(0, 3).toUpperCase() || 'DOC';
  }
}
