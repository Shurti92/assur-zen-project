/// Devis en ligne moto (contrat : `docs/api-contracts/devis-en-ligne.md` de la Gateway).
/// Tous les montants sont en F CFA entiers et viennent du serveur : rien n'est calculé ici.
library;

int _int(Object? v) => (v as num?)?.toInt() ?? 0;

DateTime? _date(Object? v) => v == null ? null : DateTime.tryParse(v.toString());

String? _text(Object? v) => (v == null || v.toString().isEmpty) ? null : v.toString();

class MotoQuoteDuration {
  const MotoQuoteDuration({required this.months, required this.total});

  final int months;
  final int total;
}

/// Paramètres du formulaire, pilotés par le serveur (`GET /public/quotes/moto/form`).
class MotoQuoteForm {
  const MotoQuoteForm({
    required this.vehicleTypes,
    required this.durations,
    required this.formulaId,
    required this.formulaName,
    this.minEffectiveDate,
    this.brands = const [],
  });

  /// Marques proposées par le serveur (liste déroulante). Vide = saisie libre.
  final List<String> brands;
  final List<String> vehicleTypes; // MOTO, TRICYCLE
  final List<MotoQuoteDuration> durations;
  final String formulaId;
  final String formulaName;
  final DateTime? minEffectiveDate;

  factory MotoQuoteForm.fromJson(Map<String, dynamic> j) => MotoQuoteForm(
        vehicleTypes: [for (final t in (j['vehicleTypes'] as List? ?? const [])) t.toString()],
        durations: [
          for (final d in (j['durations'] as List? ?? const []))
            MotoQuoteDuration(months: _int((d as Map)['months']), total: _int(d['total'])),
        ],
        formulaId: (j['formulaId'] ?? '').toString(),
        formulaName: (j['formulaName'] ?? '').toString(),
        minEffectiveDate: _date(j['minEffectiveDate']),
        brands: [
          for (final b in (j['brands'] as List? ?? const []))
            if (b.toString().trim().isNotEmpty) b.toString().trim(),
        ],
      );
}

/// Saisie du client pour une simulation ou un devis enregistré.
class MotoQuoteRequest {
  const MotoQuoteRequest({
    required this.vehicleType,
    required this.cylinderCc,
    required this.brand,
    required this.model,
    required this.durationMonths,
    required this.effectiveDate,
    this.registrationNumber,
    this.chassisNumber,
    this.formulaId,
  });

  final String vehicleType;
  final int cylinderCc;
  final String brand;
  final String model;
  final int durationMonths;
  final DateTime effectiveDate;
  final String? registrationNumber;
  final String? chassisNumber;
  final String? formulaId;

  static String _day(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Map<String, Object?> toJson() => {
        'vehicleType': vehicleType,
        'cylinderCc': cylinderCc,
        'brand': brand,
        'model': model,
        'durationMonths': durationMonths,
        'effectiveDate': _day(effectiveDate),
        if (registrationNumber != null && registrationNumber!.trim().isNotEmpty)
          'registrationNumber': registrationNumber!.trim(),
        if (chassisNumber != null && chassisNumber!.trim().isNotEmpty) 'chassisNumber': chassisNumber!.trim(),
        if (formulaId != null && formulaId!.isNotEmpty) 'formulaId': formulaId,
      };
}

class MotoPremium {
  const MotoPremium({
    required this.netPremium,
    required this.taxes,
    required this.accessories,
    required this.fga,
    required this.total,
    this.currency = 'XOF',
    this.cedeao = 0,
    this.attestation = 0,
    this.discount = 0,
  });

  final int netPremium;
  final int taxes;
  final int accessories;
  final int fga;
  final int total;
  final String currency;

  /// Lignes ajoutées pour le VTC (0 pour la moto) : total = prime nette - remise + taxes
  /// + accessoires + FGA + CEDEAO + attestation.
  final int cedeao;
  final int attestation;
  final int discount;

  factory MotoPremium.fromJson(Map<String, dynamic> j) => MotoPremium(
        netPremium: _int(j['netPremium']),
        taxes: _int(j['taxes']),
        accessories: _int(j['accessories']),
        fga: _int(j['fga']),
        total: _int(j['total']),
        currency: (j['currency'] ?? 'XOF').toString(),
        cedeao: _int(j['cedeao']),
        attestation: _int(j['attestation']),
        discount: _int(j['discount']),
      );
}

class MotoGuaranteeLine {
  const MotoGuaranteeLine({required this.label, required this.amount});

  final String label;

  /// Texte déjà formaté par le serveur (ex. « 500 000 F CFA »). Absent = non communiqué.
  final String? amount;
}

class MotoGuarantee {
  const MotoGuarantee({required this.label, required this.details});

