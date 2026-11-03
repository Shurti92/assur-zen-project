import 'package:flutter/cupertino.dart' show CupertinoPageTransitionsBuilder;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'az_colors.dart';
import 'az_typography.dart';

/// Thème Material 3 personnalisé AssurZen.
///
/// Volontairement minimal : les composants (boutons, champs, cartes, barres)
/// sont stylés dans les widgets `Az…` de `core/widgets`, ce qui garde un rendu
/// identique quelle que soit la version de Flutter.
abstract final class AzTheme {
  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AzColors.blue700,
      brightness: Brightness.light,
    ).copyWith(
      primary: AzColors.blue700,
      onPrimary: Colors.white,
      secondary: AzColors.orange500,
      onSecondary: Colors.white,
      error: AzColors.errorStrong,
      onError: Colors.white,
      surface: AzColors.surface,
      onSurface: AzColors.ink,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      fontFamily: AzText.family,
      textTheme: AzText.textTheme(),
      scaffoldBackgroundColor: AzColors.background,
      splashFactory: InkRipple.splashFactory,
      visualDensity: VisualDensity.standard,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      dividerColor: AzColors.line,
      pageTransitionsTheme: const PageTransitionsTheme(builders: {
        TargetPlatform.android: ZoomPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      }),
    );
  }

  /// Barres système : icônes sombres sur fond clair.
  static const SystemUiOverlayStyle lightOverlay = SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.dark,
    statusBarBrightness: Brightness.light,
    systemNavigationBarColor: Colors.white,
    systemNavigationBarIconBrightness: Brightness.dark,
  );

  /// Barres système : icônes claires sur fond bleu (écrans de marque).
  static const SystemUiOverlayStyle brandOverlay = SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    statusBarBrightness: Brightness.dark,
    systemNavigationBarColor: Colors.white,
    systemNavigationBarIconBrightness: Brightness.dark,
  );
}
