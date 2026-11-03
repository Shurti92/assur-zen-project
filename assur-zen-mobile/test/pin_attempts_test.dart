import 'dart:convert';

import 'package:assurzen_mobile/core/network/api_client.dart';
import 'package:assurzen_mobile/core/network/api_exception.dart';
import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/core/state/async_view.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

// Réponse réelle de la Gateway sur un PIN faux (relevée sur le gateway dev) :
// {"error":"INVALID_CREDENTIALS","message":"Identifiants invalides","remainingAttempts":4,"code":"INVALID_CREDENTIALS"}

http.Response _json(int status, Object? body) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

Future<ApiException> _failWith(Map<String, Object?> body, {int status = 401}) async {
  var unauthorizedCalls = 0;
  final client = ApiClient(
    baseUrl: 'http://gw.test',
    tokenProvider: () async => 'jeton',
    onUnauthorized: () => unauthorizedCalls++,
    httpClient: MockClient((_) async => _json(status, body)),
  );
  try {
    await client.post(Endpoints.mobilePinVerify, body: {'pin': '111222'});
  } on ApiException catch (e) {
    // Un mauvais PIN est une erreur métier : il ne verrouille pas l'application.
    expect(unauthorizedCalls, 0);
    return e;
  }
  fail('une ApiException était attendue');
}

void main() {
  test('un PIN faux : les tentatives restantes du serveur sont lues et annoncées', () async {
    final e = await _failWith({
      'error': 'INVALID_CREDENTIALS',
      'message': 'Identifiants invalides',
      'remainingAttempts': 4,
      'code': 'INVALID_CREDENTIALS',
    });

    expect(e.code, 'INVALID_CREDENTIALS');
    expect(e.remainingAttempts, 4);
    expect(errorMessage(e), 'Identifiants invalides. Il vous reste 4 tentatives.');
  });

  test('au singulier quand il reste une seule tentative', () async {
    final e = await _failWith({'code': 'INVALID_CREDENTIALS', 'message': 'Identifiants invalides', 'remainingAttempts': 1});

    expect(errorMessage(e), 'Identifiants invalides. Il vous reste 1 tentative.');
  });

  test('rien n\'est inventé : sans chiffre du serveur, ou à zéro, le message reste celui du serveur', () async {
    final sans = await _failWith({'code': 'INVALID_CREDENTIALS', 'message': 'Identifiants invalides'});
    final zero = await _failWith({'code': 'INVALID_CREDENTIALS', 'message': 'Identifiants invalides', 'remainingAttempts': 0});

    expect(sans.remainingAttempts, isNull);
    expect(errorMessage(sans), 'Identifiants invalides');
    expect(errorMessage(zero), 'Identifiants invalides');
  });
}
