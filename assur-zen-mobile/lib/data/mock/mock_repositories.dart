import 'dart:math';

import '../../core/network/api_exception.dart';
import '../../core/network/downloaded_file.dart';
import '../../core/utils/formatters.dart';
import '../models/engagement.dart';
import '../models/models.dart';
import '../models/moto_quote.dart';
import '../repositories/repositories.dart';
import 'mock_data.dart';

/// Dépôts de démonstration : données fictives, délais réseau simulés.
///
/// Règles de démo utiles pour tester les états :
/// - connexion : tout numéro ivoirien valide + n’importe quel PIN de 6 chiffres,
///   sauf « 000000 » qui simule un PIN erroné ;
/// - code SMS : « 000000 » simule un code invalide, tout autre code passe ;
/// - paiement Mobile Money : un numéro qui finit par « 99 » simule un échec ;
///   sinon le paiement est confirmé après environ 5 secondes.
abstract final class MockRepositories {
  static Repositories create({bool withContracts = true}) {
    final state = _MockState(withContracts: withContracts);
    return Repositories(
      auth: _MockAuth(state),
      catalog: _MockCatalog(),
      quotes: _MockQuotes(),
      motoQuotes: _MockMotoQuotes(),
      payments: _MockPayments(),
      contracts: _MockContracts(state),
      claims: _MockClaims(state),
      notifications: _MockNotifications(state),
      support: _MockSupport(),
      engagement: _MockEngagement(),
    );
  }
}

Future<void> _latency([int ms = 700]) => Future.delayed(Duration(milliseconds: ms));

class _MockState {
  _MockState({required this.withContracts})
      : notifications = List.of(MockData.notifications),
        claims = List.of(MockData.claims);

  bool withContracts;
  final List<AppNotification> notifications;
  final List<Claim> claims;
}

class _MockAuth implements AuthRepository {
  _MockAuth(this._state);

  final _MockState _state;

  @override
  Future<LoginResult> login({required String phone, required String pin}) async {
    await _latency(900);
    if (pin == '000000') {
      throw const ApiException(
        kind: ApiErrorKind.business,
        statusCode: 401,
        message: 'Numéro ou code PIN incorrect. Il vous reste 4 essais.',
      );
    }
    _state.withContracts = true;
    return LoginSuccess(
      Session(
        token: 'demo-token',
        user: AppUser(id: 'u-demo', fullName: MockData.demoUser.fullName, phone: phoneDigits(phone), email: MockData.demoUser.email),
        expiresAt: DateTime.now().add(const Duration(days: 30)),
      ),
    );
  }

  @override
  Future<Session> verifyLoginOtp({required String challengeToken, required String code, required String phone}) async {
    await _latency(700);
    if (code == '000000') {
      throw const ApiException(kind: ApiErrorKind.validation, field: 'code', message: 'Code incorrect ou expiré.');
    }
    _state.withContracts = true;
    return Session(
      token: 'demo-token',
      user: AppUser(id: 'u-demo', fullName: MockData.demoUser.fullName, phone: phoneDigits(phone), email: MockData.demoUser.email),
      expiresAt: DateTime.now().add(const Duration(days: 30)),
    );
  }

  @override
  Future<void> resendLoginOtp(String challengeToken) => _latency(600);

  @override
  Future<Session> resumeSession(String refreshToken, {String? phone}) async {
    await _latency(500);
    _state.withContracts = true;
    return Session(token: 'demo-token', user: MockData.demoUser, expiresAt: DateTime.now().add(const Duration(days: 30)));
  }

  @override
  Future<void> verifyPin(String pin) async {
    await _latency(500);
    if (pin == '000000') {
      throw const ApiException(kind: ApiErrorKind.business, statusCode: 401, message: 'Code PIN incorrect.');
    }
  }

  @override
  Future<void> requestOtp(String phone, {required OtpPurpose purpose}) => _latency(600);

  @override
  Future<String> verifyOtp({required String phone, required String code}) async {
    await _latency(700);
    if (code == '000000') {
      throw const ApiException(kind: ApiErrorKind.validation, field: 'code', message: 'Code incorrect ou expiré.');
    }
    return 'demo-verification-token';
  }

