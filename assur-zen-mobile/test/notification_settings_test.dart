import 'package:assurzen_mobile/app/app_scope.dart';
import 'package:assurzen_mobile/app/session_controller.dart';
import 'package:assurzen_mobile/core/storage/local_store.dart';
import 'package:assurzen_mobile/data/api/api_notification_repository.dart';
import 'package:assurzen_mobile/data/mock/mock_data.dart';
import 'package:assurzen_mobile/data/mock/mock_repositories.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/repositories/repositories.dart';
import 'package:assurzen_mobile/features/profile/notification_settings_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/fonts.dart';

/// Réglages fixes : seule la lecture est utilisée par l'écran à l'ouverture.
class _FakeNotifications implements NotificationRepository {
  _FakeNotifications(this.settings);

  final NotificationSettings settings;

  @override
  Future<NotificationSettings> getSettings() async => settings;

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError('${invocation.memberName}');
}

void main() {
  setUpAll(loadAppFonts);

  test('les canaux et les catégories du serveur sont lus', () {
    final s = ApiNotificationRepository.settingsFromJson({
      'channels': {
        'push': {'enabled': true},
        'sms': {'enabled': true, 'consentAt': '2026-11-07T10:00:00Z', 'consentVersion': 'v1', 'phone': '07 ** ** ** 86'},
      },
      'categories': [
        {'category': 'EXPIRY', 'push': true, 'sms': true},
        {'category': 'CLAIM', 'push': true, 'sms': false},
      ],
    });
    expect(s.hasChannels, isTrue);
    expect(s.smsEnabled, isTrue);
    expect(s.smsConsentVersion, 'v1');
    expect(
      ApiNotificationRepository.settingsFromJson({
        'channels': {
          'sms': {'enabled': false, 'currentConsentVersion': 'v2', 'consentText': 'Texte du serveur'},
        },
      }).smsConsentVersion,
      'v2',
    );
    expect(s.smsPhone, '07 ** ** ** 86');
    expect(s.categories.length, 2);
    expect(s.categories.first.category, NotificationCategory.echeance);
    expect(s.categories.first.sms, isTrue);
    expect(s.categories.last.sms, isFalse);
  });

  test('ancienne forme sans canaux : rien n’est inventé', () {
    final s = ApiNotificationRepository.settingsFromJson({
      'items': [
        {'category': 'EXPIRY', 'enabled': true},
      ],
    });
    expect(s.hasChannels, isFalse);
    expect(s.smsEnabled, isFalse);
    expect(s.categories, isEmpty);
  });

  // Accord SMS : le texte est celui du serveur. Sans lui, les SMS ne peuvent pas être activés ;
  // on peut toujours les couper s'ils sont déjà actifs.
  for (final (sms, text, activable) in [(false, null, false), (false, 'Texte du serveur', true), (true, null, true)]) {
    testWidgets('SMS ${sms ? 'actifs' : 'coupés'}, texte ${text == null ? 'absent' : 'fourni'} : bouton ${activable ? 'utilisable' : 'bloqué'}', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 800);
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
        contracts: base.contracts,
        claims: base.claims,
        notifications: _FakeNotifications(NotificationSettings(
          hasChannels: true,
          pushEnabled: true,
          smsEnabled: sms,
          smsConsentVersion: 'v1',
          smsConsentText: text,
          categories: const [],
        )),
        support: base.support,
        engagement: base.engagement,
      );
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(
        AppScope(repos: repos, session: session, store: store, child: const MaterialApp(home: NotificationSettingsPage())),
      );
      await tester.pump(const Duration(milliseconds: 500));
      final switches = tester.widgetList<Switch>(find.byType(Switch)).toList();
      expect(switches.length, greaterThanOrEqualTo(2));
      expect(switches[1].onChanged != null, activable);
      expect(find.text('Recevoir des SMS ?'), findsNothing);
    });
  }

  for (final (size, scale) in [(const Size(320, 640), 1.3), (const Size(360, 800), 1.0)]) {
    testWidgets('l’écran tient sur ${size.width.toInt()} dp, texte ${(scale * 100).toInt()} %', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({'lock_choice_done': true});
      final store = await LocalStore.open();
      final repos = MockRepositories.create();
      final session = SessionController(store: store, repos: repos)..updateUser(MockData.demoUser);
      await tester.pumpWidget(
        AppScope(
          repos: repos,
          session: session,
          store: store,
          child: MaterialApp(
            builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
            home: const NotificationSettingsPage(),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text('Notifications push'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
