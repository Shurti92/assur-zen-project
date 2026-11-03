import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../app/env.dart';
import 'api_exception.dart';
import 'endpoints.dart';

/// Client HTTP unique vers le backend Java / Spring Boot.
///
/// - ajoute le jeton de session (en-tête `Authorization: Bearer …`) ;
/// - sur un 401 d’une route protégée, renouvelle le jeton UNE fois
///   (`POST /auth/refresh`, jeton rotatif) puis rejoue l’appel ;
/// - si le renouvellement échoue, appelle [onUnauthorized] (écran de PIN) ;
/// - convertit les erreurs réseau, 4xx et 5xx en [ApiException].
///
/// Format d’erreur du gateway : `{ code | error, message, timestamp }` (+ `field`
/// et `remainingSeconds` / `retryAfterSeconds` selon le cas).
class ApiClient {
  ApiClient({
    required this.baseUrl,
    required this.tokenProvider,
    this.refreshTokenProvider,
    this.onTokensRefreshed,
    this.onUnauthorized,
    http.Client? httpClient,
  }) : _http = httpClient ?? http.Client();

  final String baseUrl;
  final Future<String?> Function() tokenProvider;
  final Future<String?> Function()? refreshTokenProvider;

  /// Appelé avec le nouveau couple de jetons après un renouvellement réussi.
  final Future<void> Function(String accessToken, String refreshToken)? onTokensRefreshed;
  final void Function()? onUnauthorized;
  final http.Client _http;

  /// Un seul renouvellement à la fois : le refresh token est à usage unique.
  Future<bool>? _refreshing;

  Future<dynamic> get(String path, {Map<String, String>? query}) =>
      _send('GET', path, query: query);

  Future<dynamic> post(String path, {Object? body}) => _send('POST', path, body: body);

  Future<dynamic> put(String path, {Object? body}) => _send('PUT', path, body: body);

  Future<dynamic> patch(String path, {Object? body}) => _send('PATCH', path, body: body);

  Future<dynamic> delete(String path) => _send('DELETE', path);

  /// Envoi d’un fichier (pièce justificative, photo de sinistre).
  Future<dynamic> upload(String path, {required String filePath, required String field, Map<String, String>? fields}) async {
    final request = http.MultipartRequest('POST', _uri(path));
    request.headers.addAll(await _headers(json: false));
    if (fields != null) request.fields.addAll(fields);
    request.files.add(await http.MultipartFile.fromPath(field, filePath));
    try {
      final streamed = await _http.send(request).timeout(const Duration(seconds: 60));
      final response = await http.Response.fromStream(streamed);
      return _decode(response, path: path);
    } on SocketException {
      throw ApiException.network();
    } on TimeoutException {
      throw ApiException.timeout();
    }
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    final base = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;
    return Uri.parse('$base$path').replace(queryParameters: query);
  }

  Future<Map<String, String>> _headers({bool json = true, String path = ''}) async {
    final token = Endpoints.isPublicAuthPath(path) ? null : await tokenProvider();
    return {
      'Accept': 'application/json',
      'Accept-Language': 'fr-CI',
      if (json) 'Content-Type': 'application/json; charset=utf-8',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<dynamic> _send(String method, String path, {Object? body, Map<String, String>? query, bool retried = false}) async {
    final request = http.Request(method, _uri(path, query));
    request.headers.addAll(await _headers(path: path));
    if (body != null) request.body = jsonEncode(body);
    try {
      final streamed = await _http.send(request).timeout(Env.requestTimeout);
      final response = await http.Response.fromStream(streamed);
      if (response.statusCode == 401 &&
          !retried &&
          !Endpoints.isPublicAuthPath(path) &&
          _errorCode(response) != 'INVALID_CREDENTIALS' &&
          await _tryRefresh()) {
        return await _send(method, path, body: body, query: query, retried: true);
      }
      return _decode(response, path: path);
    } on SocketException {
      throw ApiException.network();
    } on TimeoutException {
      throw ApiException.timeout();
    } on http.ClientException {
      throw ApiException.network();
    }
  }

  Future<bool> _tryRefresh() {
    final provider = refreshTokenProvider;
    final saved = onTokensRefreshed;
    if (provider == null || saved == null) return Future.value(false);
    return _refreshing ??= () async {
      try {
        final refresh = await provider();
        if (refresh == null) return false;
        final response = await _http
            .post(
              _uri(Endpoints.refresh),
              headers: {'Accept': 'application/json', 'Content-Type': 'application/json; charset=utf-8'},
              body: jsonEncode({'refreshToken': refresh}),
            )
            .timeout(Env.requestTimeout);
        if (response.statusCode < 200 || response.statusCode >= 300) return false;
        final data = _tryJson(utf8.decode(response.bodyBytes));
        final root = data is Map<String, dynamic> ? data : null;
        final tokens = root?['tokens'] is Map<String, dynamic> ? root!['tokens'] as Map<String, dynamic> : root;
        final access = tokens?['accessToken']?.toString();
        final newRefresh = tokens?['refreshToken']?.toString();
        if (access == null || newRefresh == null) return false;
        await saved(access, newRefresh);
        return true;
      } catch (_) {
        return false;
      } finally {
        _refreshing = null;
      }
    }();
  }

  String? _errorCode(http.Response response) {
    final data = _tryJson(utf8.decode(response.bodyBytes));
    return data is Map<String, dynamic> ? (data['code'] ?? data['error'])?.toString() : null;
  }

  dynamic _decode(http.Response response, {String path = ''}) {
    final status = response.statusCode;
    final text = utf8.decode(response.bodyBytes);
    final dynamic data = text.isEmpty ? null : _tryJson(text);

    if (status >= 200 && status < 300) return data;
    final map = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    // Le gateway utilise `code`, et `error` sur le refus d’identifiants.
    final code = (map['code'] ?? map['error'])?.toString();

    // 401 sur une route protégée : jeton expiré et non renouvelable → écran de PIN.
    // INVALID_CREDENTIALS (ex. mauvais PIN sur `pin/verify`) est une erreur métier.
    if (status == 401 && !Endpoints.isPublicAuthPath(path) && code != 'INVALID_CREDENTIALS') {
      onUnauthorized?.call();
      throw ApiException.unauthorized();
    }
    if (status >= 400 && status < 500) {
      var field = map['field']?.toString();
      var message = map['message']?.toString() ?? 'Votre demande n’a pas pu être traitée.';
      // Validation sans `field` : le message a la forme « champ: raison ».
      if (code == 'VALIDATION_FAILED' && field == null) {
        final m = RegExp(r'^(\w+):\s*(.+)$').firstMatch(message);
        if (m != null) {
          field = m.group(1);
          final reason = m.group(2)!;
          message = '${reason[0].toUpperCase()}${reason.substring(1)}';
        }
      }
      final retry = map['retryAfterSeconds'] ?? map['remainingSeconds'];
      final attempts = map['remainingAttempts'];
      throw ApiException(
        kind: status == 400 || status == 422 ? ApiErrorKind.validation : ApiErrorKind.business,
        statusCode: status,
        code: code,
        field: field,
        message: message,
        retryAfterSeconds: retry is num ? retry.toInt() : null,
        remainingAttempts: attempts is num ? attempts.toInt() : null,
      );
    }
    throw ApiException.server(status);
  }

  dynamic _tryJson(String text) {
    try {
      return jsonDecode(text);
    } on FormatException {
      return text;
    }
  }
}
