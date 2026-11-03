import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/network/endpoints.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

/// Authentification branchée sur le gateway Java (routes existantes, contrat
/// vérifié sur `/v3/api-docs`).
///
/// Le PIN à 6 chiffres est envoyé comme « mot de passe », le téléphone comme
/// « username ». Inscription, PIN oublié, vérification du PIN et suppression du
/// compte utilisent les routes `/auth/mobile/*` du lot 1.
class ApiAuthRepository implements AuthRepository {
  ApiAuthRepository(this._client, {required this.readRefreshToken, required this.saveTokens});

  final ApiClient _client;
  final Future<String?> Function() readRefreshToken;
  final Future<void> Function(String accessToken, String refreshToken) saveTokens;

  @override
  Future<LoginResult> login({required String phone, required String pin}) async {
    final data = await _client.post(Endpoints.login, body: {'username': phone, 'password': pin});
    final json = _asMap(data);
    if (json['otpRequired'] == true) {
      return LoginOtpChallenge(
        challengeToken: json['challengeToken'].toString(),
        maskedPhone: (json['maskedPhone'] ?? '').toString(),
        expiresInSeconds: (json['expiresInSeconds'] as num?)?.toInt() ?? 300,
      );
    }
    return LoginSuccess(_sessionFrom(json, phone: phone));
  }

  @override
  Future<Session> verifyLoginOtp({required String challengeToken, required String code, required String phone}) async {
    final data = await _client.post(Endpoints.loginOtpVerify, body: {'challengeToken': challengeToken, 'code': code});
    return _sessionFrom(_asMap(data), phone: phone);
  }

  @override
  Future<void> resendLoginOtp(String challengeToken) async {
    await _client.post(Endpoints.loginOtpResend, body: {'challengeToken': challengeToken});
  }

  @override
  Future<Session> resumeSession(String refreshToken, {String? phone}) async {
    final data = _asMap(await _client.post(Endpoints.refresh, body: {'refreshToken': refreshToken}));
    final tokens = _asMap(data['tokens'] ?? data);
    final access = tokens['accessToken'].toString();
    final newRefresh = tokens['refreshToken']?.toString();
    // Le jeton d’accès doit être en place avant l’appel suivant.
    if (newRefresh != null) await saveTokens(access, newRefresh);
    final me = _asMap(await _client.get(Endpoints.me));
    return Session(
      token: access,
      refreshToken: newRefresh,
      expiresAt: _expiry(tokens),
      user: _userFrom(me, phone: phone),
    );
  }

  @override
  Future<void> verifyPin(String pin) async {
    await _client.post(Endpoints.mobilePinVerify, body: {'pin': pin});
  }

  @override
  Future<void> requestOtp(String phone, {required OtpPurpose purpose}) async {
    switch (purpose) {
      case OtpPurpose.resetPin:
        await _client.post(Endpoints.mobileOtpRequest, body: {'phone': phone, 'purpose': 'RESET_PIN'});
      case OtpPurpose.register:
        await _client.post(Endpoints.mobileOtpRequest, body: {'phone': phone, 'purpose': 'REGISTER'});
    }
  }

  @override
  Future<String> verifyOtp({required String phone, required String code}) async {
    final data = _asMap(await _client.post(
      Endpoints.mobileOtpVerify,
      body: {'phone': phone, 'code': code, 'purpose': 'REGISTER'},
    ));
    final token = data['verificationToken']?.toString();
    if (token == null || token.isEmpty) throw ApiException.server();
    return token;
  }

  @override
  Future<Session> register({
    required String fullName,
    required String phone,
    required String pin,
    required String verificationToken,
  }) async {
    final data = _asMap(await _client.post(
      Endpoints.mobileRegister,
      body: {'fullName': fullName.trim(), 'phone': phone, 'pin': pin, 'verificationToken': verificationToken},
    ));
    final session = _sessionFrom(data, phone: phone);
    return Session(
      token: session.token,
      refreshToken: session.refreshToken,
      expiresAt: session.expiresAt,
      user: session.user,
      linkedCount: (data['linkedCount'] as num?)?.toInt() ?? (data['linkedInsured'] == true ? 1 : 0),
    );
  }

  @override
  Future<void> resetPin({required String phone, required String code, required String newPin}) async {
    await _client.post(Endpoints.mobilePinReset, body: {'phone': phone, 'code': code, 'newPin': newPin});
  }

  @override
  Future<void> logout() async {
    final refresh = await readRefreshToken();
    await _client.post(Endpoints.logout, body: refresh == null ? null : {'refreshToken': refresh});
  }

  @override
  Future<String> requestAccountDeletion({required String pin}) async {
    final data = _asMap(await _client.post(Endpoints.mobileDeleteRequest, body: {'pin': pin}));
    return (data['reference'] ?? '').toString();
  }

  @override
  Future<AppUser> updateProfile({String? fullName, String? email}) async {
    // `email` vide efface l'adresse ; un champ non fourni n'est pas modifié.
    final data = _asMap(await _client.patch(
      Endpoints.mobileMe,
      body: {
        if (fullName != null) 'fullName': fullName.trim(),
        if (email != null) 'email': email.trim(),
      },
    ));
    return _userFrom(data);
  }

  // Conversion JSON ----------------------------------------------------------

  Session _sessionFrom(Map<String, dynamic> json, {String? phone}) {
    final tokens = _asMap(json['tokens']);
    return Session(
      token: tokens['accessToken'].toString(),
      refreshToken: tokens['refreshToken']?.toString(),
      expiresAt: _expiry(tokens),
      user: _userFrom(_asMap(json['user']), phone: phone),
    );
  }

  AppUser _userFrom(Map<String, dynamic> j, {String? phone}) => AppUser(
        id: j['id'].toString(),
        fullName: (j['fullName'] ?? '').toString(),
        // `phone` sera fourni par /auth/me (ajout demandé à la Gateway) ; en attendant, numéro saisi.
        phone: (j['phone'] ?? phone ?? '').toString(),
        email: (j['email'] as String?)?.trim().isEmpty ?? true ? null : j['email'].toString(),
      );

  DateTime _expiry(Map<String, dynamic> tokens) =>
      DateTime.now().add(Duration(seconds: (tokens['expiresIn'] as num?)?.toInt() ?? 3600));

  Map<String, dynamic> _asMap(Object? data) {
    if (data is Map<String, dynamic>) return data;
    throw ApiException.server();
  }

}