  @override
  Future<Session> register({
    required String fullName,
    required String phone,
    required String pin,
    required String verificationToken,
  }) async {
    await _latency(900);
    _state.withContracts = false; // nouveau client : aucun contrat
    return Session(
      token: 'demo-token',
      user: AppUser(id: 'u-new', fullName: fullName.trim(), phone: phoneDigits(phone)),
      expiresAt: DateTime.now().add(const Duration(days: 30)),
    );
  }

  @override
  Future<void> resetPin({required String phone, required String code, required String newPin}) => _latency(800);

  @override
  Future<void> logout() => _latency(200);

  @override
  Future<AppUser> updateProfile({String? fullName, String? email}) async {
    await _latency(500);
    final u = MockData.demoUser;
    return AppUser(id: u.id, fullName: fullName ?? u.fullName, phone: u.phone, email: (email == null || email.isEmpty) ? null : email);
  }

  @override
  Future<String> requestAccountDeletion({required String pin}) async {
    await _latency(800);
    if (pin == '000000') {
      throw const ApiException(kind: ApiErrorKind.business, statusCode: 401, message: 'Code PIN incorrect.');
    }
    return 'DEL-DEMO-000001';
  }
}

class _MockCatalog implements CatalogRepository {
  @override
  Future<List<Product>> getProducts() async {
    await _latency(500);
    return MockData.products;
  }

  @override
  Future<Product> getProduct(String id) async {
    await _latency(200);
    return MockData.products.firstWhere((p) => p.id == id, orElse: () => MockData.products.first);
  }

  @override
  Future<QuoteReferentials> getReferentials(String productId) async {
    await _latency(300);
    return MockData.referentials(productId);
  }
}

class _MockQuotes implements QuoteRepository {
  @override
  Future<Quote> computeQuote(QuoteDraft d) async {
    await _latency(1600);
    // Calcul FICTIF pour la démonstration. En production, le montant vient du serveur.
    const base = {
      '2 – 4 CV': 32000, '5 – 7 CV': 41000, '8 – 10 CV': 56000, '11 CV et +': 72000,
      '≤ 50 cm³': 9000, '51 – 125 cm³': 14000, '126 – 250 cm³': 19000, '> 250 cm³': 26000,
    };
    const formulaFactor = {'tiers': 1.0, 'tiers_plus': 1.6, 'tous_risques': 2.9};
    const durationFactor = {1: 0.12, 3: 0.32, 6: 0.58, 12: 1.0};
    final usage = d.usage == 'Transport de personnes' ? 1.45 : (d.usage == 'Professionnel' ? 1.2 : 1.0);
    final raw = (base[d.power] ?? 41000) * (formulaFactor[d.formulaId] ?? 1.0) * (durationFactor[d.durationMonths] ?? 1.0) * usage;
    final net = (raw / 50).round() * 50;
    final taxes = (net * 0.145 / 50).round() * 50;
    const fees = 2500;
    final formula = MockData.formulas.firstWhere((f) => f.id == d.formulaId, orElse: () => MockData.formulas.first);
    final total = net + taxes + fees;
    return Quote(
      number: 'DV-2026-${(Random().nextInt(9000) + 1000)}',
      productId: d.productId,
      netPremium: net,
      fees: fees,
      taxes: taxes,
      guarantees: formula.guarantees,
      validUntil: DateTime.now().add(const Duration(days: 30)),
      requiredDocuments: RequiredDocument.values,
      installmentAmount: (total / 4 / 50).ceil() * 50,
      isDemo: true,
    );
  }

  @override
  Future<void> uploadDocument({required String quoteNumber, required RequiredDocument type, required String filePath}) =>
      _latency(900);

  @override
  Future<CallbackReceipt> requestCallback({required String phone, required String slot, String? productId}) async {
    await _latency(800);
    // Démonstration : le mode réel n'annonce un délai que s'il vient du serveur.
    return const CallbackReceipt(reference: 'CB-DEMO-000001', expectedWithinMinutes: 30);
  }
}

/// Démonstration uniquement : montants fictifs, jamais utilisés en mode réel.
class _MockMotoQuotes implements MotoQuoteRepository {
  final List<MotoQuote> _saved = [];

  static const _premium = MotoPremium(netPremium: 6447, taxes: 925, accessories: 500, fga: 128, total: 8000);

  MotoQuoteSimulation _simulation(MotoQuoteRequest r) => MotoQuoteSimulation(
        formulaId: 'demo',
        formulaName: 'Formule de démonstration',
        vehicleType: r.vehicleType,
        durationMonths: r.durationMonths,
        effectiveDate: r.effectiveDate,
        expiryDate: DateTime(r.effectiveDate.year + 1, r.effectiveDate.month, r.effectiveDate.day - 1),
        premium: _premium,
        guarantees: const [
          MotoGuarantee(label: 'Garantie de démonstration', details: []),
        ],
      );

