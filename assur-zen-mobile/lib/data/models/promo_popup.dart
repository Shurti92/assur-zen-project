/// Pop-up publicitaire paramétré au back-office (`GET /public/promotions/popups`).
///
/// Aucune campagne n'est écrite en dur : le serveur décide du média, du lien, du délai et de la fréquence.
library;

import '../../app/env.dart';

enum PromoMediaType { image, video }

enum PromoFrequency { once, daily, everyOpen }

class PromoPopup {
  const PromoPopup({
    required this.id,
    required this.mediaType,
    required this.mediaUrl,
    required this.frequency,
    this.title,
    this.posterUrl,
    this.linkUrl,
    this.productId,
    this.linkLabel,
    this.displayAfterSeconds = 0,
    this.version,
  });

  final String id;
  final PromoMediaType mediaType;
  final String mediaUrl;
  final PromoFrequency frequency;
  final String? title;
  final String? posterUrl;

  /// Lien https externe, ou fiche d'un produit (exclusifs côté serveur).
  final String? linkUrl;
  final String? productId;

  /// Texte du bouton ; sans lien ni produit, il n'y a pas de bouton.
  final String? linkLabel;
  final int displayAfterSeconds;

  /// Date de dernière modification : une campagne modifiée est montrée à nouveau.
  final String? version;

  bool get hasAction => (linkUrl != null || productId != null) && linkLabel != null;

  /// Lecture tolérante : une campagne sans identifiant, sans média https ou de type inconnu est ignorée.
  static PromoPopup? tryParse(Object? raw) {
    if (raw is! Map) return null;
    final id = _text(raw['id']);
    final media = _https(raw['mediaUrl']);
    final type = switch (_text(raw['mediaType'])?.toUpperCase()) {
      'IMAGE' => PromoMediaType.image,
      'VIDEO' => PromoMediaType.video,
      _ => null,
    };
    if (id == null || media == null || type == null) return null;
    final frequency = switch (_text(raw['frequency'])?.toUpperCase()) {
      'ONCE' => PromoFrequency.once,
      'EVERY_OPEN' => PromoFrequency.everyOpen,
      _ => PromoFrequency.daily,
    };
    final seconds = raw['displayAfterSeconds'];
    return PromoPopup(
      id: id,
      mediaType: type,
      mediaUrl: media,
      frequency: frequency,
      title: _text(raw['title']),
      posterUrl: _https(raw['posterUrl']),
      linkUrl: _https(raw['linkUrl']),
      productId: _text(raw['productId']),
      linkLabel: _text(raw['linkLabel']),
      displayAfterSeconds: seconds is num ? seconds.toInt().clamp(0, 30) : 0,
      version: _text(raw['version']),
    );
  }

  static List<PromoPopup> listFromJson(Object? data) {
    final items = data is Map ? data['items'] : data;
    return [
      for (final e in (items is List ? items : const []))
        if (tryParse(e) case final p?) p,
    ];
  }
}

String? _text(Object? v) {
  final s = v?.toString().trim();
  return (s == null || s.isEmpty) ? null : s;
}

/// Seules les adresses https sont acceptées (médias et liens). En environnement local, le média servi par la
/// Gateway de développement est en http : toléré seulement là.
String? _https(Object? v) {
  final s = _text(v);
  if (s == null) return null;
  final uri = Uri.tryParse(s);
  if (uri == null || uri.host.isEmpty) return null;
  if (uri.scheme == 'https') return s;
  return (uri.scheme == 'http' && Env.appEnv == 'local') ? s : null;
}

/// Choix du pop-up à montrer : le premier de la liste (déjà triée par priorité) que la fréquence autorise.
///
/// [seen] : pour chaque campagne, `version|jour` de sa dernière apparition (jour au format `AAAA-MM-JJ`).
PromoPopup? pickPopup(List<PromoPopup> list, Map<String, String> seen, DateTime now) {
  final today = dayKey(now);
  for (final p in list) {
    final last = seen[p.id];
    if (last == null) return p;
    final cut = last.lastIndexOf('|');
    final lastVersion = cut < 0 ? last : last.substring(0, cut);
    final lastDay = cut < 0 ? '' : last.substring(cut + 1);
    // Campagne modifiée au back-office : elle redevient éligible.
    if ((p.version ?? '') != lastVersion) return p;
    switch (p.frequency) {
      case PromoFrequency.once:
        break;
      case PromoFrequency.daily:
        if (lastDay != today) return p;
      case PromoFrequency.everyOpen:
        return p;
    }
  }
  return null;
}

String dayKey(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
