import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../theme/az_colors.dart';

/// Grille 3×3 pour dessiner un schéma. Renvoie les points (0 à 8, de gauche à
/// droite puis de haut en bas) dans l'ordre où ils ont été reliés.
class AzPatternPad extends StatefulWidget {
  const AzPatternPad({super.key, required this.onCompleted, this.error = false, this.enabled = true});

  final ValueChanged<List<int>> onCompleted;

  /// Colore le tracé en rouge (schéma refusé).
  final bool error;
  final bool enabled;

  @override
  State<AzPatternPad> createState() => _AzPatternPadState();
}

class _AzPatternPadState extends State<AzPatternPad> {
  static const double _size = 270;
  final List<int> _dots = [];
  Offset? _finger;

  Offset _center(int i) {
    const cell = _size / 3;
    return Offset(cell * (i % 3) + cell / 2, cell * (i ~/ 3) + cell / 2);
  }

  void _touch(Offset p) {
    for (var i = 0; i < 9; i++) {
      if (!_dots.contains(i) && (p - _center(i)).distance < 34) {
        setState(() => _dots.add(i));
        break;
      }
    }
    setState(() => _finger = p);
  }

  void _end() {
    final result = List<int>.of(_dots);
    setState(() {
      _finger = null;
      _dots.clear();
    });
    if (result.isNotEmpty) widget.onCompleted(result);
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Grille de schéma de déverrouillage',
      child: Center(
        // La grille se trouve dans une page ou une feuille défilante : sans cela,
        // un geste vers le bas fait défiler la page et coupe le tracé. Le
        // recognizer « eager » gagne l'arène dès le contact ; les points sont
        // lus directement dans les événements du pointeur.
        child: RawGestureDetector(
          gestures: {
            EagerGestureRecognizer: GestureRecognizerFactoryWithHandlers<EagerGestureRecognizer>(
              EagerGestureRecognizer.new,
              (_) {},
            ),
          },
          child: Listener(
            onPointerDown: widget.enabled ? (e) => _touch(e.localPosition) : null,
            onPointerMove: widget.enabled ? (e) => _touch(e.localPosition) : null,
            onPointerUp: widget.enabled ? (_) => _end() : null,
            onPointerCancel: widget.enabled ? (_) => _end() : null,
            child: CustomPaint(
              size: const Size(_size, _size),
              painter: _PatternPainter(List.of(_dots), _finger, widget.error ? AzColors.error : AzColors.blue700, _center),
            ),
          ),
        ),
      ),
    );
  }
}

class _PatternPainter extends CustomPainter {
  _PatternPainter(this.dots, this.finger, this.color, this.center);

  final List<int> dots;
  final Offset? finger;
  final Color color;
  final Offset Function(int) center;

  @override
  void paint(Canvas canvas, Size size) {
    final line = Paint()
      ..color = color.withValues(alpha: 0.6)
      ..strokeWidth = 6
      ..strokeCap = StrokeCap.round;
    for (var i = 1; i < dots.length; i++) {
      canvas.drawLine(center(dots[i - 1]), center(dots[i]), line);
    }
    if (dots.isNotEmpty && finger != null) canvas.drawLine(center(dots.last), finger!, line);
    for (var i = 0; i < 9; i++) {
      final on = dots.contains(i);
      canvas.drawCircle(center(i), on ? 14 : 10, Paint()..color = on ? color : Colors.grey.shade400);
    }
  }

  @override
  bool shouldRepaint(_PatternPainter old) => true;
}