  @override
  Future<MotoQuoteForm> getForm() async {
    await _latency(300);
    return MotoQuoteForm(
      vehicleTypes: const ['MOTO', 'TRICYCLE'],
      durations: const [MotoQuoteDuration(months: 12, total: 8000)],
      formulaId: 'demo',
      formulaName: 'Formule de démonstration',
      brands: const ['Marque démo A', 'Marque démo B'],
      minEffectiveDate: DateTime.now(),
    );
  }

  @override
  Future<MotoQuoteSimulation> simulate(MotoQuoteRequest request) async {
    await _latency(500);
    return _simulation(request);
  }

  @override
  Future<MotoQuote> create(MotoQuoteRequest request) async {
    await _latency(500);
    final quote = MotoQuote(
      reference: 'DEV-DEMO-${(_saved.length + 1).toString().padLeft(6, '0')}',
      status: MotoQuoteStatus.calcule,
      canAccept: true,
      simulation: _simulation(request),
      cylinderCc: request.cylinderCc,
      brand: request.brand,
      model: request.model,
      registrationNumber: request.registrationNumber,
      chassisNumber: request.chassisNumber,
      validUntil: DateTime.now().add(const Duration(days: 30)),
      createdAt: DateTime.now(),
    );
    _saved.insert(0, quote);
    return quote;
  }

  @override
  Future<List<MotoQuote>> list() async => List.of(_saved);

  @override
  Future<MotoQuote> get(String reference) async => _saved.firstWhere((q) => q.reference == reference);

  @override
  Future<MotoQuote> accept(String reference) async {
    await _latency(400);
    final i = _saved.indexWhere((q) => q.reference == reference);
    final accepted = _saved[i].copyAccepted(DateTime.now());
    _saved[i] = accepted;
    return accepted;
  }

  // VTC : montants fictifs, jamais utilisés en mode réel.
  static const _vtcPremium = MotoPremium(
    netPremium: 181363,
    taxes: 0,
    accessories: 2024,
    fga: 280,
    cedeao: 1000,
    attestation: 1000,
    total: 185667,
  );

  MotoQuoteSimulation _vtcSimulation(VtcQuoteRequest r) => MotoQuoteSimulation(
        formulaId: 'demo-vtc',
        formulaName: 'Formule de démonstration',
        vehicleType: 'VTC',
        durationMonths: r.durationMonths,
        effectiveDate: r.effectiveDate,
        expiryDate: DateTime(r.effectiveDate.year + 1, r.effectiveDate.month, r.effectiveDate.day - 1),
        premium: _vtcPremium,
        guarantees: const [
          MotoGuarantee(label: 'Garantie de démonstration', details: []),
        ],
      );

  @override
  Future<VtcQuoteForm> getVtcForm() async {
    await _latency(300);
    return VtcQuoteForm(
      energies: const ['ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE'],
      durations: const [MotoQuoteDuration(months: 12, total: 185667)],
      formulaId: 'demo-vtc',
      formulaName: 'Formule de démonstration',
      brands: const ['Marque démo A', 'Marque démo B'],
      platforms: const ['Plateforme démo'],
      minFiscalPower: 3,
      minSeats: 4,
      maxSeats: 8,
      minEffectiveDate: DateTime.now(),
    );
  }

  @override
  Future<MotoQuoteSimulation> simulateVtc(VtcQuoteRequest request) async {
    await _latency(500);
    return _vtcSimulation(request);
  }

  @override
  Future<MotoQuote> createVtc(VtcQuoteRequest request) async {
    await _latency(500);
    final quote = MotoQuote(
      reference: 'DEV-DEMO-${(_saved.length + 1).toString().padLeft(6, '0')}',
      status: MotoQuoteStatus.calcule,
      canAccept: true,
      simulation: _vtcSimulation(request),
      cylinderCc: 0,
      brand: request.brand,
      model: request.model,
      registrationNumber: request.registrationNumber,
      chassisNumber: request.chassisNumber,
      validUntil: DateTime.now().add(const Duration(days: 30)),
      createdAt: DateTime.now(),
      productKey: 'automobile-vtc',
      fiscalPower: request.fiscalPower,
      energy: request.energy,
      seats: request.seats,
      firstRegistrationYear: request.firstRegistrationYear,
      vtcDeclared: request.vtcDeclared,
      platform: request.platform,
    );
    _saved.insert(0, quote);
    return quote;
  }
}

