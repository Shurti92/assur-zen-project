import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_catalog_repository.dart';
import 'package:assurzen_mobile/data/api/api_claim_repository.dart';
import 'package:assurzen_mobile/data/api/api_support_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

ApiClient _client(MockClientHandler handler, [List<http.Request>? calls]) => ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => null,
      httpClient: MockClient((req) async {
        calls?.add(req);
        return handler(req);
      }),
    );

void main() {
  group('Coordonnées (GET /public/contact-info)', () {
    test('valeurs de dev : téléphone, WhatsApp, e-mail, horaires ; le reste absent', () async {
      final calls = <http.Request>[];
      final repo = ApiSupportRepository(_client(
        (req) async => _json(200, {
          'phones': [
            {'label': 'Support Technique', 'number': '0700000002'},
          ],
          'whatsapp': '0700000001',
          'email': 'aide@exemple.ci',
          'address': null,
          'hours': 'Lun-Sam 8h-17h',
          'emergencyNumbers': [],
          'supportAvailability': null,
        }),
        calls,
      ));

      final info = await repo.getContactInfo();

      expect(calls.single.url.path, Endpoints.contactInfo);
      expect(calls.single.url.path, endsWith('/public/contact-info'));
      expect(info.primaryPhone, '0700000002');
      expect(info.phones.single.label, 'Support Technique');
      expect(info.whatsapp, '0700000001');
      expect(info.email, 'aide@exemple.ci');
      expect(info.address, isNull, reason: 'absent = rubrique masquée, jamais de valeur de remplacement');
      expect(info.emergencyNumbers, isEmpty);
      expect(info.supportAvailability, isNull);
      expect(info.availabilityText, 'Lun-Sam 8h-17h', reason: 'sans disponibilité du support, on affiche les horaires du serveur');
    });

    test('la disponibilité du support prime sur les horaires', () {
      final info = ApiSupportRepository.contactInfoFromJson({'hours': 'Lun-Sam 8h-17h', 'supportAvailability': '24h/24'});
      expect(info.availabilityText, '24h/24');
    });

    test('numéros d’urgence et libellés vides ou espaces', () {
      final info = ApiSupportRepository.contactInfoFromJson({
        'phones': [
          {'label': '  ', 'number': ' 0700000000 '},
          {'label': 'Sans numéro', 'number': ''},
        ],
        'emergencyNumbers': [
          {'label': 'SAMU', 'number': '185'},
        ],
        'email': '   ',
      });
      expect(info.phones, hasLength(1));
      expect(info.phones.single.number, '0700000000');
      expect(info.phones.single.label, '');
      expect(info.emergencyNumbers.single.number, '185');
      expect(info.email, isNull);
    });

    test('réponse vide : aucune coordonnée, rien n’est inventé', () {
      final info = ApiSupportRepository.contactInfoFromJson(const {});
      expect(info.isEmpty, isTrue);
      expect(info.primaryPhone, isNull);
      expect(info.availabilityText, isNull);
    });
  });

  group('Fiche produit enrichie (GET /public/products/{id})', () {
    Map<String, dynamic> full() => {
          'id': 'auto',
          'title': 'Assurance Auto',
          'shortName': 'Auto',
          'family': 'VEHICLE',
          'onlineSubscription': false,
          'description': 'Roulez couvert.',
          'benefits': ['Assistance'],
          'howItWorks': 'Un conseiller vous accompagne.',
          'eligibility': 'Véhicule immatriculé.',
          'guarantees': [
            {'id': 'g1', 'name': 'Responsabilité civile', 'kind': 'MANDATORY', 'description': 'Dommages aux tiers.', 'limit': null, 'formulaIds': ['f1', 'f2']},
            {'id': 'g2', 'name': 'Bris de glace', 'kind': 'OPTIONAL', 'description': null, 'limit': 'Plafond 300 000 FCFA', 'formulaIds': ['f2']},
          ],
          'formulas': [
            {'id': 'f1', 'name': 'Tiers', 'description': 'Le minimum légal.', 'guaranteeIds': ['g1']},
            {'id': 'f2', 'name': 'Tous risques', 'description': null, 'guaranteeIds': ['g1', 'g2', 'g-inconnue']},
          ],
          'exclusions': ['Conduite sans permis'],
          'requiredDocuments': ['Carte grise', 'Permis de conduire'],
          'faq': [
            {'question': 'Délai de l’attestation ?', 'answer': 'Immédiat.'},
            {'question': 'Sans réponse', 'answer': '  '},
          ],
          'icon': 'car',
          'displayOrder': 20,
          'contentVersion': '2026-11-03T04:14:17Z',
        };

    test('garanties détaillées, formules résolues, exclusions, pièces, FAQ, version', () {
      final p = ApiCatalogRepository.productFromJson(full());

      expect(p.guarantees.map((g) => g.name), ['Responsabilité civile', 'Bris de glace'], reason: 'ordre du serveur conservé');
      expect(p.guarantees[0].description, 'Dommages aux tiers.');
      expect(p.guarantees[0].limit, isNull);
      expect(p.guarantees[0].formulaIds, ['f1', 'f2']);
      expect(p.guarantees[1].description, isNull);
      expect(p.guarantees[1].limit, 'Plafond 300 000 FCFA');
      expect(p.guarantees[1].kind, GuaranteeKind.selonFormule);

      expect(p.formulas.map((f) => f.name), ['Tiers', 'Tous risques']);
      expect(p.formulas[0].guarantees, ['Responsabilité civile']);
      expect(p.formulas[1].guarantees, ['Responsabilité civile', 'Bris de glace'], reason: 'identifiant inconnu ignoré, rien d’inventé');
      expect(p.formulas[1].description, '');

      expect(p.exclusions, ['Conduite sans permis']);
      expect(p.requiredDocuments, ['Carte grise', 'Permis de conduire']);
      expect(p.faq, hasLength(1), reason: 'une question sans réponse n’est pas affichée');
      expect(p.faq.single.answer, 'Immédiat.');
      expect(p.contentVersion, '2026-11-03T04:14:17Z');
    });

    test('contenu vide (dev) : toutes les rubriques sont vides, donc masquées', () {
      final p = ApiCatalogRepository.productFromJson({
        'id': 'moto',
        'title': 'Assurance Moto',
        'description': null,
        'benefits': [],
        'howItWorks': '',
        'eligibility': null,
        'guarantees': [],
        'formulas': [],
        'exclusions': [],
        'requiredDocuments': [],
        'faq': [],
        'icon': 'moto',
        'contentVersion': '2026-11-03T04:14:24Z',
      });
      expect(p.guarantees, isEmpty);
      expect(p.formulas, isEmpty);
      expect(p.exclusions, isEmpty);
      expect(p.requiredDocuments, isEmpty);
      expect(p.faq, isEmpty);
      expect(p.description, '');
    });
  });

  group('Délais fournis par le serveur', () {
    test('sinistre : expectedHandlingHours repris seulement s’il est fourni', () async {
      Future<Claim> declare(Object? hours) async {
        final repo = ApiClaimRepository(_client((req) async {
          if (req.method == 'POST') {
            return _json(201, {'id': '1', 'number': 'SIN1', 'status': 'DECLARED', 'declaredAt': '2026-11-03T04:12:00Z', 'expectedHandlingHours': hours});
          }
          return _json(200, {
            'id': '1',
            'number': 'SIN1',
            'typeLabel': 'Vol',
            'contractLabel': 'Auto',
            'declaredAt': '2026-11-03T04:12:00Z',
            'closed': false,
            'currentStep': 0,
            'steps': [
              {'label': 'Déclaration reçue', 'state': 'CURRENT'},
            ],
            'history': [],
          });
        }));
        return repo.declare(ClaimDraft()
          ..contractId = '653'
          ..typeId = 'THEFT');
      }

      expect((await declare(null)).expectedHandlingHours, isNull, reason: 'clé de configuration vide = aucun engagement');
      expect((await declare(48)).expectedHandlingHours, 48);
    });
  });
}
