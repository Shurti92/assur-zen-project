import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_contract_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _json(int status, Object? body) => http.Response.bytes(
      body == null ? const [] : utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

/// Réponses de la liste, telles que décrites par la Gateway pour les 3 contrats de démo.
Map<String, dynamic> _listItem(String id, String number, String productId, String name, String status, int daysLeft, bool eligible) => {
      'id': id,
      'number': number,
      'productId': productId,
      'productName': name,
      'insuredLabel': 'Mobile Demo TEST',
      'status': status,
      'startDate': '2026-01-15',
      'endDate': '2026-11-14',
      'premium': 25000,
      'renewal': {'eligible': eligible, 'opensOn': '2026-10-15', 'daysLeft': daysLeft},
      'updatedAt': '2026-11-02T09:12:00Z',
    };

void main() {
  late List<http.Request> calls;

  ApiContractRepository build(MockClientHandler handler) {
    calls = [];
    return ApiContractRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'tok',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  group('Liste des contrats', () {
    test('trois contrats de démo : statuts et renouvellement du serveur', () async {
      final repo = build((req) async => _json(200, {
            'items': [
              _listItem('655', 'DEMO-MOB-ECHEANCE', 'moto', 'Assurance Moto', 'ACTIVE', 12, true),
              _listItem('653', 'DEMO-MOB-ACTIF', 'auto', 'Assurance Auto', 'ACTIVE', 210, false),
              _listItem('654', 'DEMO-MOB-ATTENTE', 'sante', 'Assurance Santé', 'PENDING', 0, false),
            ],
          }));

      final list = await repo.getContracts();

      expect(calls.single.url.path, Endpoints.contracts);
      expect(calls.single.headers['Authorization'], 'Bearer tok');
      expect(list.map((c) => c.status), [ContractStatus.actif, ContractStatus.actif, ContractStatus.enAttente]);
      expect(list[0].id, '655');
      expect(list[0].name, 'Assurance Moto');
      expect(list[0].insuredObject, 'Mobile Demo TEST');
      expect(list[0].renewalOpen, isTrue);
      expect(list[0].renewal!.daysLeft, 12);
      expect(list[1].renewalOpen, isFalse);
      expect(list[0].endDate, DateTime(2026, 11, 14));
      expect(list[0].premium, 25000);
    });

    test('client sans assuré lié : liste vide, pas d’erreur', () async {
      final repo = build((req) async => _json(200, {'items': []}));
      expect(await repo.getContracts(), isEmpty);
    });

    test('rôle non CLIENT : 403 affiché tel quel', () async {
      final repo = build((req) async => _json(403, {'code': 'ACCESS_DENIED', 'message': 'Accès refusé.'}));
      await expectLater(repo.getContracts(), throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 403)));
    });
  });

  group('Détail d’un contrat', () {
    test('contrat renouvelable : montant, durée et actions du serveur', () async {
      final repo = build((req) async => _json(200, {
            ..._listItem('655', 'DEMO-MOB-ECHEANCE', 'moto', 'Assurance Moto', 'ACTIVE', 12, true),
            'holderName': 'Mobile Demo TEST',
            'guarantees': [],
            'documents': [],
            'attestation': null,
            'renewal': {
              'eligible': true,
              'opensOn': '2026-10-15',
              'daysLeft': 12,
              'amount': 27500,
              'durationMonths': 10,
              'payableReference': null,
            },
            'actions': {'canDeclareClaim': true, 'canRequestChange': true, 'canRequestTermination': true},
          }));

      final c = await repo.getContract('655');

      expect(calls.single.url.path, Endpoints.contract('655'));
      expect(c.holderName, 'Mobile Demo TEST');
      expect(c.renewal!.amount, 27500);
      expect(c.renewal!.durationMonths, 10);
      expect(c.renewal!.payableReference, isNull);
      expect(c.actions.canDeclareClaim, isTrue);
      expect(c.guarantees, isEmpty);
      expect(c.documents, isEmpty);
      expect(c.attestation, isNull);
      expect(c.hasDigitalAttestation, isFalse, reason: 'pas d’attestation numérique tant que le serveur n’en fournit pas');
    });

    test('contrat en attente : aucune action, pas de renouvellement', () async {
      final repo = build((req) async => _json(200, {
            ..._listItem('654', 'DEMO-MOB-ATTENTE', 'sante', 'Assurance Santé', 'PENDING', 0, false),
            'renewal': null,
            'actions': {'canDeclareClaim': false, 'canRequestChange': false, 'canRequestTermination': false},
          }));

      final c = await repo.getContract('654');

      expect(c.status, ContractStatus.enAttente);
      expect(c.renewal, isNull);
      expect(c.renewalOpen, isFalse);
      expect(c.actions.canDeclareClaim, isFalse);
      expect(c.actions.canRequestTermination, isFalse);
    });

    test('garanties, documents et attestation quand le serveur les fournit', () async {
      final repo = build((req) async => _json(200, {
            ..._listItem('1', 'N-1', 'auto', 'Assurance Auto', 'ACTIVE', 100, false),
            'guarantees': [
              {'name': 'Responsabilité civile', 'kind': 'MANDATORY'},
              {'name': 'Bris de glace', 'kind': 'OPTIONAL'},
              {'name': 'Vol', 'kind': 'TO_CONFIRM'},
            ],
            'documents': [
              {'id': 'd1', 'type': 'ATTESTATION', 'name': 'Attestation', 'status': 'AVAILABLE'},
              {'id': 'd2', 'type': 'CEDEAO', 'name': 'Carte brune', 'status': 'GENERATING'},
              {'id': 'd3', 'type': 'AUTRE', 'name': 'Divers', 'status': 'INCONNU'},
            ],
            'attestation': {'available': true, 'verificationCode': 'https://v.test/a/XYZ', 'validFrom': '2026-01-01', 'validUntil': '2026-12-31'},
            'actions': {'canDeclareClaim': true},
          }));

      final c = await repo.getContract('1');

      expect(c.guarantees.map((g) => g.kind), [GuaranteeKind.obligatoire, GuaranteeKind.selonFormule, GuaranteeKind.aConfirmer]);
      expect(c.documents.map((d) => d.type), [DocumentType.attestation, DocumentType.carteBrune, DocumentType.autre]);
      expect(c.documents.map((d) => d.status), [DocumentStatus.disponible, DocumentStatus.enGeneration, DocumentStatus.nonDisponible]);
      expect(c.documents.every((d) => d.url == null), isTrue);
      expect(c.hasDigitalAttestation, isTrue);
      expect(c.verificationCode, 'https://v.test/a/XYZ');
      expect(c.actions.canRequestChange, isFalse, reason: 'champ absent : jamais autorisé par défaut');
    });

    test('contrat d’un tiers ou inexistant : 404 CONTRACT_NOT_FOUND', () async {
      final repo = build((req) async => _json(404, {'code': 'CONTRACT_NOT_FOUND', 'message': 'Contrat introuvable.'}));

      await expectLater(
        repo.getContract('999'),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'CONTRACT_NOT_FOUND')
            .having((e) => e.message, 'message', 'Contrat introuvable.')),
      );
    });
  });

  group('Demandes de modification / résiliation', () {
    test('résiliation : type TERMINATION, référence et délai renvoyés', () async {
      final repo = build((req) async => _json(201, {'reference': 'RQ-2026-000003', 'expectedWithinHours': 24}));

      final r = await repo.requestChange(contractId: '653', type: ContractRequestType.termination);

      expect(calls.single.url.path, Endpoints.contractRequests('653'));
      expect(jsonDecode(calls.single.body), {'type': 'TERMINATION'});
      expect(r.reference, 'RQ-2026-000003');
      expect(r.expectedWithinHours, 24);
    });

    test('modification avec message', () async {
      final repo = build((req) async => _json(201, {'reference': 'RQ-2026-000004', 'expectedWithinHours': 24}));

      await repo.requestChange(contractId: '653', type: ContractRequestType.modification, message: '  Changer de véhicule ');

      expect(jsonDecode(calls.single.body), {'type': 'MODIFICATION', 'message': 'Changer de véhicule'});
    });

    test('contrat non actif : 409 CONTRACT_NOT_ACTIVE', () async {
      final repo = build((req) async => _json(409, {'code': 'CONTRACT_NOT_ACTIVE', 'message': 'Ce contrat n’est pas actif.'}));

      await expectLater(
        repo.requestChange(contractId: '654', type: ContractRequestType.modification),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'CONTRACT_NOT_ACTIVE')),
      );
    });
  });
}
