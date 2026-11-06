import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import {
  Popup,
  PopupFrequency,
  PopupLimits,
  PopupMediaType,
  PopupProduct,
  PopupRequest,
  PopupStatus,
  PopupSummary,
} from '../../core/mobile-admin/models/mobile-popup.model';
import { MobilePopupService } from '../../core/mobile-admin/mobile-popup.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type ActionKind = 'NONE' | 'LINK' | 'PRODUCT';
type StatusFilter = 'all' | PopupStatus;

/** Brouillon du formulaire (dates au format AAAA-MM-JJ). */
interface Draft {
  id: string | null;
  title: string;
  internalName: string;
  placement: string;
  mediaType: PopupMediaType;
  mediaUrl: string | null;
  posterUrl: string | null;
  action: ActionKind;
  linkUrl: string;
  productId: string | null;
  linkLabel: string;
  frequency: PopupFrequency;
  displayAfterSeconds: number;
  startDate: string;
  endDate: string;
  priority: number;
  active: boolean;
  stats: Popup['stats'] | null;
}

const STATUS_LABEL: Record<PopupStatus, string> = {
  ACTIVE: 'Actif',
  SCHEDULED: 'Programmé',
  EXPIRED: 'Expiré',
  INACTIVE: 'Inactif',
};

const FREQUENCY_LABEL: Record<PopupFrequency, string> = {
  DAILY: '1 fois par jour',
  ONCE: '1 seule fois',
  EVERY_OPEN: 'À chaque ouverture',
};

const PLACEMENT_LABEL: Record<string, string | undefined> = {
  HOME: "Accueil de l'application",
  APP_OPEN: "Ouverture de l'application",
};

function blank(): Draft {
  return {
    id: null,
    title: '',
    internalName: '',
    placement: 'HOME',
    mediaType: 'IMAGE',
    mediaUrl: null,
    posterUrl: null,
    action: 'NONE',
    linkUrl: '',
    productId: null,
    linkLabel: '',
    frequency: 'DAILY',
    displayAfterSeconds: 2,
    startDate: '',
    endDate: '',
    priority: 50,
    active: false,
    stats: null,
  };
}

/** Mobile AssurZen > Pop-up publicitaires : liste, création, modification, aperçu (ADMIN). Maquette validée le 2026-11-06. */
@Component({
  selector: 'app-popups-mobile',
  standalone: true,
  imports: [AlertMessageComponent, FormsModule, SpinnerComponent],
  templateUrl: './popups-mobile.component.html',
  styleUrl: './popups-mobile.component.scss',
})
export class PopupsMobileComponent {
  private readonly service = inject(MobilePopupService);

  readonly items = signal<Popup[]>([]);
  readonly summary = signal<PopupSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly statusFilter = signal<StatusFilter>('all');
  readonly search = signal('');
  readonly placementFilter = signal('');

  readonly limits = signal<PopupLimits | null>(null);
  readonly products = signal<PopupProduct[]>([]);

  /** Formulaire : null = liste affichée. */
  readonly draft = signal<Draft | null>(null);
  readonly saving = signal(false);
  readonly uploading = signal<'MEDIA' | 'POSTER' | null>(null);
  readonly formError = signal<string | null>(null);
  readonly deleteTarget = signal<Popup | null>(null);

  readonly shown = computed(() => {
    const st = this.statusFilter();
    const q = this.search().trim().toLowerCase();
    const pl = this.placementFilter();
    return this.items().filter(
      (p) =>
        (st === 'all' || p.status === st) &&
        (!pl || p.placement === pl) &&
        (!q || `${p.title} ${p.internalName ?? ''}`.toLowerCase().includes(q)),
    );
  });

  readonly statusLabel = STATUS_LABEL;
  readonly frequencyLabel = FREQUENCY_LABEL;
  readonly placementLabel = PLACEMENT_LABEL;
  readonly frequencies = Object.keys(FREQUENCY_LABEL) as PopupFrequency[];

