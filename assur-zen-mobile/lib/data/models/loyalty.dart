/// Programme de fidélité Zen Points, phase A (points, niveaux, historique).
///
/// Tout vient du serveur : solde, niveau, seuils, barème, libellés et dates. L'application
/// n'ajoute, ne retire ni ne calcule jamais un point.
library;

class LoyaltyConsent {
  const LoyaltyConsent({required this.accepted, this.currentVersion, this.text, this.acceptedAt});

  final bool accepted;

  /// Version à renvoyer à l'acceptation (`409 CONSENT_VERSION_MISMATCH` si elle a changé).
  final String? currentVersion;

  /// Conditions du programme rédigées par AssurZen ; absent : l'adhésion n'est pas proposée.
  final String? text;
  final DateTime? acceptedAt;

  bool get canAccept => text != null && currentVersion != null;
}

class LoyaltyNextLevel {
  const LoyaltyNextLevel({required this.label, required this.threshold, required this.pointsToGo});

  final String label;
  final int threshold;
  final int pointsToGo;
}

class LoyaltyLevel {
  const LoyaltyLevel({required this.code, required this.label, required this.qualifyingPoints, this.next});

  final String code;
  final String label;

  /// Points gagnés sur 12 mois glissants : base du niveau.
  final int qualifyingPoints;

  /// Absent : niveau maximal.
  final LoyaltyNextLevel? next;
}

class LoyaltyExpiring {
  const LoyaltyExpiring({required this.points, this.date});

  final int points;
  final DateTime? date;
}

class LoyaltyOverview {
  const LoyaltyOverview({required this.consent, this.balance, this.level, this.expiring});

  final LoyaltyConsent consent;

  /// Absents tant que l'assuré n'a pas accepté les conditions.
  final int? balance;
  final LoyaltyLevel? level;
  final LoyaltyExpiring? expiring;

  bool get joined => consent.accepted && balance != null;
}

class LoyaltyAction {
  const LoyaltyAction({required this.code, required this.label, required this.points, this.limit});

  final String code;
  final String label;
  final int points;
  final String? limit;
}

class LoyaltyLevelRule {
  const LoyaltyLevelRule({required this.code, required this.label, required this.threshold, this.perks = const []});

  final String code;
  final String label;
  final int threshold;
  final List<String> perks;
}

class LoyaltyRules {
  const LoyaltyRules({this.termsVersion, this.expiryMonths, this.actions = const [], this.levels = const []});

  final String? termsVersion;
  final int? expiryMonths;
  final List<LoyaltyAction> actions;
  final List<LoyaltyLevelRule> levels;
}

/// Un palier de remise : durée de garantie (bornes incluses, en mois) et pourcentage de la prime nette.
class LoyaltyDiscountTier {
  const LoyaltyDiscountTier({required this.productKey, required this.minMonths, required this.maxMonths, required this.percent});

  /// Clé du produit du devis : `moto` (moto et tricycle) ou `automobile-vtc`.
  final String productKey;
  final int minMonths;
  final int maxMonths;
  final num percent;

  String get durationLabel {
    if (minMonths == maxMonths) return '$minMonths mois';
    return '$minMonths à $maxMonths mois';
  }
}

/// Remises Zen Points du client. Le serveur ne liste que les paliers dont le client bénéficie.
class LoyaltyDiscounts {
  const LoyaltyDiscounts({required this.tiers, this.levelLabel});

  final String? levelLabel;
  final List<LoyaltyDiscountTier> tiers;
}

enum LoyaltyEntryType { earn, expire, adjust, cancel }

class LoyaltyEntry {
  const LoyaltyEntry({required this.id, required this.date, required this.type, required this.label, required this.points});

  final String id;
  final DateTime date;
  final LoyaltyEntryType type;
  final String label;

  /// Signé : négatif pour une expiration ou une annulation.
  final int points;
}

class LoyaltyHistory {
  const LoyaltyHistory({required this.items, required this.total});

  final List<LoyaltyEntry> items;
  final int total;
}
