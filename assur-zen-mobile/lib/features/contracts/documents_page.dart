import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../core/utils/file_viewer.dart';
import '../../data/models/models.dart';
import 'add_document_sheet.dart';

/// Mes documents : un bloc par contrat (véhicule, attestation, vignette, autres
/// documents). Tout vient du serveur : un document absent n'est pas affiché, et
/// rien n'est fabriqué par l'application.
class DocumentsPage extends StatefulWidget {
  const DocumentsPage({super.key});

  @override
  State<DocumentsPage> createState() => _DocumentsPageState();
}

enum _Filter { all, active, expired }

class _DocumentsPageState extends State<DocumentsPage> {
  late Future<List<Contract>> _future = _load();
  _Filter _filter = _Filter.all;
  String? _expandedId;

  Future<List<Contract>> _load() async {
    final repos = context.repos;
    final all = await repos.contracts.getContracts();
    // Documents et attestation ne figurent que dans le détail d'un contrat.
    final details = await Future.wait(all.map((c) => repos.contracts.getContract(c.id)));
    // Les contrats actifs d'abord, puis l'échéance la plus proche.
    details.sort((a, b) {
      final byStatus = (a.status == ContractStatus.actif ? 0 : 1).compareTo(b.status == ContractStatus.actif ? 0 : 1);
      if (byStatus != 0) return byStatus;
      final x = a.endDate, y = b.endDate;
      if (x == null || y == null) return x == null ? (y == null ? 0 : 1) : -1;
      return x.compareTo(y);
    });
    return details;
  }

  void _reload() {
    // Bloc (et non flèche) : setState refuse un rappel qui renvoie un Future.
    setState(() {
      _future = _load();
    });
  }

