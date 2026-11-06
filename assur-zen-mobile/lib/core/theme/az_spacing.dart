import 'package:flutter/widgets.dart';

import 'az_colors.dart';

/// Espacements (dp).
abstract final class AzSpace {
  static const double xs = 4;
  static const double s = 8;
  static const double m = 12;
  static const double l = 16;
  static const double screen = 20; // marge latérale des écrans
  static const double xl = 24;
  static const double xxl = 32;
  static const double xxxl = 40;
}

/// Rayons de bordure (dp).
abstract final class AzRadius {
  static const double tile = 8;
  static const double field = 12;
  static const double button = 14;
  static const double card = 16;
  static const double hero = 22;
  static const double bento = 24; // blocs de l'accueil et des onglets
  static const double sheet = 24;
  static const double pill = 999;
}

/// Élévations : ombres douces, jamais de dégradés.
abstract final class AzShadow {
  static const List<BoxShadow> card = [
    BoxShadow(color: Color(0x0F0F1B33), blurRadius: 2, offset: Offset(0, 1)),
    BoxShadow(color: Color(0x0F0F1B33), blurRadius: 12, offset: Offset(0, 4)),
  ];
  static const List<BoxShadow> floating = [
    BoxShadow(color: Color(0x1F0F1B33), blurRadius: 24, offset: Offset(0, 8)),
  ];
  static const List<BoxShadow> sheet = [
    BoxShadow(color: Color(0x2E0F1B33), blurRadius: 32, offset: Offset(0, -8)),
  ];
  static const List<BoxShadow> fab = [
    BoxShadow(color: Color(0x61FB6404), blurRadius: 16, offset: Offset(0, 6)),
  ];
}

/// Bordure standard d’une carte.
const Border azCardBorder = Border.fromBorderSide(BorderSide(color: AzColors.line));
