import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/env.dart';
import '../../app/routes.dart';
import '../../core/network/api_exception.dart';
import '../../core/security/biometric.dart';
import '../../core/ui.dart';
import '../../data/models/engagement.dart';
import '../../data/models/models.dart';
import '../support/support_page.dart' show SupportPage;
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
  ProtectionScore? _score;

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
    _loadScore();
  }

  /// Score de protection de la carte d'identité : seulement si le serveur l'active et que le client a donné son accord.
  /// Toute erreur masque simplement la ligne, le profil n'en dépend pas.
  Future<void> _loadScore() async {
    try {
      final repo = context.repos.engagement;
      final e = await repo.getEngagement();
      if (!e.showsScore || !e.consent.accepted) return;
      final score = await repo.getProtectionScore();
      if (mounted) setState(() => _score = score);
    } catch (_) {
      // pas de ligne de score
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
              _IdentityCard(user: user, score: _score, onEdit: () => _editProfile(user)),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: _ShortcutTile(icon: Icons.request_quote_outlined, label: 'Mes devis', onTap: () => context.push(AppRoutes.myQuotes)),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _ShortcutTile(
                      icon: Icons.receipt_long_outlined,
                      label: 'Paiements et reçus',
                      onTap: () => showAzSnack(context, 'Les reçus sont disponibles dans chaque contrat (onglet Assurances).'),
                    ),
                  ),
                ],
              ),
              _Group(title: 'Sécurité', children: [
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
              ]),
              // Préférences de notification (push, et SMS quand le serveur les propose) : tenues par le serveur.
              if (_pushPrefs.isNotEmpty)
                _Group(title: 'Notifications', children: [
                  _Item(icon: Icons.notifications_none_rounded, label: 'Mes notifications', onTap: () => context.push(AppRoutes.notificationSettings)),
                ]),
              _Group(title: 'Aide et informations', children: [
                _Item(icon: Icons.support_agent_outlined, label: 'Contact et assistance', onTap: () => context.push(AppRoutes.support)),
                _Item(icon: Icons.help_outline_rounded, label: 'Questions fréquentes', onTap: () => context.push(AppRoutes.support, SupportPage.faqArg)),
                if (_legalLinks.termsUrl != null)
                  _Item(icon: Icons.description_outlined, label: 'Conditions générales', onTap: () => _openLegal(_legalLinks.termsUrl!)),
                if (_legalLinks.privacyUrl != null)
                  _Item(icon: Icons.privacy_tip_outlined, label: 'Politique de confidentialité', onTap: () => _openLegal(_legalLinks.privacyUrl!)),
              ]),
              const SizedBox(height: 24),
              Semantics(
                button: true,
                label: 'Se déconnecter',
                excludeSemantics: true,
                child: Material(
                  color: Colors.transparent,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18), side: const BorderSide(color: AzColors.error, width: 2)),
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: _logout,
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 50),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.logout_rounded, color: AzColors.error, size: 18),
                          const SizedBox(width: 8),
                          Text('Se déconnecter', style: AzText.button.copyWith(color: AzColors.error, fontSize: 14.5)),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              // Suppression du compte : hors de la liste pour éviter un toucher par erreur (le PIN reste redemandé).
              const SizedBox(height: 8),
              Center(child: AzTextLink('Supprimer mon compte', color: AzColors.errorStrong, onPressed: _deleteAccount)),
              const SizedBox(height: 8),
              const AzDemoNote(text: 'AssurZen Mobile · version 1.0.0'),
            ],
          ),
        );
      },
    );
  }
}

/// Carte bleue d'identité : initiales, nom, numéro, Modifier, score de protection quand il existe.
class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.user, required this.score, required this.onEdit});

  final AppUser user;
  final ProtectionScore? score;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final s = score;
    return AzCard(
      color: AzColors.blue700,
      borderColor: null,
      radius: 28,
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 60,
                height: 60,
                alignment: Alignment.center,
                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                child: ExcludeSemantics(child: Text(initials(user.fullName), style: AzText.title.copyWith(color: AzColors.blue700))),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(user.fullName, maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.section.copyWith(color: Colors.white, fontSize: 18)),
                    // Le numéro tient toujours sur une seule ligne : il rétrécit légèrement au lieu de passer à la ligne.
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerLeft,
                      child: Text('+225 ${formatPhone(user.phone)}', maxLines: 1, softWrap: false, style: AzText.bodyMedium.copyWith(color: AzColors.blue100)),
                    ),
                    if (Env.useMock) Text('Données fictives', style: AzText.caption.copyWith(color: AzColors.blue100)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              // Modifier : le crayon seul suffit (libellé conservé pour l'accessibilité).
              Tooltip(
                message: 'Modifier',
                child: Semantics(
                  button: true,
                  label: 'Modifier mes informations',
                  excludeSemantics: true,
                  child: Material(
                    color: Colors.white,
                    shape: const CircleBorder(),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: onEdit,
                      child: const SizedBox(width: 44, height: 44, child: Icon(Icons.edit_outlined, color: AzColors.blue700, size: 20)),
                    ),
                  ),
                ),
              ),
            ],
          ),
          if (s != null) ...[
            const SizedBox(height: 14),
            AzCard(
              color: AzColors.blue900,
              borderColor: null,
              radius: 18,
              onTap: () => context.push(AppRoutes.protectionScore),
              semanticLabel: 'Ma protection : ${s.score} %',
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: [
                  Text('${s.score}', style: AzText.amount.copyWith(color: Colors.white, fontSize: 20)),
                  const SizedBox(width: 12),
                  Expanded(child: Text('Ma protection · ${s.todo.isEmpty ? 'tout est en règle' : s.todo.first.label}', maxLines: 2, overflow: TextOverflow.ellipsis, style: AzText.bodyMedium.copyWith(color: AzColors.blue100, fontSize: 13))),
                  const Icon(Icons.chevron_right_rounded, color: AzColors.blue100, size: 20),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Bloc de raccourci (Mes devis, Paiements et reçus).
class _ShortcutTile extends StatelessWidget {
  const _ShortcutTile({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AzCard(
      radius: AzRadius.bento,
      onTap: onTap,
      semanticLabel: label,
      padding: const EdgeInsets.all(14),
      child: SizedBox(
        height: 72,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: AzColors.blue700, size: 26),
            const Spacer(),
            Text(label, maxLines: 2, style: AzText.label.copyWith(color: AzColors.ink, fontWeight: FontWeight.w700, height: 1.2)),
          ],
        ),
      ),
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
            radius: AzRadius.bento,
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
  const _Item({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const color = AzColors.ink;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Icon(icon, color: AzColors.blue700, size: 22),
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
              // Interrupteur plein, sans contour : piste bleue ou grise, pastille blanche (maquette).
              Switch(
                value: value,
                onChanged: onChanged,
                thumbColor: const WidgetStatePropertyAll(Colors.white),
                trackColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? AzColors.blue700 : AzColors.line),
                trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
                thumbIcon: const WidgetStatePropertyAll(Icon(null, size: 0)),
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

