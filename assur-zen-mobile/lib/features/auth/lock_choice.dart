import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../core/security/biometric.dart';
import '../../core/ui.dart';
import '../profile/pattern_setup.dart';

/// Choix du client : comment rouvrir l'application après une absence.
typedef LockChoice = ({bool biometric, bool pattern});

/// Propose une seule fois par appareil de choisir le mode de déverrouillage (empreinte, schéma),
/// le code PIN restant toujours disponible. Sans effet si le choix a déjà été fait, ou si le client
/// n'est pas connecté. Le choix se modifie ensuite dans le profil.
Future<void> askLockChoice(BuildContext context) async {
  final session = context.session;
  if (!session.isLoggedIn || session.isLocked || session.lockChoiceDone) return;

  final bioAvailable = await Biometric.isAvailable();
  if (!context.mounted) return;
  final choice = await showAzSheet<LockChoice>(
    context,
    title: 'Comment protéger votre appli ?',
    builder: (_) => _LockChoiceSheet(
      biometricAvailable: bioAvailable,
      biometric: session.biometricEnabled,
      pattern: session.patternEnabled,
    ),
  );
  // Fermée sans valider : le code PIN seul, et on ne redemande pas.
  await session.setLockChoiceDone(true);
  if (choice == null || !context.mounted) return;

  if (choice.biometric && !session.biometricEnabled) {
    if (await Biometric.authenticate('Activez la connexion par empreinte')) {
      await session.setBiometric(true);
    } else if (context.mounted) {
      showAzSnack(context, 'Empreinte non activée. Vous pourrez le faire dans votre profil.');
    }
  } else if (!choice.biometric && session.biometricEnabled) {
    await session.setBiometric(false);
  }
  if (!context.mounted) return;

  if (choice.pattern && !session.patternEnabled) {
    final dots = await askNewPattern(context);
    if (dots != null) {
      await session.setPattern(dots);
    } else if (context.mounted) {
      showAzSnack(context, 'Schéma non créé. Vous pourrez le faire dans votre profil.');
    }
  } else if (!choice.pattern && session.patternEnabled) {
    await session.clearPattern();
  }
}

class _LockChoiceSheet extends StatefulWidget {
  const _LockChoiceSheet({required this.biometricAvailable, required this.biometric, required this.pattern});

  final bool biometricAvailable;
  final bool biometric;
  final bool pattern;

  @override
  State<_LockChoiceSheet> createState() => _LockChoiceSheetState();
}

class _LockChoiceSheetState extends State<_LockChoiceSheet> {
  late bool _biometric = widget.biometric && widget.biometricAvailable;
  late bool _pattern = widget.pattern;

  Widget _check(bool on) => Icon(
        on ? Icons.check_circle_rounded : Icons.circle_outlined,
        color: on ? AzColors.blue700 : AzColors.fieldBorder,
      );

  @override
  Widget build(BuildContext context) {
    return AzGap(
      gap: 12,
      children: [
        Text(
          'Choisissez comment rouvrir AssurZen après une absence. Votre code PIN reste toujours disponible, '
          'et vous pourrez changer ce choix dans votre profil.',
          style: AzText.bodyMedium.copyWith(color: AzColors.muted),
        ),
        AzOptionTile(
          title: 'Code PIN',
          subtitle: 'Toujours disponible',
          selected: true,
          showRadio: false,
          leading: const Icon(Icons.pin_outlined, color: AzColors.blue700),
          trailing: _check(true),
          onTap: () {},
        ),
        if (widget.biometricAvailable)
          AzOptionTile(
            title: 'Empreinte digitale',
            subtitle: 'Rouvrez l’appli d’un geste.',
            selected: _biometric,
            showRadio: false,
            leading: const Icon(Icons.fingerprint, color: AzColors.blue700),
            trailing: _check(_biometric),
            onTap: () => setState(() => _biometric = !_biometric),
          ),
        AzOptionTile(
          title: 'Schéma',
          subtitle: 'Dessinez un tracé sur une grille de 9 points.',
          selected: _pattern,
          showRadio: false,
          leading: const Icon(Icons.pattern, color: AzColors.blue700),
          trailing: _check(_pattern),
          onTap: () => setState(() => _pattern = !_pattern),
        ),
        AzButton('Continuer', onPressed: () => Navigator.of(context).pop<LockChoice>((biometric: _biometric, pattern: _pattern))),
      ],
    );
  }
}
