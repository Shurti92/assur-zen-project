import 'formatters.dart';

/// Règles de validation validées par AssurZen (questionnaire du 25/10/2026).
/// Chaque fonction renvoie `null` si la valeur est valide, sinon le message
/// d’erreur en français simple à afficher sous le champ.
abstract final class Validators {
  /// Mobile ivoirien : 10 chiffres commençant par 01, 05 ou 07.
  static String? phone(String? value) {
    final d = phoneDigits(value ?? '');
    if (d.isEmpty) return 'Saisissez votre numéro de téléphone.';
    if (d.length != 10 || !RegExp(r'^(01|05|07)').hasMatch(d)) {
      return 'Numéro invalide : 10 chiffres commençant par 01, 05 ou 07.';
    }
    return null;
  }

  static bool isPhone(String? value) => phone(value) == null;

  /// Code PIN : exactement 6 chiffres.
  static String? pin(String? value) {
    final v = value ?? '';
    if (!RegExp(r'^\d{6}$').hasMatch(v)) return 'Le code PIN comporte 6 chiffres.';
    return null;
  }

  /// Création du PIN : 6 chiffres, ni répétitif ni suite évidente.
  static String? newPin(String? value) {
    final base = pin(value);
    if (base != null) return base;
    final v = value!;
    const weak = {'123456', '654321', '012345', '543210', '121212', '112233'};
    if (RegExp(r'^(\d)\1{5}$').hasMatch(v) || weak.contains(v)) {
      return 'Ce code PIN est trop simple. Évitez 000000 ou 123456.';
    }
    return null;
  }

  /// Code de vérification SMS : 6 chiffres.
  static String? otp(String? value) {
    if (!RegExp(r'^\d{6}$').hasMatch(value ?? '')) return 'Le code comporte 6 chiffres.';
    return null;
  }

  static String? fullName(String? value) {
    if ((value ?? '').trim().length < 3) return 'Indiquez votre nom et vos prénoms.';
    return null;
  }

  /// E-mail facultatif : vide accepté.
  static String? optionalEmail(String? value) {
    final v = (value ?? '').trim();
    if (v.isEmpty) return null;
    if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(v)) {
      return 'L’adresse e-mail ne semble pas valide.';
    }
    return null;
  }

  /// Immatriculation : au moins 5 caractères hors espaces (ex. 1234 AB 01).
  static String? plate(String? value) {
    if ((value ?? '').replaceAll(' ', '').length < 5) {
      return 'Saisissez l’immatriculation figurant sur la carte grise (ex. 1234 AB 01).';
    }
    return null;
  }

  static String? minLength(String? value, int min, String message) {
    if ((value ?? '').trim().length < min) return message;
    return null;
  }
}
