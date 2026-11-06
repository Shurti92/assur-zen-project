import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/core/utils/contact_directory.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/support/support_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

class _Support implements SupportRepository {
  _Support({this.faq = const [], this.requests = const []});

  final List<FaqItem> faq;
  final List<SupportRequestItem> requests;
  final sent = <(String, String)>[];

  @override
  Future<List<FaqItem>> getFaq() async => faq;

  @override
  Future<List<SupportRequestItem>> getRequests() async => requests;

  @override
  Future<List<SupportTopic>> getTopics() async => const [SupportTopic('CONTRACT', 'Contrat'), SupportTopic('OTHER', 'Autre')];

  @override
  Future<SupportReceipt> sendRequest({required String topicId, required String message}) async {
    sent.add((topicId, message));
    return const SupportReceipt(reference: 'SP-1');
  }

  @override
  Future<ContactInfo> getContactInfo() async => ContactInfo.empty;

  @override
  Future<LegalLinks> getLegalLinks() async => LegalLinks.empty;

  @override
  Future<String> sendFeedback({required int rating, String? comment, String? contractId}) async => 'FB';
}

List<FaqItem> _faq(int n) => [for (var i = 1; i <= n; i++) FaqItem('Question $i ?', 'Réponse numéro $i.')];

void main() {
  setUpAll(loadAppFonts);
  tearDown(() => ContactDirectory.info.value = ContactInfo.empty);

  Future<_Support> show(
    WidgetTester tester, {
    ContactInfo info = ContactInfo.empty,
    List<FaqItem> faq = const [],
    List<SupportRequestItem> requests = const [],
    Size size = const Size(360, 740),
    bool focusFaq = false,
  }) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = size;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    final store = await LocalStore.open();
    final base = MockRepositories.create();
    final support = _Support(faq: faq, requests: requests);
    final repos = Repositories(
      auth: base.auth,
      catalog: base.catalog,
      quotes: base.quotes,
      motoQuotes: base.motoQuotes,
      payments: base.payments,
      contracts: base.contracts,
      claims: base.claims,
      notifications: base.notifications,
      support: support,
      engagement: base.engagement,
    );
    ContactDirectory.info.value = info;
    await tester.pumpWidget(AppScope(
      repos: repos,
      session: SessionController(store: store, repos: repos),
      store: store,
      child: MaterialApp(home: SupportPage(focusFaq: focusFaq)),
    ));
    await tester.pump(const Duration(milliseconds: 400));
    return support;
  }

  const full = ContactInfo(
    phones: [ContactNumber('Support Technique', '0700000001')],
    whatsapp: '0700000002',
    email: 'support@assurzen.ci',
    hours: 'Lun-Sam 8h-17h',
  );

  testWidgets('coordonnées complètes : trois accès rapides, urgence, horaires dans le bandeau', (tester) async {
    await show(tester, info: full);

    expect(find.text('Contact et assistance'), findsOneWidget);
    expect(find.text('Lun-Sam 8h-17h · Nous vous répondons'), findsOneWidget);
    for (final l in ['Appeler', 'WhatsApp', 'E-mail']) {
      expect(find.text(l), findsWidgets, reason: 'accès rapide, et ligne des coordonnées pour WhatsApp et E-mail');
    }
    expect(find.text('Urgence · Assistance'), findsOneWidget);
  });

  testWidgets('le serveur ne fournit rien : aucun accès rapide, aucune urgence, aucune coordonnée', (tester) async {
    await show(tester);

    expect(find.text('Appeler'), findsNothing);
    expect(find.text('WhatsApp'), findsNothing);
    expect(find.text('Urgence · Assistance'), findsNothing);
    expect(find.text('Nos coordonnées'), findsNothing);
    expect(find.text('Nous vous répondons.'), findsOneWidget, reason: 'sans horaires, le bandeau n\'invente rien');
  });

  testWidgets('sans WhatsApp ni e-mail : seul « Appeler » est proposé', (tester) async {
    await show(tester, info: const ContactInfo(phones: [ContactNumber('Support', '0700000001')]));

    expect(find.text('Appeler'), findsOneWidget);
    expect(find.text('WhatsApp'), findsNothing);
    expect(find.text('E-mail'), findsNothing);
  });

  group('Questions fréquentes', () {
    testWidgets('FAQ vide côté serveur : rubrique masquée', (tester) async {
      await show(tester);
      expect(find.text('Questions fréquentes'), findsNothing);
      expect(find.text('Rechercher une question'), findsNothing);
    });

    testWidgets('trois questions d\'abord, la première dépliée, puis « Voir les 13 questions »', (tester) async {
      await show(tester, faq: _faq(13));

      expect(find.text('Question 1 ?'), findsOneWidget);
      expect(find.text('Question 3 ?'), findsOneWidget);
      expect(find.text('Question 4 ?'), findsNothing);
      expect(find.text('Réponse numéro 1.'), findsOneWidget, reason: 'la première est dépliée');
      expect(find.text('Réponse numéro 2.'), findsNothing);
      expect(find.text('Voir les 13 questions'), findsOneWidget);
    });

    testWidgets('« Voir les 13 questions » affiche tout, « Voir moins » revient à trois', (tester) async {
      await show(tester, faq: _faq(13));

      await tester.ensureVisible(find.text('Voir les 13 questions'));
      await tester.tap(find.text('Voir les 13 questions'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Question 13 ?', skipOffstage: false), findsOneWidget);
      expect(find.text('Voir moins'), findsOneWidget);

      await tester.ensureVisible(find.text('Voir moins'));
      await tester.tap(find.text('Voir moins'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Question 13 ?', skipOffstage: false), findsNothing);
    });

    testWidgets('moins de quatre questions : pas de bouton « Voir »', (tester) async {
      await show(tester, faq: _faq(3));

      expect(find.textContaining('Voir les'), findsNothing);
    });

    testWidgets('recherche : filtre sur la question et la réponse, tous les résultats', (tester) async {
      await show(tester, faq: [
        const FaqItem('Comment payer ?', 'Par mobile money.'),
        const FaqItem('Comment déclarer un sinistre ?', 'Depuis la rubrique Sinistres.'),
        const FaqItem('Qui peut souscrire ?', 'Toute personne, par mobile money ou espèces.'),
        const FaqItem('Contact ?', 'Au téléphone.'),
      ]);

      await tester.enterText(find.widgetWithText(TextField, 'Rechercher une question'), 'mobile money');
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('Comment payer ?'), findsOneWidget);
      expect(find.text('Qui peut souscrire ?'), findsOneWidget);
      expect(find.text('Comment déclarer un sinistre ?'), findsNothing);
      expect(find.text('Contact ?'), findsNothing);
      expect(find.textContaining('Voir les'), findsNothing, reason: 'pas de bouton pendant une recherche');
    });

    testWidgets('recherche sans résultat : message clair', (tester) async {
      await show(tester, faq: _faq(5));

      await tester.enterText(find.widgetWithText(TextField, 'Rechercher une question'), 'zzz');
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.textContaining('Aucune question ne correspond'), findsOneWidget);
    });
  });

  group('Écrire au service client', () {
    testWidgets('message trop court : erreur, rien n\'est envoyé', (tester) async {
      final support = await show(tester);

      await tester.ensureVisible(find.text('Envoyer'));
      await tester.tap(find.text('Envoyer'));
      await tester.pump(const Duration(milliseconds: 300));

      expect(support.sent, isEmpty);
      expect(find.text('Votre message doit contenir au moins 10 caractères.'), findsOneWidget);
    });

    testWidgets('sujet choisi et message valide : envoyé, accusé affiché', (tester) async {
      final support = await show(tester);

      await tester.ensureVisible(find.text('Contrat'));
      await tester.tap(find.text('Contrat'));
      await tester.pump();
      await tester.enterText(find.widgetWithText(TextField, 'Décrivez votre demande'), 'Je voudrais une copie de mon contrat.');
      await tester.ensureVisible(find.text('Envoyer'));
      await tester.tap(find.text('Envoyer'));
      await tester.pump(const Duration(milliseconds: 400));

      expect(support.sent.single.$1, 'CONTRACT');
      expect(find.textContaining('Demande envoyée.', findRichText: true), findsOneWidget);
    });
  });

  group('Ouverture sur la FAQ (depuis le profil)', () {
    testWidgets("sans l'option, la FAQ est sous la ligne de flottaison d'un petit écran", (tester) async {
      await show(tester, info: full, faq: _faq(13), size: const Size(320, 300));

      expect(tester.getTopLeft(find.text('Questions fréquentes', skipOffstage: false)).dy, greaterThan(300));
    });

    testWidgets("depuis « Questions fréquentes » du profil : la page descend jusqu'à la FAQ", (tester) async {
      await show(tester, info: full, faq: _faq(13), size: const Size(320, 300), focusFaq: true);
      await tester.pump(const Duration(milliseconds: 50));
      await tester.pump(const Duration(milliseconds: 500));

      final y = tester.getTopLeft(find.text('Questions fréquentes')).dy;
      expect(y, lessThan(300));
      expect(y, greaterThan(-40), reason: 'le titre est en haut de la page, pas défilé hors vue');
    });
  });

  group('Mise en page', () {
    for (final size in [const Size(320, 568), const Size(411, 891)]) {
      testWidgets('aucun débordement (${size.width.round()} dp), page complète', (tester) async {
        await show(tester, info: full, faq: _faq(13), size: size);
        for (var i = 0; i < 6; i++) {
          await tester.drag(find.byType(SingleChildScrollView).first, const Offset(0, -500));
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(tester.takeException(), isNull);
      });
    }
  });
}
