import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/engagement.dart';
import '../../data/models/loyalty.dart';

/// Carte « Ma protection » de l'accueil.
///
/// Elle reste invisible tant que le serveur ne l'active pas, et en cas d'erreur : l'engagement est
/// facultatif, il ne doit jamais gêner l'accueil. Sans consentement, elle propose de l'accorder ;
/// avec consentement, elle montre le score et le premier geste à faire.
class EngagementCard extends StatefulWidget {
  const EngagementCard({super.key});

  @override
  State<EngagementCard> createState() => _EngagementCardState();
}

class _EngagementCardState extends State<EngagementCard> {
  Engagement? _engagement;
  ProtectionScore? _score;
  List<AppBadge>? _badges;
  QuizQuestion? _quiz;
  LoyaltyOverview? _loyalty;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final repo = context.repos.engagement;
      final loyaltyRepo = context.repos.loyalty;
      final e = await repo.getEngagement();
      ProtectionScore? score;
      List<AppBadge>? badges;
      if (e.showsScore && e.consent.accepted) {
        try {
          score = await repo.getProtectionScore();
        } on ApiException {
          score = null; // consentement retiré entre-temps, ou fonction coupée : la carte se replie
        }
      }
      if (e.showsBadges && e.consent.accepted) {
        try {
          badges = await repo.getBadges();
        } on ApiException {
          badges = null;
        }
      }
      // Zen Points : facultatif, une erreur ou l'absence du programme masque seulement la ligne.
      LoyaltyOverview? loyalty;
      if (e.consent.accepted) {
        try {
          loyalty = await loyaltyRepo.getOverview();
        } catch (_) {
          loyalty = null;
        }
      }
      QuizQuestion? quiz;
      if (e.showsQuiz && e.consent.accepted) {
        try {
          quiz = await repo.getQuizToday();
        } on ApiException {
          quiz = null; // pas de question, fonction coupée, accord retiré : la carte n'apparaît pas
        }
      }
      if (!mounted) return;
      setState(() {
        _engagement = e;
        _score = score;
        _badges = badges;
        _quiz = quiz;
        _loyalty = loyalty;
        _loading = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _engagement = null;
          _loading = false;
        });
      }
    }
  }

  Future<void> _askConsent() async {
    final e = _engagement;
    if (e == null) return;
    final accepted = await showConsentSheet(context, e);
    if (accepted == true && mounted) {
      setState(() => _loading = true);
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final e = _engagement;
    if (_loading || e == null || !e.showsAnything) return const SizedBox.shrink();

    // Pas de consentement : on le propose seulement si le serveur fournit son texte.
    if (!e.consent.accepted) {
      if (!e.canAskConsent) return const SizedBox.shrink();
      return Padding(
        padding: const EdgeInsets.fromLTRB(AzSpace.screen, 22, AzSpace.screen, 0),
        child: AzCard(
          color: AzColors.blue50,
          borderColor: null,
          radius: 26,
          onTap: _askConsent,
          semanticLabel: 'Ma protection : activer',
          child: Row(
            children: [
              const AzIconTile(icon: Icons.shield_moon_outlined, size: 44),
              const SizedBox(width: 14),
              const Expanded(child: Text('Ma protection', style: AzText.titleSmall)),
              Text('Activer', style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w800)),
              const Icon(Icons.chevron_right_rounded, color: AzColors.blue700),
            ],
          ),
        ),
      );
    }

    final score = _score;
    final rows = <Widget>[
      if (score != null) _scoreRow(context, score),
      if (_quiz case final quiz?) _quizRow(context, quiz),
      if (_badges case final badges? when badges.isNotEmpty) _badgesRow(context, badges),
      if (_loyalty case final loyalty?) _loyaltyRow(context, loyalty),
    ];
    if (rows.isEmpty) return const SizedBox.shrink();

    // Une seule carte à lignes sous le titre « Ma protection » : score, quiz du jour, badges.
    return Padding(
      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 22, AzSpace.screen, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Semantics(header: true, child: Text('Ma protection', style: AzText.section.copyWith(fontSize: 17))),
          ),
          AzCard(
            radius: 26,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Column(
              children: [
                for (var i = 0; i < rows.length; i++) ...[if (i > 0) const Divider(height: 1, color: AzColors.lineSoft), rows[i]],
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Ligne cliquable de la carte : icône ou jauge, deux lignes de texte, élément de fin.
  Widget _row({required String label, required VoidCallback onTap, required Widget leading, required Widget middle, Widget? trailing}) {
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Row(
            children: [
              leading,
              const SizedBox(width: 14),
              Expanded(child: middle),
              if (trailing != null) ...[const SizedBox(width: 8), trailing],
            ],
          ),
        ),
      ),
    );
  }

  static const _chevron = Icon(Icons.chevron_right_rounded, color: AzColors.muted, size: 22);

  Widget _scoreRow(BuildContext context, ProtectionScore score) {
    final next = score.todo.isEmpty ? null : score.todo.first;
    final more = score.todo.length;
    return _row(
      label: 'Ma protection : ${score.score} %',
      onTap: () => context.push(AppRoutes.protectionScore),
      leading: ScoreGauge(score: score.score, size: 64),
      middle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(next?.label ?? 'Tout est en règle', maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.titleSmall.copyWith(fontSize: 15)),
          const SizedBox(height: 2),
          Text(
            '${score.score} sur 100${more > 0 ? ' · $more action${more > 1 ? 's' : ''} pour progresser' : ''}',
            style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500),
          ),
        ],
      ),
      trailing: _chevron,
    );
  }

  Widget _quizRow(BuildContext context, QuizQuestion quiz) {
    // Pas encore répondu : on invite ; déjà répondu : on rappelle la série (rien d'écrit en dur sur demain).
    final streak = quiz.streak.current;
    return _row(
      label: 'Quiz du jour',
      onTap: () async {
        await context.push(AppRoutes.quiz);
        if (mounted) _load();
      },
      leading: const AzIconTile(icon: Icons.quiz_outlined, size: 44),
      middle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Quiz du jour', style: AzText.titleSmall.copyWith(fontSize: 15)),
          if (!quiz.answered)
            Text(quiz.theme ?? quiz.question, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500))
          else if (streak > 0)
            Text('$streak ${streak > 1 ? 'jours' : 'jour'} de suite', style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
        ],
      ),
      trailing: quiz.answered
          ? const Row(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.check_circle_rounded, color: AzColors.success, size: 22), _chevron])
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [Text('Jouer', style: AzText.label.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w800)), _chevron],
            ),
    );
  }

  Widget _loyaltyRow(BuildContext context, LoyaltyOverview o) {
    final level = o.level;
    final subtitle = o.joined
        ? [if (level != null) 'Niveau ${level.label}', '${o.balance} point${(o.balance ?? 0) > 1 ? 's' : ''}'].join(' · ')
        : 'Rejoindre les Zen Points';
    return _row(
      label: 'Zen Points : $subtitle',
      onTap: () async {
        await context.push(AppRoutes.loyalty);
        if (mounted) _load();
      },
      leading: const AzIconTile(icon: Icons.stars_rounded, size: 44),
      middle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Zen Points', style: AzText.titleSmall.copyWith(fontSize: 15)),
          Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(fontSize: 12.5, fontWeight: FontWeight.w500)),
        ],
      ),
      trailing: _chevron,
    );
  }

  Widget _badgesRow(BuildContext context, List<AppBadge> badges) {
    final earned = badges.where((b) => b.earned).length;
    return _row(
      label: 'Mes badges : $earned sur ${badges.length}',
      onTap: () => context.push(AppRoutes.badges),
      leading: const AzIconTile(icon: Icons.military_tech_outlined, size: 44),
      middle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Mes badges · $earned sur ${badges.length}', style: AzText.titleSmall.copyWith(fontSize: 15)),
          const SizedBox(height: 8),
          AzSegments(total: badges.length, filled: earned, height: 5),
        ],
      ),
      trailing: _chevron,
    );
  }
}

