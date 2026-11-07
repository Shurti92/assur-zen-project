import '../../core/network/downloaded_file.dart';
import '../models/engagement.dart';
import '../models/loyalty.dart';
import '../models/models.dart';
import '../models/moto_quote.dart';
import '../models/promo_popup.dart';

/// Contrats des dépôts de données.
///
/// L’interface ne change pas entre la démonstration ([MockRepositories]) et la
/// production ([ApiRepositories]) : les écrans ne savent pas d’où viennent les
/// données. Chaque méthode peut lever une `ApiException`.

abstract interface class AuthRepository {
  /// Connexion par numéro + code PIN (décisions 4.1 et 4.2). Peut demander une
  /// double authentification par SMS ([LoginOtpChallenge]).
  Future<LoginResult> login({required String phone, required String pin});

  /// Seconde étape de la connexion : code SMS reçu après un [LoginOtpChallenge].
  Future<Session> verifyLoginOtp({required String challengeToken, required String code, required String phone});

  /// Renvoie le code SMS de la double authentification.
  Future<void> resendLoginOtp(String challengeToken);

  /// Reprise de session avec le jeton de renouvellement (après déverrouillage).
  Future<Session> resumeSession(String refreshToken, {String? phone});

  /// Vérifie le PIN de l’utilisateur déjà connecté (avant un paiement) sans
  /// ouvrir de nouvelle session.
  Future<void> verifyPin(String pin);

  /// Envoi d’un code SMS : inscription ou PIN oublié. 3 envois par heure maximum.
  Future<void> requestOtp(String phone, {required OtpPurpose purpose});

  /// Vérifie le code SMS d’inscription et renvoie le jeton de vérification
  /// (à usage unique, valable 10 min) à transmettre à [register].
  Future<String> verifyOtp({required String phone, required String code});

  Future<Session> register({
    required String fullName,
    required String phone,
    required String pin,
    required String verificationToken,
  });

  Future<void> resetPin({required String phone, required String code, required String newPin});

  Future<void> logout();

  /// Décision 8.5 : demande de suppression du compte (PIN re-saisi). Renvoie la
  /// référence de la demande.
  Future<String> requestAccountDeletion({required String pin});

  /// Met à jour le profil (nom, e-mail). Le téléphone n'est pas modifiable.
  /// Renvoie le profil enregistré par le serveur.
  Future<AppUser> updateProfile({String? fullName, String? email});
}

enum OtpPurpose { register, resetPin }

abstract interface class CatalogRepository {
  Future<List<Product>> getProducts();

  Future<Product> getProduct(String id);

  Future<QuoteReferentials> getReferentials(String productId);
}

abstract interface class QuoteRepository {
  /// Calcul du devis par le serveur. Le montant n’est jamais calculé localement.
  Future<Quote> computeQuote(QuoteDraft draft);

  Future<void> uploadDocument({required String quoteNumber, required RequiredDocument type, required String filePath});

  /// Demande de rappel par un conseiller. [slot] : ASAP, MORNING, AFTERNOON ou
  /// EVENING. Le délai de rappel n'est annoncé que s'il est fourni par le serveur.
  Future<CallbackReceipt> requestCallback({required String phone, required String slot, String? productId});
}

/// Devis en ligne moto : simulation publique, puis devis enregistrés du client connecté.
/// Le prix est toujours celui du serveur.
abstract interface class MotoQuoteRepository {
  Future<MotoQuoteForm> getForm();

  Future<MotoQuoteSimulation> simulate(MotoQuoteRequest request);

  Future<MotoQuote> create(MotoQuoteRequest request);

  Future<List<MotoQuote>> list();

  Future<MotoQuote> get(String reference);

  Future<MotoQuote> accept(String reference);

  /// Devis VTC (assurance automobile) : même cycle que la moto.
  Future<VtcQuoteForm> getVtcForm();

  Future<MotoQuoteSimulation> simulateVtc(VtcQuoteRequest request);

  Future<MotoQuote> createVtc(VtcQuoteRequest request);

  /// Assurance Voyage : formulaire (pays, bornes, date du jour du serveur), simulation publique puis devis enregistré.
  Future<VoyageForm> getVoyageForm();

  Future<VoyageSimulation> simulateVoyage(VoyageRequest request);

  Future<MotoQuote> createVoyage(VoyageRequest request);
}

