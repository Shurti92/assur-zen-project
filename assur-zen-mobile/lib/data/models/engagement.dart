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
