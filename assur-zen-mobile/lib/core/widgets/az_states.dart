import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';
import 'az_buttons.dart';

/// Bloc gris animé affiché pendant un chargement (> 300 ms).
class AzSkeleton extends StatefulWidget {
  const AzSkeleton({super.key, this.width, this.height = 14, this.radius = 8});

  final double? width;
  final double height;
  final double radius;

  @override
  State<AzSkeleton> createState() => _AzSkeletonState();
}

class _AzSkeletonState extends State<AzSkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1100))
    ..repeat(reverse: true);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween(begin: 0.55, end: 1.0).animate(_c),
      child: Container(
        width: widget.width,
        height: widget.height,
        decoration: BoxDecoration(color: AzColors.skeleton, borderRadius: BorderRadius.circular(widget.radius)),
      ),
    );
  }
}

/// Liste de cartes squelettes (chargement d’une liste).
class AzSkeletonList extends StatelessWidget {
  const AzSkeletonList({super.key, this.count = 3, this.padding = const EdgeInsets.all(AzSpace.screen)});

  final int count;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Chargement en cours',
      child: ListView.separated(
        padding: padding,
        physics: const NeverScrollableScrollPhysics(),
        shrinkWrap: true,
        itemCount: count,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (_, __) => Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AzRadius.card),
            border: Border.all(color: AzColors.line),
          ),
          child: const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                AzSkeleton(width: 44, height: 44, radius: 12),
                SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    AzSkeleton(width: 140),
                    SizedBox(height: 8),
                    AzSkeleton(width: 200, height: 12),
                  ]),
                ),
              ]),
              SizedBox(height: 14),
              AzSkeleton(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}

/// État vide : icône, titre, message, action facultative.
class AzEmptyState extends StatelessWidget {
  const AzEmptyState({super.key, required this.icon, required this.title, required this.message, this.actionLabel, this.onAction});

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 48),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(color: AzColors.blue50, borderRadius: BorderRadius.circular(26)),
              child: Icon(icon, size: 38, color: AzColors.blue700),
            ),
            const SizedBox(height: 16),
            Text(title, style: AzText.title.copyWith(fontSize: 18), textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, style: AzText.bodyMedium.copyWith(color: AzColors.muted), textAlign: TextAlign.center),
            if (actionLabel != null) ...[
              const SizedBox(height: 20),
              AzButton(actionLabel!, onPressed: onAction, expand: false),
            ],
          ],
        ),
      ),
    );
  }
}

/// État d’erreur avec bouton « Réessayer ».
class AzErrorState extends StatelessWidget {
  const AzErrorState({super.key, required this.message, required this.onRetry, this.offline = false});

  final String message;
  final VoidCallback onRetry;
  final bool offline;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 48),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(color: AzColors.errorBg, borderRadius: BorderRadius.circular(28)),
              child: Icon(offline ? Icons.wifi_off_rounded : Icons.error_outline, size: 40, color: AzColors.error),
            ),
            const SizedBox(height: 16),
            Text(
              offline ? 'Vous êtes hors connexion' : 'Impossible de charger',
              style: AzText.title,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(message, style: AzText.body.copyWith(color: AzColors.muted), textAlign: TextAlign.center),
            const SizedBox(height: 20),
            AzButton('Réessayer', onPressed: onRetry, expand: false, icon: Icons.refresh_rounded),
          ],
        ),
      ),
    );
  }
}

/// Indicateur de chargement plein écran.
class AzLoader extends StatelessWidget {
  const AzLoader({super.key, this.message});

  final String? message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 44,
            height: 44,
            child: CircularProgressIndicator(strokeWidth: 4, color: AzColors.blue700, backgroundColor: AzColors.blue100),
          ),
          if (message != null) ...[
            const SizedBox(height: 16),
            Text(message!, style: AzText.section, textAlign: TextAlign.center),
          ],
        ],
      ),
    );
  }
}