abstract interface class PaymentRepository {
  Future<List<PaymentMethod>> getMethods();

  /// Lance une tentative de paiement ; renvoie sa référence unique.
  Future<PaymentAttempt> initiate({
    required String payableReference, // n° de devis ou de contrat
    required int amount,
    required String methodId,
    String? phone,
  });

  /// Paie un devis accepté (`POST /mobile/quotes/{reference}/convert`) : même tentative que
  /// [initiate] (page de l'opérateur à ouvrir, statut PENDING, expiration). Le montant vient du serveur ;
  /// [amount] n'est envoyé que pour contrôle.
  Future<PaymentAttempt> convertQuote({
    required String quoteReference,
    required int amount,
    required String methodId,
    String? phone,
  });

  /// Statut confirmé par le serveur (webhook de l’agrégateur côté backend).
  Future<PaymentAttempt> status(String reference);
}

/// Engagement : le serveur décide de tout (activation, texte, score) ; l'application affiche.
abstract interface class EngagementRepository {
  /// Réglages et consentement du client (`GET /mobile/engagement`).
  Future<Engagement> getEngagement();

  /// Accepte ou retire le consentement. [consentVersion] = version du texte que le client a lue.
  /// Retirer le consentement efface les données d'engagement du client côté serveur.
  Future<Engagement> setConsent({required bool accepted, int? consentVersion});

  /// Score de protection. Sans consentement : `ApiException` de code `ENGAGEMENT_CONSENT_REQUIRED` ;
  /// fonction coupée : `FEATURE_DISABLED`.
  Future<ProtectionScore> getProtectionScore();

  /// Badges du client, obtenus ou non (`GET /mobile/badges`). Mêmes erreurs que le score.
  Future<List<AppBadge>> getBadges();

  /// Question du jour. `null` : le serveur n'a rien à proposer (204, aucune question active ou toutes répondues).
  Future<QuizQuestion?> getQuizToday();

  /// Répond à la question du jour. Une 2e réponse à la même question renvoie le résultat déjà enregistré.
  /// 409 `QUIZ_QUESTION_NOT_CURRENT` : la question n'est plus celle du jour.
  Future<QuizAnswer> answerQuiz({required String questionId, required String choiceId});
}

abstract interface class ContractRepository {
  Future<List<Contract>> getContracts();

  Future<Contract> getContract(String id);

  /// Fichier d'un document du contrat (téléchargé avec le jeton de session).
  Future<DownloadedFile> downloadDocument(String contractId, String documentId);

  /// Fichier de l'attestation du contrat.
  Future<DownloadedFile> downloadAttestation(String contractId);

  /// Ajout d'un document par l'assuré (PDF, JPG ou PNG, 5 Mo). Le serveur le
  /// met « en attente » ; AssurZen le valide ou le refuse.
  Future<void> uploadDocument({required String contractId, required ClientDocumentType type, required String filePath});

  /// Suppression d'un document ajouté par l'assuré et non validé.
  Future<void> deleteDocument({required String contractId, required String documentId});

  /// Demande de modification ou de résiliation, traitée par un conseiller.
  Future<ContractRequestReceipt> requestChange({
    required String contractId,
    required ContractRequestType type,
    String? message,
  });
}

abstract interface class ClaimRepository {
  Future<List<Claim>> getClaims();

  Future<Claim> getClaim(String id);

  Future<List<ClaimType>> getClaimTypes(String productId);

  Future<Claim> declare(ClaimDraft draft);
}

abstract interface class NotificationRepository {
  /// Notifications du client, de la plus récente à la plus ancienne (textes du serveur).
  Future<List<AppNotification>> getNotifications();

  /// Nombre de notifications non lues (pastille de la cloche).
  Future<int> getUnreadCount();

  /// Préférences push par catégorie (tout est activé par défaut côté serveur).
  Future<List<NotificationPreference>> getPreferences();

  /// Active ou désactive le push d'une catégorie ; renvoie la liste à jour.
  Future<List<NotificationPreference>> setPreference(NotificationCategory category, bool enabled);

  /// Préférences par canal (push, SMS) et par catégorie. `hasChannels` faux = le serveur
  /// ne les fournit pas encore.
  Future<NotificationSettings> getSettings();

