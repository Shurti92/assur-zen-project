import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/security/biometric.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import '../shell/main_shell.dart';

class PaymentArgs {
  const PaymentArgs({
    required this.payableReference,
    required this.amount,
    required this.caption,
    required this.objectLabel,
    this.isRenewal = false,
    this.clearDraftOnSuccess = false,
  });

  final String payableReference; // n° de devis ou de contrat
  final int amount;
  final String caption; // « Prime totale · 12 mois »…
  final String objectLabel;
  final bool isRenewal;
  final bool clearDraftOnSuccess;
}

enum _Stage { choose, pending, confirmed, failed, timeout, agency }

/// Paiement : choix du moyen, confirmation par PIN, attente de l’opérateur,
/// résultat. Jamais de « payé » sans confirmation du serveur.
class PaymentPage extends StatefulWidget {
  const PaymentPage({super.key, required this.args});

  final PaymentArgs args;

  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> {
  late final Future<List<PaymentMethod>> _methods = context.repos.payments.getMethods();
  final _number = TextEditingController();
  String _methodId = '';
  String? _numberError;
  _Stage _stage = _Stage.choose;
  bool _starting = false;
  PaymentAttempt? _attempt;
  Timer? _poll;
  DateTime? _startedAt;
  int _rating = 0;
  bool _ratingSent = false;

  @override
  void initState() {
    super.initState();
    final phone = context.session.user?.phone ?? context.store.knownPhone;
    if (phone != null) _number.text = formatPhone(phone);
  }

  @override
  void dispose() {
    _poll?.cancel();
    _number.dispose();
    super.dispose();
  }

  Future<void> _pay(List<PaymentMethod> methods) async {
    final method = methods.firstWhere((m) => m.id == _methodId);
    if (method.needsPhoneNumber) {
      final err = Validators.phone(_number.text);
      if (err != null) return setState(() => _numberError = 'Numéro Mobile Money invalide : 10 chiffres commençant par 01, 05 ou 07.');
    }
    // Décision 4.5 : PIN (ou empreinte) avant chaque paiement.
    final confirmed = await _confirmIdentity();
    if (!confirmed || !mounted) return;

    setState(() => _starting = true);
    try {
      final attempt = await context.repos.payments.initiate(
        payableReference: widget.args.payableReference,
        amount: widget.args.amount,
        methodId: method.id,
        phone: method.needsPhoneNumber ? phoneDigits(_number.text) : null,
      );
      if (!mounted) return;
      if (method.id == 'agence') {
        setState(() {
          _attempt = attempt;
          _stage = _Stage.agency;
        });
        return;
      }
      setState(() {
        _attempt = attempt;
        _stage = _Stage.pending;
        _startedAt = DateTime.now();
      });
      _poll = Timer.periodic(const Duration(seconds: 3), (_) => _checkStatus());
    } catch (e) {
      if (mounted) showAzSnack(context, errorMessage(e));
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  Future<void> _checkStatus() async {
    final ref = _attempt?.reference;
    if (ref == null) return;
    if (_startedAt != null && DateTime.now().difference(_startedAt!) > Env.paymentWaitMax) {
      _poll?.cancel();
      if (mounted) setState(() => _stage = _Stage.timeout);
      return;
    }
    try {
      final a = await context.repos.payments.status(ref);
      if (!mounted || _stage != _Stage.pending) return;
      if (a.status == PaymentStatus.confirmed) {
        _poll?.cancel();
        if (widget.args.clearDraftOnSuccess) await context.store.clearDraft();
        if (mounted) {
          setState(() {
          _attempt = a;
          _stage = _Stage.confirmed;
        });
        }
      } else if (a.status == PaymentStatus.failed) {
        _poll?.cancel();
        setState(() {
          _attempt = a;
          _stage = _Stage.failed;
        });
      }
    } catch (_) {
      // Coupure réseau passagère : on réessaie au prochain passage.
    }
  }

  /// Avis après souscription : envoyé au serveur (une seule fois par passage).
  Future<void> _rate(int stars) async {
    if (_ratingSent) return;
    setState(() => _rating = stars);
    try {
      await context.repos.support.sendFeedback(rating: stars);
      _ratingSent = true;
      if (!mounted) return;
      // Décision 7.5 : invitation Play Store seulement après 4 ou 5.
      showAzSnack(context, stars >= 4 ? 'Merci ! Notez-nous aussi sur le Play Store.' : 'Merci pour votre retour.');
    } catch (e) {
      if (mounted) {
        setState(() => _rating = 0);
        showAzSnack(context, errorMessage(e));
      }
    }
  }

  Future<bool> _confirmIdentity() async {
    final session = context.session;
    if (session.biometricEnabled && await Biometric.isAvailable()) {
      final ok = await Biometric.authenticate('Confirmez le paiement de ${formatFcfa(widget.args.amount)}');
      if (ok) return true;
    }
    if (!mounted) return false;
    final ok = await showAzSheet<bool>(
      context,
      title: 'Confirmez le paiement',
      builder: (_) => _PinConfirm(amount: widget.args.amount),
    );
    return ok ?? false;
  }

  void _finish() {
    context.resetTo(AppRoutes.shell, widget.args.isRenewal ? ShellTab.contracts : ShellTab.home);
    showAzSnack(context, 'Merci ! Votre contrat apparaîtra dès sa validation par AssurZen.');
  }

  @override
  Widget build(BuildContext context) {
    final busy = _stage == _Stage.pending;
    return PopScope(
      canPop: !busy,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && busy) showAzSnack(context, 'Paiement en cours : attendez la confirmation de l’opérateur.');
      },
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AzTopBar(
          title: 'Paiement',
          showBack: !busy && _stage != _Stage.confirmed,
        ),
        body: FutureBuilder<List<PaymentMethod>>(
          future: _methods,
          builder: (context, snap) {
            if (snap.hasError) {
              return AzErrorState(message: errorMessage(snap.error!), onRetry: () => Navigator.of(context).pop());
            }
            if (!snap.hasData) return const AzLoader();
            final methods = snap.data!;
            if (methods.isEmpty) {
              return const Padding(
                padding: EdgeInsets.all(AzSpace.screen),
                child: AzBanner(
                  kind: AzBannerKind.info,
                  text: 'Le paiement en ligne n’est pas encore disponible. Contactez un conseiller AssurZen pour régler votre prime.',
                ),
              );
            }
            // Le moyen par défaut est le premier actif déclaré par le serveur.
            if (!methods.any((m) => m.id == _methodId)) _methodId = methods.first.id;
            return switch (_stage) {
              _Stage.choose => _choose(methods),
              _Stage.pending => _pending(methods),
              _Stage.confirmed => _confirmed(),
              _Stage.failed => _failed(methods),
              _Stage.timeout => _timeout(),
              _Stage.agency => _agency(),
            };
          },
        ),
      ),
    );
  }

