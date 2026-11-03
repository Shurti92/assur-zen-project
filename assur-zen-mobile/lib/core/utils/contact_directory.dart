import 'package:flutter/foundation.dart';

import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// Coordonnées AssurZen partagées par tous les écrans, chargées une fois depuis
/// le serveur (`GET /public/contact-info`, paramétrable dans le back-office).
///
/// Tant qu'elles ne sont pas chargées, ou si le chargement échoue, la valeur est
/// [ContactInfo.empty] : les rubriques et boutons de contact sont masqués, rien
/// n'est écrit en dur.
abstract final class ContactDirectory {
  static final ValueNotifier<ContactInfo> info = ValueNotifier(ContactInfo.empty);

  static ContactInfo get current => info.value;

  static Future<void> load(SupportRepository support) async {
    try {
      info.value = await support.getContactInfo();
    } catch (_) {
      // Hors connexion ou serveur indisponible : coordonnées masquées, on réessaiera.
    }
  }
}
