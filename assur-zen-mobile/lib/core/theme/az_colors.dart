import 'package:flutter/material.dart';

/// Couleurs AssurZen.
///
/// Bleu et orange mesurés sur le logo officiel (AssurZen, octobre 2026).
/// Règle : le bleu porte les actions et le texte de marque (10,6:1 sur blanc).
/// L’orange (3,1:1 sur blanc) sert aux accents graphiques : bouton central
/// « Souscrire », pastilles, éléments avec icône blanche. Pour un texte orange,
/// utiliser [orange700].
abstract final class AzColors {
  // Marque
  static const blue900 = Color(0xFF022766); // état pressé
  static const blue700 = Color(0xFF033892); // BLEU ASSURZEN — primary
  static const blue100 = Color(0xFFD6E0F3);
  static const blue50 = Color(0xFFEEF2FA);
  static const orange500 = Color(0xFFFB6404); // ORANGE ASSURZEN — accent
  static const orange400 = Color(0xFFFE8908);
  static const orange700 = Color(0xFFB84500); // orange lisible pour du texte
  static const orange50 = Color(0xFFFFF1E6);

  // Texte et surfaces
  static const ink = Color(0xFF0F1B33);
  static const ink2 = Color(0xFF3A4660);
  static const muted = Color(0xFF5A6478);
  static const line = Color(0xFFE3E7EF);
  static const lineSoft = Color(0xFFEEF1F6);
  static const fieldBorder = Color(0xFFD3D9E4);
  static const background = Color(0xFFF5F7FB);
  static const surface = Color(0xFFFFFFFF);
  static const disabled = Color(0xFFA7B5D6);
  static const scrim = Color(0x730F1B33);
  static const skeleton = Color(0xFFE6EAF1);

  // Sémantique (texte / fond)
  static const success = Color(0xFF17663F);
  static const successBg = Color(0xFFE5F4EC);
  static const warning = Color(0xFF7A4E00);
  static const warningBg = Color(0xFFFDF1DC);
  static const warningInk = Color(0xFF5C3B00);
  static const error = Color(0xFFA1281E);
  static const errorStrong = Color(0xFFB42318);
  static const errorBg = Color(0xFFFDECEA);
  static const errorInk = Color(0xFF7A1D15);
  static const info = Color(0xFF1D4F9C);
  static const infoBg = Color(0xFFE8F0FC);
  static const neutral = Color(0xFF4A5752);
  static const neutralBg = Color(0xFFEEF1F0);

  // Teintes de fond des produits
  static const tintBlue = Color(0xFFEEF2FA);
  static const tintSky = Color(0xFFE9F0F6);
  static const tintOrange = Color(0xFFFFF1E6);
  static const tintRose = Color(0xFFFCEAEA);
  static const tintLilac = Color(0xFFEEEAF8);
  static const tintGrey = Color(0xFFEEF1F0);
}
