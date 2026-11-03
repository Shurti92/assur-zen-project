import 'package:assurzen_mobile/app/env.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('chaque environnement a sa gateway', () {
    expect(Env.resolveBaseUrl('local', ''), 'http://localhost:9191');
    expect(Env.resolveBaseUrl('recette', ''), 'https://medicis-apps.com:9443/assur-zen-gateway-recette/');
    expect(Env.resolveBaseUrl('prod', ''), 'https://medicis-apps.com:9443/assur-zen-gateway/');
  });

  test('API_BASE_URL est prioritaire et un environnement inconnu est refusé', () {
    expect(Env.resolveBaseUrl('prod', 'http://x:1'), 'http://x:1');
    expect(() => Env.resolveBaseUrl('autre', ''), throwsStateError);
  });
}
