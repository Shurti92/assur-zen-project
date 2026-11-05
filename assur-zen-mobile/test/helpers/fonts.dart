import 'package:flutter/services.dart';

/// Charge les vraies polices de l'application (Sora et Plus Jakarta Sans) dans les tests.
///
/// Sans cela, les tests dessinent le texte avec une police de substitution beaucoup plus large :
/// ils signaleraient des débordements qui n'existent pas sur un téléphone, ou en manqueraient.
Future<void> loadAppFonts() async {
  Future<void> load(String family, List<String> weights) async {
    final loader = FontLoader(family);
    for (final weight in weights) {
      loader.addFont(rootBundle.load('assets/fonts/$family-$weight.ttf'));
    }
    await loader.load();
  }

  await load('Sora', const ['SemiBold', 'Bold', 'ExtraBold']);
  await load('PlusJakartaSans', const ['Regular', 'Medium', 'SemiBold', 'Bold', 'ExtraBold']);
}
