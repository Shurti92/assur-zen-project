// ignore: unnecessary_import
import 'dart:ui' show FontFeature;

import 'package:flutter/material.dart';

import 'az_colors.dart';

/// Échelle typographique — police Manrope embarquée (aucun téléchargement).
abstract final class AzText {
  static const String family = 'Manrope';

  static const TextStyle display = TextStyle(
      fontFamily: family, fontSize: 32, height: 40 / 32, fontWeight: FontWeight.w800, letterSpacing: -0.6, color: AzColors.ink);
  static const TextStyle headline = TextStyle(
      fontFamily: family, fontSize: 24, height: 32 / 24, fontWeight: FontWeight.w800, letterSpacing: -0.4, color: AzColors.ink);
  static const TextStyle title = TextStyle(
      fontFamily: family, fontSize: 20, height: 28 / 20, fontWeight: FontWeight.w800, letterSpacing: -0.2, color: AzColors.ink);
  static const TextStyle section = TextStyle(
      fontFamily: family, fontSize: 17, height: 24 / 17, fontWeight: FontWeight.w800, letterSpacing: -0.2, color: AzColors.ink);
  static const TextStyle titleSmall = TextStyle(
      fontFamily: family, fontSize: 15, height: 21 / 15, fontWeight: FontWeight.w800, color: AzColors.ink);
  static const TextStyle body = TextStyle(
      fontFamily: family, fontSize: 16, height: 24 / 16, fontWeight: FontWeight.w500, color: AzColors.ink);
  static const TextStyle bodyMedium = TextStyle(
      fontFamily: family, fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w500, color: AzColors.ink2);
  static const TextStyle label = TextStyle(
      fontFamily: family, fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w600, color: AzColors.ink2);
  static const TextStyle caption = TextStyle(
      fontFamily: family, fontSize: 12, height: 16 / 12, fontWeight: FontWeight.w600, color: AzColors.muted);
  static const TextStyle overline = TextStyle(
      fontFamily: family, fontSize: 12, height: 16 / 12, fontWeight: FontWeight.w800, letterSpacing: 0.8, color: AzColors.muted);
  static const TextStyle button = TextStyle(
      fontFamily: family, fontSize: 16, height: 20 / 16, fontWeight: FontWeight.w700);
  static const TextStyle amount = TextStyle(
      fontFamily: family,
      fontSize: 28,
      height: 34 / 28,
      fontWeight: FontWeight.w800,
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
