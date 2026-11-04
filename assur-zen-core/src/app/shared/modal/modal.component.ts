import { Component, input, output } from '@angular/core';
import { ButtonCloseDirective } from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';

@Component({
  selector: 'app-modal',
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.scss',
  imports: [ButtonCloseDirective, IconDirective],
})
export class ModalComponent {
  readonly title = input.required<string>();
  readonly visible = input(false);
  readonly size = input<'md' | 'lg' | 'xl' | 'xxl' | 'full'>('md');
  /** 'brand' : en-tête bleu dégradé avec pastille d'icône et sous-titre (parcours guidés, voir la
   * maquette Vignettes moto). Le corps n'a alors plus de marge — c'est au contenu de gérer la sienne. */
  readonly variant = input<'default' | 'brand' | 'custom'>('default');
  // 'custom' : l'en-tête est fourni par la page via [modalHeader] (bouton Fermer conservé), ex. la
  // fiche commercial (avatar, badge de statut, informations clés). `title` sert alors d'aria-label.
  readonly subtitle = input('');
  /** Nom d'icône CoreUI (ex. `cilUser`), affiché dans la pastille de l'en-tête 'brand'. */
  readonly icon = input('');
  /** 'brand' : en-tête en aplat (couleur unique, sans dégradé) au lieu du dégradé. */
  readonly flat = input(false);
  /** 'brand' : texte court (ex. un emoji) affiché dans la pastille à la place de l'icône CoreUI. */
  readonly glyph = input('');

  readonly closed = output<void>();

  onBackdropClick(): void {
    this.closed.emit();
  }
}
