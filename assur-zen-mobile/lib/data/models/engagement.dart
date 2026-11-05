/// Engagement : consentement et score de protection (vague 1).
///
/// Tout vient du serveur. Un champ absent ou vide n'est jamais remplacé par un texte local :
/// la rubrique correspondante est simplement masquée.
library;

DateTime? _date(Object? v) => v == null ? null : DateTime.tryParse(v.toString())?.toLocal();

String? _text(Object? v) {
  final s = v?.toString().trim();
  return (s == null || s.isEmpty) ? null : s;
}

/// Fonctions d'engagement que le serveur active une par une.
class EngagementFeatures {
  const EngagementFeatures({this.protectionScore = false, this.dailyQuiz = false, this.badges = false});

  final bool protectionScore;
  final bool dailyQuiz;
  final bool badges;

  static const none = EngagementFeatures();

  factory EngagementFeatures.fromJson(Object? v) {
    if (v is! Map) return none;
    return EngagementFeatures(
      protectionScore: v['protectionScore'] == true,
      dailyQuiz: v['dailyQuiz'] == true,
      badges: v['badges'] == true,
    );
  }
}

/// Consentement du client : version acceptée = preuve de ce qu'il a lu.
class EngagementConsent {
  const EngagementConsent({this.accepted = false, this.acceptedAt, this.acceptedVersion});

  final bool accepted;
  final DateTime? acceptedAt;
  final int? acceptedVersion;

  factory EngagementConsent.fromJson(Object? v) {
    if (v is! Map) return const EngagementConsent();
    return EngagementConsent(
      accepted: v['accepted'] == true,
      acceptedAt: _date(v['acceptedAt']),
      acceptedVersion: (v['acceptedVersion'] as num?)?.toInt(),
    );
  }
}

/// Réponse de `GET /mobile/engagement`.
class Engagement {
  const Engagement({
    required this.enabled,
    required this.consent,
    required this.features,
    this.consentText,
    this.consentVersion,
  });

  final bool enabled;
  final EngagementConsent consent;
  final EngagementFeatures features;

  /// Texte de consentement fourni par le serveur ; absent = on ne propose pas d'accepter.
  final String? consentText;
  final int? consentVersion;

  static const disabled = Engagement(enabled: false, consent: EngagementConsent(), features: EngagementFeatures.none);

  /// Le score est proposé : engagement actif, fonction active.
  bool get showsScore => enabled && features.protectionScore;

  /// Les badges sont proposés : engagement actif, fonction active.
  bool get showsBadges => enabled && features.badges;

  /// Le quiz est proposé : engagement actif, fonction active.
  bool get showsQuiz => enabled && features.dailyQuiz;

  /// Au moins une fonction visible : sinon l'accueil n'affiche rien.
  bool get showsAnything => showsScore || showsBadges || showsQuiz;

  /// On peut demander le consentement : le texte et sa version existent.
  bool get canAskConsent => consentText != null && consentVersion != null;

  factory Engagement.fromJson(Object? v) {
    if (v is! Map) return disabled;
    return Engagement(
      enabled: v['enabled'] == true,
      consent: EngagementConsent.fromJson(v['consent']),
      features: EngagementFeatures.fromJson(v['features']),
      consentText: _text(v['consentText']),
      consentVersion: (v['consentVersion'] as num?)?.toInt(),
    );
  }
}

/// Gestes de la liste fermée que l'application sait ouvrir. Tout autre code : pas de bouton.
enum ScoreAction { profile, contacts, documents, renewal, support }

ScoreAction? scoreActionFrom(Object? v) => switch (v?.toString()) {
      'PROFILE' => ScoreAction.profile,
      'CONTACTS' => ScoreAction.contacts,
      'DOCUMENTS' => ScoreAction.documents,
      'RENEWAL' => ScoreAction.renewal,
      'SUPPORT' => ScoreAction.support,
      _ => null,
    };

/// Un critère du score (libellé et aide viennent du serveur).
class ScoreItem {
  const ScoreItem({required this.id, required this.label, required this.done, this.hint, this.action});

  final String id; // code stable du critère
  final String label;
  final bool done;
  final String? hint;
  final ScoreAction? action;

  factory ScoreItem.fromJson(Map<String, dynamic> j) => ScoreItem(
        id: (j['id'] ?? '').toString(),
        label: (j['label'] ?? '').toString(),
        done: j['done'] == true,
        hint: _text(j['hint']),
        action: scoreActionFrom(j['action']),
      );
}

/// Réponse de `GET /mobile/protection-score`.
class ProtectionScore {
  const ProtectionScore({required this.score, required this.items, this.updatedAt});

  final int score; // 0 à 100, calculé par le serveur
  final List<ScoreItem> items;
  final DateTime? updatedAt;

  /// Critères restants (ceux qui proposent un geste).
  List<ScoreItem> get todo => [for (final i in items) if (!i.done) i];

  factory ProtectionScore.fromJson(Object? v) {
    final j = v is Map<String, dynamic> ? v : const <String, dynamic>{};
    final raw = (j['score'] as num?)?.round() ?? 0;
    return ProtectionScore(
      score: raw.clamp(0, 100),
      items: [
        for (final e in (j['items'] as List? ?? const []))
          if (e is Map<String, dynamic> && (e['label'] ?? '').toString().trim().isNotEmpty) ScoreItem.fromJson(e),
      ],
      updatedAt: _date(j['updatedAt']),
    );
  }
}