  bool _isExpired(Contract c) => c.status == ContractStatus.expire || c.status == ContractStatus.resilie;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Mes documents'),
      body: FutureBuilder<List<Contract>>(
        future: _future,
        builder: (context, snap) {
          if (snap.hasError) return AzErrorState(message: errorMessage(snap.error!), onRetry: _reload);
          final contracts = snap.data;
          if (contracts == null) return const AzSkeletonList(count: 3);
          if (contracts.isEmpty) {
            return const AzEmptyState(
              icon: Icons.description_outlined,
              title: 'Aucun document',
              message: 'Vos documents apparaîtront ici dès la souscription d’un contrat.',
            );
          }
          final shown = contracts.where((c) {
            return switch (_filter) {
              _Filter.all => true,
              _Filter.active => !_isExpired(c),
              _Filter.expired => _isExpired(c),
            };
          }).toList();
          final multiple = contracts.length > 2;
          // Un seul bloc ouvert à la fois ; avec un contrat unique, il reste ouvert.
          final expandedId = contracts.length == 1 ? contracts.first.id : (_expandedId ?? shown.firstOrNull?.id);
          return RefreshIndicator(
            onRefresh: () async {
              _reload();
              await _future;
            },
            child: ListView(
              padding: EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
              children: [
                Text(
                  contracts.length == 1 ? 'Attestation et documents de votre véhicule.' : 'Attestations et documents de vos contrats.',
                  style: AzText.bodyMedium.copyWith(color: AzColors.muted),
                ),
                if (multiple) ...[
                  const SizedBox(height: 14),
                  SizedBox(
                    height: 46,
                    child: ListView(
                      scrollDirection: Axis.horizontal,
                      children: [
                        _FilterPill('Tous', contracts.length, _filter == _Filter.all, () => setState(() => _filter = _Filter.all)),
                        _FilterPill('En cours', contracts.where((c) => !_isExpired(c)).length, _filter == _Filter.active,
                            () => setState(() => _filter = _Filter.active)),
                        _FilterPill('Expirés', contracts.where(_isExpired).length, _filter == _Filter.expired,
                            () => setState(() => _filter = _Filter.expired)),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 14),
                if (shown.isEmpty)
                  const AzEmptyState(icon: Icons.description_outlined, title: 'Aucun contrat', message: 'Aucun contrat dans cette catégorie.')
                else
                  for (final c in shown) ...[
                    _ContractDocuments(
                      key: ValueKey(c.id),
                      contract: c,
                      expanded: c.id == expandedId,
                      collapsible: contracts.length > 1,
                      onToggle: () => setState(() => _expandedId = c.id == expandedId ? '' : c.id),
                      onChanged: _reload,
                    ),
                    const SizedBox(height: 16),
                  ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _FilterPill extends StatelessWidget {
  const _FilterPill(this.label, this.count, this.selected, this.onTap);

  final String label;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: Semantics(
        button: true,
        selected: selected,
        label: '$label, $count',
        child: Material(
          color: selected ? AzColors.blue700 : Colors.white,
          shape: StadiumBorder(side: BorderSide(color: selected ? AzColors.blue700 : const Color(0xFFE3E7EF))),
          child: InkWell(
            customBorder: const StadiumBorder(),
            onTap: onTap,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 44),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(label, style: AzText.body.copyWith(fontSize: 13, fontWeight: FontWeight.w700, color: selected ? Colors.white : AzColors.ink)),
                    const SizedBox(width: 6),
                    Text('$count', style: AzText.caption.copyWith(fontSize: 12, fontWeight: FontWeight.w700, color: selected ? const Color(0xFFD6E0F3) : AzColors.muted)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ContractDocuments extends StatelessWidget {
  const _ContractDocuments({super.key, required this.contract, required this.expanded, required this.collapsible, required this.onToggle, required this.onChanged});

  final Contract contract;
  final bool expanded;
  final bool collapsible;
  final VoidCallback onToggle;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    final official = c.documents.where((d) => !d.addedByClient).toList();
    final mine = c.documents.where((d) => d.addedByClient).toList();
    final attestationDocs = official.where((d) => d.type == DocumentType.attestation).toList();
    final vignetteDocs = official.where((d) => d.type == DocumentType.vignette).toList();
    final others = official.where((d) => d.type != DocumentType.attestation && d.type != DocumentType.vignette).toList();
    final canAdd = c.status != ContractStatus.expire && c.status != ContractStatus.resilie;
    final renewal = c.renewalOpen && c.status == ContractStatus.actif;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _VehicleCard(contract: c, expanded: expanded, collapsible: collapsible, onToggle: onToggle),
        if (expanded) ...[
          if (renewal) ...[
            const SizedBox(height: 10),
            _RenewalCard(contract: c),
          ],
          if (c.hasDigitalAttestation || c.attestation?.available == true || attestationDocs.isNotEmpty) ...[
            const SizedBox(height: 14),
            _Section('Attestation', c.hasDigitalAttestation || c.attestation?.available == true ? AzStatusBadge.document(DocumentStatus.disponible) : null),
            const SizedBox(height: 8),
            _AttestationBlock(contract: c),
          ],
          // La vignette n'existe que si le serveur en fournit une pour ce contrat.
          if (vignetteDocs.isNotEmpty) ...[
            const SizedBox(height: 14),
            for (final d in vignetteDocs) ...[
              _Section('Vignette', AzStatusBadge.document(d.status)),
              const SizedBox(height: 8),
              _DocumentBlock(contract: c, document: d, icon: Icons.sell_outlined, hint: d.isAvailable ? null : 'Elle sera disponible dès son émission.'),
            ],
          ],
          if (others.isNotEmpty) ...[
            const SizedBox(height: 14),
            const _Section('Autres documents', null),
            const SizedBox(height: 8),
            AzCard(
              radius: 26,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
              child: Column(
                children: [
                  for (var i = 0; i < others.length; i++) ...[
                    if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                    _DocumentLine(contract: c, document: others[i]),
                  ],
                ],
              ),
            ),
          ],
          if (canAdd) ...[
            const SizedBox(height: 14),
            _AddButton(
              onTap: () async {
                if (await showAddDocumentSheet(context, c) && context.mounted) onChanged();
              },
            ),
          ],
          if (mine.isNotEmpty) ...[
            const SizedBox(height: 14),
            _Section('Mes ajouts', AzStatusBadge(label: '${mine.length}', foreground: AzColors.blue700, background: AzColors.blue50, dot: false)),
            const SizedBox(height: 8),
            for (final d in mine) ...[
              _ClientDocumentCard(contract: c, document: d, onChanged: onChanged),
              const SizedBox(height: 10),
            ],
          ],
          const SizedBox(height: 12),
          AzButton('Détails du contrat', variant: AzButtonVariant.secondary, height: 50, radius: 16, onPressed: () => context.push(AppRoutes.contract, c.id)),
        ],
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(this.title, this.badge);

  final String title;
  final Widget? badge;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: Text(title, style: AzText.section)),
        if (badge != null) badge!,
      ],
    );
  }
}

/// Carte bleue du contrat : véhicule (marque, modèle, plaque) ou objet assuré.
class _VehicleCard extends StatelessWidget {
  const _VehicleCard({required this.contract, required this.expanded, required this.collapsible, required this.onToggle});

  final Contract contract;
  final bool expanded;
  final bool collapsible;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final c = contract;
    final v = c.vehicle;
    final title = (v != null && v.title.isNotEmpty) ? v.title : c.insuredObject;
    final plate = v?.plate;
    return Material(
      color: AzColors.blue700,
      borderRadius: BorderRadius.circular(28),
      child: InkWell(
        borderRadius: BorderRadius.circular(28),
        onTap: collapsible ? onToggle : null,
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), borderRadius: BorderRadius.circular(16)),
                    child: Icon(v?.isMoto == true ? Icons.two_wheeler_rounded : Icons.shield_outlined, color: Colors.white),
                  ),
                  const Spacer(),
                  Flexible(child: AzStatusBadge.contract(c.status)),
                  if (collapsible) ...[
                    const SizedBox(width: 8),
                    Icon(expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded, color: Colors.white),
                  ],
                ],
              ),
              const SizedBox(height: 12),
              Text(title, maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.headline.copyWith(color: Colors.white)),
              if (plate != null) ...[
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                  child: Text(plate, style: AzText.caption.copyWith(color: AzColors.ink, fontWeight: FontWeight.w800, letterSpacing: 1)),
                ),
              ],
              const SizedBox(height: 4),
              Text('${c.name} · N° ${c.number}', maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(color: const Color(0xFFD6E0F3), fontSize: 13)),
              if (c.startDate != null || c.endDate != null) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    if (c.startDate != null) Expanded(child: _Fact('Prise d’effet', formatDate(c.startDate))),
                    if (c.startDate != null && c.endDate != null) const SizedBox(width: 8),
                    if (c.endDate != null) Expanded(child: _Fact('Échéance', formatDate(c.endDate))),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(16)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(color: const Color(0xFFD6E0F3), fontSize: 11.5)),
          Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 13.5)),
        ],
      ),
    );
  }
}

