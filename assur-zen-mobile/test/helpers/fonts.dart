import 'package:flutter/services.dart';

/// Charge la vraie police de l'application (Manrope) dans les tests.
///
/// Sans cela, les tests dessinent le texte avec une police de substitution beaucoup plus large :
/// ils signaleraient des débordements qui n'existent pas sur un téléphone, ou en manqueraient.
Future<void> loadAppFonts() async {
  final loader = FontLoader('Manrope');
  for (final weight in const ['Regular', 'Medium', 'SemiBold', 'Bold', 'ExtraBold']) {
    loader.addFont(rootBundle.load('assets/fonts/Manrope-$weight.ttf'));
  }
  await loader.load();
}