  Widget _amountCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AzColors.background, borderRadius: BorderRadius.circular(16)),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.args.caption, style: AzText.caption.copyWith(fontSize: 13)),
                Text(widget.args.objectLabel, style: AzText.titleSmall),
              ],
            ),
          ),
          Text(formatFcfa(widget.args.amount), style: AzText.amount.copyWith(fontSize: 20)),
        ],
      ),
    );
  }

  Widget _choose(List<PaymentMethod> methods) {
    final method = methods.firstWhere((m) => m.id == _methodId, orElse: () => methods.first);
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 4, AzSpace.screen, 24),
            children: [
              _amountCard(),
              const SizedBox(height: 20),
              const Text('Moyen de paiement', style: AzText.label),
              const SizedBox(height: 10),
              for (final m in methods) ...[
                AzOptionTile(
                  title: m.name,
                  subtitle: m.subtitle,
                  selected: m.id == _methodId,
                  onTap: () => setState(() {
                    _methodId = m.id;
                    _numberError = null;
                  }),
                  leading: Container(
                    width: 40,
                    height: 40,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: Color(m.brandColor), borderRadius: BorderRadius.circular(12)),
                    child: Text(m.shortLabel, style: AzText.caption.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                  ),
                ),
                const SizedBox(height: 10),
              ],
              if (method.needsPhoneNumber) ...[
                const SizedBox(height: 6),
                AzPhoneField(controller: _number, label: 'Numéro ${method.name} à débiter', error: _numberError),
              ],
              const SizedBox(height: 14),
              if (method.simulated) ...[
                const AzBanner(
                  kind: AzBannerKind.warning,
                  text: 'Paiement simulé (environnement de test) : aucun argent n’est débité.',
                ),
                const SizedBox(height: 10),
              ],
              const AzBanner(
                kind: AzBannerKind.info,
                text: 'Aucun frais n’est ajouté. Le paiement n’est confirmé qu’après la réponse de l’opérateur.',
              ),
            ],
          ),
        ),
        AzBottomBar(children: [
          AzButton(
            method.id == 'agence' ? 'Recevoir la référence de paiement' : 'Payer ${formatFcfa(widget.args.amount)}',
            loading: _starting,
            onPressed: () => _pay(methods),
          ),
        ]),
      ],
    );
  }

  Widget _pending(List<PaymentMethod> methods) {
    final m = methods.firstWhere((x) => x.id == _methodId, orElse: () => methods.first);
    return ListView(
      padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
      children: [
        const SizedBox(
          height: 72,
          child: Center(
            child: SizedBox(
              width: 64,
              height: 64,
              child: CircularProgressIndicator(strokeWidth: 5, color: AzColors.blue700, backgroundColor: AzColors.blue100),
            ),
          ),
        ),
        const SizedBox(height: 16),
        const Text('Paiement en attente', textAlign: TextAlign.center, style: AzText.headline),
        const SizedBox(height: 8),
        Text(
          'Validez l’opération sur votre téléphone (${m.name}). Nous attendons la confirmation de l’opérateur : ne fermez pas l’application.',
          textAlign: TextAlign.center,
          style: AzText.body.copyWith(color: AzColors.muted),
        ),
        const SizedBox(height: 20),
        AzSummaryCard(rows: [
          ('Montant', formatFcfa(widget.args.amount)),
          ('Référence', _attempt?.reference ?? '—'),
        ]),
        if (Env.useMock) ...[
          const SizedBox(height: 16),
          const AzDemoNote(text: 'Démo : confirmation automatique après 5 s · un numéro finissant par 99 simule un échec'),
        ],
      ],
    );
  }

  Widget _resultIcon(IconData icon, Color fg, Color bg) => Center(
        child: Container(
          width: 88,
          height: 88,
          decoration: BoxDecoration(color: bg, shape: BoxShape.circle),
          child: Icon(icon, size: 44, color: fg),
        ),
      );

  Widget _confirmed() {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
            children: [
              _resultIcon(Icons.check_rounded, AzColors.success, AzColors.successBg),
              const SizedBox(height: 16),
              const Text('Paiement confirmé', textAlign: TextAlign.center, style: AzText.headline),
              const SizedBox(height: 8),
              Text(
                widget.args.isRenewal
                    ? 'Votre contrat est renouvelé dès réception de la confirmation définitive par AssurZen.'
                    : 'Votre souscription est enregistrée. Le contrat sera actif après validation par AssurZen.',
                textAlign: TextAlign.center,
                style: AzText.body.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 20),
              AzSummaryCard(rows: [
                ('Montant payé', formatFcfa(widget.args.amount)),
                ('Référence', _attempt?.reference ?? '—'),
                if (Env.useMock) ('Attestation', 'En cours de génération'),
              ]),
              const SizedBox(height: 12),
              if (_attempt?.simulated == true) ...[
                const AzBanner(
                  kind: AzBannerKind.warning,
                  text: 'Paiement simulé (environnement de test) : aucun argent n’a été débité.',
                ),
                const SizedBox(height: 10),
              ],
              const AzBanner(
                kind: AzBannerKind.info,
                text: Env.useMock
                    ? 'Reçu envoyé par SMS. Vous serez notifié dès que l’attestation est disponible dans « Mes assurances ».'
                    : 'Retrouvez votre contrat dans « Mes assurances ».',
              ),
              const SizedBox(height: 16),
              AzCard(
                child: Column(
                  children: [
                    const Text('Comment s’est passée votre souscription ?', style: AzText.titleSmall, textAlign: TextAlign.center),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        for (var i = 1; i <= 5; i++)
                          IconButton(
                            tooltip: '$i sur 5',
                            onPressed: () => _rate(i),
                            icon: Icon(i <= _rating ? Icons.star_rounded : Icons.star_outline_rounded, color: AzColors.orange500, size: 32),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        AzBottomBar(children: [AzButton('Terminer', onPressed: _finish)]),
      ],
    );
  }

  Widget _failed(List<PaymentMethod> methods) {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
            children: [
              _resultIcon(Icons.close_rounded, AzColors.error, AzColors.errorBg),
              const SizedBox(height: 16),
              const Text('Paiement non abouti', textAlign: TextAlign.center, style: AzText.headline),
              const SizedBox(height: 8),
              Text(
                'L’opérateur n’a pas confirmé la transaction. Aucun montant n’a été débité.',
                textAlign: TextAlign.center,
                style: AzText.body.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 20),
              AzSummaryCard(rows: [
                ('Montant', formatFcfa(widget.args.amount)),
                ('Motif', _attempt?.failureReason ?? 'Non précisé'),
              ]),
            ],
          ),
        ),
        AzBottomBar(children: [
          AzButton('Réessayer', onPressed: () => setState(() => _stage = _Stage.choose)),
          AzButton.ghost('Choisir un autre moyen', onPressed: () => setState(() => _stage = _Stage.choose)),
        ]),
      ],
    );
  }

  Widget _timeout() {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
            children: [
              _resultIcon(Icons.hourglass_top_rounded, AzColors.warning, AzColors.warningBg),
              const SizedBox(height: 16),
              const Text('Paiement en cours de vérification', textAlign: TextAlign.center, style: AzText.headline),
              const SizedBox(height: 8),
              Text(
                'L’opérateur n’a pas encore répondu. Vous recevrez une notification dès la confirmation. Ne relancez pas le paiement pour éviter un double débit.',
                textAlign: TextAlign.center,
                style: AzText.body.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 20),
              AzSummaryCard(rows: [('Référence', _attempt?.reference ?? '—')]),
            ],
          ),
        ),
        AzBottomBar(children: [AzButton('Retour à l’accueil', onPressed: () => context.resetTo(AppRoutes.shell, ShellTab.home))]),
      ],
    );
  }

  Widget _agency() {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 24, AzSpace.screen, 24),
            children: [
              _resultIcon(Icons.storefront_outlined, AzColors.blue700, AzColors.blue50),
              const SizedBox(height: 16),
              const Text('Référence envoyée par SMS', textAlign: TextAlign.center, style: AzText.headline),
              const SizedBox(height: 8),
              Text(
                () {
                  final address = ContactDirectory.current.address;
                  return 'Présentez cette référence en agence${address == null ? '' : ' ($address)'} pour régler votre prime. Le contrat est activé à réception du paiement.';
                }(),
                textAlign: TextAlign.center,
                style: AzText.body.copyWith(color: AzColors.muted),
              ),
              const SizedBox(height: 20),
              AzSummaryCard(rows: [
                ('Référence', _attempt?.reference ?? '—'),
                ('Montant', formatFcfa(widget.args.amount)),
              ]),
            ],
          ),
        ),
        AzBottomBar(children: [AzButton('Terminer', onPressed: _finish)]),
      ],
    );
  }
}

/// Saisie du PIN avant paiement (dans une feuille du bas).
class _PinConfirm extends StatefulWidget {
  const _PinConfirm({required this.amount});

  final int amount;

  @override
  State<_PinConfirm> createState() => _PinConfirmState();
}

class _PinConfirmState extends State<_PinConfirm> {
  final _pin = TextEditingController();
  String? _error;
  bool _loading = false;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  Future<void> _confirm() async {
    final err = Validators.pin(_pin.text);
    if (err != null) return setState(() => _error = err);
    setState(() => _loading = true);
    try {
      // Vérification du PIN par le serveur, sans ouvrir de nouvelle session.
      await context.repos.auth.verifyPin(_pin.text);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _error = errorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AzGap(
      gap: 14,
      children: [
        Text('Saisissez votre code PIN pour autoriser le paiement de ${formatFcfa(widget.amount)}.',
            style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
        AzCodeField(controller: _pin, label: 'Code PIN', autofocus: true, error: _error, onCompleted: (_) => _confirm()),
        AzButton('Confirmer', loading: _loading, onPressed: _confirm),
      ],
    );
  }
}
