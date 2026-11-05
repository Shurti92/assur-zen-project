import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../network/downloaded_file.dart';
import '../widgets/az_feedback.dart';

/// Enregistre un fichier reçu du serveur dans le dossier temporaire de l'application (privé, jamais
/// la galerie ni le stockage partagé), puis l'ouvre avec l'application de lecture du téléphone.
abstract final class FileViewer {
  /// Remplaçables dans les tests : aucun accès réel au disque ni à Android.
  static Future<String> Function(DownloadedFile file, String name) save = _save;
  static Future<ResultType> Function(String path) open = _open;

  static const _folder = 'documents';
  static const _keepFor = Duration(hours: 1);

  /// Ouvre [file]. Un type inattendu est refusé ; une absence d'application de lecture est expliquée.
  static Future<void> show(BuildContext context, DownloadedFile file, {String fallbackName = 'document'}) async {
    if (!file.isSupported) {
      showAzSnack(context, 'Ce type de document n’est pas pris en charge.');
      return;
    }
    if (file.bytes.isEmpty) {
      showAzSnack(context, 'Le document est vide.');
      return;
    }
    try {
      final path = await save(file, file.safeName(fallbackName));
      final result = await open(path);
      if (!context.mounted) return;
      switch (result) {
        case ResultType.done:
          break;
        case ResultType.noAppToOpen:
          showAzSnack(context, 'Aucune application ne peut ouvrir ce document. Installez un lecteur PDF.');
        case ResultType.permissionDenied:
          showAzSnack(context, 'L’ouverture du document n’est pas autorisée sur ce téléphone.');
        case ResultType.fileNotFound || ResultType.error:
          showAzSnack(context, 'Impossible d’ouvrir ce document.');
      }
    } catch (_) {
      if (context.mounted) showAzSnack(context, 'Impossible d’enregistrer ce document sur le téléphone.');
    }
  }

  static Future<String> _save(DownloadedFile file, String name) async {
    final dir = Directory('${(await getTemporaryDirectory()).path}/$_folder');
    await dir.create(recursive: true);
    // Ménage : les anciens fichiers (plus d'une heure) sont supprimés.
    final limit = DateTime.now().subtract(_keepFor);
    for (final e in dir.listSync()) {
      if (e is File && e.lastModifiedSync().isBefore(limit)) {
        try {
          e.deleteSync();
        } on FileSystemException {
          // ignoré : le fichier est peut-être ouvert ailleurs
        }
      }
    }
    final target = File('${dir.path}/$name');
    await target.writeAsBytes(file.bytes, flush: true);
    return target.path;
  }

  static Future<ResultType> _open(String path) async => (await OpenFilex.open(path)).type;
}
