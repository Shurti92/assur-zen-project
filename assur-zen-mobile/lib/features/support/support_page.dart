import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../auth/auth_layout.dart';

/// Contact et assistance : accès rapides, urgence, questions fréquentes, formulaire, demandes, coordonnées.
/// Tout vient du serveur (coordonnées, horaires, sujets, demandes, FAQ) ; une rubrique sans donnée disparaît.
class SupportPage extends StatefulWidget {
  const SupportPage({super.key, this.focusFaq = false});

  /// Argument de route : ouvre la page directement sur les questions fréquentes.
  static const faqArg = 'faq';

  final bool focusFaq;

  @override
  State<SupportPage> createState() => _SupportPageState();
}

class _SupportPageState extends State<SupportPage> {
  final _message = TextEditingController();
  final _search = TextEditingController();
  String? _topicId;
  String? _error;
  SupportReceipt? _sent;
  bool _sending = false;
  bool _allQuestions = false;
  int _requestsVersion = 0;
  final _faqKey = GlobalKey();
  bool _scrolledToFaq = false;

  /// Nombre de questions montrées avant « Voir les N questions ».
  static const _faqPreview = 3;

  @override
  void dispose() {
    _message.dispose();
    _search.dispose();
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
    return ValueListenableBuilder<ContactInfo>(
      valueListenable: ContactDirectory.info,
      builder: (context, info, _) {
        final availability = info.availabilityText;
        final hasQuick = info.primaryPhone != null || info.whatsapp != null || info.email != null;
        return AuthLayout(
          title: 'Contact et assistance',
          subtitle: availability == null ? 'Nous vous répondons.' : '$availability · Nous vous répondons',
          showBack: true,
          titleSize: 27,
          sheetColor: AzColors.background,
          children: [
            if (hasQuick) _QuickActions(info: info),
            if (info.primaryPhone != null) _UrgencyCard(info: info),
            _faq(),
            _writeSection(),
            // Mes demandes et réponses du conseiller (client connecté).
            if (context.session.isLoggedIn)
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
                            AzSectionHeader('Mes demandes', trailing: Text('${requests.length} demande${requests.length > 1 ? 's' : ''}', style: AzText.label.copyWith(color: AzColors.blue700))),
                            const SizedBox(height: 10),
                            for (final r in requests) ...[_RequestCard(request: r), const SizedBox(height: 10)],
                          ],
                        ),
                ),
              ),
            _Coordinates(info: info),
          ],
        );
      },
    );
  }

  /// Questions fréquentes : recherche, trois questions, puis « Voir les N questions ». Masquée si la FAQ est vide.
  Widget _faq() {
    return AsyncView<List<FaqItem>>(
      load: () => context.repos.support.getFaq(),
      loading: const SizedBox.shrink(),
      builder: (context, faq, _) {
        if (faq.isEmpty) return const SizedBox.shrink();
        final q = _search.text.trim().toLowerCase();
        final matches = q.isEmpty ? faq : faq.where((f) => '${f.question} ${f.answer}'.toLowerCase().contains(q)).toList();
        // Recherche en cours : tous les résultats. Sinon, trois questions sauf si le client en demande plus.
        final shown = (q.isNotEmpty || _allQuestions) ? matches : matches.take(_faqPreview).toList();
        // Ouvert depuis « Questions fréquentes » du profil : on descend jusqu'à la FAQ, une seule fois, quand elle est affichée.
        if (widget.focusFaq && !_scrolledToFaq) {
          _scrolledToFaq = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            final ctx = _faqKey.currentContext;
            if (ctx != null) Scrollable.ensureVisible(ctx, duration: const Duration(milliseconds: 300), curve: Curves.easeOut, alignment: 0.05);
          });
        }
        return Column(
          key: _faqKey,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AzSectionHeader('Questions fréquentes'),
            const SizedBox(height: 8),
            TextField(
              controller: _search,
              onChanged: (_) => setState(() {}),
              textInputAction: TextInputAction.search,
              style: AzText.body.copyWith(fontSize: 15),
              decoration: InputDecoration(
                hintText: 'Rechercher une question',
                prefixIcon: const Icon(Icons.search_rounded, color: AzColors.muted),
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(tooltip: 'Effacer', icon: const Icon(Icons.close_rounded, color: AzColors.muted), onPressed: () => setState(_search.clear)),
                filled: true,
                fillColor: Colors.white,
                contentPadding: const EdgeInsets.symmetric(vertical: 14),
                enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: const BorderSide(color: AzColors.line)),
                focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: const BorderSide(color: AzColors.blue700, width: 2)),
              ),
            ),
            const SizedBox(height: 10),
            if (shown.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Text('Aucune question ne correspond à « ${_search.text.trim()} ».', textAlign: TextAlign.center, style: AzText.body.copyWith(color: AzColors.muted)),
              )
            else
              for (var i = 0; i < shown.length; i++) ...[
                if (i > 0) const SizedBox(height: 10),
                AzAccordion(
                  key: ValueKey('faq-${shown[i].question}'),
                  initiallyOpen: q.isEmpty && i == 0,
                  title: shown[i].question,
                  body: [Text(shown[i].answer, style: AzText.bodyMedium.copyWith(color: AzColors.ink2, fontSize: 13.5))],
                ),
              ],
            if (q.isEmpty && faq.length > _faqPreview)
              Center(
                child: AzTextLink(
                  _allQuestions ? 'Voir moins' : 'Voir les ${faq.length} questions',
                  onPressed: () => setState(() => _allQuestions = !_allQuestions),
                ),
              ),
          ],
        );
      },
    );
  }

  /// Écrire au service client : sujet (du serveur), message, envoi.
  Widget _writeSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
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
                builder: (context, topics, _) => AzChipFill<String>(
                  minChipWidth: 130,
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
              AzButton('Envoyer', variant: AzButtonVariant.accent, height: 52, radius: 16, icon: Icons.send_rounded, loading: _sending, onPressed: _send),
            ],
          ),
      ],
    );
  }
}

