import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Mes notifications : canaux push et SMS, puis une grille catégorie × canal.
/// Tout est tenu par le serveur ; l'application n'envoie rien d'autre que le choix de l'assuré.
class NotificationSettingsPage extends StatefulWidget {
  const NotificationSettingsPage({super.key});

  @override
  State<NotificationSettingsPage> createState() => _NotificationSettingsPageState();
}

class _NotificationSettingsPageState extends State<NotificationSettingsPage> {
  NotificationSettings? _settings;
  Object? _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final s = await context.repos.notifications.getSettings();
      if (mounted) setState(() => _settings = s);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  /// Applique un changement tout de suite à l'écran, l'annule si le serveur refuse.
  Future<void> _apply(NotificationSettings optimistic, Future<NotificationSettings> Function() save) async {
    final previous = _settings;
    setState(() {
      _settings = optimistic;
      _saving = true;
    });
    try {
      final saved = await save();
      if (mounted) setState(() => _settings = saved.hasChannels ? saved : optimistic);
    } catch (e) {
      if (!mounted) return;
      setState(() => _settings = previous);
      showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _togglePush(bool v) {
    final s = _settings!;
    return _apply(s.copyWith(pushEnabled: v), () => context.repos.notifications.updateSettings(push: v));
  }

  Future<void> _toggleSms(bool v) async {
    final s = _settings!;
    if (v) {
      // L'accord est explicite : le texte vient du serveur quand il le fournit.
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Recevoir des SMS ?'),
          content: Text(
            s.smsConsentText ??
                'Vous acceptez de recevoir des SMS d’AssurZen${s.smsPhone == null ? '' : ' au ${s.smsPhone}'}. Ils ne contiennent ni montant ni numéro de contrat. Vous pouvez retirer cet accord à tout moment.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuler')),
            TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('J’accepte')),
          ],
        ),
      );
      if (ok != true || !mounted) return;
    }
    final repo = context.repos.notifications;
    return _apply(s.copyWith(smsEnabled: v), () => repo.updateSettings(sms: v, smsConsentVersion: s.smsConsentVersion));
  }

  Future<void> _toggleCategory(CategoryChannels c, {bool? push, bool? sms}) {
    final s = _settings!;
    final updated = c.copyWith(push: push, sms: sms);
    final list = [for (final x in s.categories) x.category == c.category ? updated : x];
    return _apply(s.copyWith(categories: list), () => context.repos.notifications.updateSettings(categories: [updated]));
  }

  @override
  Widget build(BuildContext context) {
    final s = _settings;
    return Scaffold(
      appBar: const AzTopBar(title: 'Mes notifications'),
      body: _error != null
          ? AzErrorState(message: errorMessage(_error!), onRetry: _load)
          : s == null
              ? const AzSkeletonList(count: 3)
              : ListView(
                  padding: EdgeInsets.fromLTRB(AzSpace.screen, 0, AzSpace.screen, 32 + MediaQuery.viewPaddingOf(context).bottom),
                  children: [
                    Text('Choisissez comment être prévenu.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                    const SizedBox(height: 16),
                    const Text('CANAUX', style: AzText.overline),
                    const SizedBox(height: 8),
                    AzCard(
                      radius: 26,
                      padding: EdgeInsets.zero,
                      child: Column(
                        children: [
                          _ChannelRow(
                            icon: Icons.notifications_none_rounded,
                            label: 'Notifications push',
                            sub: 'Sur ce téléphone',
                            value: s.pushEnabled,
                            onChanged: _saving ? null : _togglePush,
                          ),
                          if (s.hasChannels) ...[
                            const Divider(height: 1, indent: 16, endIndent: 16, color: AzColors.lineSoft),
                            _ChannelRow(
                              icon: Icons.sms_outlined,
                              label: 'SMS',
                              sub: s.smsPhone == null ? 'Sur votre numéro' : 'Au ${s.smsPhone}',
                              value: s.smsEnabled,
                              onChanged: _saving ? null : _toggleSms,
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (s.hasChannels) ...[
                      const SizedBox(height: 12),
                      AzBanner(
                        kind: AzBannerKind.info,
                        text: s.smsEnabled
                            ? 'Les SMS ne contiennent ni montant ni numéro de contrat.${s.smsConsentAt == null ? '' : ' Accord donné le ${formatDate(s.smsConsentAt)}.'} Vous pouvez le retirer à tout moment.'
                            : 'Les SMS sont désactivés. Activez-les si vous voulez être prévenu même sans connexion internet.',
                      ),
                    ],
                    const SizedBox(height: 20),
                    Text(s.hasChannels ? 'QUOI, ET PAR QUEL CANAL' : 'TYPES DE NOTIFICATION', style: AzText.overline),
                    const SizedBox(height: 8),
                    if (s.categories.isEmpty)
                      AzCard(radius: 26, child: Text('Aucun réglage disponible pour le moment.', style: AzText.bodyMedium.copyWith(color: AzColors.muted)))
                    else
                      AzCard(
                        radius: 26,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                        child: Column(
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(top: 10, bottom: 4),
                              child: Row(
                                children: [
                                  const Expanded(child: Text('TYPE', style: AzText.overline)),
                                  const SizedBox(width: 56, child: Center(child: Text('PUSH', style: AzText.overline))),
                                  if (s.hasChannels && s.smsEnabled) const SizedBox(width: 56, child: Center(child: Text('SMS', style: AzText.overline))),
                                ],
                              ),
                            ),
                            for (final c in s.categories) ...[
                              const Divider(height: 1, color: AzColors.lineSoft),
                              _CategoryRow(
                                channels: c,
                                showSms: s.hasChannels && s.smsEnabled,
                                enabled: !_saving,
                                onPush: (v) => _toggleCategory(c, push: v),
                                onSms: (v) => _toggleCategory(c, sms: v),
                              ),
                            ],
                          ],
                        ),
                      ),
                    const SizedBox(height: 12),
                    const AzBanner(
                      kind: AzBannerKind.warning,
                      text: 'Écran verrouillé : aucun montant, nom ni numéro de contrat n’y est affiché. Les détails s’ouvrent dans l’application.',
                    ),
                  ],
                ),
    );
  }
}

class _ChannelRow extends StatelessWidget {
  const _ChannelRow({required this.icon, required this.label, required this.sub, required this.value, required this.onChanged});

  final IconData icon;
  final String label;
  final String sub;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: InkWell(
        onTap: onChanged == null ? null : () => onChanged!(!value),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Padding(
            padding: const EdgeInsets.only(left: 16, right: 8, top: 6, bottom: 6),
            child: Row(
              children: [
                AzIconTile(icon: icon, size: 36),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(label, style: AzText.body.copyWith(fontSize: 15, fontWeight: FontWeight.w600)),
                      Text(sub, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
                    ],
                  ),
                ),
                _AzSwitch(value: value, onChanged: onChanged),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CategoryRow extends StatelessWidget {
  const _CategoryRow({required this.channels, required this.showSms, required this.enabled, required this.onPush, required this.onSms});

  final CategoryChannels channels;
  final bool showSms;
  final bool enabled;
  final ValueChanged<bool> onPush;
  final ValueChanged<bool> onSms;

  @override
  Widget build(BuildContext context) {
    final c = channels;
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Row(
        children: [
          Expanded(child: Text(c.category.label, style: AzText.body.copyWith(fontSize: 14.5, fontWeight: FontWeight.w600))),
          SizedBox(
            width: 56,
            child: Center(child: Semantics(label: '${c.category.label}, push', child: _AzSwitch(value: c.push, onChanged: enabled ? onPush : null, compact: true))),
          ),
          if (showSms)
            SizedBox(
              width: 56,
              child: Center(child: Semantics(label: '${c.category.label}, SMS', child: _AzSwitch(value: c.sms, onChanged: enabled ? onSms : null, compact: true))),
            ),
        ],
      ),
    );
  }
}

/// Interrupteur plein de l'application : piste bleue ou grise, pastille blanche.
class _AzSwitch extends StatelessWidget {
  const _AzSwitch({required this.value, required this.onChanged, this.compact = false});

  final bool value;
  final ValueChanged<bool>? onChanged;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final sw = Switch(
      value: value,
      onChanged: onChanged,
      thumbColor: const WidgetStatePropertyAll(Colors.white),
      trackColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? AzColors.blue700 : AzColors.line),
      trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
      thumbIcon: const WidgetStatePropertyAll(Icon(null, size: 0)),
    );
    return compact ? Transform.scale(scale: 0.85, child: sw) : sw;
  }
}
