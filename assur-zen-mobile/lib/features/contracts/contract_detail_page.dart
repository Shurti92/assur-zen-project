import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../../core/utils/file_viewer.dart';
import '../payment/payment_page.dart';

/// Détail d’un contrat : synthèse, renouvellement, attestation, documents, garanties.
class ContractDetailPage extends StatelessWidget {
  const ContractDetailPage({super.key, required this.contractId});

  final String contractId;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Détail du contrat'),
      body: AsyncView<Contract>(
        load: () async {
          final repos = context.repos;
          final store = context.store;
          final c = await repos.contracts.getContract(contractId);
          // Décision 5.4 : l’attestation reste lisible hors connexion.
          if (c.hasDigitalAttestation) await store.cacheAttestation(c);
          return c;
        },
        loading: const AzSkeletonList(count: 3),
        builder: (context, c, reload) => RefreshIndicator(
          onRefresh: reload,
          child: ListView(
            // + barre de navigation Android : sans cela, « Demander la résiliation » reste cachée.
            padding: EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
            children: [
              _Summary(contract: c),
              ..._renewal(context, c),
              if (c.status == ContractStatus.enAttente) ...[
                const SizedBox(height: 14),
                const AzBanner(
                  kind: AzBannerKind.warning,
                  text: 'Votre dossier est en cours de validation par AssurZen. Les documents seront disponibles après validation.',
                ),
              ],
              if (c.hasDigitalAttestation) ...[
                const SizedBox(height: 22),
                _AttestationCard(contract: c),
              ] else if (c.attestation?.available == true) ...[
                // Attestation jointe par AssurZen, sans code de vérification : on propose le fichier.
                const SizedBox(height: 22),
                _AttestationFileCard(contract: c),
              ],
              const SizedBox(height: 22),
              const AzSectionHeader('Documents'),
              const SizedBox(height: 8),
              if (c.documents.isEmpty)
                AzCard(
                  radius: 26,
                  child: Text('Aucun document disponible pour le moment.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                )
              else
                AzCard(
                  radius: 26,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                  child: Column(
                    children: [
                      for (var i = 0; i < c.documents.length; i++) ...[
                        if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                        _DocumentRow(contract: c, document: c.documents[i]),
                      ],
                    ],
                  ),
                ),
              const SizedBox(height: 14),
              const AzSectionHeader('Garanties', trailing: Env.useMock ? AzStatusBadge.pending('Exemple') : null),
              const SizedBox(height: 8),
              if (c.guarantees.isEmpty)
                AzCard(
                  radius: 26,
                  child: Text(
                    'Le détail de vos garanties n’est pas encore disponible dans l’application. Votre conseiller vous le communique sur demande.',
                    style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                  ),
                )
              else
                AzCard(
                  radius: 26,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                  child: Column(
                    children: [
                      for (var i = 0; i < c.guarantees.length; i++) ...[
                        if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                        _GuaranteeRow(guarantee: c.guarantees[i]),
                      ],
                    ],
                  ),
                ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    flex: 3, // libellé plus long que « Contacter » : évite « Déclarer un sin… »
                    child: AzButton.secondary(
                      'Déclarer un sinistre',
                      height: 50,
                      radius: 16,
                      onPressed: c.actions.canDeclareClaim ? () => context.push(AppRoutes.claimNew, c.id) : null,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: AzButton.secondary(
                      'Contacter',
                      height: 50,
                      radius: 16,
                      onPressed: () => Launch.whatsapp(context, message: 'Bonjour AssurZen, je vous contacte au sujet de mon contrat ${c.number}.'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 12,
                children: [
                  if (c.actions.canRequestChange)
                    AzTextLink('Demander une modification', onPressed: () => _request(context, c, ContractRequestType.modification)),
                  if (c.actions.canRequestTermination)
                    AzTextLink('Demander la résiliation', color: AzColors.muted, onPressed: () => _request(context, c, ContractRequestType.termination)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Décisions 5.6 et 5.7 : demande transmise à un conseiller.
  Future<void> _request(BuildContext context, Contract c, ContractRequestType type) async {
    final kind = type == ContractRequestType.termination ? 'résiliation' : 'modification';
    final ok = await showAzConfirm(
      context,
      title: 'Demande de $kind',
      message: 'Un conseiller AssurZen traite votre demande sur le contrat ${c.number}.',
      confirmLabel: 'Envoyer',
    );
    if (!ok || !context.mounted) return;
    try {
      final receipt = await context.repos.contracts.requestChange(contractId: c.id, type: type);
      final hours = receipt.expectedWithinHours;
      final delay = hours == null ? '' : ' Réponse sous $hours h.';
      if (context.mounted) {
        showAzSnack(context, 'Demande envoyée (référence ${receipt.reference}).$delay');
      }
    } catch (e) {
      if (context.mounted) showAzSnack(context, errorMessage(e));
    }
  }

  /// Le serveur décide de l’éligibilité, de la fenêtre et du montant (`renewal`).
  List<Widget> _renewal(BuildContext context, Contract c) {
    final r = c.renewal;
    if (r == null || !r.eligible) return const [];
    final days = r.daysLeft;
    final chip = switch (days) {
      null => 'Bientôt',
      0 => 'Aujourd’hui',
      1 => '1 jour',
      final d => '$d jours',
    };
    final when = days == null ? 'Échéance proche. ' : (days == 0 ? 'Échéance aujourd’hui. ' : 'Échéance dans $days jours. ');
    return [
      const SizedBox(height: 10),
      AzCard(
        color: AzColors.orange50,
        borderColor: null,
        radius: 26,
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(child: Text('À FAIRE', style: AzText.overline.copyWith(color: AzColors.orange700))),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                  decoration: BoxDecoration(color: AzColors.orange500, borderRadius: BorderRadius.circular(AzRadius.pill)),
                  child: Text(chip, style: AzText.caption.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text.rich(
              TextSpan(children: [
                TextSpan(text: when, style: const TextStyle(fontWeight: FontWeight.w800)),
                const TextSpan(text: 'Renouvelez pour rester couvert sans interruption.'),
              ]),
              style: AzText.title.copyWith(fontSize: 17, height: 1.25),
            ),
            if (r.amount != null) ...[
              const SizedBox(height: 6),
              Text(
                'Renouvellement · ${r.durationMonths ?? 12} mois · Prime de renouvellement : ${formatFcfa(r.amount!)}',
                style: AzText.label.copyWith(color: AzColors.warningInk, fontWeight: FontWeight.w500),
              ),
              if (r.hasDiscount) ...[
                const SizedBox(height: 4),
                Text(
                  'Remise Zen Points −${formatPercent(r.discountPercent!)} % : −${formatFcfa(r.discountAmount!)} sur ${formatFcfa(r.fullAmount!)}',
                  style: AzText.label.copyWith(color: AzColors.success, fontWeight: FontWeight.w700),
                ),
              ],
            ],
            const SizedBox(height: 12),
            AzButton(
              'Renouveler',
              variant: AzButtonVariant.accent,
              icon: Icons.arrow_forward_rounded,
              height: 50,
              radius: 16,
              onPressed: () {
                // Le serveur fournit la référence payable quand le renouvellement l'est.
                // En démonstration seulement, le numéro de contrat en tient lieu.
                final ref = r.payableReference ?? (Env.useMock ? c.number : null);
                if (ref == null) {
                  showAzSnack(context, 'Le renouvellement en ligne n’est pas disponible pour le moment. Contactez un conseiller.');
                  return;
                }
                context.push(
                  AppRoutes.payment,
                  PaymentArgs(
                    payableReference: ref,
                    amount: r.amount ?? c.premium ?? 0,
                    caption: 'Renouvellement · ${r.durationMonths ?? 12} mois',
                    objectLabel: '${c.name} · ${c.number}',
                    isRenewal: true,
                  ),
                );
              },
            ),
          ],
        ),
      ),
    ];
  }
}

/// Carte bleue d'identité du contrat : produit, statut, quatre repères.
class _Summary extends StatelessWidget {
  const _Summary({required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    final ok = c.status == ContractStatus.actif;
    return AzCard(
      color: AzColors.blue700,
      borderColor: null,
      radius: 28,
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              AzIconTile(icon: productIcon(c.productId), size: 42, background: const Color(0x29FFFFFF), color: Colors.white, radius: 15),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AzRadius.pill)),
                child: Text(c.status.label, style: AzText.caption.copyWith(color: ok ? AzColors.success : AzColors.ink2, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(c.name, style: AzText.title.copyWith(color: Colors.white, fontSize: 19, height: 1.15)),
          Text(c.insuredObject, style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(child: _Fact('N° de contrat', c.number)),
              const SizedBox(width: 8),
              Expanded(child: _Fact('Prime', c.premium == null ? 'À confirmer' : formatFcfa(c.premium!))),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: _Fact('Prise d’effet', formatDate(c.startDate))),
              const SizedBox(width: 8),
              Expanded(child: _Fact('Échéance', formatDate(c.endDate))),
            ],
          ),
        ],
      ),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(color: const Color(0x1FFFFFFF), borderRadius: BorderRadius.circular(16)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AzText.caption.copyWith(color: AzColors.blue100, fontWeight: FontWeight.w500, fontSize: 11.5)),
          const SizedBox(height: 2),
          Text(value, style: AzText.label.copyWith(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13.5)),
        ],
      ),
    );
  }
}

/// Une garantie : libellé et pastille (Obligatoire, Incluse, Selon formule, À confirmer).
class _GuaranteeRow extends StatelessWidget {
  const _GuaranteeRow({required this.guarantee});

  final Guarantee guarantee;

  @override
  Widget build(BuildContext context) {
    final (Color fg, Color bg) = switch (guarantee.kind) {
      GuaranteeKind.obligatoire => (AzColors.success, AzColors.successBg),
      GuaranteeKind.incluse => (AzColors.blue700, AzColors.blue50),
      _ => (AzColors.warning, AzColors.warningBg),
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          Expanded(child: Text(guarantee.name, style: AzText.bodyMedium.copyWith(color: AzColors.ink, fontWeight: FontWeight.w600))),
          const SizedBox(width: 10),
          AzStatusBadge(label: guarantee.kindLabel, foreground: fg, background: bg, dot: false),
        ],
      ),
    );
  }
}

class _AttestationCard extends StatefulWidget {
  const _AttestationCard({required this.contract});

  final Contract contract;

  @override
  State<_AttestationCard> createState() => _AttestationCardState();
}

class _AttestationCardState extends State<_AttestationCard> {
  bool _busy = false;

  Future<void> _download() async {
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadAttestation(widget.contract.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: 'attestation');
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final contract = widget.contract;
    return AzCard(
      radius: 26,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 84,
                height: 84,
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14), border: Border.all(color: AzColors.line)),
                child: QrImageView(
                  data: contract.verificationCode!,
                  padding: EdgeInsets.zero,
                  semanticsLabel: 'QR code de vérification de l’attestation',
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Attestation numérique', style: AzText.titleSmall),
                    const SizedBox(height: 4),
                    Text('QR code vérifiable lors des contrôles routiers. Valide jusqu’au ${formatDate(contract.endDate)}.',
                        style: AzText.caption.copyWith(fontSize: 12.5, height: 17 / 12.5, fontWeight: FontWeight.w500)),
                    const SizedBox(height: 4),
                    Text('Disponible hors connexion', style: AzText.caption.copyWith(color: AzColors.success, fontWeight: FontWeight.w700)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          AzButton('Présenter l’attestation', height: 50, radius: 16, icon: Icons.qr_code_2_rounded, onPressed: () => context.push(AppRoutes.attestation, contract)),
          if (contract.attestation?.available == true) ...[
            const SizedBox(height: 8),
            AzButton(
              'Télécharger l’attestation',
              variant: AzButtonVariant.tonal,
              height: 50,
              radius: 16,
              icon: Icons.download_rounded,
              loading: _busy,
              onPressed: _download,
            ),
          ],
        ],
      ),
    );
  }
}

/// Attestation du contrat sous forme de fichier (PDF ou image), sans QR code.
class _AttestationFileCard extends StatefulWidget {
  const _AttestationFileCard({required this.contract});

  final Contract contract;

  @override
  State<_AttestationFileCard> createState() => _AttestationFileCardState();
}

class _AttestationFileCardState extends State<_AttestationFileCard> {
  bool _busy = false;

  Future<void> _download() async {
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadAttestation(widget.contract.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: 'attestation');
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.contract.attestation;
    final until = a?.validUntil;
    return AzCard(
      radius: 26,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('Attestation d’assurance', style: AzText.titleSmall),
          const SizedBox(height: 4),
          Text(
            until == null ? 'Document remis par AssurZen.' : 'Valable jusqu’au ${formatDate(until)}.',
            style: AzText.bodyMedium.copyWith(color: AzColors.muted),
          ),
          const SizedBox(height: 12),
          AzButton('Télécharger l’attestation', height: 50, radius: 16, icon: Icons.download_rounded, loading: _busy, onPressed: _download),
        ],
      ),
    );
  }
}