/// Progression vers un badge (série de jours, par exemple).
class BadgeProgress {
  const BadgeProgress({required this.current, required this.target});

  final int current;
  final int target;

  /// Part réalisée, entre 0 et 1 ; une cible nulle ne donne aucune barre.
  double get ratio => target <= 0 ? 0 : (current / target).clamp(0, 1).toDouble();

  static BadgeProgress? fromJson(Object? v) {
    if (v is! Map) return null;
    final target = (v['target'] as num?)?.toInt();
    if (target == null || target <= 0) return null;
    return BadgeProgress(current: ((v['current'] as num?)?.toInt() ?? 0).clamp(0, target), target: target);
  }
}

/// Un badge. Libellé et description viennent du serveur ; l'icône est choisie par le code (présentation).
class AppBadge {
  const AppBadge({required this.id, required this.label, required this.earned, this.description, this.earnedAt, this.progress});

  final String id; // code stable (PROFILE_COMPLETE, QUIZ_STREAK_7...)
  final String label;
  final String? description;
  final bool earned;
  final DateTime? earnedAt;
  final BadgeProgress? progress;

  factory AppBadge.fromJson(Map<String, dynamic> j) => AppBadge(
        id: (j['id'] ?? '').toString(),
        label: (j['label'] ?? '').toString(),
        description: _text(j['description']),
        earned: j['earned'] == true,
        earnedAt: _date(j['earnedAt']),
        progress: BadgeProgress.fromJson(j['progress']),
      );

  /// Réponse de `GET /mobile/badges` : un badge sans libellé est ignoré, jamais de texte de remplacement.
  static List<AppBadge> listFromJson(Object? v) {
    final items = v is Map ? v['items'] : v;
    if (items is! List) return const [];
    return [
      for (final e in items)
        if (e is Map<String, dynamic> && (e['label'] ?? '').toString().trim().isNotEmpty) AppBadge.fromJson(e),
    ];
  }
}

/// Série de jours de réponse au quiz, calculée par le serveur.
class QuizStreak {
  const QuizStreak({this.current = 0, this.best = 0});

  final int current;
  final int best;

  static const none = QuizStreak();

  factory QuizStreak.fromJson(Object? v) {
    if (v is! Map) return none;
    return QuizStreak(
      current: ((v['current'] as num?)?.toInt() ?? 0).clamp(0, 100000),
      best: ((v['best'] as num?)?.toInt() ?? 0).clamp(0, 100000),
    );
  }
}

/// Un choix de réponse. L'identifiant est une chaîne (celle du serveur), jamais un indice local.
class QuizChoice {
  const QuizChoice({required this.id, required this.label});

  final String id;
  final String label;
}

/// Résultat d'une réponse : la bonne réponse et l'explication viennent du serveur.
class QuizResult {
  const QuizResult({required this.choiceId, required this.correct, required this.correctChoiceId, this.explanation});

  /// Choix du client ; peut être absent si le serveur ne le rappelle pas.
  final String? choiceId;
  final bool correct;
  final String? correctChoiceId;
  final String? explanation;

  static QuizResult? fromJson(Object? v) {
    if (v is! Map) return null;
    return QuizResult(
      choiceId: _text(v['choiceId']),
      correct: v['correct'] == true,
      correctChoiceId: _text(v['correctChoiceId']),
      explanation: _text(v['explanation']),
    );
  }
}

/// Question du jour (`GET /mobile/quiz/today`). Le contenu est celui du back-office.
class QuizQuestion {
  const QuizQuestion({
    required this.id,
    required this.question,
    required this.choices,
    required this.answered,
    this.theme,
    this.result,
    this.streak = QuizStreak.none,
  });

  final String id;
  final String? theme;
  final String question;
  final List<QuizChoice> choices;

  /// Déjà répondu aujourd'hui : [result] redonne SON résultat, sans nouvelle réponse.
  final bool answered;
  final QuizResult? result;
  final QuizStreak streak;

  /// Une question sans texte ou avec moins de deux choix ne se joue pas : on la masque.
  bool get isPlayable => question.trim().isNotEmpty && choices.length >= 2;

  /// Identifiants ET textes viennent du serveur ; un choix sans identifiant ou sans texte est écarté.
  static QuizQuestion? fromJson(Object? v) {
    if (v is! Map<String, dynamic>) return null;
    final id = _text(v['id']);
    if (id == null) return null;
    final q = QuizQuestion(
      id: id,
      theme: _text(v['theme']),
      question: (v['question'] ?? '').toString(),
      choices: [
        for (final c in (v['choices'] as List? ?? const []))
          if (c is Map && _text(c['id']) != null && _text(c['label']) != null) QuizChoice(id: _text(c['id'])!, label: _text(c['label'])!),
      ],
      answered: v['answered'] == true,
      result: QuizResult.fromJson(v['result']),
      streak: QuizStreak.fromJson(v['streak']),
    );
    return q.isPlayable ? q : null;
  }
}

/// Réponse de `POST /mobile/quiz/{id}/answer`.
class QuizAnswer {
  const QuizAnswer({required this.result, required this.streak});

  final QuizResult result;
  final QuizStreak streak;

  static QuizAnswer? fromJson(Object? v, {String? choiceId}) {
    if (v is! Map) return null;
    return QuizAnswer(
      result: QuizResult(
        choiceId: choiceId,
        correct: v['correct'] == true,
        correctChoiceId: _text(v['correctChoiceId']),
        explanation: _text(v['explanation']),
      ),
      streak: QuizStreak.fromJson(v['streak']),
    );
  }
}
