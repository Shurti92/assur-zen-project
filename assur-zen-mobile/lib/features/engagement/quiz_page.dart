import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/engagement.dart';

/// Quiz du jour. La question, les choix, la bonne réponse et l'explication viennent du back-office :
/// l'application n'embarque aucune question et ne connaît pas la bonne réponse avant d'avoir répondu.
class QuizPage extends StatefulWidget {
  const QuizPage({super.key});

  @override
  State<QuizPage> createState() => _QuizPageState();
}

class _QuizPageState extends State<QuizPage> {
  late Future<QuizQuestion?> _future = _load();
  String? _selected;
  bool _sending = false;
  String? _error;
  QuizAnswer? _answer;

  Future<QuizQuestion?> _load() => context.repos.engagement.getQuizToday();

  void _reload() => setState(() {
        _future = _load();
        _selected = null;
        _answer = null;
        _error = null;
      });

  Future<void> _send(QuizQuestion q) async {
    final choice = _selected;
    if (choice == null || _sending) return;
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final a = await context.repos.engagement.answerQuiz(questionId: q.id, choiceId: choice);
      if (mounted) setState(() => _answer = a);
    } on ApiException catch (e) {
      if (!mounted) return;
      if (e.code == 'QUIZ_QUESTION_NOT_CURRENT') {
        // La journée a changé pendant la lecture : on recharge la question du jour.
        showAzSnack(context, 'La question a changé. Voici celle d’aujourd’hui.');
        _reload();
      } else {
        setState(() => _error = errorMessage(e));
      }
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AzColors.background,
      appBar: const AzTopBar(title: 'Quiz du jour'),
      body: SafeArea(
        top: false,
        child: FutureBuilder<QuizQuestion?>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) return const AzSkeletonList(count: 3);
            if (snap.hasError) return AzErrorState(message: errorMessage(snap.error!), onRetry: _reload);
            final q = snap.data;
            if (q == null) {
              return const AzEmptyState(icon: Icons.quiz_outlined, title: 'Pas de question pour le moment', message: '');
            }
            return ListView(
              padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32),
              children: _body(q),
            );
          },
        ),
      ),
    );
  }

  List<Widget> _body(QuizQuestion q) {
    // Résultat à montrer : celui qui vient d'être reçu, ou celui que le serveur rappelle (déjà répondu).
    final result = _answer?.result ?? (q.answered ? q.result : null);
    final streak = _answer?.streak ?? q.streak;
    return [
      _StreakBanner(streak: streak),
      const SizedBox(height: 16),
      if (q.theme != null) Text(q.theme!.toUpperCase(), style: AzText.overline),
      const SizedBox(height: 4),
      Text(q.question, style: AzText.title),
      const SizedBox(height: 16),
      for (final c in q.choices) ...[
        _ChoiceTile(
          choice: c,
          selected: _selected == c.id || result?.choiceId == c.id,
          state: result == null ? null : _stateOf(c, result),
          onTap: result != null || _sending ? null : () => setState(() => _selected = c.id),
        ),
        const SizedBox(height: 10),
      ],
      if (_error != null) ...[
        const SizedBox(height: 4),
        AzBanner(kind: AzBannerKind.error, text: _error!),
        const SizedBox(height: 10),
      ],
      if (result == null)
        AzButton('Valider', loading: _sending, onPressed: _selected == null ? null : () => _send(q))
      else
        _ResultCard(result: result),
    ];
  }

  _ChoiceState _stateOf(QuizChoice c, QuizResult r) {
    if (c.id == r.correctChoiceId) return _ChoiceState.correct;
    if (c.id == r.choiceId && !r.correct) return _ChoiceState.wrong;
    return _ChoiceState.neutral;
  }
}

enum _ChoiceState { correct, wrong, neutral }

class _StreakBanner extends StatelessWidget {
  const _StreakBanner({required this.streak});

  final QuizStreak streak;

  @override
  Widget build(BuildContext context) {
    // Rien à montrer tant qu'il n'y a pas de série : pas de « 0 jour » décourageant.
    if (streak.current <= 0 && streak.best <= 0) return const SizedBox.shrink();
    return AzCard(
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const AzIconTile(icon: Icons.local_fire_department_outlined, size: 44, background: AzColors.orange50, color: AzColors.orange700),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${streak.current} ${streak.current > 1 ? 'jours' : 'jour'} de suite', style: AzText.titleSmall),
                if (streak.best > streak.current) Text('Meilleure série : ${streak.best}', style: AzText.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChoiceTile extends StatelessWidget {
  const _ChoiceTile({required this.choice, required this.selected, required this.onTap, this.state});

  final QuizChoice choice;
  final bool selected;
  final VoidCallback? onTap;
  final _ChoiceState? state;

  @override
  Widget build(BuildContext context) {
    final (border, bg, icon) = switch (state) {
      _ChoiceState.correct => (AzColors.success, AzColors.successBg, Icons.check_circle_rounded),
      _ChoiceState.wrong => (AzColors.error, AzColors.errorBg, Icons.cancel_rounded),
      _ => (selected ? AzColors.blue700 : AzColors.fieldBorder, selected ? const Color(0xFFF2F5FC) : Colors.white, null),
    };
    final tint = switch (state) {
      _ChoiceState.correct => AzColors.success,
      _ChoiceState.wrong => AzColors.error,
      _ => AzColors.blue700,
    };
    return Semantics(
      button: onTap != null,
      selected: selected,
      child: Material(
        color: bg,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AzRadius.button),
          side: BorderSide(color: border, width: selected || state != null ? 2 : 1.5),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(child: Text(choice.label, style: AzText.body.copyWith(fontWeight: FontWeight.w600))),
                if (icon != null) Icon(icon, color: tint),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ResultCard extends StatelessWidget {
  const _ResultCard({required this.result});

  final QuizResult result;

  @override
  Widget build(BuildContext context) {
    final ok = result.correct;
    final kind = ok ? AzBannerKind.success : AzBannerKind.warning;
    final verdict = ok ? 'Bonne réponse' : 'Pas tout à fait';
    // L'explication est celle du serveur ; absente, on n'écrit rien à sa place (ni promesse de question demain).
    final explanation = result.explanation;
    return explanation == null ? AzBanner(kind: kind, text: verdict) : AzBanner(kind: kind, title: verdict, text: explanation);
  }
}
