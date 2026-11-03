import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Déclaration de sinistre en 4 étapes :
/// 1 Contrat et type · 2 Quand et où · 3 Circonstances et photos · 4 Vérification.
class ClaimDeclarePage extends StatefulWidget {
  const ClaimDeclarePage({super.key, this.contractId});

  final String? contractId;

  @override
  State<ClaimDeclarePage> createState() => _ClaimDeclarePageState();
}

class _ClaimDeclarePageState extends State<ClaimDeclarePage> {
  final ClaimDraft _d = ClaimDraft();
  late final Future<(List<Contract>, List<ClaimType>)> _setup = _load();
  int _step = 1;
  String? _error;
  bool _sending = false;
  TimeOfDay _time = TimeOfDay.now();
  final _place = TextEditingController();
  final _desc = TextEditingController();
  final _thirdName = TextEditingController();
  final _thirdPlate = TextEditingController();
  final _thirdInsurer = TextEditingController();
  final _picker = ImagePicker();

  static const _titles = {1: 'Contrat et type', 2: 'Quand et où ?', 3: 'Circonstances et photos', 4: 'Vérification'};
  static const _maxPhotos = 6;
  static const _maxDocs = 3;

  Future<(List<Contract>, List<ClaimType>)> _load() async {
    final repos = context.repos;
    final all = await repos.contracts.getContracts();
    // Les droits (`actions`) ne figurent que dans le détail d'un contrat, pas dans
    // la liste : le serveur décide, on lit donc le détail de chacun.
    final details = await Future.wait(all.map((c) => repos.contracts.getContract(c.id)));
    final active = details.where((c) => c.actions.canDeclareClaim).toList();
    _d.contractId = widget.contractId ?? (active.isEmpty ? null : active.first.id);
    final productId = active.where((c) => c.id == _d.contractId).firstOrNull?.productId ?? 'auto';
    final types = await repos.claims.getClaimTypes(productId);
    return (active, types);
  }

  @override
  void dispose() {
    for (final c in [_place, _desc, _thirdName, _thirdPlate, _thirdInsurer]) {
      c.dispose();
    }
    super.dispose();
  }

  void _sync() {
    _d
      ..place = _place.text.trim()
      ..description = _desc.text.trim()
      ..thirdPartyName = _thirdName.text.trim()
      ..thirdPartyPlate = _thirdPlate.text.trim().toUpperCase()
      ..thirdPartyInsurer = _thirdInsurer.text.trim();
    final day = _d.occurredAt;
    _d.occurredAt = DateTime(day.year, day.month, day.day, _time.hour, _time.minute);
  }

  String? _validate() {
    switch (_step) {
      case 1:
        if (_d.contractId == null) return 'Choisissez le contrat concerné.';
        if (_d.type == null) return 'Choisissez le type de sinistre.';
      case 2:
        if (_d.occurredAt.isAfter(DateTime.now())) return 'La date du sinistre ne peut pas être dans le futur.';
        if (_d.place.length < 3) return 'Indiquez le lieu du sinistre.';
        if (_d.thirdPartyInvolved == null) return 'Précisez si un tiers est impliqué.';
      case 3:
        if (_d.description.length < 20) return 'Décrivez les circonstances en 20 caractères minimum.';
      case 4:
        if (!_d.certified) return 'Cochez la case de certification pour envoyer.';
    }
    return null;
  }

