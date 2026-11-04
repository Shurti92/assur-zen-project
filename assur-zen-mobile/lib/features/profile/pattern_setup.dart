import 'package:flutter/material.dart';

import '../../core/security/pattern_lock.dart';
import '../../core/ui.dart';

/// Création du schéma de déverrouillage : tracé, puis confirmation par un second tracé.
/// Renvoie les points du schéma (feuille fermée avec `pop(dots)`), ou null si la feuille est fermée.
class PatternSetupSheet extends StatefulWidget {
  const PatternSetupSheet({super.key});

  @override
  State<PatternSetupSheet> createState() => _PatternSetupSheetState();
}

class _PatternSetupSheetState extends State<PatternSetupSheet> {
  List<int>? _first;
  String? _message;
  bool _error = false;

  void _onDots(List<int> dots) {
    final invalid = PatternLock.validate(dots);
    if (invalid != null) {
      return setState(() {
        _message = invalid;
        _error = true;
      });
    }
    if (_first == null) {
      return setState(() {
        _first = dots;
        _message = 'Dessinez-le une seconde fois pour confirmer.';
        _error = false;
      });
    }
    if (_first!.join() == dots.join()) {
      Navigator.of(context).pop(dots);
    } else {
      setState(() {
        _first = null;
        _message = 'Les deux schémas sont différents. Recommencez.';
        _error = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AzGap(
      gap: 14,
      children: [
        Text(
          _message ?? 'Reliez au moins ${PatternLock.minDots} points pour créer votre schéma.',
          style: AzText.bodyMedium.copyWith(color: _error ? AzColors.error : AzColors.muted),
        ),
        AzPatternPad(error: _error, onCompleted: _onDots),
      ],
    );
  }
}

/// Ouvre la feuille de création du schéma ; null si le client la ferme sans terminer.
Future<List<int>?> askNewPattern(BuildContext context) =>
    showAzSheet<List<int>>(context, title: 'Schéma de déverrouillage', builder: (_) => const PatternSetupSheet());
