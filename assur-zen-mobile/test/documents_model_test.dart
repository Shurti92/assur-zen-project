import 'package:assurzen_mobile/data/api/api_contract_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Map<String, dynamic> base() => {'id': 'c1', 'productId': 'moto', 'productName': 'Assurance Moto', 'insuredLabel': 'Yamaha · 0000TT01', 'number': 'POL-1', 'status': 'ACTIVE'};

  test('le véhicule est lu quand le serveur le fournit', () {
    final c = ApiContractRepository.contractFromJson({
      ...base(),
      'vehicle': {'type': 'MOTO', 'brand': 'Yamaha', 'model': 'Crypton', 'plate': '0000TT01'},
    });
    expect(c.vehicle?.title, 'Yamaha Crypton');
    expect(c.vehicle?.plate, '0000TT01');
    expect(c.vehicle?.isMoto, isTrue);
  });

  test('sans véhicule ou avec un objet vide, rien n’est inventé', () {
    expect(ApiContractRepository.contractFromJson(base()).vehicle, isNull);
    expect(ApiContractRepository.contractFromJson({...base(), 'vehicle': <String, dynamic>{}}).vehicle, isNull);
    final partial = ApiContractRepository.contractFromJson({
      ...base(),
      'vehicle': {'plate': '1234AB01'},
    });
    expect(partial.vehicle?.title, '');
    expect(partial.vehicle?.plate, '1234AB01');
  });

  test('le type VIGNETTE et l’attestation officielle sont reconnus', () {
    final c = ApiContractRepository.contractFromJson({
      ...base(),
      'documents': [
        {'id': 'd1', 'name': 'Vignette moto', 'type': 'VIGNETTE', 'status': 'GENERATING'},
        {'id': 'd2', 'name': 'Attestation', 'type': 'ATTESTATION_ASSURANCE', 'status': 'AVAILABLE'},
      ],
    });
    expect(c.documents[0].type, DocumentType.vignette);
    expect(c.documents[0].status, DocumentStatus.enGeneration);
    expect(c.documents[1].type, DocumentType.attestation);
  });
}
