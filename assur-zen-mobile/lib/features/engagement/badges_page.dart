import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/engagement.dart';

/// Icône d'un badge selon son code stable (présentation seulement : libellé et description
/// viennent du serveur). Un code inconnu reçoit une icône générique.
IconData badgeIcon(String code) => switch (code) {
      'PROFILE_COMPLETE' => Icons.person_outline_rounded,
      'PHONE_VERIFIED' => Icons.phone_android_rounded,
      'ATTESTATION_DOWNLOADED' => Icons.download_done_rounded,
      'PAID_BEFORE_DUE' => Icons.event_available_rounded,
      'FEEDBACK_GIVEN' => Icons.star_outline_rounded,
      'QUIZ_STREAK_7' => Icons.local_fire_department_outlined,
      'QUIZ_STREAK_30' => Icons.emoji_events_outlined,
      _ => Icons.military_tech_outlined,
    };

/// « Mes badges » : obtenus en premier, puis ceux à gagner avec leur progression.
class BadgesPage extends StatelessWidget {
  const BadgesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Mes badges'),
      body: SafeArea(
        top: false,
        child: AsyncView<List<AppBadge>>(
          load: () => context.repos.engagement.getBadges(),
          isEmpty: (items) => items.isEmpty,
          empty: const AzEmptyState(icon: Icons.military_tech_outlined, title: 'Aucun badge pour le moment', message: ''),
          builder: (context, items, reload) {
            // Obtenus d'abord (les plus récents en tête), puis à gagner dans l'ordre du serveur.
            final earned = [for (final b in items) if (b.earned) b]
              ..sort((a, b) => (b.earnedAt ?? DateTime(0)).compareTo(a.earnedAt ?? DateTime(0)));
            final todo = [for (final b in items) if (!b.earned) b];
            return RefreshIndicator(
              onRefresh: reload,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32),
                children: [
                  Text('${earned.length} sur ${items.length}', style: AzText.title),
                  const SizedBox(height: 14),
                  for (final b in [...earned, ...todo]) ...[BadgeTile(badge: b), const SizedBox(height: 10)],
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class BadgeTile extends StatelessWidget {
  const BadgeTile({super.key, required this.badge});

  final AppBadge badge;

  @override
  Widget build(BuildContext context) {
    final b = badge;
    final p = b.progress;
    return AzCard(
      padding: const EdgeInsets.all(14),
      semanticLabel: '${b.label}, ${b.earned ? 'obtenu' : 'à gagner'}',
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AzIconTile(
            icon: badgeIcon(b.id),
            size: 48,
            background: b.earned ? AzColors.successBg : AzColors.background,
            color: b.earned ? AzColors.success : AzColors.muted,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(b.label, style: AzText.body.copyWith(fontWeight: FontWeight.w800, color: b.earned ? AzColors.ink : AzColors.ink2)),
                if (b.description != null) ...[
                  const SizedBox(height: 2),
                  Text(b.description!, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                ],
                if (b.earned && b.earnedAt != null) ...[
                  const SizedBox(height: 4),
                  Text('Obtenu le ${formatDate(b.earnedAt)}', style: AzText.caption.copyWith(color: AzColors.success)),
                ] else if (!b.earned && p != null) ...[
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: LinearProgressIndicator(value: p.ratio, minHeight: 6, color: AzColors.blue700, backgroundColor: AzColors.blue100),
                  ),
                  const SizedBox(height: 4),
                  Text('${p.current} sur ${p.target}', style: AzText.caption),
                ],
              ],
            ),
          ),
          if (b.earned) const Icon(Icons.check_circle_rounded, color: AzColors.success),
        ],
      ),
    );
  }
}