/// Renouvellement : visible seulement si le serveur l'ouvre (`renewal.eligible`).
class _RenewalCard extends StatelessWidget {
  const _RenewalCard({required this.contract});

  final Contract contract;

  @override
  Widget build(BuildContext context) {
    final days = contract.renewal?.daysLeft;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AzColors.orange50, borderRadius: BorderRadius.circular(26)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            days == null ? 'Votre contrat arrive à échéance.' : (days <= 0 ? 'Votre contrat expire aujourd’hui.' : 'Votre contrat expire dans $days jour${days > 1 ? 's' : ''}.'),
            style: AzText.titleSmall,
          ),
          const SizedBox(height: 4),
          Text('Renouvelez pour rester couvert sans interruption.', style: AzText.bodyMedium.copyWith(color: AzColors.ink2)),
          const SizedBox(height: 10),
          AzButton('Voir le renouvellement', variant: AzButtonVariant.accent, height: 50, radius: 16, onPressed: () => context.push(AppRoutes.contract, contract.id)),
        ],
      ),
    );
  }
}

/// Attestation : présentation (QR) quand le serveur fournit un code, sinon fichier.
class _AttestationBlock extends StatefulWidget {
  const _AttestationBlock({required this.contract});

  final Contract contract;

  @override
  State<_AttestationBlock> createState() => _AttestationBlockState();
}