class _DocumentRow extends StatefulWidget {
  const _DocumentRow({required this.contract, required this.document});

  final Contract contract;
  final ContractDocument document;

  @override
  State<_DocumentRow> createState() => _DocumentRowState();
}

class _DocumentRowState extends State<_DocumentRow> {
  bool _busy = false;

  Contract get contract => widget.contract;
  ContractDocument get document => widget.document;

  Future<void> _download() async {
    final d = document;
    // Lien fourni tel quel par un ancien serveur ; sinon téléchargement avec le jeton de session.
    if (d.url != null) {
      Launch.url(context, d.url!);
      return;
    }
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadDocument(contract.id, d.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: d.name);
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = document;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          const AzIconTile(icon: Icons.description_outlined),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(d.name, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700, fontSize: 14)),
                const SizedBox(height: 4),
                AzStatusBadge.document(d.status),
              ],
            ),
          ),
          if (d.isAvailable) ...[
            _busy
                ? const SizedBox(width: 44, height: 44, child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator(strokeWidth: 2.5)))
                : _SmallAction(icon: Icons.download_rounded, tooltip: 'Télécharger ${d.name}', onTap: _download),
            const SizedBox(width: 6),
            _SmallAction(
              icon: Icons.share_outlined,
              tooltip: 'Partager ${d.name}',
              // Décision 5.5 : lien sécurisé à durée limitée (48 h), généré par le serveur.
              // Le lien sécurisé de 48 h est généré par le serveur (pas encore disponible) : jamais de faux lien.
              onTap: () => showAzSnack(context, 'Le partage sécurisé sera disponible prochainement.'),
            ),
          ],
        ],
      ),
    );
  }
}

class _SmallAction extends StatelessWidget {
  const _SmallAction({required this.icon, required this.tooltip, required this.onTap});

  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Material(
        color: AzColors.blue50,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: onTap,
          child: SizedBox(width: 44, height: 44, child: Icon(icon, size: 20, color: AzColors.blue700)),
        ),
      ),
    );
  }
}
