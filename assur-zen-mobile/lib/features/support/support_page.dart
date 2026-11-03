import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';

/// Contact et assistance : coordonnées officielles, formulaire, FAQ.
class SupportPage extends StatefulWidget {
  const SupportPage({super.key});

  @override
  State<SupportPage> createState() => _SupportPageState();
}

class _SupportPageState extends State<SupportPage> {
  final _message = TextEditingController();
  String? _topicId;
  String? _error;
  SupportReceipt? _sent;
  bool _sending = false;
  int _requestsVersion = 0;

  @override
  void dispose() {
    _message.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final err = Validators.minLength(_message.text, 10, 'Votre message doit contenir au moins 10 caractères.');
    if (err != null) return setState(() => _error = err);
    if (_topicId == null) return setState(() => _error = 'Choisissez le sujet de votre demande.');
    setState(() => _sending = true);
    try {
      final receipt = await context.repos.support.sendRequest(topicId: _topicId!, message: _message.text.trim());
      if (mounted) {
        setState(() {
          _sent = receipt;
          _requestsVersion++; // recharge « Mes demandes »
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AzTopBar(title: 'Contact et assistance'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 32),
        children: [
          // Coordonnées, horaires et disponibilité : fournis par le serveur, rubriques absentes masquées.
          ValueListenableBuilder<ContactInfo>(
            valueListenable: ContactDirectory.info,
            builder: (context, info, _) {
              final availability = info.availabilityText;
              final rows = <Widget>[
                for (final p in info.phones)
                  _ContactRow(p.label.isEmpty ? 'Téléphone' : p.label, formatPhone(p.number), onTap: () => Launch.phone(context, p.number)),
                if (info.whatsapp != null) _ContactRow('WhatsApp', formatPhone(info.whatsapp!), onTap: () => Launch.whatsapp(context)),
                if (info.email != null) _ContactRow('E-mail', info.email!, onTap: () => Launch.email(context)),
                if (info.address != null) _ContactRow('Adresse', info.address!),
                if (info.hours != null) _ContactRow('Horaires', info.hours!),
                for (final e in info.emergencyNumbers) _ContactRow(e.label.isEmpty ? 'Urgence' : e.label, e.number),
              ];
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  AzHeroCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('On est là pour vous.', style: AzText.title.copyWith(color: Colors.white)),
                        if (availability != null) ...[
                          const SizedBox(height: 4),
                          Text(availability, style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
                        ],
                      ],
                    ),
                  ),
                  if (info.primaryPhone != null || info.whatsapp != null || info.email != null) ...[
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        if (info.primaryPhone != null)
                          Expanded(child: _Action(icon: Icons.call_outlined, label: 'Appeler', onTap: () => Launch.phone(context))),
                        if (info.primaryPhone != null && (info.whatsapp != null || info.email != null)) const SizedBox(width: 10),
                        if (info.whatsapp != null)
                          Expanded(child: _Action(icon: Icons.chat_outlined, label: 'WhatsApp', onTap: () => Launch.whatsapp(context))),
                        if (info.whatsapp != null && info.email != null) const SizedBox(width: 10),
                        if (info.email != null)
                          Expanded(child: _Action(icon: Icons.mail_outline_rounded, label: 'E-mail', onTap: () => Launch.email(context))),
                      ],
                    ),
                  ],
                  if (rows.isNotEmpty) ...[
                    const SizedBox(height: 14),
                    AzCard(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                      child: Column(children: rows),
                    ),
                  ],
                ],
              );
            },
          ),
          const SizedBox(height: 24),
          const AzSectionHeader('Écrire au service client'),
          const SizedBox(height: 10),
          if (_sent != null)
            AzBanner(
              kind: AzBannerKind.success,
              title: 'Demande envoyée.',
              // Délai annoncé seulement s'il vient du serveur (sinon, aucun engagement).
              text: _sent!.expectedWithinMinutes == null
                  ? 'Référence ${_sent!.reference}. Vous recevrez une réponse par SMS ou dans les notifications.'
                  : 'Référence ${_sent!.reference}. Réponse attendue sous ${_formatMinutes(_sent!.expectedWithinMinutes!)}.',
            )
          else
            AzGap(
              gap: 14,
              children: [
                // Sujets fournis par le serveur (paramétrables dans le back-office).
                AsyncView<List<SupportTopic>>(
                  load: () => context.repos.support.getTopics(),
                  loading: const AzSkeletonList(count: 1, padding: EdgeInsets.zero),
                  builder: (context, topics, _) => AzChipWrap<String>(
                    options: [for (final t in topics) t.label],
                    selected: topics.where((t) => t.id == _topicId).map((t) => t.label).firstOrNull,
                    onSelected: (label) => setState(() => _topicId = topics.firstWhere((t) => t.label == label).id),
                  ),
                ),
                AzTextField(
                  label: 'Votre message',
                  controller: _message,
                  hint: 'Décrivez votre demande',
                  maxLines: 4,
                  error: _error,
                  capitalization: TextCapitalization.sentences,
                ),
                AzButton('Envoyer', loading: _sending, onPressed: _send),
              ],
            ),
          // Mes demandes et réponses du conseiller (client connecté).
          if (context.session.isLoggedIn) ...[
            const SizedBox(height: 24),
            KeyedSubtree(
              key: ValueKey(_requestsVersion),
              child: AsyncView<List<SupportRequestItem>>(
                load: () => context.repos.support.getRequests(),
                loading: const AzSkeletonList(count: 1, padding: EdgeInsets.zero),
                builder: (context, requests, _) => requests.isEmpty
                    ? const SizedBox.shrink()
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const AzSectionHeader('Mes demandes'),
                          const SizedBox(height: 10),
                          for (final r in requests) ...[_RequestCard(request: r), const SizedBox(height: 10)],
                        ],
                      ),
              ),
            ),
          ],
          // FAQ gérée dans le back-office : rubrique masquée si elle est vide.
          AsyncView<List<FaqItem>>(
            load: () => context.repos.support.getFaq(),
            loading: const SizedBox.shrink(),
            builder: (context, faq, _) => faq.isEmpty
                ? const SizedBox.shrink()
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const SizedBox(height: 24),
                      const AzSectionHeader('Questions fréquentes'),
                      const SizedBox(height: 10),
                      AzCard(
                        padding: EdgeInsets.zero,
                        child: Column(
                          children: [
                            for (var i = 0; i < faq.length; i++) ...[
                              if (i > 0) const Divider(height: 1, color: AzColors.lineSoft),
                              Theme(
                                data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                                child: ExpansionTile(
                                  title: Text(faq[i].question, style: AzText.body.copyWith(fontSize: 15, fontWeight: FontWeight.w700)),
                                  tilePadding: const EdgeInsets.symmetric(horizontal: 16),
                                  childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                                  iconColor: AzColors.blue700,
                                  collapsedIconColor: AzColors.muted,
                                  expandedAlignment: Alignment.centerLeft,
                                  children: [Text(faq[i].answer, style: AzText.bodyMedium)],
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

/// Une demande au support et, quand il existe, la réponse du conseiller (texte du serveur).
class _RequestCard extends StatelessWidget {
  const _RequestCard({required this.request});

  final SupportRequestItem request;

  String get _statusLabel => switch (request.status) {
        SupportStatus.enAttente => 'En attente',
        SupportStatus.enCours => 'En cours',
        SupportStatus.repondu => 'Répondu',
        SupportStatus.cloture => 'Clôturée',
      };

  @override
  Widget build(BuildContext context) {
    return AzCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text('${request.topicLabel} · ${request.reference}', style: AzText.body.copyWith(fontWeight: FontWeight.w800))),
              Text(_statusLabel, style: AzText.caption.copyWith(fontWeight: FontWeight.w700, color: AzColors.blue700)),
            ],
          ),
          const SizedBox(height: 2),
          Text(formatRelative(request.createdAt), style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
          const SizedBox(height: 8),
          Text(request.message, style: AzText.bodyMedium),
          if (request.response != null) ...[
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(12)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Réponse d’AssurZen', style: AzText.label),
                  const SizedBox(height: 4),
                  Text(request.response!, style: AzText.bodyMedium),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Action extends StatelessWidget {
  const _Action({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      onTap: onTap,
      semanticLabel: label,
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
      child: Column(
        children: [
          AzIconTile(icon: icon),
          const SizedBox(height: 8),
          Text(label, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _ContactRow extends StatelessWidget {
  const _ContactRow(this.label, this.value, {this.onTap});

  final String label;
  final String value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: AzColors.lineSoft))),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                value,
                textAlign: TextAlign.right,
                style: AzText.bodyMedium.copyWith(
                  color: onTap != null ? AzColors.blue700 : AzColors.ink,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// « 45 minutes », « 2 h » : durée annoncée par le serveur, jamais en dur.
String _formatMinutes(int minutes) {
  if (minutes < 60) return '$minutes minutes';
  final h = minutes ~/ 60;
  final m = minutes % 60;
  return m == 0 ? '$h h' : '$h h $m';
}