  constructor() {
    this.load();
    this.service.limits().subscribe({ next: (l) => this.limits.set(l), error: () => this.limits.set(null) });
    this.service.products().subscribe({ next: (p) => this.products.set(p), error: () => this.products.set([]) });
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (r) => {
        this.items.set(r.items);
        this.summary.set(r.summary);
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(
          e.status === 404
            ? "La passerelle ne propose pas encore les pop-up publicitaires (version à déployer)."
            : 'Impossible de charger les pop-up publicitaires.',
        );
        this.loading.set(false);
      },
    });
  }

  // ---------- Liste ----------
  period(p: Popup): string {
    const d = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }) : null);
    const y = (iso: string | null) => (iso ? new Date(iso).getFullYear() : '');
    if (!p.startAt && !p.endAt) return 'Sans limite';
    return `${d(p.startAt) ?? '…'} → ${d(p.endAt) ?? '…'}${y(p.endAt || p.startAt) ? '/' + y(p.endAt || p.startAt) : ''}`;
  }

  pillClass(s: PopupStatus): string {
    return {
      ACTIVE: 'mb-pill mb-pill--ok',
      SCHEDULED: 'mb-pill mb-pill--info',
      EXPIRED: 'mb-pill mb-pill--mut',
      INACTIVE: 'mb-pill mb-pill--warn',
    }[s];
  }

  toggleActive(p: Popup): void {
    this.error.set(null);
    this.service.setActive(p.id, !p.active).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) => this.error.set(this.message(e, "Changement d'état impossible.")),
    });
  }

  askDelete(p: Popup): void {
    this.deleteTarget.set(p);
  }

  confirmDelete(): void {
    const p = this.deleteTarget();
    if (!p) return;
    this.service.remove(p.id).subscribe({
      next: () => {
        this.deleteTarget.set(null);
        this.info.set('Pop-up supprimé.');
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.deleteTarget.set(null);
        this.error.set(this.message(e, 'Suppression impossible.'));
      },
    });
  }

  // ---------- Formulaire ----------
  newPopup(): void {
    this.formError.set(null);
    this.draft.set(blank());
  }

  edit(p: Popup): void {
    this.formError.set(null);
    this.draft.set({
      id: p.id,
      title: p.title,
      internalName: p.internalName ?? '',
      placement: p.placement,
      mediaType: p.mediaType,
      mediaUrl: p.mediaUrl,
      posterUrl: p.posterUrl,
      action: p.productId ? 'PRODUCT' : p.linkUrl ? 'LINK' : 'NONE',
      linkUrl: p.linkUrl ?? '',
      productId: p.productId,
      linkLabel: p.linkLabel ?? '',
      frequency: p.frequency,
      displayAfterSeconds: p.displayAfterSeconds,
      startDate: p.startAt ? p.startAt.slice(0, 10) : '',
      endDate: p.endAt ? p.endAt.slice(0, 10) : '',
      priority: p.priority,
      active: p.active,
      stats: p.stats,
    });
  }

  cancel(): void {
    this.draft.set(null);
  }

  setMediaType(d: Draft, t: PopupMediaType): void {
    if (d.mediaType === t) return;
    d.mediaType = t;
    d.mediaUrl = null;
    if (t === 'IMAGE') d.posterUrl = null;
  }

  /** Téléverse un fichier après contrôle du type et du poids annoncés par le serveur. */
  onFile(d: Draft, kind: 'MEDIA' | 'POSTER', ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.formError.set(null);
    const l = this.limits();
    if (l) {
      const rule = kind === 'POSTER' ? l.poster : d.mediaType === 'VIDEO' ? l.video : l.image;
      if (rule.types.length > 0 && !rule.types.includes(file.type)) {
        this.formError.set(`Format refusé (${file.type || 'inconnu'}). Formats acceptés : ${this.typeNames(rule.types)}.`);
        return;
      }
      if (file.size > rule.maxBytes) {
        this.formError.set(`Fichier trop lourd (${this.bytes(file.size)}). Maximum : ${this.bytes(rule.maxBytes)}.`);
        return;
      }
    }
    this.uploading.set(kind);
    this.service.upload(file, kind).subscribe({
      next: (r) => {
        if (kind === 'POSTER') d.posterUrl = r.url;
        else d.mediaUrl = r.url;
        this.uploading.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.uploading.set(null);
        this.formError.set(e.status === 413 ? 'Fichier trop lourd pour le serveur.' : this.message(e, 'Téléversement impossible.'));
      },
    });
  }

  removeMedia(d: Draft, kind: 'MEDIA' | 'POSTER'): void {
    if (kind === 'POSTER') d.posterUrl = null;
    else d.mediaUrl = null;
  }

  /** Contrôles avant envoi ; renvoie le message d'erreur ou null. */
  private validate(d: Draft): string | null {
    const max = this.limits()?.maxDisplayAfterSeconds ?? 30;
    const maxPrio = this.limits()?.maxPriority ?? 100;
    if (!d.title.trim()) return 'Le titre est obligatoire.';
    if (d.title.trim().length > 80) return 'Le titre fait 80 caractères au maximum.';
    if (d.active && !d.mediaUrl) return 'Téléversez le média avant d\'activer le pop-up.';
    if (d.action === 'LINK' && !/^https:\/\/\S+$/i.test(d.linkUrl.trim())) return 'Le lien doit commencer par https://.';
    if (d.action === 'PRODUCT' && !d.productId) return 'Choisissez le produit à ouvrir.';
    if (d.action !== 'NONE' && !d.linkLabel.trim()) return 'Le texte du bouton est obligatoire quand il y a un lien ou un produit.';
    if (d.linkLabel.trim().length > 30) return 'Le texte du bouton fait 30 caractères au maximum.';
    if (!(d.displayAfterSeconds >= 0 && d.displayAfterSeconds <= max)) return `Le délai d'apparition va de 0 à ${max} secondes.`;
    if (!(d.priority >= 0 && d.priority <= maxPrio)) return `La priorité va de 0 à ${maxPrio}.`;
    if (d.startDate && d.endDate && d.endDate <= d.startDate) return 'La date de fin doit suivre la date de début.';
    return null;
  }

  save(): void {
    const d = this.draft();
    if (!d || this.saving()) return;
    const problem = this.validate(d);
    if (problem) {
      this.formError.set(problem);
      return;
    }
    const withAction = d.action !== 'NONE';
    const payload: PopupRequest = {
      title: d.title.trim(),
      internalName: d.internalName.trim() || null,
      placement: d.placement,
      mediaType: d.mediaType,
      mediaUrl: d.mediaUrl,
      posterUrl: d.mediaType === 'VIDEO' ? d.posterUrl : null,
      linkUrl: d.action === 'LINK' ? d.linkUrl.trim() : null,
      productId: d.action === 'PRODUCT' ? d.productId : null,
      linkLabel: withAction ? d.linkLabel.trim() : null,
      displayAfterSeconds: Number(d.displayAfterSeconds),
      frequency: d.frequency,
      priority: Number(d.priority),
      startAt: d.startDate ? `${d.startDate}T00:00:00Z` : null,
      endAt: d.endDate ? `${d.endDate}T23:59:59Z` : null,
      active: d.active,
    };
    this.saving.set(true);
    this.formError.set(null);
    const call = d.id ? this.service.update(d.id, payload) : this.service.create(payload);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.draft.set(null);
        this.info.set(d.id ? 'Pop-up enregistré.' : 'Pop-up créé.');
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(this.message(e, "Enregistrement impossible."));
      },
    });
  }

  // ---------- Aperçu et utilitaires ----------
  previewDelay(d: Draft): string {
    return `Apparaît après ${d.displayAfterSeconds || 0} s · ${FREQUENCY_LABEL[d.frequency].toLowerCase()}`;
  }

  productTitle(id: string | null): string {
    return this.products().find((p) => p.id === id)?.title ?? id ?? '';
  }

  bytes(n: number): string {
    return n >= 1024 * 1024 ? `${(n / (1024 * 1024)).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo` : `${Math.round(n / 1024)} Ko`;
  }

  typeNames(types: string[]): string {
    return types.map((t) => t.replace('image/', '').replace('video/', '').toUpperCase()).join(', ');
  }

  private message(e: HttpErrorResponse, fallback: string): string {
    const body = e.error as { message?: string; error?: string; field?: string } | null;
    const text = body?.message ?? body?.error;
    return text ? `${text}${body?.field ? ` (champ ${body.field})` : ''}` : fallback;
  }
}
