/// Formatage des montants, téléphones et dates — sans dépendance externe.
library;

const _months = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
];

/// 186400 → « 186 400 FCFA » (espace insécable entre les milliers).
String formatFcfa(num amount) {
  final rounded = amount.round();
  final negative = rounded < 0;
  final digits = rounded.abs().toString();
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    final fromEnd = digits.length - i;
    buffer.write(digits[i]);
    if (fromEnd > 1 && fromEnd % 3 == 1) buffer.write(' ');
  }
  return '${negative ? '-' : ''}$buffer FCFA';
}

/// 10 → « 10 », 7.5 → « 7,5 » (le signe % est ajouté par l'écran).
String formatPercent(num v) => (v % 1 == 0 ? v.toInt().toString() : v.toString()).replaceAll('.', ',');

/// Ne garde que les chiffres (10 au maximum pour un numéro ivoirien).
String phoneDigits(String input) {
  final d = input.replaceAll(RegExp(r'\D'), '');
  return d.length > 10 ? d.substring(0, 10) : d;
}

/// « 0708123456 » → « 07 08 12 34 56 ».
String formatPhone(String input) {
  final d = phoneDigits(input);
  final parts = <String>[];
  for (var i = 0; i < d.length; i += 2) {
    parts.add(d.substring(i, i + 2 > d.length ? d.length : i + 2));
  }
  return parts.join(' ');
}

/// Format international pour les liens tel: et WhatsApp : « 2250708123456 ».
String internationalPhone(String input) => '225${phoneDigits(input)}';

/// 25/10/2026
String formatDate(DateTime? date) {
  if (date == null) return '—';
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year}';
}

/// 25 octobre 2026
String formatDateLong(DateTime? date) {
  if (date == null) return '—';
  return '${date.day} ${_months[date.month - 1]} ${date.year}';
}

/// 17:30
String formatTime(DateTime date) =>
    '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';

/// « à l’instant », « il y a 2 h », « hier », sinon la date.
String formatRelative(DateTime date, {DateTime? now}) {
  final ref = now ?? DateTime.now();
  final diff = ref.difference(date);
  if (diff.inMinutes < 1) return 'à l’instant';
  if (diff.inMinutes < 60) return 'il y a ${diff.inMinutes} min';
  if (diff.inHours < 24 && ref.day == date.day) return 'il y a ${diff.inHours} h';
  final yesterday = DateTime(ref.year, ref.month, ref.day - 1);
  if (date.year == yesterday.year && date.month == yesterday.month && date.day == yesterday.day) {
    return 'hier';
  }
  return formatDate(date);
}

/// Nombre de jours entiers entre aujourd’hui et [date] (négatif si passé).
int daysUntil(DateTime date, {DateTime? now}) {
  final ref = now ?? DateTime.now();
  final a = DateTime(ref.year, ref.month, ref.day);
  final b = DateTime(date.year, date.month, date.day);
  return b.difference(a).inDays;
}

/// Salutation selon l’heure.
String greeting({DateTime? now}) {
  final h = (now ?? DateTime.now()).hour;
  if (h < 12) return 'Bonjour';
  if (h < 18) return 'Bon après-midi';
  return 'Bonsoir';
}

/// Initiales : « Awa Koné » → « AK ».
String initials(String fullName) {
  final parts = fullName.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.isEmpty) return '?';
  if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
  return (parts.first.substring(0, 1) + parts.last.substring(0, 1)).toUpperCase();
}

/// Premier mot du nom complet.
String firstName(String fullName) {
  final parts = fullName.trim().split(RegExp(r'\s+'));
  // En Côte d’Ivoire le nom de famille vient souvent en premier : on affiche
  // le dernier mot (prénom) s’il y en a plusieurs.
  return parts.length > 1 ? parts.last : parts.first;
}

/// Empêche un montant de se couper en fin de ligne (« 19 678 / 710 F CFA ») : l'espace entre deux groupes
/// de chiffres et celles de « F CFA » deviennent insécables. À l'affichage seulement : les textes du
/// serveur sont modifiables au back-office et restent tels qu'ils ont été saisis.
String keepNumbersTogether(String text) => text
    .replaceAllMapped(RegExp(r'(?<=\d) (?=\d{3}(?!\d))'), (_) => '\u00A0')
    .replaceAll(' F CFA', '\u00A0F\u00A0CFA')
    .replaceAll(' FCFA', '\u00A0FCFA');