class _AttestationBlockState extends State<_AttestationBlock> {
  bool _busy = false;

  Future<void> _download() async {
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadAttestation(widget.contract.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: 'attestation');
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.contract;
    final until = c.attestation?.validUntil ?? c.endDate;
    final canDownload = c.attestation?.available == true;
    return AzCard(
      radius: 26,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const AzIconTile(icon: Icons.qr_code_2_rounded),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Attestation d’assurance', style: AzText.titleSmall),
                    const SizedBox(height: 2),
                    Text(
                      until == null ? 'Remise par AssurZen.' : 'Valable jusqu’au ${formatDate(until)}.',
                      style: AzText.caption.copyWith(fontWeight: FontWeight.w500),
                    ),
                    if (c.hasDigitalAttestation) Text('Disponible hors connexion', style: AzText.caption.copyWith(color: AzColors.success, fontWeight: FontWeight.w700)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (c.hasDigitalAttestation)
            AzButton('Présenter l’attestation', height: 50, radius: 16, icon: Icons.qr_code_2_rounded, onPressed: () => context.push(AppRoutes.attestation, c)),
          if (canDownload) ...[
            if (c.hasDigitalAttestation) const SizedBox(height: 8),
            AzButton(
              'Télécharger l’attestation',
              variant: c.hasDigitalAttestation ? AzButtonVariant.tonal : AzButtonVariant.primary,
              height: 50,
              radius: 16,
              icon: Icons.download_rounded,
              loading: _busy,
              onPressed: _download,
            ),
          ],
        ],
      ),
    );
  }
}

/// Un document mis en avant (vignette) : carte avec téléchargement.
class _DocumentBlock extends StatefulWidget {
  const _DocumentBlock({required this.contract, required this.document, required this.icon, this.hint});

  final Contract contract;
  final ContractDocument document;
  final IconData icon;
  final String? hint;

  @override
  State<_DocumentBlock> createState() => _DocumentBlockState();
}

class _DocumentBlockState extends State<_DocumentBlock> {
  bool _busy = false;

  Future<void> _open() async {
    final d = widget.document;
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadDocument(widget.contract.id, d.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: d.name);
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = widget.document;
    return AzCard(
      radius: 26,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              AzIconTile(icon: widget.icon),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(d.name, style: AzText.titleSmall),
                    if (widget.hint != null) Text(widget.hint!, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
            ],
          ),
          if (d.isAvailable) ...[
            const SizedBox(height: 12),
            AzButton('Télécharger', variant: AzButtonVariant.tonal, height: 50, radius: 16, icon: Icons.download_rounded, loading: _busy, onPressed: _open),
          ],
        ],
      ),
    );
  }
}

class _DocumentLine extends StatefulWidget {
  const _DocumentLine({required this.contract, required this.document});

  final Contract contract;
  final ContractDocument document;

  @override
  State<_DocumentLine> createState() => _DocumentLineState();
}

class _DocumentLineState extends State<_DocumentLine> {
  bool _busy = false;

  Future<void> _open() async {
    final d = widget.document;
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadDocument(widget.contract.id, d.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: d.name);
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = widget.document;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          const AzIconTile(icon: Icons.description_outlined),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(d.name, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700, fontSize: 14)),
                const SizedBox(height: 4),
                AzStatusBadge.document(d.status),
              ],
            ),
          ),
          if (d.isAvailable)
            _busy
                ? const SizedBox(width: 44, height: 44, child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator(strokeWidth: 2.5)))
                : IconButton(
                    tooltip: 'Télécharger ${d.name}',
                    onPressed: _open,
                    icon: const Icon(Icons.download_rounded, color: AzColors.blue700),
                  ),
        ],
      ),
    );
  }
}

