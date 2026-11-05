import 'package:flutter/material.dart';

import '../data/models/models.dart';
import '../features/auth/forgot_pin_page.dart';
import '../features/auth/lock_page.dart';
import '../features/auth/login_page.dart';
import '../features/auth/otp_page.dart';
import '../features/auth/register_page.dart';
import '../features/catalog/finder_page.dart';
import '../features/catalog/product_page.dart';
import '../features/claims/claim_declare_page.dart';
import '../features/claims/claim_detail_page.dart';
import '../features/claims/claim_done_page.dart';
import '../features/contracts/attestation_page.dart';
import '../features/contracts/contract_detail_page.dart';
import '../features/notifications/notifications_page.dart';
import '../features/onboarding/onboarding_page.dart';
import '../features/onboarding/splash_page.dart';
import '../features/onboarding/welcome_page.dart';
import '../features/payment/payment_page.dart';
import '../data/models/moto_quote.dart';
import '../features/quote/moto_quote_page.dart';
import '../features/engagement/protection_score_page.dart';
import '../features/quote/my_quotes_page.dart';
import '../features/quote/quote_flow_page.dart';
import '../features/quote/vtc_quote_page.dart';
import '../features/shell/main_shell.dart';
import '../features/support/support_page.dart';

/// Noms des routes (kebab-case) et construction des pages.
abstract final class AppRoutes {
  static const splash = '/';
  static const onboarding = '/onboarding';
  static const welcome = '/welcome';
  static const login = '/login';
  static const register = '/register';
  static const otp = '/otp';
  static const forgotPin = '/forgot-pin';
  static const lock = '/lock';
  static const shell = '/app';
  static const notifications = '/notifications';
  static const product = '/product';
  static const finder = '/finder';
  static const quote = '/quote';
  static const motoQuote = '/moto-quote';
  static const vtcQuote = '/vtc-quote';
  static const myQuotes = '/my-quotes';
  static const motoQuoteDetail = '/moto-quote-detail';
  static const payment = '/payment';
  static const contract = '/contract';
  static const attestation = '/attestation';
  static const claimNew = '/claim-new';
  static const claimDone = '/claim-done';
  static const claim = '/claim';
  static const support = '/support';
  static const protectionScore = '/protection-score';

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    final args = settings.arguments;
    final Widget page = switch (settings.name) {
      splash => const SplashPage(),
      onboarding => const OnboardingPage(),
      welcome => const WelcomePage(),
      login => const LoginPage(),
      register => RegisterPage(args: args is RegisterArgs ? args : const RegisterArgs()),
      otp => OtpPage(args: args as OtpArgs),
      forgotPin => const ForgotPinPage(),
      lock => const LockPage(),
      shell => MainShell(initialTab: args is int ? args : ShellTab.home),
      notifications => const NotificationsPage(),
      product => ProductPage(productId: args as String),
      finder => const FinderPage(),
      quote => QuoteFlowPage(args: args as QuoteArgs),
      motoQuote => MotoQuotePage(title: args is String ? args : null),
      vtcQuote => VtcQuotePage(title: args is String ? args : null),
      myQuotes => const MyQuotesPage(),
      motoQuoteDetail => MotoQuoteDetailPage(quote: args as MotoQuote),
      payment => PaymentPage(args: args as PaymentArgs),
      contract => ContractDetailPage(contractId: args as String),
      attestation => AttestationPage(contract: args as Contract),
      claimNew => ClaimDeclarePage(contractId: args as String?),
      claimDone => ClaimDonePage(claim: args as Claim),
      claim => ClaimDetailPage(claimId: args as String),
      support => const SupportPage(),
      protectionScore => const ProtectionScorePage(),
      _ => const SplashPage(),
    };
    final fullscreen = settings.name == lock || settings.name == attestation;
    return MaterialPageRoute<dynamic>(builder: (_) => page, settings: settings, fullscreenDialog: fullscreen);
  }
}

/// Raccourcis de navigation.
extension NavX on BuildContext {
  /// Les routes sont générées en `MaterialPageRoute<dynamic>` : on pousse en
  /// `Object?` (sinon le cast vers `Route<T>` échoue) puis on filtre le résultat.
  Future<T?> push<T>(String route, [Object? args]) async {
    final navigator = Navigator.of(this);
    final result = await navigator.pushNamed<Object?>(route, arguments: args);
    return result is T ? result : null;
  }

  /// Remplace toute la pile (après connexion, déconnexion, fin de parcours).
  Future<void> resetTo(String route, [Object? args]) =>
      Navigator.of(this).pushNamedAndRemoveUntil(route, (_) => false, arguments: args);

  void pop<T>([T? result]) => Navigator.of(this).pop<T>(result);
}
