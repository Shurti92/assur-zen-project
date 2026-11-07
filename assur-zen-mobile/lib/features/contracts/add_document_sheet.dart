import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../app/app_scope.dart';
import '../../core/network/api_exception.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Formats et taille acceptés par le serveur.
const kDocumentExtensions = ['pdf', 'jpg', 'jpeg', 'png'];
const kDocumentMaxBytes = 5 * 1024 * 1024;

/// Message clair pour une erreur d'ajout ou de suppression d'un document.
String documentErrorMessage(Object e) {
  if (e is ApiException) {
    switch (e.code) {
      case 'FILE_LIMIT_REACHED':
        return 'Vous avez déjà beaucoup de documents en attente. Patientez le temps de leur vérification, ou supprimez-en un.';
      case 'DOCUMENT_VALIDATED':
        return 'Ce document est déjà validé : il ne peut plus être supprimé.';
    }
    if (e.statusCode == 404 || e.statusCode == 405) return 'Cette fonction n’est pas encore disponible. Réessayez plus tard.';
  }
  return errorMessage(e);
}

/// Feuille « Ajouter un document » : choix du type, puis photo, galerie ou fichier.
/// Renvoie `true` quand un document a été envoyé (la liste doit être rechargée).
Future<bool> showAddDocumentSheet(BuildContext context, Contract contract) async {
  final sent = await showAzSheet<bool>(
    context,
    title: 'Ajouter un document',
    builder: (_) => _AddDocumentBody(contract: contract),
  );
  return sent == true;
}

class _AddDocumentBody extends StatefulWidget {
  const _AddDocumentBody({required this.contract});

  final Contract contract;

  @override
  State<_AddDocumentBody> createState() => _AddDocumentBodyState();
}

class _AddDocumentBodyState extends State<_AddDocumentBody> {
  ClientDocumentType _type = ClientDocumentType.attestation;
  bool _busy = false;
  String? _error;
  bool _done = false;
  final _picker = ImagePicker();

  Future<void> _send(String path) async {
    final repos = context.repos;
    final file = File(path);
    final ext = path.split('.').last.toLowerCase();
    if (!kDocumentExtensions.contains(ext)) {
      setState(() => _error = 'Format non accepté. Choisissez un PDF, une image JPG ou PNG.');
      return;
    }
    if (await file.length() > kDocumentMaxBytes) {
      setState(() => _error = 'Ce fichier dépasse 5 Mo. Choisissez un fichier plus léger ou prenez une photo.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await repos.contracts.uploadDocument(contractId: widget.contract.id, type: _type, filePath: path);
      if (mounted) setState(() => _done = true);
    } catch (e) {
      if (mounted) setState(() => _error = documentErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(source: source, imageQuality: 80, maxWidth: 2000);
      if (picked != null && mounted) await _send(picked.path);
    } catch (_) {
      if (mounted) setState(() => _error = 'Impossible d’accéder à l’appareil photo ou à la galerie. Vérifiez les autorisations.');
    }
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: kDocumentExtensions);
      final path = result?.files.single.path;
      if (path != null && mounted) await _send(path);
    } catch (_) {
      if (mounted) setState(() => _error = 'Impossible d’ouvrir les fichiers du téléphone.');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_done) return _Sent(type: _type, onClose: () => Navigator.of(context).pop(true));
    final v = widget.contract.vehicle;
    final label = (v != null && v.title.isNotEmpty) ? v.title : widget.contract.insuredObject;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('Pour le contrat $label.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
        const SizedBox(height: 12),
        for (final t in ClientDocumentType.values) ...[
          _TypeOption(type: t, selected: t == _type, onTap: _busy ? null : () => setState(() => _type = t)),
          const SizedBox(height: 10),
        ],
        if (_type == ClientDocumentType.attestation)
          const AzBanner(
            kind: AzBannerKind.info,
            text: 'Une attestation ajoutée par vos soins est vérifiée par AssurZen avant d’être reconnue.',
          ),
        if (_error != null) ...[
          const SizedBox(height: 10),
          AzBanner(kind: AzBannerKind.error, text: _error!),
        ],
        const SizedBox(height: 14),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AzButton(
              'Prendre une photo',
              variant: AzButtonVariant.tonal,
              height: 50,
              radius: 16,
              icon: Icons.photo_camera_outlined,
              loading: _busy,
              onPressed: _busy ? null : () => _pickImage(ImageSource.camera),
            ),
            const SizedBox(height: 8),
            AzButton(
              'Choisir un fichier',
              variant: AzButtonVariant.tonal,
              height: 50,
              radius: 16,
              icon: Icons.attach_file_rounded,
              onPressed: _busy ? null : _pickFile,
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: _busy ? null : () => _pickImage(ImageSource.gallery),
          child: const Text('Choisir dans la galerie'),
        ),
        Text(
          'PDF, JPG ou PNG · 5 Mo maximum',
          textAlign: TextAlign.center,
          style: AzText.caption.copyWith(fontWeight: FontWeight.w500),
        ),
      ],
    );
  }
}

class _TypeOption extends StatelessWidget {
  const _TypeOption({required this.type, required this.selected, required this.onTap});

  final ClientDocumentType type;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      label: '${type.label}. ${type.hint}',
      child: Material(
        color: selected ? AzColors.blue50 : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(22),
          side: BorderSide(color: selected ? AzColors.blue700 : AzColors.line, width: selected ? 2 : 1),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 60),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: [
                  AzIconTile(
                    icon: switch (type) {
                      ClientDocumentType.contrat => Icons.description_outlined,
                      ClientDocumentType.recu => Icons.receipt_long_outlined,
                      ClientDocumentType.attestation => Icons.verified_user_outlined,
                    },
                    size: 40,
                    background: selected ? AzColors.blue700 : AzColors.blue50,
                    color: selected ? Colors.white : AzColors.blue700,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(type.label, style: AzText.body.copyWith(fontSize: 14.5, fontWeight: FontWeight.w700)),
                        Text(type.hint, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ),
                  Icon(selected ? Icons.radio_button_checked_rounded : Icons.radio_button_unchecked_rounded,
                      color: selected ? AzColors.blue700 : AzColors.fieldBorder),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Sent extends StatelessWidget {
  const _Sent({required this.type, required this.onClose});

  final ClientDocumentType type;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 8),
        Center(
          child: Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(color: AzColors.successBg, shape: BoxShape.circle),
            child: const Icon(Icons.check_rounded, color: AzColors.success, size: 36),
          ),
        ),
        const SizedBox(height: 12),
        const Text('Document envoyé', textAlign: TextAlign.center, style: AzText.headline),
        const SizedBox(height: 6),
        Text(
          'AssurZen va vérifier votre ${type.label.toLowerCase()}. Vous serez prévenu par notification dès qu’il est validé ou refusé.',
          textAlign: TextAlign.center,
          style: AzText.bodyMedium.copyWith(color: AzColors.muted),
        ),
        const SizedBox(height: 14),
        const AzBanner(
          kind: AzBannerKind.info,
          text: 'Tant qu’il n’est pas validé, ce document ne remplace pas l’attestation officielle.',
        ),
        const SizedBox(height: 16),
        AzButton('Retour à mes documents', height: 50, radius: 16, onPressed: onClose),
      ],
    );
  }
}