class _MockPayments implements PaymentRepository {
  final Map<String, DateTime> _startedAt = {};
  final Map<String, bool> _willFail = {};

  @override
  Future<List<PaymentMethod>> getMethods() async {
    await _latency(300);
    return MockData.paymentMethods;
  }

  @override
  Future<PaymentAttempt> initiate({required String payableReference, required int amount, required String methodId, String? phone}) async {
    await _latency(900);
    final ref = 'TX-${DateTime.now().millisecondsSinceEpoch % 100000000}';
    _startedAt[ref] = DateTime.now();
    _willFail[ref] = (phone ?? '').endsWith('99');
    return PaymentAttempt(reference: ref, status: PaymentStatus.pending);
  }

  @override
  Future<PaymentAttempt> convertQuote({required String quoteReference, required int amount, required String methodId, String? phone}) =>
      initiate(payableReference: quoteReference, amount: amount, methodId: methodId, phone: phone);

  @override
  Future<PaymentAttempt> status(String reference) async {
    await _latency(400);
    final started = _startedAt[reference] ?? DateTime.now();
    if (DateTime.now().difference(started) < const Duration(seconds: 5)) {
      return PaymentAttempt(reference: reference, status: PaymentStatus.pending);
    }
    if (_willFail[reference] ?? false) {
      return PaymentAttempt(reference: reference, status: PaymentStatus.failed, failureReason: 'Solde insuffisant ou opération refusée.');
    }
    return PaymentAttempt(reference: reference, status: PaymentStatus.confirmed);
  }
}

/// Démonstration : textes clairement marqués « Démo ». En service réel, tout vient de la Gateway.
class _MockEngagement implements EngagementRepository {
  bool _accepted = false;

  Engagement get _current => Engagement(
        enabled: true,
        consent: EngagementConsent(accepted: _accepted, acceptedAt: _accepted ? DateTime.now() : null, acceptedVersion: _accepted ? 1 : null),
        features: const EngagementFeatures(protectionScore: true),
        consentText: '[Démo] J’accepte qu’AssurZen enregistre mes actions dans l’application pour calculer mon score de protection.',
        consentVersion: 1,
      );

  @override
  Future<Engagement> getEngagement() async {
    await _latency(300);
    return _current;
  }

  @override
  Future<Engagement> setConsent({required bool accepted, int? consentVersion}) async {
    await _latency(400);
    _accepted = accepted;
    return _current;
  }

  @override
  Future<ProtectionScore> getProtectionScore() async {
    await _latency(400);
    if (!_accepted) {
      throw const ApiException(kind: ApiErrorKind.business, statusCode: 403, code: 'ENGAGEMENT_CONSENT_REQUIRED', message: 'Consentement requis.');
    }
    return ProtectionScore(
      score: 60,
      updatedAt: DateTime.now(),
      items: const [
        ScoreItem(id: 'PROFILE_COMPLETE', label: '[Démo] Profil complet', done: true),
        ScoreItem(id: 'ACTIVE_CONTRACT', label: '[Démo] Un contrat actif', done: true),
        ScoreItem(id: 'ATTESTATION_DOWNLOADED', label: '[Démo] Attestation téléchargée', done: false, hint: 'Ouvrez votre attestation.', action: ScoreAction.documents),
      ],
    );
  }
}

class _MockContracts implements ContractRepository {
  _MockContracts(this._state);

  final _MockState _state;

  @override
  Future<List<Contract>> getContracts() async {
    await _latency(800);
    return _state.withContracts ? MockData.contracts : const [];
  }

  @override
  Future<Contract> getContract(String id) async {
    await _latency(400);
    return MockData.contracts.firstWhere((c) => c.id == id, orElse: () => MockData.contracts.first);
  }

  // Démonstration : aucun fichier réel.
  @override
  Future<DownloadedFile> downloadDocument(String contractId, String documentId) async {
    await _latency(400);
    throw ApiException.notImplemented('téléchargement de document');
  }

  @override
  Future<DownloadedFile> downloadAttestation(String contractId) async {
    await _latency(400);
    throw ApiException.notImplemented('téléchargement de l’attestation');
  }

