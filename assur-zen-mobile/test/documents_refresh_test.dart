import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/network/downloaded_file.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/contracts/documents_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

/// Faux dépôt : un document ajouté par l'assuré apparaît à partir du 2e chargement.
class _FakeContracts implements ContractRepository {
  int loads = 0;

  Contract _contract() => Contract(
        id: 'c1',
        productId: 'moto',
        name: 'Assurance Moto',
        insuredObject: 'Yamaha',
        number: 'POL-1',
        status: ContractStatus.actif,
        guarantees: const [],
        documents: [
          if (loads >= 2)
            const ContractDocument(
              id: 'd1',
              name: 'attestation_test.pdf',
              type: DocumentType.attestation,
              status: DocumentStatus.enVerification,
              addedByClient: true,
              removable: true,
            ),
        ],
      );

  @override
  Future<List<Contract>> getContracts() async => [_contract()];

  @override
  Future<Contract> getContract(String id) async {
    loads++;
    return _contract();
  }

  @override
  Future<DownloadedFile> downloadDocument(String contractId, String documentId) => throw UnimplementedError();
  @override
  Future<DownloadedFile> downloadAttestation(String contractId) => throw UnimplementedError();
  @override
  Future<void> uploadDocument({required String contractId, required ClientDocumentType type, required String filePath}) => throw UnimplementedError();
  @override
  Future<void> deleteDocument({required String contractId, required String documentId}) => throw UnimplementedError();
  @override
  Future<ContractRequestReceipt> requestChange({required String contractId, required ContractRequestType type, String? message}) =>
      throw UnimplementedError();
}

void main() {
  setUpAll(loadAppFonts);

  testWidgets('tirer pour rafraîchir affiche le document ajouté après le premier chargement', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 900);
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({'lock_choice_done': true});
    final store = await LocalStore.open();
    final base = MockRepositories.create();
    final repos = Repositories(
      auth: base.auth,
      catalog: base.catalog,
      quotes: base.quotes,
      motoQuotes: base.motoQuotes,
      payments: base.payments,
      contracts: _FakeContracts(),
      claims: base.claims,
      notifications: base.notifications,
      support: base.support,
      engagement: base.engagement,
    );
    final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
    await tester.pumpWidget(AppScope(repos: repos, session: session, store: store, child: const MaterialApp(home: DocumentsPage())));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('Mes ajouts'), findsNothing);

    await tester.fling(find.byType(ListView).first, const Offset(0, 400), 1000);
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Mes ajouts'), findsOneWidget);
    expect(find.text('attestation_test.pdf'), findsOneWidget);
  });
}
