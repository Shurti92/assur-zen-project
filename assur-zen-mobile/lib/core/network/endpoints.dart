/// Routes de l’API AssurZen.
///
/// Les routes d’authentification viennent du gateway réel (OpenAPI
/// `/v3/api-docs`, préfixe `/api-assur-zen`). Les autres sont encore PROVISOIRES
/// (`/TODO/...`) : elles seront remplacées lot par lot, après la confirmation
/// écrite « API disponible » de la Session Gateway. Un seul fichier à modifier.
abstract final class Endpoints {
  static const prefix = '/api-assur-zen';

  // Authentification (existant) ----------------------------------------------
  static const login = '$prefix/auth/login';
  static const loginOtpVerify = '$prefix/auth/login/otp-verify';
  static const loginOtpResend = '$prefix/auth/login/otp-resend';
  static const refresh = '$prefix/auth/refresh';
  static const logout = '$prefix/auth/logout';
  static const me = '$prefix/auth/me';
  static const forgotPassword = '$prefix/auth/forgot-password';
  static const resetPassword = '$prefix/auth/reset-password';

  // Authentification mobile (lot 1, livré par la Gateway) ---------------------
  static const mobileOtpRequest = '$prefix/auth/mobile/otp/request';
  static const mobileOtpVerify = '$prefix/auth/mobile/otp/verify';
  static const mobileRegister = '$prefix/auth/mobile/register';
  static const mobilePinReset = '$prefix/auth/mobile/pin/reset';
  static const mobilePinVerify = '$prefix/auth/mobile/pin/verify'; // Bearer
  static const mobileDeleteRequest = '$prefix/auth/mobile/me/delete-request'; // Bearer

  /// Routes publiques d’authentification : aucun jeton ne doit être envoyé,
  /// sinon un ancien jeton périmé ferait refuser une connexion légitime.
  static bool isPublicAuthPath(String path) => const {
        login,
        loginOtpVerify,
        loginOtpResend,
        refresh,
        forgotPassword,
        resetPassword,
        mobileOtpRequest,
        mobileOtpVerify,
        mobileRegister,
        mobilePinReset,
      }.contains(path);

  // Catalogue et devis
  // Catalogue public (sans jeton obligatoire), contenu paramétré depuis le back-office.
  static const products = '$prefix/public/products';
  static String product(String id) => '$prefix/public/products/$id';
  // Référentiels de devis : NON livrés par la Gateway (aucune grille tarifaire).
  static String referentials(String productId) => '/TODO/products/$productId/referentials';
  static const quotes = '/TODO/quotes';
  static String quoteDocuments(String quoteNumber) => '/TODO/quotes/$quoteNumber/documents';

  // Paiement
  static const paymentMethods = '$prefix/mobile/payments/methods';
  static const payments = '$prefix/mobile/payments';
  static String payment(String reference) => '$prefix/mobile/payments/$reference';

  // Contrats
  static const contracts = '$prefix/mobile/contracts';
  static String contract(String id) => '$prefix/mobile/contracts/$id';
  static String contractRequests(String id) => '$prefix/mobile/contracts/$id/requests';

  // Sinistres
  static const claims = '$prefix/mobile/claims';
  static String claim(String id) => '$prefix/mobile/claims/$id';
  static String claimTypes(String productId) => '$prefix/mobile/products/$productId/claim-types';
  static String claimFiles(String claimId) => '$prefix/mobile/claims/$claimId/files';

  // Profil, notifications, assistance et mentions légales
  static const mobileMe = '$prefix/mobile/me';
  static const notifications = '$prefix/mobile/notifications';
  static const notificationsUnreadCount = '$prefix/mobile/notifications/unread-count';
  static const notificationsReadAll = '$prefix/mobile/notifications/read-all';
  static String notificationRead(String id) => '$prefix/mobile/notifications/$id/read';
  static const notificationPreferences = '$prefix/mobile/notification-preferences';
  static const devices = '$prefix/mobile/devices';
  static String device(String id) => '$prefix/mobile/devices/$id';
  static const supportRequests = '$prefix/mobile/support/requests';
  static const supportTopics = '$prefix/public/support/topics';
  static const faq = '$prefix/public/support/faq';
  static const legal = '$prefix/public/legal';
  static const feedback = '$prefix/mobile/feedback';

  // Coordonnées AssurZen : route publique, paramétrée dans le back-office.
  static const contactInfo = '$prefix/public/contact-info';

  // Demande de rappel : route publique (avec ou sans compte).
  static const callbacks = '$prefix/public/callbacks';

  // Devis en ligne moto (lots 1 et 2) : simulation publique, devis enregistrés du client.
  static const motoQuoteForm = '$prefix/public/quotes/moto/form';
  static const motoQuoteSimulate = '$prefix/public/quotes/moto/simulate';
  static const motoQuotes = '$prefix/mobile/quotes'; // GET : liste du client
  static const motoQuoteCreate = '$prefix/mobile/quotes/moto'; // POST
  static String motoQuote(String reference) => '$prefix/mobile/quotes/$reference';
  static String motoQuoteAccept(String reference) => '$prefix/mobile/quotes/$reference/accept';
}
