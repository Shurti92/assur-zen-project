import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/api/api_contract_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/features/contracts/add_document_sheet.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

void main() {
  setUpAll(loadAppFonts);

  test('les statuts et champs des documents ajoutés par l’assuré sont lus', () {
    final c = ApiContractRepository.contractFromJson({
      'id': 'c1',
      'productId': 'moto',
      'productName': 'Moto',
      'insuredLabel': 'x',
      'number': 'P1',
      'status': 'ACTIVE',
      'documents': [
        {'id': 'a', 'name': 'Attestation', 'type': 'ATTESTATION_ASSURANCE', 'status': 'PENDING_REVIEW', 'source': 'CLIENT', 'removable': true},
        {'id': 'b', 'name': 'Reçu', 'type': 'RECU_ACHAT', 'status': 'REJECTED', 'source': 'CLIENT', 'rejectReason': 'Photo floue', 'removable': true},
        {'id': 'c', 'name': 'Contrat', 'type': 'CONTRAT', 'status': 'AVAILABLE', 'source': 'BACKOFFICE'},
      ],
    });
    expect(c.documents[0].status, DocumentStatus.enVerification);
    expect(c.documents[0].addedByClient, isTrue);
    expect(c.documents[0].removable, isTrue);
    expect(c.documents[1].status, DocumentStatus.refuse);
    expect(c.documents[1].rejectReason, 'Photo floue');
    expect(c.documents[2].addedByClient, isFalse);
    expect(c.documents[2].removable, isFalse);
  });

  test('les codes envoyés au serveur', () {
    expect(ClientDocumentType.contrat.code, 'CONTRAT_ASSURANCE');
    expect(ClientDocumentType.recu.code, 'RECU_ACHAT');
    expect(ClientDocumentType.attestation.code, 'ATTESTATION_ASSURANCE');
  });

  test('messages clairs pour les refus du serveur', () {
    const limit = ApiException(kind: ApiErrorKind.business, message: 'x', code: 'FILE_LIMIT_REACHED', statusCode: 409);
    const validated = ApiException(kind: ApiErrorKind.business, message: 'x', code: 'DOCUMENT_VALIDATED', statusCode: 409);
    expect(documentErrorMessage(limit), contains('en attente'));
    expect(documentErrorMessage(validated), contains('validé'));
  });

  for (final (size, scale) in [(const Size(320, 640), 1.3), (const Size(360, 800), 1.0)]) {
    testWidgets('la feuille d’ajout tient sur ${size.width.toInt()} dp, texte ${(scale * 100).toInt()} %', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(
        AppScope(
          repos: repos,
          session: session,
          store: store,
          child: MaterialApp(
            builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
            home: Builder(
              builder: (context) => Scaffold(
                body: Center(child: ElevatedButton(onPressed: () => showAddDocumentSheet(context, MockData.contracts.first), child: const Text('ouvrir'))),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('ouvrir'));
      await tester.pump(const Duration(milliseconds: 600));
      expect(find.text('Ajouter un document'), findsOneWidget);
      expect(find.text('Contrat d’assurance'), findsOneWidget);
      expect(find.text('Prendre une photo'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