/// Bouton pointillé « Ajouter un document ».
class _AddButton extends StatelessWidget {
  const _AddButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: 'Ajouter un document. Contrat, reçu d’achat ou attestation',
      child: Material(
        color: AzColors.blue50,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(26), side: const BorderSide(color: AzColors.blue700, width: 1.6)),
        child: InkWell(
          borderRadius: BorderRadius.circular(26),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                const AzIconTile(icon: Icons.add_rounded, background: AzColors.blue700, color: Colors.white),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Ajouter un document', style: AzText.titleSmall.copyWith(color: AzColors.blue700)),
                      Text('Contrat, reçu d’achat ou attestation', style: AzText.caption.copyWith(color: AzColors.ink2, fontWeight: FontWeight.w500)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Document ajouté par l'assuré : statut donné par le serveur, motif de refus,
/// consultation, remplacement et suppression selon ce que le serveur autorise.
class _ClientDocumentCard extends StatefulWidget {
  const _ClientDocumentCard({required this.contract, required this.document, required this.onChanged});

  final Contract contract;
  final ContractDocument document;
  final VoidCallback onChanged;

  @override
  State<_ClientDocumentCard> createState() => _ClientDocumentCardState();
}

class _ClientDocumentCardState extends State<_ClientDocumentCard> {
  bool _busy = false;

  ContractDocument get d => widget.document;

  Future<void> _view() async {
    setState(() => _busy = true);
    try {
      final file = await context.repos.contracts.downloadDocument(widget.contract.id, d.id);
      if (mounted) await FileViewer.show(context, file, fallbackName: d.name);
    } catch (e) {
      if (mounted) showAzSnack(context, documentErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Supprimer ce document ?'),
        content: Text('« ${d.name} » sera retiré de votre dossier.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuler')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Supprimer')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() => _busy = true);
    try {
      await context.repos.contracts.deleteDocument(contractId: widget.contract.id, documentId: d.id);
      if (mounted) widget.onChanged();
    } catch (e) {
      if (mounted) showAzSnack(context, documentErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _replace() async {
    if (await showAddDocumentSheet(context, widget.contract) && mounted) {
      // Le nouvel envoi est parti : l'ancien document refusé peut être retiré.
      try {
        await context.repos.contracts.deleteDocument(contractId: widget.contract.id, documentId: d.id);
      } catch (_) {}
      if (mounted) widget.onChanged();
    }
  }

  @override
  Widget build(BuildContext context) {
    final rejected = d.status == DocumentStatus.refuse;
    final pending = d.status == DocumentStatus.enVerification;
    return AzCard(
      radius: 26,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const AzIconTile(icon: Icons.description_outlined),
              const SizedBox(width: 12),
              Expanded(child: Text(d.name, maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.titleSmall)),
              const SizedBox(width: 8),
              Flexible(child: AzStatusBadge.document(d.status)),
            ],
          ),
          if (pending) ...[
            const SizedBox(height: 10),
            const AzBanner(
              kind: AzBannerKind.warning,
              text: 'AssurZen vérifie ce document. Il ne remplace pas l’attestation officielle tant qu’il n’est pas validé.',
            ),
          ],
          if (rejected && d.rejectReason != null) ...[
            const SizedBox(height: 10),
            AzBanner(kind: AzBannerKind.error, text: 'Motif : ${d.rejectReason}'),
          ],
          const SizedBox(height: 12),
          if (_busy)
            const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2.5)))
          else
            Row(
              children: [
                if (rejected) ...[
                  Expanded(child: AzButton('Remplacer', height: 50, radius: 16, horizontalPadding: 8, icon: Icons.refresh_rounded, onPressed: _replace)),
                  const SizedBox(width: 8),
                ] else ...[
                  Expanded(
                    child: AzButton('Voir', variant: AzButtonVariant.tonal, height: 50, radius: 16, horizontalPadding: 8, icon: Icons.visibility_outlined, onPressed: _view),
                  ),
                  if (d.removable) const SizedBox(width: 8),
                ],
                if (d.removable)
                  Expanded(
                    child: AzButton('Supprimer', variant: AzButtonVariant.tonal, height: 50, radius: 16, horizontalPadding: 8, icon: Icons.delete_outline_rounded, onPressed: _delete),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}
