import 'package:assurzen_mobile/core/security/pattern_lock.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('le hachage dépend du schéma et du sel', () {
    final a = PatternLock.hash([0, 1, 2, 5], 'sel1');
    expect(PatternLock.hash([0, 1, 2, 5], 'sel1'), a);
    expect(PatternLock.hash([5, 2, 1, 0], 'sel1'), isNot(a));
    expect(PatternLock.hash([0, 1, 2, 5], 'sel2'), isNot(a));
    expect(a.contains('0125'), isFalse);
  });

  test('un schéma de moins de 4 points est refusé', () {
    expect(PatternLock.validate([0, 1, 2]), isNotNull);
    expect(PatternLock.validate([0, 1, 2, 5]), isNull);
  });
}