  @override
  Future<ContractRequestReceipt> requestChange({
    required String contractId,
    required ContractRequestType type,
    String? message,
  }) async {
    await _latency(600);
    return const ContractRequestReceipt(reference: 'RQ-DEMO-000001', expectedWithinHours: 24);
  }
}

class _MockClaims implements ClaimRepository {
  _MockClaims(this._state);

  final _MockState _state;

  @override
  Future<List<Claim>> getClaims() async {
    await _latency(700);
    return _state.withContracts ? List.of(_state.claims) : const [];
  }

  @override
  Future<Claim> getClaim(String id) async {
    await _latency(400);
    return _state.claims.firstWhere((c) => c.id == id, orElse: () => _state.claims.first);
  }

  @override
  Future<List<ClaimType>> getClaimTypes(String productId) async {
    await _latency(200);
    return [for (final t in MockData.claimTypes) ClaimType(t, t)];
  }

  @override
  Future<Claim> declare(ClaimDraft draft) async {
    await _latency(1400);
    final contract = MockData.contracts.firstWhere((c) => c.id == draft.contractId, orElse: () => MockData.contracts.first);
    final claim = Claim(
      id: 'k${_state.claims.length + 1}',
      type: draft.type ?? 'Autre',
      contractLabel: '${contract.name} · ${contract.insuredObject.split(' · ').last}',
      number: 'SIN-2026-00${900 + _state.claims.length}',
      declaredAt: DateTime.now(),
      steps: MockData.claimSteps,
      currentStep: 0,
      history: [
        ClaimEvent(DateTime.now(),
            'Déclaration reçue avec ${draft.photoPaths.length} photo(s) et ${draft.documentPaths.length} document(s).'),
      ],
    );
    _state.claims.insert(0, claim);
    return claim;
  }
}

class _MockNotifications implements NotificationRepository {
  _MockNotifications(this._state);

  final _MockState _state;

  @override
  Future<List<AppNotification>> getNotifications() async {
    await _latency(600);
    return List.of(_state.notifications);
  }

  @override
  Future<int> getUnreadCount() async => _state.notifications.where((n) => !n.read).length;

  final Map<NotificationCategory, bool> _prefs = {for (final c in NotificationCategory.values) c: true};

  @override
  Future<List<NotificationPreference>> getPreferences() async =>
      [for (final c in NotificationCategory.values) NotificationPreference(c, _prefs[c] ?? true)];

  @override
  Future<List<NotificationPreference>> setPreference(NotificationCategory category, bool enabled) async {
    _prefs[category] = enabled;
    return getPreferences();
  }

  @override
  Future<String> registerDevice({required String token, String? appVersion}) async => 'demo-device';

  @override
  Future<void> unregisterDevice(String deviceId) async {}

  @override
  Future<void> markRead(String id) async {
    final i = _state.notifications.indexWhere((n) => n.id == id);
    if (i >= 0) _state.notifications[i] = _state.notifications[i].markRead();
  }

  @override
  Future<void> markAllRead() async {
    for (var i = 0; i < _state.notifications.length; i++) {
      _state.notifications[i] = _state.notifications[i].markRead();
    }
  }
}

class _MockSupport implements SupportRepository {
  @override
  Future<ContactInfo> getContactInfo() async => MockData.contactInfo;

  @override
  Future<LegalLinks> getLegalLinks() async => LegalLinks.empty;

  @override
  Future<List<SupportTopic>> getTopics() async => const [
        SupportTopic('CONTRACT', 'Contrat'),
        SupportTopic('PAYMENT', 'Paiement'),
        SupportTopic('CLAIM', 'Sinistre'),
        SupportTopic('OTHER', 'Autre'),
      ];

  @override
  Future<List<SupportRequestItem>> getRequests() async => const [];

  @override
  Future<String> sendFeedback({required int rating, String? comment, String? contractId}) async {
    await _latency(300);
    return 'FB-DEMO-000001';
  }

  @override
  Future<List<FaqItem>> getFaq() async {
    await _latency(300);
    return MockData.faq;
  }

  @override
  Future<SupportReceipt> sendRequest({required String topicId, required String message}) async {
    await _latency(900);
    // Démonstration seulement : le mode réel n'annonce un délai que s'il vient du serveur.
    return SupportReceipt(reference: 'SP-DEMO-${Random().nextInt(9000) + 1000}', expectedWithinMinutes: 120);
  }
}
