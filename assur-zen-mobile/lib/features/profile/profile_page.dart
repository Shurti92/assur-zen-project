import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/security/biometric.dart';
import '../../core/ui.dart';
import '../../data/models/models.dart';
import 'pattern_setup.dart';

/// Profil et paramètres.
class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  List<NotificationPreference> _pushPrefs = const [];
  LegalLinks _legalLinks = LegalLinks.empty;

  @override
  void initState() {
    super.initState();
    // Liens légaux paramétrés dans le back-office : un lien absent est masqué.
    context.repos.support.getLegalLinks().then((links) {
      if (mounted) setState(() => _legalLinks = links);
    }).catchError((_) {});
    context.repos.notifications.getPreferences().then((prefs) {
      if (mounted) setState(() => _pushPrefs = prefs);
    }).catchError((_) {});
  }

  Future<void> _togglePush(NotificationCategory category, bool enabled) async {
    final previous = _pushPrefs;
    // Mise à jour immédiate de l'interrupteur, annulée si le serveur refuse.
    setState(() => _pushPrefs = [for (final p in previous) p.category == category ? NotificationPreference(category, enabled) : p]);
    try {
      final saved = await context.repos.notifications.setPreference(category, enabled);
      if (mounted && saved.isNotEmpty) setState(() => _pushPrefs = saved);
    } catch (e) {
      if (!mounted) return;
      setState(() => _pushPrefs = previous);
      showAzSnack(context, errorMessage(e));
    }
  }

  Future<void> _toggleBiometric(bool value) async {
    final session = context.session;
    if (value && !await Biometric.isAvailable()) {
      if (mounted) showAzSnack(context, 'Aucune empreinte enregistrée sur ce téléphone.');
      return;
    }
    if (value && !await Biometric.authenticate('Activez la connexion par empreinte')) return;
    await session.setBiometric(value);
    if (mounted) setState(() {});
  }

  Future<void> _togglePattern(bool value) async {
    final session = context.session;
    if (!value) {
      await session.clearPattern();
      if (mounted) setState(() {});
      return;
    }
    final dots = await askNewPattern(context);
    if (dots == null) return;
    await session.setPattern(dots);
    if (mounted) {
      setState(() {});
      showAzSnack(context, 'Schéma enregistré. Votre code PIN reste valable pour vous déverrouiller.');
    }
  }

  Future<void> _editProfile(AppUser user) async {
    final updated = await showAzSheet<AppUser>(context, title: 'Informations personnelles', builder: (_) => _EditProfile(user: user));
    if (updated != null && mounted) {
      context.session.updateUser(updated);
      showAzSnack(context, 'Informations mises à jour.');
    }
  }

  Future<void> _logout() async {
    final ok = await showAzConfirm(
      context,
      title: 'Se déconnecter ?',
      message: 'Vous devrez saisir à nouveau votre code PIN.',
      confirmLabel: 'Déconnexion',
      danger: true,
    );
    if (!ok || !mounted) return;
    await context.session.signOut();
    if (mounted) context.resetTo(AppRoutes.login);
  }

  /// Décision 8.5 : suppression du compte depuis l’application.
  Future<void> _deleteAccount() async {
    final ok = await showAzConfirm(
      context,
      title: 'Supprimer votre compte ?',
      message:
          'Votre demande est transmise à AssurZen. Vos contrats en cours et les documents que la loi impose de conserver restent archivés le temps nécessaire, puis sont effacés.',
      confirmLabel: 'Demander la suppression',
      danger: true,
      icon: Icons.delete_outline_rounded,
    );
    if (!ok || !mounted) return;
    final session = context.session;
    // Le PIN est re-saisi et vérifié par le serveur avec la demande.
    final reference = await showAzSheet<String>(
      context,
      title: 'Confirmez votre code PIN',
      builder: (_) => const _DeletePinSheet(),
    );
    if (reference == null || !mounted) return;
    await session.signOut(forgetDevice: true);
    if (!mounted) return;
    context.resetTo(AppRoutes.welcome);
    showAzSnack(context, reference.isEmpty ? 'Demande de suppression enregistrée.' : 'Demande de suppression enregistrée (référence $reference).');
  }

  void _openLegal(String url) => Launch.url(context, url);

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session;
    return ListenableBuilder(
      listenable: session,
      builder: (context, _) {
        final user = session.user;
        if (user == null) return const SizedBox.shrink();
        return SafeArea(
          bottom: false,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AzSpace.screen, 20, AzSpace.screen, 32),
            children: [
              Semantics(header: true, child: const Text('Profil', style: AzText.headline)),
              const SizedBox(height: 16),
              AzCard(
                padding: const EdgeInsets.all(18),
                child: Row(
                  children: [
                    Container(
                      width: 60,
                      height: 60,
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(color: AzColors.blue700, shape: BoxShape.circle),
                      child: Text(initials(user.fullName), style: AzText.title.copyWith(color: Colors.white)),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(user.fullName, style: AzText.section),
                          Text('+225 ${formatPhone(user.phone)}', style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
                          if (Env.useMock) const Text('Données fictives', style: AzText.caption),
                        ],
                      ),
                    ),
                    AzTextLink('Modifier', onPressed: () => _editProfile(user)),
                  ],
                ),
              ),
              _Group(title: 'Mon compte', children: [
                _Item(icon: Icons.person_outline_rounded, label: 'Informations personnelles', onTap: () => _editProfile(user)),
                _Item(icon: Icons.request_quote_outlined, label: 'Mes devis', onTap: () => context.push(AppRoutes.myQuotes)),
                _Item(icon: Icons.lock_outline_rounded, label: 'Modifier mon code PIN', onTap: () => context.push(AppRoutes.forgotPin)),
                _Toggle(
                  icon: Icons.fingerprint,
                  label: 'Connexion par empreinte',
                  value: session.biometricEnabled,
                  onChanged: _toggleBiometric,
                ),
                _Toggle(
                  icon: Icons.pattern,
                  label: 'Déverrouillage par schéma',
                  value: session.patternEnabled,
                  onChanged: _togglePattern,
                ),
                _Item(
                  icon: Icons.receipt_long_outlined,
                  label: 'Paiements et reçus',
                  onTap: () => showAzSnack(context, 'Les reçus sont disponibles dans chaque contrat (onglet Assurances).'),
                ),
              ]),
              // Préférences push par catégorie : tenues par le serveur (la notification
              // reste toujours listée dans l'écran, seul le push est concerné).
              if (_pushPrefs.isNotEmpty)
                _Group(title: 'Notifications', children: [
                  for (final p in _pushPrefs)
                    _Toggle(
                      icon: notificationIcon(p.category),
                      label: p.category.label,
                      value: p.enabled,
                      onChanged: (v) => _togglePush(p.category, v),
                    ),
                ]),
              _Group(title: 'Aide et informations', children: [
                _Item(icon: Icons.support_agent_outlined, label: 'Contact et assistance', onTap: () => context.push(AppRoutes.support)),
                _Item(icon: Icons.help_outline_rounded, label: 'Questions fréquentes', onTap: () => context.push(AppRoutes.support)),
                if (_legalLinks.termsUrl != null)
                  _Item(icon: Icons.description_outlined, label: 'Conditions générales', onTap: () => _openLegal(_legalLinks.termsUrl!)),
                if (_legalLinks.privacyUrl != null)
                  _Item(icon: Icons.privacy_tip_outlined, label: 'Politique de confidentialité', onTap: () => _openLegal(_legalLinks.privacyUrl!)),
                _Item(icon: Icons.delete_outline_rounded, label: 'Supprimer mon compte', danger: true, onTap: _deleteAccount),
              ]),
              const SizedBox(height: 24),
              AzButton(
                'Se déconnecter',
                icon: Icons.logout_rounded,
                variant: AzButtonVariant.dangerOutline,
                onPressed: _logout,
              ),
              const SizedBox(height: 16),
              const AzDemoNote(text: 'AssurZen Mobile · version 1.0.0'),
            ],
          ),
        );
      },
    );
  }
}