  Future<void> _next() async {
    _sync();
    final err = _validate();
    setState(() => _error = err);
    if (err != null) return;
    if (_step < 4) return setState(() => _step++);
    setState(() => _sending = true);
    try {
      final claim = await context.repos.claims.declare(_d);
      if (!mounted) return;
      Navigator.of(context).pushReplacementNamed(AppRoutes.claimDone, arguments: claim);
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _back() {
    if (_sending) return;
    if (_step > 1) {
      _sync();
      setState(() {
        _step--;
        _error = null;
      });
    } else {
      Navigator.of(context).pop();
    }
  }

  Future<void> _addFile({required bool photo}) async {
    final list = photo ? _d.photoPaths : _d.documentPaths;
    if (list.length >= (photo ? _maxPhotos : _maxDocs)) {
      return showAzSnack(context, photo ? '6 photos au maximum.' : '3 documents au maximum.');
    }
    final source = await showAzSheet<ImageSource>(
      context,
      title: photo ? 'Ajouter une photo' : 'Ajouter un document',
      builder: (context) => AzGap(
        gap: 10,
        children: [
          AzButton('Prendre une photo', icon: Icons.photo_camera_outlined, onPressed: () => Navigator.of(context).pop(ImageSource.camera)),
          AzButton.secondary('Choisir dans la galerie', icon: Icons.photo_library_outlined, onPressed: () => Navigator.of(context).pop(ImageSource.gallery)),
        ],
      ),
    );
    if (source == null) return;
    try {
      // Décision 6.4 : compression sur le téléphone (< 1 Mo par photo).
      final file = await _picker.pickImage(source: source, imageQuality: 65, maxWidth: 1600);
      if (file != null && mounted) setState(() => list.add(file.path));
    } catch (_) {
      if (mounted) showAzSnack(context, 'Impossible d’accéder à l’appareil photo. Vérifiez les autorisations.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _step == 1 && !_sending,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        body: FutureBuilder<(List<Contract>, List<ClaimType>)>(
          future: _setup,
          builder: (context, snap) {
            if (snap.hasError) {
              return SafeArea(child: AzErrorState(message: errorMessage(snap.error!), onRetry: () => Navigator.of(context).pop()));
            }
            if (!snap.hasData) return const AzLoader();
            final (contracts, types) = snap.data!;
            if (contracts.isEmpty) {
              return const SafeArea(
                child: Column(children: [
                  AzTopBar(title: 'Déclarer un sinistre'),
                  Expanded(
                    child: AzEmptyState(
                      icon: Icons.shield_outlined,
                      title: 'Aucun contrat actif',
                      message: 'Un contrat actif est nécessaire pour déclarer un sinistre. Contactez-nous si besoin.',
                    ),
                  ),
                ]),
              );
            }
            return Column(
              children: [
                AzTopBar(overline: 'Déclarer un sinistre', title: _titles[_step], onBack: _back),
                Padding(
                  padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 12),
                  child: AzProgressHeader(step: _step, total: 4),
                ),
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(AzSpace.screen, 8, AzSpace.screen, 24),
                    children: [
                      switch (_step) {
                        1 => _step1(contracts, types),
                        2 => _step2(),
                        3 => _step3(),
                        _ => _step4(contracts),
                      },
                      if (_error != null) ...[
                        const SizedBox(height: 14),
                        AzBanner(kind: AzBannerKind.error, text: _error!),
                      ],
                    ],
                  ),
                ),
                AzBottomBar(children: [
                  AzButton(_step < 4 ? 'Continuer' : 'Envoyer la déclaration', loading: _sending, onPressed: _next),
                ]),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _step1(List<Contract> contracts, List<ClaimType> types) {
    return AzGap(
      gap: 18,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Contrat concerné', style: AzText.label),
            const SizedBox(height: 8),
            for (final c in contracts) ...[
              AzOptionTile(
                title: c.name,
                subtitle: c.insuredObject,
                leading: AzIconTile(icon: productIcon(c.productId), size: 40),
                selected: _d.contractId == c.id,
                onTap: () => setState(() => _d.contractId = c.id),
              ),
              const SizedBox(height: 10),
            ],
          ],
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Type de sinistre', style: AzText.label),
            const SizedBox(height: 8),
            AzChipWrap<String>(
              options: [for (final t in types) t.label],
              selected: _d.type,
              onSelected: (v) => setState(() {
                _d.type = v;
                _d.typeId = types.firstWhere((t) => t.label == v).id;
              }),
            ),
          ],
        ),
      ],
    );
  }

  Widget _step2() {
    return AzGap(
      gap: 18,
      children: [
        Row(
          children: [
            Expanded(
              child: AzDateField(
                label: 'Date',
                value: _d.occurredAt,
                firstDate: DateTime.now().subtract(const Duration(days: 365)),
                lastDate: DateTime.now(),
                onChanged: (v) => setState(() => _d.occurredAt = v),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(child: AzTimeField(label: 'Heure', value: _time, onChanged: (v) => setState(() => _time = v))),
          ],
        ),
        AzTextField(label: 'Lieu', controller: _place, hint: 'Ex. Boulevard VGE, Marcory', capitalization: TextCapitalization.sentences),
        const AzBanner(
          kind: AzBannerKind.info,
          text: 'Indiquez la commune et un repère connu (carrefour, station, pharmacie).',
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Un tiers est-il impliqué ?', style: AzText.label),
            const SizedBox(height: 8),
            AzChipGrid<bool>(
              options: const [true, false],
              selected: _d.thirdPartyInvolved,
              labelOf: (v) => v ? 'Oui' : 'Non',
              onSelected: (v) => setState(() => _d.thirdPartyInvolved = v),
            ),
          ],
        ),
        if (_d.thirdPartyInvolved == true)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(16)),
            child: AzGap(
              gap: 14,
              children: [
                AzTextField(label: 'Nom du tiers', controller: _thirdName, capitalization: TextCapitalization.words),
                AzTextField(label: 'Immatriculation du tiers', controller: _thirdPlate, hint: 'Ex. 4567 CD 01', capitalization: TextCapitalization.characters),
                AzTextField(label: 'Assureur du tiers (si connu)', controller: _thirdInsurer),
              ],
            ),
          ),
      ],
    );
  }