/// Trois accès rapides : Appeler, WhatsApp (orange), E-mail. Seuls ceux que le serveur fournit sont affichés.
class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.info});

  final ContactInfo info;

  @override
  Widget build(BuildContext context) {
    final items = <Widget>[
      if (info.primaryPhone != null) _QuickAction(icon: Icons.call_outlined, label: 'Appeler', onTap: () => Launch.phone(context)),
      if (info.whatsapp != null) _QuickAction(icon: Icons.chat_outlined, label: 'WhatsApp', accent: true, onTap: () => Launch.whatsapp(context)),
      if (info.email != null) _QuickAction(icon: Icons.mail_outline_rounded, label: 'E-mail', onTap: () => Launch.email(context)),
    ];
    // Les trois cartes ont la même hauteur : celle de la plus haute (texte agrandi compris).
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < items.length; i++) ...[if (i > 0) const SizedBox(width: 8), Expanded(child: items[i])],
        ],
      ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({required this.icon, required this.label, required this.onTap, this.accent = false});

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool accent;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      color: accent ? AzColors.orange500 : Colors.white,
      borderColor: accent ? AzColors.orange500 : AzColors.line,
      radius: 20,
      onTap: onTap,
      semanticLabel: label,
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
      child: Column(
        children: [
          AzIconTile(icon: icon, size: 38, radius: 14, background: accent ? const Color(0x33FFFFFF) : AzColors.blue50, color: accent ? Colors.white : AzColors.blue700),
          const SizedBox(height: 6),
          Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: AzText.label.copyWith(color: accent ? Colors.white : AzColors.blue700, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

/// Bloc sombre « Urgence · Assistance » : appelle l'assistance. Horaires et numéros d'urgence viennent du serveur.
class _UrgencyCard extends StatelessWidget {
  const _UrgencyCard({required this.info});

  final ContactInfo info;

  @override
  Widget build(BuildContext context) {
    final lines = [
      if (info.availabilityText != null) info.availabilityText!,
      if (info.emergencyNumbers.isNotEmpty)
        'Urgences : ${info.emergencyNumbers.map((e) => e.label.isEmpty ? e.number : '${e.label} ${e.number}').join(', ')}.',
    ];
    return AzCard(
      color: AzColors.ink,
      borderColor: null,
      radius: 26,
      onTap: () => Launch.phone(context),
      semanticLabel: 'Urgence, appeler l’assistance AssurZen',
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          const AzIconTile(icon: Icons.call_rounded, size: 44, radius: 16, background: Color(0x24FFFFFF), color: Color(0xFFFFB27A)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Urgence · Assistance', style: AzText.titleSmall.copyWith(color: Colors.white, fontSize: 15)),
                if (lines.isNotEmpty)
                  Text(lines.join(' '), maxLines: 3, overflow: TextOverflow.ellipsis, style: AzText.caption.copyWith(color: const Color(0xFFB9C3DA), fontWeight: FontWeight.w500, fontSize: 12.5)),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: Color(0xFFB9C3DA), size: 20),
        ],
      ),
    );
  }
}

/// Coordonnées détaillées (téléphones nommés, adresse, horaires, numéros d'urgence) : rubriques absentes masquées.
class _Coordinates extends StatelessWidget {
  const _Coordinates({required this.info});

  final ContactInfo info;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[
      for (final p in info.phones)
        _ContactRow(Icons.call_outlined, p.label.isEmpty ? 'Téléphone' : p.label, formatPhone(p.number), onTap: () => Launch.phone(context, p.number)),
      if (info.whatsapp != null) _ContactRow(Icons.chat_outlined, 'WhatsApp', formatPhone(info.whatsapp!), onTap: () => Launch.whatsapp(context)),
      if (info.email != null) _ContactRow(Icons.mail_outline_rounded, 'E-mail', info.email!, onTap: () => Launch.email(context)),
      if (info.address != null) _ContactRow(Icons.place_outlined, 'Adresse', info.address!),
      if (info.hours != null) _ContactRow(Icons.schedule_rounded, 'Horaires', info.hours!),
      for (final e in info.emergencyNumbers) _ContactRow(Icons.warning_amber_rounded, e.label.isEmpty ? 'Urgence' : e.label, e.number),
    ];
    if (rows.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const AzSectionHeader('Nos coordonnées'),
        const SizedBox(height: 8),
        AzCard(
          radius: 26,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          child: Column(
            children: [
              for (var i = 0; i < rows.length; i++) ...[if (i > 0) const Divider(height: 1, color: AzColors.lineSoft), rows[i]],
            ],
          ),
        ),
      ],
    );
  }
}

