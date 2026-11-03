import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Suivi d’un dossier : frise des étapes (statuts réels de l’API) et historique.
class ClaimDetailPage extends StatelessWidget {
  const ClaimDetailPage({super.key, required this.claimId});

  final String claimId;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Suivi du dossier'),
      body: AsyncView<Claim>(
        load: () => context.repos.claims.getClaim(claimId),
        loading: const AzSkeletonList(count: 3),
        builder: (context, k, reload) => RefreshIndicator(
          onRefresh: reload,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32),
            children: [
              AzCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [Expanded(child: Text(k.type, style: AzText.section)), AzStatusBadge.claim(k)]),
                    const SizedBox(height: 4),
                    Text(k.contractLabel, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                    const Divider(height: 22, color: AzColors.lineSoft),
                    Row(
                      children: [
                        Text('Dossier ', style: AzText.caption.copyWith(fontSize: 13)),
                        Text(k.number, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w800)),
                        const Spacer(),
                        Text(formatDate(k.declaredAt), style: AzText.caption.copyWith(fontSize: 13)),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              const AzSectionHeader('Avancement'),
              const SizedBox(height: 12),
              ClaimTimeline(steps: k.steps, current: k.currentStep, closed: k.closed),
              const SizedBox(height: 20),
              const AzSectionHeader('Historique'),
              const SizedBox(height: 8),
              for (final h in k.history) ...[
                AzCard(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${formatDate(h.date)} · ${formatTime(h.date)}', style: AzText.caption),
                      const SizedBox(height: 4),
                      Text(h.text, style: AzText.bodyMedium.copyWith(color: AzColors.ink)),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(child: AzButton.secondary('Appeler', icon: Icons.call_outlined, height: 48, onPressed: () => Launch.phone(context))),
                  const SizedBox(width: 10),
                  Expanded(
                    child: AzButton(
                      'WhatsApp',
                      icon: Icons.chat_outlined,
                      height: 48,
                      onPressed: () => Launch.whatsapp(context, message: 'Bonjour AssurZen, je vous écris au sujet du dossier ${k.number}.'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Frise chronologique verticale.
class ClaimTimeline extends StatelessWidget {
  const ClaimTimeline({super.key, required this.steps, required this.current, this.closed = false});

  final List<String> steps;
  final int current;
  final bool closed;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var i = 0; i < steps.length; i++)
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 24,
                  child: Column(
                    children: [
                      _Dot(done: i < current || closed, current: i == current && !closed),
                      if (i < steps.length - 1)
                        Expanded(
                          child: Container(
                            width: 2,
                            margin: const EdgeInsets.symmetric(vertical: 2),
                            color: i < current || closed ? AzColors.blue700 : AzColors.fieldBorder,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 18),
                    child: Semantics(
                      label: '${steps[i]} : ${i < current || closed ? 'terminé' : i == current ? 'en cours' : 'à venir'}',
                      excludeSemantics: true,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            steps[i],
                            style: AzText.body.copyWith(
                              fontSize: 15,
                              fontWeight: i == current && !closed ? FontWeight.w800 : (i < current || closed ? FontWeight.w700 : FontWeight.w600),
                              color: i == current && !closed ? AzColors.blue700 : (i < current || closed ? AzColors.ink : AzColors.muted),
                            ),
                          ),
                          Text(
                            i < current || closed ? 'Terminé' : (i == current ? 'En cours' : 'À venir'),
                            style: AzText.caption.copyWith(fontSize: 13, fontWeight: FontWeight.w500),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot({required this.done, required this.current});

  final bool done;
  final bool current;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 24,
      height: 24,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: done ? AzColors.blue700 : Colors.white,
        border: done ? null : Border.all(color: current ? AzColors.blue700 : AzColors.fieldBorder, width: current ? 3 : 2),
      ),
      child: done ? const Icon(Icons.check_rounded, size: 14, color: Colors.white) : null,
    );
  }
}