  Widget _step3() {
    return AzGap(
      gap: 18,
      children: [
        AzTextField(
          label: 'Décrivez les circonstances',
          controller: _desc,
          hint: 'Que s’est-il passé ? Indiquez le sens de circulation, les dégâts constatés…',
          maxLines: 5,
          capitalization: TextCapitalization.sentences,
          onChanged: (_) => setState(() {}),
          helper: '${_desc.text.trim().length} / 20 caractères minimum',
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Photos des dégâts (${_d.photoPaths.length}/$_maxPhotos)', style: AzText.label),
            const SizedBox(height: 8),
            LayoutBuilder(builder: (context, c) {
              final size = (c.maxWidth - 16) / 3;
              return Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (var i = 0; i < _d.photoPaths.length; i++)
                    _PhotoThumb(
                      path: _d.photoPaths[i],
                      size: size,
                      onRemove: () => setState(() => _d.photoPaths.removeAt(i)),
                    ),
                  if (_d.photoPaths.length < _maxPhotos)
                    SizedBox(
                      width: size,
                      height: size,
                      child: Material(
                        color: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                          side: const BorderSide(color: AzColors.blue700, width: 1.5),
                        ),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(14),
                          onTap: () => _addFile(photo: true),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.add_a_photo_outlined, color: AzColors.blue700),
                              const SizedBox(height: 6),
                              Text('Ajouter', style: AzText.caption.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700)),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              );
            }),
          ],
        ),
        AzCard(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              const AzIconTile(icon: Icons.description_outlined, background: AzColors.background, color: AzColors.ink2),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Constat amiable / pièces', style: AzText.titleSmall),
                    Text(
                      _d.documentPaths.isEmpty ? 'Facultatif · photo du document' : '${_d.documentPaths.length} document(s) ajouté(s)',
                      style: AzText.caption.copyWith(fontSize: 13),
                    ),
                  ],
                ),
              ),
              AzButton.secondary('Ajouter', expand: false, height: 40, onPressed: () => _addFile(photo: false)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _step4(List<Contract> contracts) {
    final c = contracts.firstWhere((x) => x.id == _d.contractId, orElse: () => contracts.first);
    return AzGap(
      gap: 16,
      children: [
        AzSummaryCard(rows: [
          ('Contrat', '${c.name} · ${c.insuredObject}'),
          ('Type', _d.type ?? '—'),
          ('Date et heure', '${formatDate(_d.occurredAt)} à ${formatTime(_d.occurredAt)}'),
          ('Lieu', _d.place),
          ('Tiers impliqué', _d.thirdPartyInvolved == true ? [_d.thirdPartyName, _d.thirdPartyPlate].where((s) => s.isNotEmpty).join(' · ').ifEmpty('Oui') : 'Non'),
          ('Pièces jointes', '${_d.photoPaths.length} photo(s) · ${_d.documentPaths.length} doc.'),
        ]),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(14)),
          child: Text(_d.description, style: AzText.bodyMedium),
        ),
        AzCheckRow(
          value: _d.certified,
          onChanged: (v) => setState(() {
            _d.certified = v;
            _error = null;
          }),
          child: const Text('Je certifie sur l’honneur l’exactitude de cette déclaration.'),
        ),
        // Aucun délai de prise en charge annoncé : le serveur n'en fournit pas.
        const AzBanner(kind: AzBannerKind.info, text: 'Vous suivrez l’avancement de votre dossier dans « Mes sinistres ».'),
      ],
    );
  }
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}

class _PhotoThumb extends StatelessWidget {
  const _PhotoThumb({required this.path, required this.size, required this.onRemove});

  final String path;
  final double size;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        fit: StackFit.expand,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: Image.file(
              File(path),
              fit: BoxFit.cover,
              cacheWidth: 300,
              errorBuilder: (_, __, ___) => Container(color: AzColors.skeleton, child: const Icon(Icons.image_outlined, color: AzColors.muted)),
            ),
          ),
          Positioned(
            top: 4,
            right: 4,
            child: Material(
              color: const Color(0xB30F1B33),
              shape: const CircleBorder(),
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onRemove,
                child: const Padding(
                  padding: EdgeInsets.all(6),
                  child: Icon(Icons.close_rounded, size: 16, color: Colors.white, semanticLabel: 'Retirer la photo'),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
