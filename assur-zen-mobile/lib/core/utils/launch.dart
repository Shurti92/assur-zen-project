import 'package:flutter/widgets.dart';
import 'package:url_launcher/url_launcher.dart';

import '../widgets/az_feedback.dart';
import 'contact_directory.dart';
import 'formatters.dart';

/// Ouverture des applications externes : téléphone, WhatsApp, e-mail, PDF.
///
/// Les coordonnées viennent du serveur ([ContactDirectory]) ; si elles ne sont
/// pas communiquées, l'action l'indique au lieu d'utiliser une valeur en dur.
abstract final class Launch {
  static const _unavailable = 'Ce moyen de contact n’est pas disponible pour le moment.';

  static Future<void> phone(BuildContext context, [String? number]) {
    final n = number ?? ContactDirectory.current.primaryPhone;
    if (n == null) {
      showAzSnack(context, _unavailable);
      return Future.value();
    }
    return _open(context, Uri(scheme: 'tel', path: '+${internationalPhone(n)}'), 'Impossible de lancer l’appel.');
  }

  /// Décision 7.2 : message prérempli (n° de contrat) pour identifier le client.
  static Future<void> whatsapp(BuildContext context, {String? message}) {
    final number = ContactDirectory.current.whatsapp;
    if (number == null) {
      showAzSnack(context, _unavailable);
      return Future.value();
    }
    final text = message ?? 'Bonjour AssurZen, j’ai besoin d’aide.';
    final uri = Uri.https('wa.me', '/${internationalPhone(number)}', {'text': text});
    return _open(context, uri, 'WhatsApp n’est pas disponible sur ce téléphone.');
  }

  /// Partage d’un texte via WhatsApp (devis, lien de document).
  static Future<void> shareWhatsapp(BuildContext context, String text) {
    final uri = Uri.https('wa.me', '/', {'text': text});
    return _open(context, uri, 'WhatsApp n’est pas disponible sur ce téléphone.');
  }

  static Future<void> email(BuildContext context, {String subject = 'Demande d’assistance', String body = ''}) {
    final address = ContactDirectory.current.email;
    if (address == null) {
      showAzSnack(context, _unavailable);
      return Future.value();
    }
    final uri = Uri(
      scheme: 'mailto',
      path: address,
      query: 'subject=${Uri.encodeComponent(subject)}&body=${Uri.encodeComponent(body)}',
    );
    return _open(context, uri, 'Aucune application de messagerie trouvée.');
  }

  /// Ouvre un document (URL sécurisée renvoyée par le serveur).
  static Future<void> url(BuildContext context, String url) =>
      _open(context, Uri.parse(url), 'Impossible d’ouvrir ce document.');

  static Future<void> _open(BuildContext context, Uri uri, String errorMessage) async {
    try {
      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok && context.mounted) showAzSnack(context, errorMessage);
    } catch (_) {
      if (context.mounted) showAzSnack(context, errorMessage);
    }
  }
}
