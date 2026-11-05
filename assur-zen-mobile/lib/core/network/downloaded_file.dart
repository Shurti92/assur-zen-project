import 'dart:typed_data';

/// Fichier reçu du serveur (document de contrat, attestation).
class DownloadedFile {
  const DownloadedFile({required this.bytes, required this.contentType, this.fileName});

  final Uint8List bytes;

  /// Type contrôlé par le serveur : `application/pdf`, `image/jpeg` ou `image/png`.
  final String contentType;

  /// Nom lu dans `Content-Disposition`, s'il est fourni.
  final String? fileName;

  /// Types acceptés. Tout autre type est refusé : on n'ouvre jamais un fichier inattendu.
  static const allowedTypes = {'application/pdf', 'image/jpeg', 'image/png'};

  bool get isSupported => allowedTypes.contains(contentType);

  /// Extension correspondant au type (jamais déduite du nom reçu).
  String? get extension => switch (contentType) {
        'application/pdf' => 'pdf',
        'image/jpeg' => 'jpg',
        'image/png' => 'png',
        _ => null,
      };

  /// Nom de fichier sûr : sans dossier ni caractère spécial, avec l'extension du type réel.
  String safeName([String fallback = 'document']) {
    var base = (fileName ?? '').split(RegExp(r'[\\/]')).last.trim();
    final dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    base = base.replaceAll(RegExp(r'[^A-Za-z0-9._-]+'), '_').replaceAll(RegExp(r'^[._]+|_+$'), '');
    if (base.isEmpty) base = fallback;
    if (base.length > 60) base = base.substring(0, 60);
    return '$base.${extension ?? 'bin'}';
  }

  /// `Content-Disposition: inline; filename="nom.pdf"` (ou `filename*=UTF-8''nom.pdf`).
  static String? fileNameFromDisposition(String? header) {
    if (header == null) return null;
    final star = RegExp(r"filename\*\s*=\s*[^']*'[^']*'([^;]+)", caseSensitive: false).firstMatch(header);
    if (star != null) {
      try {
        return Uri.decodeComponent(star.group(1)!.trim());
      } on FormatException {
        // retombe sur filename=
      }
    }
    final plain = RegExp(r'filename\s*=\s*"?([^";]+)"?', caseSensitive: false).firstMatch(header);
    return plain?.group(1)?.trim();
  }
}
