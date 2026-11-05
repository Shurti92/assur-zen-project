import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/engagement.dart';
import '../shell/main_shell.dart';
import 'engagement_card.dart';

/// Détail du score de protection : jauge, critères réalisés ou à faire, retrait du consentement.
/// Libellés, aides et gestes viennent du serveur.
class ProtectionScorePage extends StatelessWidget {
  const ProtectionScorePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Ma protection'),
      body: SafeArea(
        top: false,
        child: AsyncView<ProtectionScore>(
          load: () => context.repos.engagement.getProtectionScore(),
          builder: (context, s, reload) => RefreshIndicator(
            onRefresh: reload,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32),
              children: [
                AzCard(
                  child: Row(
                    children: [
                      ScoreGauge(score: s.score, size: 88),
                      const SizedBox(width: 18),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('${s.score} sur 100', style: AzText.title),
                            if (s.updatedAt != null)
                              Text('Mis à jour ${formatRelative(s.updatedAt!)}', style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                for (final i in s.items) ...[_ItemTile(item: i), const SizedBox(height: 10)],
                const SizedBox(height: 14),
                Center(child: AzTextLink('Retirer mon accord', color: AzColors.muted, onPressed: () => _withdraw(context))),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _withdraw(BuildContext context) async {
    final confirmed = await showAzSheet<bool>(
      context,
      title: 'Retirer mon accord',
      builder: (ctx) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Le suivi s’arrête et vos données d’engagement sont effacées.', style: AzText.body),
          const SizedBox(height: 18),
          AzButton('Retirer mon accord', onPressed: () => Navigator.of(ctx).pop(true)),
          const SizedBox(height: 8),
          AzButton.secondary('Annuler', onPressed: () => Navigator.of(ctx).pop(false)),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    try {
      await context.repos.engagement.setConsent(accepted: false);
      if (!context.mounted) return;
      Navigator.of(context).pop();
      showAzSnack(context, 'Votre accord est retiré.');
    } catch (e) {
      if (context.mounted) showAzSnack(context, errorMessage(e));
    }
  }
}

class _ItemTile extends StatelessWidget {
  const _ItemTile({required this.item});

  final ScoreItem item;

  void _open(BuildContext context, ScoreAction a) {
    switch (a) {
      case ScoreAction.profile:
        context.resetTo(AppRoutes.shell, ShellTab.profile);
      case ScoreAction.documents || ScoreAction.renewal:
        context.resetTo(AppRoutes.shell, ShellTab.contracts);
      case ScoreAction.contacts || ScoreAction.support:
        context.push(AppRoutes.support);
    }
  }

  @override
  Widget build(BuildContext context) {
    final action = item.done ? null : item.action;
    return AzCard(
      onTap: action == null ? null : () => _open(context, action),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          Icon(
            item.done ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
            color: item.done ? AzColors.success : AzColors.muted,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.label, style: AzText.body.copyWith(fontWeight: FontWeight.w700)),
                if (!item.done && item.hint != null) Text(item.hint!, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
              ],
            ),
          ),
          if (action != null) const Icon(Icons.chevron_right_rounded, color: AzColors.blue700),
        ],
      ),
    );
  }
}