  final String label;
  final List<MotoGuaranteeLine> details;
}

List<MotoGuarantee> _guarantees(Object? raw) => [
      for (final g in (raw as List? ?? const []))
        MotoGuarantee(
          label: ((g as Map)['label'] ?? '').toString(),
          details: [
            for (final d in (g['details'] as List? ?? const []))
              MotoGuaranteeLine(label: ((d as Map)['label'] ?? '').toString(), amount: _text(d['amount'])),
          ],
        ),
    ];

/// Résultat d'une simulation (rien n'est enregistré côté serveur).
class MotoQuoteSimulation {
  const MotoQuoteSimulation({
    required this.formulaId,
    required this.formulaName,
    required this.vehicleType,
    required this.durationMonths,
    required this.premium,
    required this.guarantees,
    this.effectiveDate,
    this.expiryDate,
  });

  final String formulaId;
  final String formulaName;
  final String vehicleType;
  final int durationMonths;
  final DateTime? effectiveDate;
  final DateTime? expiryDate;
  final MotoPremium premium;
  final List<MotoGuarantee> guarantees;

  factory MotoQuoteSimulation.fromJson(Map<String, dynamic> j) => MotoQuoteSimulation(
        formulaId: (j['formulaId'] ?? '').toString(),
        formulaName: (j['formulaName'] ?? '').toString(),
        vehicleType: (j['vehicleType'] ?? '').toString(),
        durationMonths: _int(j['durationMonths']),
        effectiveDate: _date(j['effectiveDate']),
        expiryDate: _date(j['expiryDate']),
        premium: MotoPremium.fromJson((j['premium'] as Map?)?.cast<String, dynamic>() ?? const {}),
        guarantees: _guarantees(j['guarantees']),
      );
}

enum MotoQuoteStatus { calcule, accepte, expire, converti, inconnu }

MotoQuoteStatus _status(Object? v) => switch (v?.toString()) {
      'CALCULE' => MotoQuoteStatus.calcule,
      'ACCEPTE' => MotoQuoteStatus.accepte,
      'EXPIRE' => MotoQuoteStatus.expire,
      'CONVERTI' => MotoQuoteStatus.converti, // lot 3
      _ => MotoQuoteStatus.inconnu,
    };

/// Devis enregistré du client connecté (prix figé).
class MotoQuote {
  const MotoQuote({
    required this.reference,
    required this.status,
    required this.canAccept,
    required this.simulation,
    required this.cylinderCc,
    required this.brand,
    required this.model,
    this.registrationNumber,
    this.chassisNumber,
    this.validUntil,
    this.createdAt,
    this.acceptedAt,
    this.productKey,
    this.fiscalPower,
    this.energy,
    this.seats,
    this.firstRegistrationYear,
    this.vtcDeclared,
    this.platform,
    this.discountPercent,
    this.channel,
  });

  final String reference; // DEV-AAAA-NNNNNN
  final MotoQuoteStatus status;
  final bool canAccept;
  final MotoQuoteSimulation simulation;
  final int cylinderCc;
  final String brand;
  final String model;
  final String? registrationNumber;
  final String? chassisNumber;
  final DateTime? validUntil;
  final DateTime? createdAt;
  final DateTime? acceptedAt;

  /// Champs propres au VTC : tous null pour un devis moto.
  final String? productKey;
  final int? fiscalPower;
  final String? energy;
  final int? seats;
  final int? firstRegistrationYear;
  final bool? vtcDeclared;
  final String? platform;
  final num? discountPercent;

  /// Origine du devis : `MOBILE` (fait dans l'application) ou `BACKOFFICE` (établi par un conseiller,
  /// puis rattaché au compte dont le numéro est identique, vérifié par SMS).
  final String? channel;

  bool get isFromAdvisor => channel == 'BACKOFFICE';

  bool get isVtc => productKey == 'automobile-vtc' || fiscalPower != null;

  /// Le même devis une fois accepté (réponse du serveur, ou démonstration).
  MotoQuote copyAccepted(DateTime at) => MotoQuote(
        reference: reference,
        status: MotoQuoteStatus.accepte,
        canAccept: false,
        simulation: simulation,
        cylinderCc: cylinderCc,
        brand: brand,
        model: model,
        registrationNumber: registrationNumber,
        chassisNumber: chassisNumber,
        validUntil: validUntil,
        createdAt: createdAt,
        acceptedAt: at,
        productKey: productKey,
        fiscalPower: fiscalPower,
        energy: energy,
        seats: seats,
        firstRegistrationYear: firstRegistrationYear,
        vtcDeclared: vtcDeclared,
        platform: platform,
        discountPercent: discountPercent,
        channel: channel,
      );

