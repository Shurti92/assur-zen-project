import 'dart:convert';
import 'dart:typed_data';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/downloaded_file.dart';
import 'package:assurzen_mobile/core/utils/file_viewer.dart';
import 'package:assurzen_mobile/data/api/api_contract_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:open_filex/open_filex.dart';

final _pdf = Uint8List.fromList(utf8.encode('%PDF-1.4 test'));

http.Response _file(int status, {String type = 'application/pdf', String? disposition = 'inline; filename="Attestation client.pdf"', List<int>? body}) =>
    http.Response.bytes(body ?? _pdf, status, headers: {
      'content-type': type,
      if (disposition != null) 'content-disposition': disposition,
      'cache-control': 'no-store',
    });

http.Response _error(int status, String code, String message) => http.Response.bytes(
      utf8.encode(jsonEncode({'code': code, 'message': message})),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

void main() {
  late List<http.Request> calls;

  ApiContractRepository build(MockClientHandler handler) {
    calls = [];
    return ApiContractRepository(ApiClient(
      baseUrl: 'http://gw.test',
      tokenProvider: () async => 'jeton-test',
      httpClient: MockClient((req) async {
        calls.add(req);
        return handler(req);
      }),
    ));
  }

  group('Téléchargement : le jeton de session est envoyé', () {
    test('document : GET avec Authorization, fichier, type et nom lus dans les en-têtes', () async {
      final repo = build((req) async => _file(200));

      final f = await repo.downloadDocument('901', '12');

      expect(calls.single.method, 'GET');
      expect(calls.single.url.path, endsWith('/mobile/contracts/901/documents/12'));
      expect(calls.single.headers['Authorization'], 'Bearer jeton-test');
      expect(f.bytes, _pdf);
      expect(f.contentType, 'application/pdf');
      expect(f.fileName, 'Attestation client.pdf');
      expect(f.isSupported, isTrue);
    });

    test('attestation : GET /attestation, image JPEG acceptée', () async {
      final repo = build((req) async => _file(200, type: 'image/jpeg', disposition: 'inline; filename="carte.jpg"'));

      final f = await repo.downloadAttestation('901');

      expect(calls.single.url.path, endsWith('/mobile/contracts/901/attestation'));
      expect(f.contentType, 'image/jpeg');
      expect(f.extension, 'jpg');
    });

    test('type avec paramètre (charset) : seul le type est retenu', () async {
      final repo = build((req) async => _file(200, type: 'application/pdf; charset=binary'));

      expect((await repo.downloadDocument('1', '2')).contentType, 'application/pdf');
    });

    test('contrat ou document d\'un tiers : 404 CONTRACT_NOT_FOUND, message du serveur', () async {
      final repo = build((req) async => _error(404, 'CONTRACT_NOT_FOUND', 'Contrat introuvable.'));

      await expectLater(
        repo.downloadDocument('999', '12'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'CONTRACT_NOT_FOUND').having((e) => e.message, 'message', 'Contrat introuvable.')),
      );
    });

    test('erreur serveur : message générique, aucun fichier', () async {
      final repo = build((req) async => http.Response('', 500));

      await expectLater(repo.downloadAttestation('901'), throwsA(isA<ApiException>().having((e) => e.statusCode, 'statusCode', 500)));
    });

    test('jeton expiré : refusé sans renouvellement possible, écran de PIN demandé', () async {
      var asked = false;
      calls = [];
      final repo = ApiContractRepository(ApiClient(
        baseUrl: 'http://gw.test',
        tokenProvider: () async => 'vieux',
        onUnauthorized: () => asked = true,
        httpClient: MockClient((req) async => _error(401, 'UNAUTHORIZED', 'Expiré')),
      ));

      await expectLater(repo.downloadDocument('901', '12'), throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.unauthorized)));
      expect(asked, isTrue);
    });
  });

  group('DownloadedFile', () {
    DownloadedFile pdf(String? name) => DownloadedFile(bytes: _pdf, contentType: 'application/pdf', fileName: name);

    test('nom lu dans Content-Disposition (simple, entre guillemets, ou encodé)', () {
      expect(DownloadedFile.fileNameFromDisposition('inline; filename="conditions.pdf"'), 'conditions.pdf');
      expect(DownloadedFile.fileNameFromDisposition('inline; filename=conditions.pdf'), 'conditions.pdf');
      expect(DownloadedFile.fileNameFromDisposition("inline; filename*=UTF-8''Attestation%20%C3%A9t%C3%A9.pdf"), 'Attestation été.pdf');
      expect(DownloadedFile.fileNameFromDisposition(null), isNull);
      expect(DownloadedFile.fileNameFromDisposition('inline'), isNull);
    });

    test('nom sûr : pas de dossier, pas de caractères spéciaux, extension du type réel', () {
      expect(pdf('Attestation client.pdf').safeName(), 'Attestation_client.pdf');
      expect(pdf('../../etc/passwd').safeName(), 'passwd.pdf', reason: 'jamais de remontée de dossier');
      expect(pdf(r'C:\Windows\evil.exe').safeName(), 'evil.pdf', reason: 'l\'extension reçue est remplacée par celle du type');
      expect(pdf('a b:c*d?.pdf').safeName(), 'a_b_c_d.pdf', reason: 'caractères spéciaux remplacés, pas de _ en fin');
      expect(pdf(null).safeName('attestation'), 'attestation.pdf');
      expect(pdf('').safeName(), 'document.pdf');
      expect(pdf('...').safeName(), 'document.pdf');
      expect(pdf('x' * 200).safeName().length, lessThanOrEqualTo(64));
    });

    test('types acceptés : PDF, JPEG, PNG ; tout le reste est refusé', () {
      for (final t in ['application/pdf', 'image/jpeg', 'image/png']) {
        expect(DownloadedFile(bytes: _pdf, contentType: t).isSupported, isTrue, reason: t);
      }
      for (final t in ['text/html', 'application/octet-stream', 'application/vnd.android.package-archive', 'image/svg+xml', '']) {
        expect(DownloadedFile(bytes: _pdf, contentType: t).isSupported, isFalse, reason: t);
      }
    });
  });

  group('FileViewer : enregistrement et ouverture', () {
    late List<String> saved;
    late List<String> opened;
    late Future<String> Function(DownloadedFile, String) oldSave;
    late Future<ResultType> Function(String) oldOpen;

    setUp(() {
      saved = [];
      opened = [];
      oldSave = FileViewer.save;
      oldOpen = FileViewer.open;
      FileViewer.save = (file, name) async {
        saved.add(name);
        return '/tmp/documents/$name';
      };
    });
    tearDown(() {
      FileViewer.save = oldSave;
      FileViewer.open = oldOpen;
    });

    Future<void> run(WidgetTester tester, DownloadedFile file, ResultType result) async {
      FileViewer.open = (path) async {
        opened.add(path);
        return result;
      };
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(onPressed: () => FileViewer.show(context, file, fallbackName: 'attestation'), child: const Text('ouvrir')),
          ),
        ),
      ));
      await tester.tap(find.text('ouvrir'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    }

    testWidgets('PDF : enregistré sous un nom sûr puis ouvert, aucun message', (tester) async {
      await run(tester, DownloadedFile(bytes: _pdf, contentType: 'application/pdf', fileName: '../Attestation client.pdf'), ResultType.done);

      expect(saved, ['Attestation_client.pdf']);
      expect(opened, ['/tmp/documents/Attestation_client.pdf']);
      expect(find.byType(SnackBar), findsNothing);
    });

    testWidgets('type inattendu : ni enregistré ni ouvert, message clair', (tester) async {
      await run(tester, DownloadedFile(bytes: _pdf, contentType: 'text/html', fileName: 'x.html'), ResultType.done);

      expect(saved, isEmpty);
      expect(opened, isEmpty);
      expect(find.textContaining('pas pris en charge'), findsOneWidget);
    });

    testWidgets('fichier vide : refusé', (tester) async {
      await run(tester, DownloadedFile(bytes: Uint8List(0), contentType: 'application/pdf'), ResultType.done);

      expect(saved, isEmpty);
      expect(find.textContaining('vide'), findsOneWidget);
    });

    testWidgets('aucune application de lecture : message qui explique quoi faire', (tester) async {
      await run(tester, DownloadedFile(bytes: _pdf, contentType: 'application/pdf'), ResultType.noAppToOpen);

      expect(find.textContaining('lecteur PDF'), findsOneWidget);
    });

    testWidgets('échec d\'enregistrement : message, pas d\'exception', (tester) async {
      FileViewer.save = (file, name) async => throw const FormatException('disque plein');
      await run(tester, DownloadedFile(bytes: _pdf, contentType: 'application/pdf'), ResultType.done);

      expect(find.textContaining('enregistrer'), findsOneWidget);
      expect(opened, isEmpty);
    });
  });
}
