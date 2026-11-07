import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Centre de notifications : toutes / non lues, lien vers le contrat ou le dossier.
class NotificationsPage extends StatefulWidget {
  const NotificationsPage({super.key});

  @override
  State<NotificationsPage> createState() => _NotificationsPageState();
}

class _NotificationsPageState extends State<NotificationsPage> {
  List<AppNotification>? _items;
  Object? _error;
  bool _unreadOnly = false;
  NotificationCategory? _category; // filtre par type ; null = tous

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (_error != null) setState(() => _error = null);
    try {
      final list = await context.repos.notifications.getNotifications();
      if (!mounted) return;
      setState(() => _items = list..sort((a, b) => b.date.compareTo(a.date)));
      context.session.setUnread(list.where((n) => !n.read).length);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  Future<void> _open(AppNotification n) async {
    if (!n.read) {
      await context.repos.notifications.markRead(n.id);
      if (!mounted) return;
      setState(() => _items = [for (final x in _items!) x.id == n.id ? x.markRead() : x]);
      context.session.setUnread(_items!.where((x) => !x.read).length);
    }
    if (!mounted) return;
    if (n.contractId != null) {
      context.push(AppRoutes.contract, n.contractId);
    } else if (n.claimId != null) {
      context.push(AppRoutes.claim, n.claimId);
    } else if (n.target == 'BADGES') {
      // Nouveau badge : on ouvre la liste des badges (liste fermée de cibles connues).
      context.push(AppRoutes.badges);
    } else if (n.quoteReference != null) {
      // « Paiement reçu » : le contrat n'existe pas encore, on ouvre le devis.
      try {
        final quote = await context.repos.motoQuotes.get(n.quoteReference!);
        if (mounted) context.push(AppRoutes.motoQuoteDetail, quote);
      } catch (e) {
        if (mounted) showAzSnack(context, errorMessage(e));
      }
    }
  }

  Future<void> _readAll() async {
    await context.repos.notifications.markAllRead();
    if (!mounted) return;
    setState(() => _items = [for (final x in _items ?? <AppNotification>[]) x.markRead()]);
    context.session.setUnread(0);
  }

  @override
  Widget build(BuildContext context) {
    final items = _items;
    final unread = items?.where((n) => !n.read).length ?? 0;
    final visible = items?.where((n) => (!_unreadOnly || !n.read) && (_category == null || n.category == _category)).toList();
    // Types réellement présents, dans l'ordre de l'énumération, avec leur compteur.
    final counts = <NotificationCategory, int>{
      for (final c in NotificationCategory.values)
        if (items != null && items.any((n) => n.category == c)) c: items.where((n) => n.category == c).length,
    };

    return Scaffold(
      appBar: AzTopBar(
        title: 'Notifications',
        actions: [if (unread > 0) AzTextLink('Tout marquer lu', onPressed: _readAll), const SizedBox(width: 8)],
      ),
      body: Column(
        children: [
          SizedBox(
            height: 50,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 12),
              children: [
                _FilterChip(
                  label: 'Toutes',
                  count: items?.length,
                  selected: !_unreadOnly && _category == null,
                  onTap: () => setState(() {
                    _unreadOnly = false;
                    _category = null;
                  }),
                ),
                if (unread > 0)
                  _FilterChip(
                    label: 'Non lues',
                    count: unread,
                    selected: _unreadOnly,
                    onTap: () => setState(() {
                      _unreadOnly = true;
                      _category = null;
                    }),
                  ),
                for (final e in counts.entries)
                  _FilterChip(
                    label: e.key.label,
                    count: e.value,
                    selected: _category == e.key,
                    onTap: () => setState(() {
                      _category = e.key;
                      _unreadOnly = false;
                    }),
                  ),
              ],
            ),
          ),
          Expanded(
            child: _error != null
                ? AzErrorState(message: errorMessage(_error!), onRetry: _load)
                : visible == null
                    ? const AzSkeletonList(count: 4)
                    : visible.isEmpty
                        ? AzEmptyState(
                            icon: Icons.notifications_none_rounded,
                            title: 'Vous êtes à jour',
                            // « Toutes » vide : aucune notification du tout ; « Non lues » vide : tout est lu.
                            message: _unreadOnly
                                ? 'Aucune notification non lue.'
                                : _category != null
                                    ? 'Aucune notification de ce type.'
                                    : 'Aucune notification pour le moment.',
                          )
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.separated(
                              padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 24),
                              itemCount: visible.length,
                              separatorBuilder: (_, __) => const SizedBox(height: 10),
                              itemBuilder: (context, i) => _NotificationTile(item: visible[i], onTap: () => _open(visible[i])),
                            ),
                          ),
          ),
        ],
      ),
    );
  }
}

/// Pastille de filtre : libellé + compteur, remplie en bleu quand elle est choisie.
class _FilterChip extends StatelessWidget {
  const _FilterChip({required this.label, required this.selected, required this.onTap, this.count});

  final String label;
  final int? count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final fg = selected ? Colors.white : AzColors.ink;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: Semantics(
        button: true,
        selected: selected,
        label: count == null ? label : '$label, $count',
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
                    Text(label, style: AzText.body.copyWith(fontSize: 13, fontWeight: FontWeight.w700, color: fg)),
                    if (count != null) ...[
                      const SizedBox(width: 6),
                      Text('$count', style: AzText.caption.copyWith(fontSize: 12, fontWeight: FontWeight.w700, color: selected ? const Color(0xFFD6E0F3) : AzColors.muted)),
                    ],
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

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.item, required this.onTap});

  final AppNotification item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (fg, bg) = notificationColors(item.category);
    return AzCard(
      onTap: onTap,
      semanticLabel: '${item.read ? '' : 'Non lue. '}${item.title}',
      color: item.read ? Colors.white : const Color(0xFFF2F5FC),
      padding: const EdgeInsets.all(14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AzIconTile(icon: notificationIcon(item.category), size: 40, background: bg, color: fg),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(child: Text(item.category.label.toUpperCase(), style: AzText.overline)),
                    Text(formatRelative(item.date), style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                  ],
                ),
                const SizedBox(height: 3),
                Text(item.title, style: AzText.body.copyWith(fontSize: 15, height: 20 / 15, fontWeight: item.read ? FontWeight.w600 : FontWeight.w800)),
                const SizedBox(height: 2),
                Text(item.body, style: AzText.caption.copyWith(fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          if (!item.read)
            Container(
              margin: const EdgeInsets.only(left: 8, top: 4),
              width: 10,
              height: 10,
              decoration: const BoxDecoration(color: AzColors.blue700, shape: BoxShape.circle),
            ),
        ],
      ),
    );
  }
}