  /// Modifie un canal et/ou des catégories ; seuls les champs donnés changent. Renvoie l'état à jour.
  Future<NotificationSettings> updateSettings({bool? push, bool? sms, String? smsConsentVersion, List<CategoryChannels>? categories});

  /// Enregistre l'appareil (jeton FCM) pour les notifications push. Idempotent par
  /// jeton ; renvoie l'identifiant de l'appareil à conserver pour la déconnexion.
  Future<String> registerDevice({required String token, String? appVersion});

  /// Retire l'appareil à la déconnexion (idempotent).
  Future<void> unregisterDevice(String deviceId);

  Future<void> markRead(String id);

  Future<void> markAllRead();
}

abstract interface class SupportRepository {
  /// Coordonnées et disponibilité fournies par le serveur (route publique).
  Future<ContactInfo> getContactInfo();

  /// Liens légaux (conditions, confidentialité) : un lien `null` est masqué.
  Future<LegalLinks> getLegalLinks();

  /// Sujets proposés, fournis par le serveur.
  Future<List<SupportTopic>> getTopics();

  /// Mes demandes au support, avec la réponse du conseiller.
  Future<List<SupportRequestItem>> getRequests();

  /// Avis après souscription (1 à 5). Renvoie la référence.
  Future<String> sendFeedback({required int rating, String? comment, String? contractId});

  Future<List<FaqItem>> getFaq();

  /// Renvoie la référence de la demande et, si le serveur en annonce un, le délai
  /// de réponse.
  Future<SupportReceipt> sendRequest({required String topicId, required String message});
}

/// Ensemble des dépôts injectés dans l’application.
class Repositories {
  const Repositories({
    required this.auth,
    required this.catalog,
    required this.quotes,
    required this.motoQuotes,
    required this.payments,
    required this.contracts,
    required this.claims,
    required this.notifications,
    required this.support,
    required this.engagement,
    this.promotions = const NoPromotions(),
    this.loyalty = const NoLoyalty(),
  });

  final AuthRepository auth;
  final CatalogRepository catalog;
  final QuoteRepository quotes;
  final MotoQuoteRepository motoQuotes;
  final PaymentRepository payments;
  final ContractRepository contracts;
  final ClaimRepository claims;
  final NotificationRepository notifications;
  final SupportRepository support;
  final EngagementRepository engagement;
  final PromotionRepository promotions;
  final LoyaltyRepository loyalty;
}

/// Pop-up publicitaires paramétrés au back-office.
abstract class PromotionRepository {
  /// Campagnes actives pour un emplacement, triées par priorité. Erreur ou rien : liste vide.
  Future<List<PromoPopup>> getPopups({String placement = 'HOME'});

  /// Mesure anonyme (VIEW, CLICK, CLOSE) ; n'échoue jamais.
  Future<void> sendPopupEvent(String id, String type);
}

/// Aucune campagne (tests, démonstration).
class NoPromotions implements PromotionRepository {
  const NoPromotions();

  @override
  Future<List<PromoPopup>> getPopups({String placement = 'HOME'}) async => const [];

  @override
  Future<void> sendPopupEvent(String id, String type) async {}
}

/// Zen Points (phase A). `getOverview` renvoie `null` quand le serveur ne propose pas le programme.
abstract interface class LoyaltyRepository {
  Future<LoyaltyOverview?> getOverview();

  /// Accepte ou retire l'accord ; renvoie la synthèse à jour.
  Future<LoyaltyOverview?> setConsent({required bool accepted, String? version});

  Future<LoyaltyRules> getRules();

  Future<LoyaltyHistory> getHistory({int page = 0, int size = 20});

  /// Remises du niveau du client ; `null` si elles sont coupées ou si le client n'y a pas droit.
  Future<LoyaltyDiscounts?> getDiscounts();
}

/// Programme absent (démonstration, serveur sans fidélité) : rien n'est affiché.
class NoLoyalty implements LoyaltyRepository {
  const NoLoyalty();

  @override
  Future<LoyaltyOverview?> getOverview() async => null;

  @override
  Future<LoyaltyOverview?> setConsent({required bool accepted, String? version}) async => null;

  @override
  Future<LoyaltyRules> getRules() async => const LoyaltyRules();

  @override
  Future<LoyaltyHistory> getHistory({int page = 0, int size = 20}) async => const LoyaltyHistory(items: [], total: 0);

  @override
  Future<LoyaltyDiscounts?> getDiscounts() async => null;
}
