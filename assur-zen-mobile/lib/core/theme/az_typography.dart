// ignore: unnecessary_import
import 'dart:ui' show FontFeature;

import 'package:flutter/material.dart';

import 'az_colors.dart';

/// Échelle typographique — deux polices embarquées (aucun téléchargement) :
/// Sora pour les titres et les montants, Plus Jakarta Sans pour le texte.
///
/// Tailles : légendes 12, libellés 13, texte secondaire 14, texte 15 ; titres en Sora 700 (30 à 14).
/// Les deux polices sont plus larges que l'ancienne Manrope : on garde donc des corps modérés.
abstract final class AzText {
  /// Titres, montants : caractère et présence.
  static const String titleFamily = 'Sora';

  /// Texte courant, libellés, boutons : lisibilité en petite taille.
  static const String family = 'PlusJakartaSans';

  static const TextStyle display = TextStyle(
      fontFamily: titleFamily, fontSize: 30, height: 38 / 30, fontWeight: FontWeight.w700, letterSpacing: -0.6, color: AzColors.ink);
  static const TextStyle headline = TextStyle(
      fontFamily: titleFamily, fontSize: 22, height: 30 / 22, fontWeight: FontWeight.w700, letterSpacing: -0.4, color: AzColors.ink);
  static const TextStyle title = TextStyle(
      fontFamily: titleFamily, fontSize: 19, height: 26 / 19, fontWeight: FontWeight.w700, letterSpacing: -0.2, color: AzColors.ink);
  static const TextStyle section = TextStyle(
      fontFamily: titleFamily, fontSize: 16, height: 22 / 16, fontWeight: FontWeight.w700, letterSpacing: -0.2, color: AzColors.ink);
  static const TextStyle titleSmall = TextStyle(
      fontFamily: titleFamily, fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w700, color: AzColors.ink);
  static const TextStyle body = TextStyle(
      fontFamily: family, fontSize: 15, height: 22 / 15, fontWeight: FontWeight.w500, color: AzColors.ink);
  static const TextStyle bodyMedium = TextStyle(
      fontFamily: family, fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w500, color: AzColors.ink2);
  static const TextStyle label = TextStyle(
      fontFamily: family, fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w600, color: AzColors.ink2);
  static const TextStyle caption = TextStyle(
      fontFamily: family, fontSize: 12, height: 16 / 12, fontWeight: FontWeight.w600, color: AzColors.muted);
  static const TextStyle overline = TextStyle(
      fontFamily: family, fontSize: 12, height: 16 / 12, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: AzColors.muted);
  static const TextStyle button = TextStyle(
      fontFamily: family, fontSize: 15, height: 20 / 15, fontWeight: FontWeight.w700);
  static const TextStyle amount = TextStyle(
      fontFamily: titleFamily,
      fontSize: 26,
      height: 32 / 26,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.4,
      color: AzColors.ink,
      fontFeatures: [FontFeature.tabularFigures()]);

  static TextTheme textTheme() => const TextTheme(
        displaySmall: display,
        headlineSmall: headline,
        titleLarge: title,
        titleMedium: section,
        titleSmall: titleSmall,
        bodyLarge: body,
        bodyMedium: bodyMedium,
        labelLarge: label,
        labelMedium: caption,
        bodySmall: caption,
      );
}