class _ContactRow extends StatelessWidget {
  const _ContactRow(this.icon, this.label, this.value, {this.onTap});

  final IconData icon;
  final String label;
  final String value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            AzIconTile(icon: icon, size: 40, iconSize: 18, radius: 14),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: AzText.caption.copyWith(fontSize: 12, fontWeight: FontWeight.w500)),
                  Text(value, style: AzText.bodyMedium.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700)),
                ],
              ),
            ),
            if (onTap != null) const Icon(Icons.chevron_right_rounded, color: AzColors.muted, size: 20),
          ],
        ),
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
    final closed = request.status == SupportStatus.cloture;
    return AzCard(
      radius: 26,
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text('${request.topicLabel} · ${request.reference}', style: AzText.body.copyWith(fontWeight: FontWeight.w800, fontSize: 14))),
              const SizedBox(width: 8),
              AzStatusBadge(
                label: _statusLabel,
                foreground: closed ? AzColors.success : AzColors.blue700,
                background: closed ? AzColors.successBg : AzColors.blue50,
                dot: false,
              ),
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
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(18)),
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

/// « 45 minutes », « 2 h » : durée annoncée par le serveur, jamais en dur.
String _formatMinutes(int minutes) {
  if (minutes < 60) return '$minutes minutes';
  final h = minutes ~/ 60;
  final m = minutes % 60;
  return m == 0 ? '$h h' : '$h h $m';
}