class _Group extends StatelessWidget {
  const _Group({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(title.toUpperCase(), style: AzText.overline),
          ),
          AzCard(
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                for (var i = 0; i < children.length; i++) ...[
                  if (i > 0) const Divider(height: 1, indent: 52, color: AzColors.lineSoft),
                  children[i],
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({required this.icon, required this.label, required this.onTap, this.danger = false});

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger ? AzColors.errorStrong : AzColors.ink;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Icon(icon, color: danger ? AzColors.errorStrong : AzColors.blue700, size: 22),
              const SizedBox(width: 14),
              Expanded(child: Text(label, style: AzText.body.copyWith(fontSize: 15, fontWeight: FontWeight.w600, color: color))),
              const Icon(Icons.chevron_right_rounded, color: AzColors.muted),
            ],
          ),
        ),
      ),
    );
  }
}

class _Toggle extends StatelessWidget {
  const _Toggle({required this.icon, required this.label, required this.value, required this.onChanged});

  final IconData icon;
  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: InkWell(
        onTap: () => onChanged(!value),
        child: Padding(
          padding: const EdgeInsets.only(left: 16, right: 8, top: 4, bottom: 4),
          child: Row(
            children: [
              Icon(icon, color: AzColors.blue700, size: 22),
              const SizedBox(width: 14),
              Expanded(child: Text(label, style: AzText.body.copyWith(fontSize: 15, fontWeight: FontWeight.w600))),
              Switch(
                value: value,
                onChanged: onChanged,
                activeTrackColor: AzColors.blue700,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EditProfile extends StatefulWidget {
  const _EditProfile({required this.user});

  final AppUser user;

  @override
  State<_EditProfile> createState() => _EditProfileState();
}

class _EditProfileState extends State<_EditProfile> {
  late final _name = TextEditingController(text: widget.user.fullName);
  late final _email = TextEditingController(text: widget.user.email ?? '');
  String? _nameError;
  String? _emailError;
  String? _formError;
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _nameError = Validators.fullName(_name.text);
      _emailError = Validators.optionalEmail(_email.text);
      _formError = null;
    });
    if (_nameError != null || _emailError != null) return;
    setState(() => _saving = true);
    try {
      // Le serveur enregistre et renvoie le profil à jour ; le téléphone n'est pas modifiable.
      final saved = await context.repos.auth.updateProfile(fullName: _name.text.trim(), email: _email.text.trim());
      if (!mounted) return;
      Navigator.of(context).pop(widget.user.copyWith(fullName: saved.fullName, email: saved.email ?? ''));
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        if (e.field == 'fullName') {
          _nameError = e.message;
        } else if (e.field == 'email') {
          _emailError = e.message;
        } else {
          _formError = e.message;
        }
      });
    } catch (e) {
      if (mounted) setState(() => _formError = errorMessage(e));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AzGap(
      children: [
        AzTextField(label: 'Nom et prénoms', controller: _name, hint: 'Ex. Koné Awa', error: _nameError, capitalization: TextCapitalization.words),
        AzTextField(label: 'E-mail (facultatif)', controller: _email, hint: 'nom@exemple.ci', error: _emailError, keyboardType: TextInputType.emailAddress),
        AzBanner(
          kind: AzBannerKind.info,
          text: 'Le numéro +225 ${formatPhone(widget.user.phone)} sert d’identifiant. Pour le changer, contactez le service client.',
        ),
        if (_formError != null) AzBanner(kind: AzBannerKind.error, text: _formError!),
        AzButton('Enregistrer', loading: _saving, onPressed: _save),
      ],
    );
  }
}

/// Saisie du PIN pour la suppression du compte : le serveur le vérifie avec la
/// demande ; en cas d’erreur (PIN faux, compte verrouillé) elle s’affiche ici.
class _DeletePinSheet extends StatefulWidget {
  const _DeletePinSheet();

  @override
  State<_DeletePinSheet> createState() => _DeletePinSheetState();
}

class _DeletePinSheetState extends State<_DeletePinSheet> {
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
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final reference = await context.repos.auth.requestAccountDeletion(pin: _pin.text);
      if (mounted) Navigator.of(context).pop(reference);
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
        Text(
          'Pour votre sécurité, saisissez votre code PIN afin de confirmer la suppression de votre compte.',
          style: AzText.bodyMedium.copyWith(color: AzColors.muted),
        ),
        AzCodeField(controller: _pin, label: 'Code PIN', autofocus: true, error: _error, onCompleted: (_) => _confirm()),
        AzButton('Confirmer la suppression', loading: _loading, onPressed: _confirm),
      ],
    );
  }
}

