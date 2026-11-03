import 'package:assurzen_mobile/core/utils/formatters.dart';
import 'package:assurzen_mobile/core/utils/validators.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Montants FCFA', () {
    test('séparateur des milliers', () {
      expect(formatFcfa(186400), '186 400 FCFA');
      expect(formatFcfa(1250000), '1 250 000 FCFA');
      expect(formatFcfa(950), '950 FCFA');
      expect(formatFcfa(0), '0 FCFA');
    });
  });

  group('Téléphone ivoirien', () {
    test('formatage par paires', () {
      expect(formatPhone('0708123456'), '07 08 12 34 56');
      expect(formatPhone('07 08 12'), '07 08 12');
      expect(internationalPhone('07 00 00 00 01'), '2250700000001');
    });

    test('validation 01 / 05 / 07 + 10 chiffres', () {
      expect(Validators.phone('0700000001'), isNull);
      expect(Validators.phone('05 00 00 00 02'), isNull);
      expect(Validators.phone('0100000001'), isNull);
      expect(Validators.phone('0900000001'), isNotNull);
      expect(Validators.phone('070000000'), isNotNull);
      expect(Validators.phone(''), isNotNull);
    });
  });

  group('Code PIN', () {
    test('6 chiffres', () {
      expect(Validators.pin('739204'), isNull);
      expect(Validators.pin('48291'), isNotNull);
      expect(Validators.pin('48291a'), isNotNull);
    });

    test('codes trop simples refusés à la création', () {
      expect(Validators.newPin('000000'), isNotNull);
      expect(Validators.newPin('123456'), isNotNull);
      expect(Validators.newPin('739204'), isNull);
    });
  });

  group('Autres règles', () {
    test('immatriculation', () {
      expect(Validators.plate('1234 AB 01'), isNull);
      expect(Validators.plate('12A'), isNotNull);
    });

    test('e-mail facultatif', () {
      expect(Validators.optionalEmail(''), isNull);
      expect(Validators.optionalEmail('contact@exemple.ci'), isNull);
      expect(Validators.optionalEmail('info@'), isNotNull);
    });

    test('jours avant échéance', () {
      final now = DateTime(2026, 10, 25, 15);
      expect(daysUntil(DateTime(2026, 11, 6), now: now), 12);
      expect(daysUntil(DateTime(2026, 10, 25), now: now), 0);
    });

    test('brouillon de devis : aller-retour JSON', () {
      final d = QuoteDraft(productId: 'auto')
        ..step = 3
        ..plate = '1234 AB 01'
        ..durationMonths = 6
        ..installments = 4;
      final back = QuoteDraft.fromJson(d.toJson().map((k, v) => MapEntry(k, v)));
      expect(back.productId, 'auto');
      expect(back.step, 3);
      expect(back.plate, '1234 AB 01');
      expect(back.durationMonths, 6);
      expect(back.installments, 4);
    });

    test('initiales et prénom', () {
      expect(initials('Koné Awa'), 'KA');
      expect(firstName('Koné Awa'), 'Awa');
    });
  });
}