/// Jauge circulaire du score (0 à 100).
class ScoreGauge extends StatelessWidget {
  const ScoreGauge({super.key, required this.score, this.size = 64});

  final int score;
  final double size;

  @override
  Widget build(BuildContext context) {
    final value = score.clamp(0, 100) / 100;
    return Semantics(
      label: 'Score $score sur 100',
      child: SizedBox(
        width: size,
        height: size,
        child: Stack(
          alignment: Alignment.center,
          children: [
            SizedBox.expand(
              child: CircularProgressIndicator(
                value: value,
                strokeWidth: size / 9,
                strokeCap: StrokeCap.round,
                color: AzColors.blue700,
                backgroundColor: AzColors.blue100,
              ),
            ),
            Text('$score', style: AzText.amount.copyWith(fontSize: size * 0.32, height: 1)),
          ],
        ),
      ),
    );
  }
}

/// Feuille de consentement : affiche le texte du serveur tel quel et enregistre la version lue.
/// Renvoie `true` si le client accepte et que le serveur l'enregistre.
Future<bool?> showConsentSheet(BuildContext context, Engagement e) {
  return showAzSheet<bool>(
    context,
    title: 'Votre accord',
    builder: (_) => _ConsentSheet(engagement: e),
  );
}

class _ConsentSheet extends StatefulWidget {
  const _ConsentSheet({required this.engagement});

  final Engagement engagement;

  @override
  State<_ConsentSheet> createState() => _ConsentSheetState();
}

class _ConsentSheetState extends State<_ConsentSheet> {
  late Engagement _e = widget.engagement;
  bool _busy = false;
  String? _notice;

  Future<void> _accept() async {
    setState(() {
      _busy = true;
      _notice = null;
    });
    try {
      await context.repos.engagement.setConsent(accepted: true, consentVersion: _e.consentVersion);
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      if (e.code == 'CONSENT_VERSION_OUTDATED') {
        // Le texte a changé entre l'affichage et l'envoi : on le relit avant d'accepter.
        try {
          final fresh = await context.repos.engagement.getEngagement();
          if (mounted) setState(() => _e = fresh);
        } catch (_) {
          // le message ci-dessous suffit : le client réessaiera
        }
        if (mounted) setState(() => _notice = 'Le texte a été mis à jour. Relisez-le avant d’accepter.');
      } else {
        setState(() => _notice = errorMessage(e));
      }
    } catch (e) {
      if (mounted) setState(() => _notice = errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = _e.consentText;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (text != null) Text(text, style: AzText.body),
        if (_notice != null) ...[
          const SizedBox(height: 12),
          AzBanner(kind: AzBannerKind.warning, text: _notice!),
        ],
        const SizedBox(height: 18),
        AzButton('J’accepte', loading: _busy, onPressed: text == null ? null : _accept),
        const SizedBox(height: 8),
        AzButton.secondary('Plus tard', onPressed: _busy ? null : () => Navigator.of(context).pop(false)),
      ],
    );
  }
}