  factory MotoQuote.fromJson(Map<String, dynamic> j) => MotoQuote(
        reference: (j['reference'] ?? '').toString(),
        status: _status(j['status']),
        canAccept: j['canAccept'] == true,
        simulation: MotoQuoteSimulation.fromJson(j),
        cylinderCc: _int(j['cylinderCc']),
        brand: (j['brand'] ?? '').toString(),
        model: (j['model'] ?? '').toString(),
        registrationNumber: _text(j['registrationNumber']),
        chassisNumber: _text(j['chassisNumber']),
        validUntil: _date(j['validUntil']),
        createdAt: _date(j['createdAt']),
        acceptedAt: _date(j['acceptedAt']),
        productKey: _text(j['productKey']),
        fiscalPower: (j['fiscalPower'] as num?)?.toInt(),
        energy: _text(j['energy']),
        seats: (j['seats'] as num?)?.toInt(),
        firstRegistrationYear: (j['firstRegistrationYear'] as num?)?.toInt(),
        vtcDeclared: j['vtcDeclared'] as bool?,
        platform: _text(j['platform']),
        discountPercent: j['discountPercent'] as num?,
        channel: _text(j['channel']),
      );
}

/// Paramètres du formulaire VTC, pilotés par le serveur (`GET /public/quotes/vtc/form`).
class VtcQuoteForm {
  const VtcQuoteForm({
    required this.energies,
    required this.durations,
    required this.formulaId,
    required this.formulaName,
    this.brands = const [],
    this.platforms = const [],
    this.minFiscalPower = 0,
    this.minSeats = 0,
    this.maxSeats = 0,
    this.minEffectiveDate,
  });

  final List<String> energies; // ESSENCE, DIESEL, HYBRIDE, ELECTRIQUE
  final List<MotoQuoteDuration> durations;
  final String formulaId;
  final String formulaName;

  /// Marques et plateformes proposées par le serveur. Vide = saisie libre.
  final List<String> brands;
  final List<String> platforms;
  final int minFiscalPower;
  final int minSeats;
  final int maxSeats;
  final DateTime? minEffectiveDate;

  /// Nombres de places acceptés par la grille tarifaire.
  List<int> get seatOptions => minSeats > 0 && maxSeats >= minSeats ? [for (var n = minSeats; n <= maxSeats; n++) n] : const [];

  static List<String> _strings(Object? raw) => [
        for (final b in (raw as List? ?? const []))
          if (b.toString().trim().isNotEmpty) b.toString().trim(),
      ];

  factory VtcQuoteForm.fromJson(Map<String, dynamic> j) => VtcQuoteForm(
        energies: _strings(j['energies']),
        durations: [
          for (final d in (j['durations'] as List? ?? const []))
            MotoQuoteDuration(months: _int((d as Map)['months']), total: _int(d['total'])),
        ],
        formulaId: (j['formulaId'] ?? '').toString(),
        formulaName: (j['formulaName'] ?? '').toString(),
        brands: _strings(j['brands']),
        platforms: _strings(j['platforms']),
        minFiscalPower: _int(j['minFiscalPower']),
        minSeats: _int(j['minSeats']),
        maxSeats: _int(j['maxSeats']),
        minEffectiveDate: _date(j['minEffectiveDate']),
      );
}

/// Saisie du client pour une simulation ou un devis VTC. Aucune remise côté client.
class VtcQuoteRequest {
  const VtcQuoteRequest({
    required this.fiscalPower,
    required this.energy,
    required this.brand,
    required this.model,
    required this.firstRegistrationYear,
    required this.seats,
    required this.vtcDeclared,
    required this.durationMonths,
    required this.effectiveDate,
    this.platform,
    this.registrationNumber,
    this.chassisNumber,
    this.formulaId,
  });

  final int fiscalPower;
  final String energy;
  final String brand;
  final String model;
  final int firstRegistrationYear;
  final int seats;
  final bool vtcDeclared;
  final int durationMonths;
  final DateTime effectiveDate;
  final String? platform;
  final String? registrationNumber;
  final String? chassisNumber;
  final String? formulaId;

  Map<String, Object?> toJson() => {
        'fiscalPower': fiscalPower,
        'energy': energy,
        'brand': brand,
        'model': model,
        'firstRegistrationYear': firstRegistrationYear,
        'seats': seats,
        'vtcDeclared': vtcDeclared,
        'durationMonths': durationMonths,
        'effectiveDate': MotoQuoteRequest._day(effectiveDate),
        if (platform != null && platform!.trim().isNotEmpty) 'platform': platform!.trim(),
        if (registrationNumber != null && registrationNumber!.trim().isNotEmpty)
          'registrationNumber': registrationNumber!.trim(),
        if (chassisNumber != null && chassisNumber!.trim().isNotEmpty) 'chassisNumber': chassisNumber!.trim(),
        if (formulaId != null && formulaId!.isNotEmpty) 'formulaId': formulaId,
      };
}
