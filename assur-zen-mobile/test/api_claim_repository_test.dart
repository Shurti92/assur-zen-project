import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_claim_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

Map<String, dynamic> _claim({int current = 1, bool closed = false}) => {
      'id': '1',
      'number': 'SIN2026001933',
      'typeLabel': 'Accident / collision',
      'contractId': '655',
      'contractLabel': 'Assurance Moto · DEMO-MOB-ECHEANCE',
      'declaredAt': '2026-11-02T09:30:00Z',
      'status': 'IN_ANALYSIS',
      'closed': closed,
      'currentStep': current,
      'steps': [
        {'label': 'Déclaration reçue', 'state': 'DONE'},
        {'label': 'Analyse du dossier', 'state': 'CURRENT'},
        {'label': 'Expertise', 'state': 'TODO'},
        {'label': 'Décision', 'state': 'TODO'},
        {'label': 'Indemnisation et clôture', 'state': 'TODO'},
      ],
      'history': [
        {'date': '2026-11-02T09:30:00Z', 'text': 'Déclaration reçue.'},
        {'date': '2026-11-03T08:00:00Z', 'text': 'Dossier en analyse.'},
      ],
    };

void main() {
  late List<http.BaseRequest> calls;

  ApiClaimRepository build(Future<http.Response> Function(http.Request) handler) {
    calls = [];
    return ApiClaimRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'tok',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  test('types : identifiant et libellé du serveur', () async {
    final repo = build((req) async => _json(200, {
          'items': [
            {'id': 'COLLISION', 'label': 'Accident / collision'},
            {'id': 'THEFT', 'label': 'Vol'},
          ],
        }));

    final types = await repo.getClaimTypes('moto');

    expect(calls.single.url.path, Endpoints.claimTypes('moto'));
    expect(types.map((t) => t.id), ['COLLISION', 'THEFT']);
    expect(types.first.label, 'Accident / collision');
  });

  test('suivi : étapes du serveur, historique du plus récent au plus ancien', () async {
    final repo = build((req) async => _json(200, _claim()));

    final c = await repo.getClaim('1');

    expect(c.number, 'SIN2026001933');
    expect(c.steps, hasLength(5));
    expect(c.currentStep, 1);
    expect(c.statusLabel, 'Analyse du dossier');
    expect(c.closed, isFalse);
    expect(c.history.first.text, 'Dossier en analyse.');
  });

  test('dossier clos : libellé « Clôturé »', () async {
    final repo = build((req) async => _json(200, _claim(current: 4, closed: true)));
    expect((await repo.getClaim('1')).statusLabel, 'Clôturé');
  });

  test('étape hors bornes : ramenée dans la liste', () async {
    final repo = build((req) async => _json(200, _claim(current: 9)));
    expect((await repo.getClaim('1')).currentStep, 4);
  });

  test('sinistre d’un tiers : 404 CLAIM_NOT_FOUND', () async {
    final repo = build((req) async => _json(404, {'code': 'CLAIM_NOT_FOUND', 'message': 'Sinistre introuvable.'}));
    await expectLater(
      repo.getClaim('999'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'CLAIM_NOT_FOUND')),
    );
  });

  test('corps de déclaration : date seule, tiers seulement si impliqué', () {
    final d = ClaimDraft()
      ..contractId = '655'
      ..typeId = 'COLLISION'
      ..occurredAt = DateTime(2026, 11, 2, 14, 30)
      ..place = '  Cocody  '
      ..thirdPartyInvolved = true
      ..thirdPartyName = 'K. Yao'
      ..thirdPartyPlate = '1234 AB 01'
      ..description = 'Choc à l’arrière au feu rouge.';

    expect(ApiClaimRepository.declarationBody(d), {
      'contractId': '655',
      'typeId': 'COLLISION',
      'occurredAt': '2026-11-02',
      'place': 'Cocody',
      'thirdPartyInvolved': true,
      'thirdParty': {'name': 'K. Yao', 'plate': '1234 AB 01'},
      'description': 'Choc à l’arrière au feu rouge.',
    });

    d.thirdPartyInvolved = false;
    expect(ApiClaimRepository.declarationBody(d).containsKey('thirdParty'), isFalse);
  });

  test('déclarer : création puis relecture, sans pièce', () async {
    final repo = build((req) async {
      if (req.method == 'POST') return _json(201, {'id': '1', 'number': 'SIN2026001933', 'status': 'DECLARED', 'declaredAt': '2026-11-02T09:30:00Z'});
      return _json(200, _claim(current: 0));
    });

    final c = await repo.declare(ClaimDraft()
      ..contractId = '655'
      ..typeId = 'THEFT');

    expect(calls.first.method, 'POST');
    expect(calls.first.url.path, Endpoints.claims);
    expect(calls.last.method, 'GET');
    expect(calls.last.url.path, Endpoints.claim('1'));
    expect(c.failedUploads, 0);
  });

  test('contrat non actif : 409 CLAIM_NOT_ALLOWED', () async {
    final repo = build((req) async => _json(409, {'code': 'CLAIM_NOT_ALLOWED', 'message': 'Ce contrat n’est pas actif.'}));
    await expectLater(
      repo.declare(ClaimDraft()
        ..contractId = '654'
        ..typeId = 'CARE'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'CLAIM_NOT_ALLOWED')),
    );
  });
}
