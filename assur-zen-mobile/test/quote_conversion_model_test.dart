import 'package:assurzen_mobile/core/network/endpoints.dart';
import 'package:assurzen_mobile/data/api/api_contract_repository.dart';
import 'package:assurzen_mobile/data/models/models.dart';
import 'package:assurzen_mobile/data/models/moto_quote.dart';
import 'package:flutter_test/flutter_test.dart';

/// Devis minimal tel que le serveur le renvoie ; les champs de conversion s'y ajoutent.
Map<String, dynamic> _quote([Map<String, dynamic> extra = const {}]) => {
      'reference': 'DEV-2026-000007',
      'status': 'ACCEPTE',
      'canAccept': false,
      'cylinderCc': 125,
      'brand': 'Yamaha',
      'model': 'YBR',
      'vehicleType': 'MOTO',
      'durationMonths': 12,
      'premium': {'total': 8000},
      ...extra,
    };

void main() {
  group('Devis : paiement et conversion en contrat', () {
    test('devis accepté sans paiement : aucune conversion, rien à afficher', () {
      final q = MotoQuote.fromJson(_quote());

      expect(q.status, MotoQuoteStatus.accepte);
      expect(q.conversion.status, QuoteConversionStatus.none);
      expect(q.conversion.paymentReference, isNull);
      expect(q.contractId, isNull);
      expect(q.policyNumber, isNull);
      expect(q.isPaid, isFalse);
    });

    test('devis payé : statut PAYE, paiement confirmé, pas encore de contrat', () {
      final q = MotoQuote.fromJson(_quote({
        'status': 'PAYE',
        'conversion': {'status': 'CONFIRMED', 'paymentReference': 'PAY-2026-000021', 'paidAt': '2026-11-05T10:30:00'},
      }));

      expect(q.status, MotoQuoteStatus.paye);
      expect(q.isPaid, isTrue);
      expect(q.canAccept, isFalse);
      expect(q.conversion.status, QuoteConversionStatus.confirmed);
      expect(q.conversion.paymentReference, 'PAY-2026-000021');
      expect(q.conversion.paidAt, DateTime(2026, 11, 5, 10, 30));
      expect(q.contractId, isNull);
    });

    test('devis converti : identifiant de contrat et numéro de police', () {
      final q = MotoQuote.fromJson(_quote({
        'status': 'CONVERTI',
        'conversion': {'status': 'CONFIRMED', 'paymentReference': 'PAY-2026-000021'},
        'contractId': 653,
        'policyNumber': 'P-2026-0042',
      }));

      expect(q.status, MotoQuoteStatus.converti);
      expect(q.isPaid, isTrue);
      expect(q.contractId, '653', reason: 'un identifiant numérique du serveur devient du texte');
      expect(q.policyNumber, 'P-2026-0042');
    });

    test('paiement en cours ou échoué : le devis reste ACCEPTE', () {
      final pending = MotoQuote.fromJson(_quote({
        'conversion': {'status': 'PENDING', 'paymentReference': 'PAY-2026-000022'},
      }));
      final failed = MotoQuote.fromJson(_quote({
        'conversion': {'status': 'FAILED', 'paymentReference': 'PAY-2026-000023'},
      }));

      expect(pending.status, MotoQuoteStatus.accepte);
      expect(pending.conversion.status, QuoteConversionStatus.pending);
      expect(pending.conversion.paymentReference, 'PAY-2026-000022', reason: 'référence à suivre au lieu d\'en lancer une autre');
      expect(failed.status, MotoQuoteStatus.accepte);
      expect(failed.conversion.status, QuoteConversionStatus.failed);
      expect(failed.isPaid, isFalse);
    });

    test('réponses imparfaites : aucune exception, valeurs neutres', () {
      expect(MotoQuote.fromJson(_quote({'conversion': null})).conversion.status, QuoteConversionStatus.none);
      expect(MotoQuote.fromJson(_quote({'conversion': 'oups'})).conversion.status, QuoteConversionStatus.none);
      expect(MotoQuote.fromJson(_quote({'conversion': {'status': 'NOUVEAU_STATUT'}})).conversion.status, QuoteConversionStatus.none);
      expect(MotoQuote.fromJson(_quote({'conversion': {'status': 'CONFIRMED', 'paymentReference': ''}})).conversion.paymentReference, isNull);
      expect(MotoQuote.fromJson(_quote({'status': 'STATUT_FUTUR'})).status, MotoQuoteStatus.inconnu);
      expect(MotoQuote.fromJson(_quote({'contractId': '', 'policyNumber': ''})).contractId, isNull);
    });

    test('une fois accepté en local, un devis n\'hérite d\'aucune conversion', () {
      final accepted = MotoQuote.fromJson(_quote({'status': 'CALCULE', 'canAccept': true})).copyAccepted(DateTime(2026, 11, 5));

      expect(accepted.status, MotoQuoteStatus.accepte);
      expect(accepted.conversion.status, QuoteConversionStatus.none);
    });
  });

  group('Documents du contrat : types envoyés par le serveur', () {
    Contract contract(List<Map<String, dynamic>> documents) => ApiContractRepository.contractFromJson({
          'id': 653,
          'productId': 'auto',
          'productName': 'Assurance Auto',
          'number': 'DEMO-MOB-ACTIF',
          'status': 'ACTIVE',
          'documents': documents,
        });

    test('ATTESTATION, CONDITIONS_PARTICULIERES, CARTE_GRISE et AUTRE', () {
      final c = contract([
        {'id': 1, 'type': 'ATTESTATION', 'name': 'Attestation', 'status': 'AVAILABLE'},
        {'id': 2, 'type': 'CONDITIONS_PARTICULIERES', 'name': 'Conditions particulières', 'status': 'AVAILABLE'},
        {'id': 3, 'type': 'CARTE_GRISE', 'name': 'Carte grise', 'status': 'AVAILABLE'},
        {'id': 4, 'type': 'AUTRE', 'name': 'Divers', 'status': 'AVAILABLE'},
      ]);

      expect(c.documents.map((d) => d.type), [
        DocumentType.attestation,
        DocumentType.conditions,
        DocumentType.carteGrise,
        DocumentType.autre,
      ]);
      expect(c.documents.every((d) => d.isAvailable), isTrue);
      expect(c.documents.map((d) => d.id), ['1', '2', '3', '4']);
    });

    test('les anciens types restent reconnus', () {
      final c = contract([
        {'id': 'a', 'type': 'CONDITIONS', 'name': 'Conditions', 'status': 'AVAILABLE'},
        {'id': 'b', 'type': 'CEDEAO', 'name': 'Carte brune', 'status': 'AVAILABLE'},
        {'id': 'c', 'type': 'RECEIPT', 'name': 'Reçu', 'status': 'AVAILABLE'},
        {'id': 'd', 'type': 'TYPE_FUTUR', 'name': 'Autre', 'status': 'AVAILABLE'},
      ]);

      expect(c.documents.map((d) => d.type), [DocumentType.conditions, DocumentType.carteBrune, DocumentType.recu, DocumentType.autre]);
    });

    test('attestation : le code de vérification null reste absent, rien n\'est inventé', () {
      final c = ApiContractRepository.contractFromJson({
        'id': 653,
        'productName': 'Assurance Auto',
        'number': 'DEMO-MOB-ACTIF',
        'status': 'ACTIVE',
        'attestation': {'available': true, 'verificationCode': null, 'validFrom': '2026-06-01', 'validUntil': '2027-05-31'},
      });

      expect(c.attestation?.available, isTrue);
      expect(c.attestation?.verificationCode, isNull);
      expect(c.attestation?.validUntil, DateTime(2027, 5, 31));
    });

    test('contrat sans attestation jointe : attestation null', () {
      final c = ApiContractRepository.contractFromJson({'id': 1, 'productName': 'Moto', 'number': 'N', 'status': 'ACTIVE', 'attestation': null});

      expect(c.attestation, isNull);
    });
  });

  group('Adresses de l\'API', () {
    test('paiement d\'un devis et fichiers d\'un contrat', () {
      expect(Endpoints.motoQuoteConvert('DEV-2026-000007'), endsWith('/mobile/quotes/DEV-2026-000007/convert'));
      expect(Endpoints.contractDocument('653', '12'), endsWith('/mobile/contracts/653/documents/12'));
      expect(Endpoints.contractAttestationFile('653'), endsWith('/mobile/contracts/653/attestation'));
    });
  });
}
