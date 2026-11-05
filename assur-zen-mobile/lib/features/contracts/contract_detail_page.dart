import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
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
                const SizedBox(height: 20),
                _AttestationCard(contract: c),
              ],
              const SizedBox(height: 24),
              const AzSectionHeader('Documents'),
              const SizedBox(height: 8),
              if (c.documents.isEmpty)
                AzCard(
                  child: Text('Aucun document disponible pour le moment.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                )
              else
                for (final d in c.documents) ...[_DocumentRow(contract: c, document: d), const SizedBox(height: 10)],
              const SizedBox(height: 14),
              const AzSectionHeader('Garanties', trailing: Env.useMock ? AzStatusBadge.pending('Exemple') : null),
              const SizedBox(height: 8),
              if (c.guarantees.isEmpty)
                AzCard(
                  child: Text(
                    'Le détail de vos garanties n’est pas encore disponible dans l’application. Votre conseiller vous le communique sur demande.',
                    style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                  ),
                )
              else
                AzCard(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Column(
                    children: [
                      for (var i = 0; i < c.guarantees.length; i++)
                        AzInfoRow(
                          c.guarantees[i].name,
                          c.guarantees[i].kindLabel,
                          last: i == c.guarantees.length - 1,
                          valueColor: switch (c.guarantees[i].kind) {
                            GuaranteeKind.obligatoire || GuaranteeKind.incluse => AzColors.success,
                            _ => AzColors.warning,
                          },
                        ),
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
                      height: 48,
                      onPressed: c.actions.canDeclareClaim ? () => context.push(AppRoutes.claimNew, c.id) : null,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: AzButton.secondary(
                      'Contacter',
                      height: 48,
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
    final when = days == null ? 'Échéance proche. ' : (days == 0 ? 'Échéance aujourd’hui. ' : 'Échéance dans $days jours. ');
    return [
      const SizedBox(height: 14),
      Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(color: AzColors.warningBg, borderRadius: BorderRadius.circular(14)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text.rich(
              TextSpan(children: [
                TextSpan(text: when, style: const TextStyle(fontWeight: FontWeight.w800)),
                const TextSpan(text: 'Renouvelez pour rester couvert sans interruption.'),
              ]),
              style: AzText.bodyMedium.copyWith(color: AzColors.warningInk),
            ),
            if (r.amount != null) ...[
              const SizedBox(height: 4),
              Text('Prime de renouvellement : ${formatFcfa(r.amount!)}', style: AzText.label.copyWith(color: AzColors.warningInk)),
            ],
            const SizedBox(height: 10),
            AzButton(
              'Renouveler',
              icon: Icons.autorenew_rounded,
              height: 46,
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

class _Summary extends StatelessWidget {
  const _Summary({required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    return AzHeroCard(
      showDot: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AzIconTile(icon: productIcon(c.productId), size: 48, background: const Color(0x24FFFFFF), color: Colors.white),
              const Spacer(),
              AzStatusBadge.contract(c.status),
            ],
          ),
          const SizedBox(height: 14),
          Text(c.name, style: AzText.title.copyWith(color: Colors.white, fontSize: 22)),
          Text(c.insuredObject, style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
          const Divider(height: 28, color: Color(0x2EFFFFFF)),
          Row(
            children: [
              Expanded(child: _Light('N° de contrat', c.number)),
              Expanded(child: _Light('Prime', c.premium == null ? 'À confirmer' : formatFcfa(c.premium!))),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _Light('Prise d’effet', formatDate(c.startDate))),
              Expanded(child: _Light('Échéance', formatDate(c.endDate))),
            ],
          ),
        ],
      ),
    );
  }
}

class _Light extends StatelessWidget {
  const _Light(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AzText.caption.copyWith(color: AzColors.blue100, fontWeight: FontWeight.w500)),
        const SizedBox(height: 2),
        Text(value, style: AzText.label.copyWith(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 14)),
      ],
    );
  }
}

class _AttestationCard extends StatelessWidget {
  const _AttestationCard({required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              QrImageView(
                data: contract.verificationCode!,
                size: 88,
                padding: EdgeInsets.zero,
                semanticsLabel: 'QR code de vérification de l’attestation',
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Attestation numérique', style: AzText.titleSmall),
                    const SizedBox(height: 4),
                    Text('QR code vérifiable lors des contrôles routiers. Valide jusqu’au ${formatDate(contract.endDate)}.',
                        style: AzText.caption.copyWith(fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w500)),
                    const SizedBox(height: 6),
                    const AzStatusBadge(
                      label: 'Disponible hors connexion',
                      foreground: AzColors.success,
                      background: AzColors.successBg,
                      dot: false,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          AzButton('Présenter l’attestation', height: 46, icon: Icons.qr_code_2_rounded, onPressed: () => context.push(AppRoutes.attestation, contract)),
        ],
      ),
    );
  }
}

class _DocumentRow extends StatelessWidget {
  const _DocumentRow({required this.contract, required this.document});

  final Contract contract;
  final ContractDocument document;

  @override
  Widget build(BuildContext context) {
    final d = document;
    return AzCard(
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const AzIconTile(icon: Icons.description_outlined, background: AzColors.background, color: AzColors.ink2),
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
            _SmallAction(
              icon: Icons.download_rounded,
              tooltip: 'Télécharger ${d.name}',
              onTap: () {
                if (d.url != null) {
                  Launch.url(context, d.url!);
                } else {
                  showAzSnack(context, 'Le téléchargement sera disponible prochainement.');
                }
              },
            ),
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
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: SizedBox(width: 44, height: 44, child: Icon(icon, size: 20, color: AzColors.blue700)),
        ),
      ),
    );
  }
}
