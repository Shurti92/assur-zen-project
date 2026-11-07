import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/core/widgets/az_icons.dart';
import 'package:assurzen_mobile/data/api/api_catalog_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter/material.dart' show Icons;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

// Contrat réel de la Gateway : GET /public/products (visible + actif, trié par
// displayOrder, contenu paramétré dans le back-office).

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

void main() {
  late List<http.Request> calls;

  ApiCatalogRepository build(MockClientHandler handler) {
    calls = [];
    return ApiCatalogRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => null,
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  test('catalogue de démo : champs vides = non communiqué, rien d’inventé', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {
              'id': 'moto',
              'title': 'Assurance Moto',
              'shortName': 'Moto',
              'description': null,
              'family': 'VEHICLE',
              'onlineSubscription': false,
              'benefits': [],
              'howItWorks': '',
              'eligibility': null,
              'guarantees': [],
              'icon': 'moto',
              'displayOrder': 1,
            },
          ],
        }));

    final list = await repo.getProducts();

    expect(calls.single.url.path, Endpoints.products);
    expect(calls.single.url.path, endsWith('/public/products'));
    final p = list.single;
    expect(p.title, 'Assurance Moto');
    expect(p.description, '');
    expect(p.benefits, isEmpty);
    expect(p.howItWorks, '');
    expect(p.eligibility, isNull);
    expect(p.guarantees, isEmpty);
    expect(p.onlineSubscription, isFalse);
    expect(p.isVehicle, isTrue);
    expect(p.iconCode, 'moto');
  });

  test('ordre : displayOrder du serveur, absent en dernier, égalité = ordre reçu', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {'id': 'c', 'title': 'C', 'displayOrder': 3},
            {'id': 'x', 'title': 'X'},
            {'id': 'a', 'title': 'A', 'displayOrder': 1},
            {'id': 'b2', 'title': 'B2', 'displayOrder': 2},
            {'id': 'b1', 'title': 'B1', 'displayOrder': 2},
          ],
        }));

    expect((await repo.getProducts()).map((p) => p.id), ['a', 'b2', 'b1', 'c', 'x']);
  });

  test('intitulé tel quel, famille absente → groupe par défaut', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {'id': 'sante', 'title': 'Assurance Sante', 'family': null},
          ],
        }));

    final p = (await repo.getProducts()).single;
    expect(p.title, 'Assurance Sante', reason: 'jamais retouché côté mobile');
    expect(p.shortName, 'Assurance Sante');
    expect(p.family, ProductFamily.professionnel);
  });

  test('produit professionnel sans souscription en ligne (Assurance Entreprise) : rappel, pas de devis', () async {
    final repo = build((req) async => _json(200, {
          'id': 'entreprise',
          'title': 'Assurance Entreprise',
          'shortName': 'Entreprise',
          'family': 'PROFESSIONAL',
          'icon': 'briefcase',
          'onlineSubscription': false,
          'description': 'Protégez votre activité.',
          'benefits': ['Un conseiller dédié'],
          'howItWorks': 'Un conseiller vous rappelle.',
          'guarantees': [],
          'formulas': [],
        }));

    final p = await repo.getProduct('entreprise');

    expect(p.family, ProductFamily.professionnel);
    expect(p.onlineSubscription, isFalse);
    expect(p.isQuoteOnly, isFalse, reason: 'pas de devis dans l’application : « Être rappelé »');
    expect(p.formulas, isEmpty);
    expect(p.guarantees, isEmpty);
    expect(p.iconCode, 'briefcase');
    expect(productIcon(p.id, code: p.iconCode), Icons.business_center_outlined);
  });

  test('produit professionnel avec devis en ligne : parcours « Sur devis »', () async {
    final repo = build((req) async => _json(200, {
          'id': 'flotte',
          'title': 'Flotte',
          'family': 'PROFESSIONAL',
          'onlineSubscription': true,
        }));

    expect((await repo.getProduct('flotte')).isQuoteOnly, isTrue);
  });

  test('fiche produit : contenu du back-office et garanties', () async {
    final repo = build((req) async => _json(200, {
          'id': 'auto',
          'title': 'Assurance Auto',
          'family': 'VEHICLE',
          'onlineSubscription': false,
          'description': 'Roulez couvert.',
          'benefits': ['Attestation immédiate', '  ', 'Assistance'],
          'howItWorks': 'Un conseiller vous accompagne.',
          'eligibility': '  Véhicule immatriculé en Côte d’Ivoire. ',
          'icon': 'icone-inconnue',
          'guarantees': [
            {'name': 'Responsabilité civile', 'kind': 'MANDATORY'},
            {'name': 'Bris de glace', 'kind': 'OPTIONAL'},
            {'name': 'Vol', 'kind': 'INCLUDED'},
            {'name': 'Défense', 'kind': 'TO_CONFIRM'},
            {'name': 'Autre', 'kind': 'NOUVEAU'},
          ],
        }));

    final p = await repo.getProduct('auto');

    expect(calls.single.url.path, Endpoints.product('auto'));
    expect(p.description, 'Roulez couvert.');
    expect(p.benefits, ['Attestation immédiate', 'Assistance']);
    expect(p.howItWorks, 'Un conseiller vous accompagne.');
    expect(p.eligibility, 'Véhicule immatriculé en Côte d’Ivoire.');
    expect(p.iconCode, 'icone-inconnue', reason: 'code libre : l’écran choisit l’icône par défaut');
    expect(p.guarantees.map((g) => g.kind), [
      GuaranteeKind.obligatoire,
      GuaranteeKind.selonFormule,
      GuaranteeKind.incluse,
      GuaranteeKind.aConfirmer,
      GuaranteeKind.aConfirmer,
    ]);
  });

  test('produit masqué ou inconnu : 404 PRODUCT_NOT_FOUND', () async {
    final repo = build((req) async => _json(404, {'code': 'PRODUCT_NOT_FOUND', 'message': 'Produit introuvable.'}));
    await expectLater(
      repo.getProduct('voyage'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'PRODUCT_NOT_FOUND')),
    );
  });

  test('catalogue vide (environnement neuf) : liste vide, pas d’erreur', () async {
    final repo = build((req) async => _json(200, {'items': []}));
    expect(await repo.getProducts(), isEmpty);
  });

  test('référentiels de devis : non livrés par la Gateway, aucun appel réseau', () async {
    final repo = build((req) async => _json(200, {}));
    await expectLater(repo.getReferentials('moto'), throwsA(isA<ApiException>()));
    expect(calls, isEmpty);
  });
}
