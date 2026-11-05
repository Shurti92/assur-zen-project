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
    final visible = items == null ? null : (_unreadOnly ? items.where((n) => !n.read).toList() : items);

    return Scaffold(
      appBar: AzTopBar(
        title: 'Notifications',
        actions: [if (unread > 0) AzTextLink('Tout marquer lu', onPressed: _readAll), const SizedBox(width: 8)],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 12),
            child: AzChipFill<bool>(
              options: const [false, true],
              selected: _unreadOnly,
              minChipWidth: 120,
              labelOf: (unreadOnly) => unreadOnly ? 'Non lues ($unread)' : 'Toutes',
              onSelected: (v) => setState(() => _unreadOnly = v),
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
                            message: _unreadOnly ? 'Aucune notification non lue.' : 'Aucune notification pour le moment.',
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
