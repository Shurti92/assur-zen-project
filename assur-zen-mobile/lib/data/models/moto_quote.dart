/// Devis en ligne moto (contrat : `docs/api-contracts/devis-en-ligne.md` de la Gateway).
/// Tous les montants sont en F CFA entiers et viennent du serveur : rien n'est calculé ici.
library;

int _int(Object? v) => (v as num?)?.toInt() ?? 0;

DateTime? _date(Object? v) => v == null ? null : DateTime.tryParse(v.toString());

String? _text(Object? v) => (v == null || v.toString().trim().isEmpty) ? null : v.toString();

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
    this.guaranteesNote,
  });

  final String formulaId;
  final String formulaName;
  final String vehicleType;
  final int durationMonths;
  final DateTime? effectiveDate;
  final DateTime? expiryDate;
  final MotoPremium premium;
  final List<MotoGuarantee> guarantees;

  /// Note de bas de page des garanties (renvoi de l'astérisque des montants), texte du serveur.
  /// Absente ou vide : aucune note. Voyage : `guaranteesNote` (simulation) ou `voyage.guaranteesNote` (devis).
  final String? guaranteesNote;

  factory MotoQuoteSimulation.fromJson(Map<String, dynamic> j) => MotoQuoteSimulation(
        formulaId: (j['formulaId'] ?? '').toString(),
        formulaName: (j['formulaName'] ?? '').toString(),
        vehicleType: (j['vehicleType'] ?? '').toString(),
        durationMonths: _int(j['durationMonths']),
        effectiveDate: _date(j['effectiveDate']),
        expiryDate: _date(j['expiryDate']),
        premium: MotoPremium.fromJson((j['premium'] as Map?)?.cast<String, dynamic>() ?? const {}),
        guarantees: _guarantees(j['guarantees']),
        guaranteesNote: _text(j['guaranteesNote']) ?? (j['voyage'] is Map ? _text((j['voyage'] as Map)['guaranteesNote']) : null),
      );
}

/// Cycle de vie : CALCULE, ACCEPTE, (paiement confirmé) PAYE, (finalisé au back-office) CONVERTI.
enum MotoQuoteStatus { calcule, accepte, paye, expire, converti, inconnu }

MotoQuoteStatus _status(Object? v) => switch (v?.toString()) {
      'CALCULE' => MotoQuoteStatus.calcule,
      'ACCEPTE' => MotoQuoteStatus.accepte,
      'PAYE' => MotoQuoteStatus.paye, // payé, en attente de souscription par un conseiller
      'EXPIRE' => MotoQuoteStatus.expire,
      'CONVERTI' => MotoQuoteStatus.converti,
      _ => MotoQuoteStatus.inconnu,
    };

/// État du paiement du devis, communiqué par le serveur (jamais déduit par l'application).
enum QuoteConversionStatus { none, pending, confirmed, failed }

QuoteConversionStatus _conversionStatus(Object? v) => switch (v?.toString()) {
      'PENDING' => QuoteConversionStatus.pending,
      'CONFIRMED' => QuoteConversionStatus.confirmed,
      'FAILED' => QuoteConversionStatus.failed,
      _ => QuoteConversionStatus.none,
    };

/// Paiement d'un devis accepté : `conversion` de la réponse du serveur.
class QuoteConversion {
  const QuoteConversion({required this.status, this.paymentReference, this.paidAt});

  final QuoteConversionStatus status;

  /// Référence `PAY-AAAA-NNNNNN` du paiement (à suivre s'il est en cours).
  final String? paymentReference;
  final DateTime? paidAt;

  static const none = QuoteConversion(status: QuoteConversionStatus.none);

  /// Absent ou mal formé : aucun paiement connu.
  factory QuoteConversion.fromJson(Object? v) {
    if (v is! Map) return none;
    final j = v.cast<String, dynamic>();
    return QuoteConversion(
      status: _conversionStatus(j['status']),
      paymentReference: _text(j['paymentReference']),
      paidAt: _date(j['paidAt']),
    );
  }
}

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
    this.conversion = QuoteConversion.none,
    this.contractId,
    this.policyNumber,
    this.voyage,
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

  /// Paiement du devis et contrat obtenu. `contractId` et `policyNumber` n'existent qu'une fois le devis
  /// converti : absents, rien n'est affiché.
  final QuoteConversion conversion;
  final String? contractId;
  final String? policyNumber;

  /// Détail d'un devis Assurance Voyage ; null pour la moto et le VTC. Pour un voyage, les champs
  /// du véhicule (marque, modèle, cylindrée…) sont vides et ne s'affichent pas.
  final VoyageInfo? voyage;

  bool get isVoyage => voyage != null || productKey == 'voyage';

  bool get isPaid => status == MotoQuoteStatus.paye || status == MotoQuoteStatus.converti;

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
        conversion: conversion,
        contractId: contractId,
        policyNumber: policyNumber,
        voyage: voyage,
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
        conversion: QuoteConversion.fromJson(j['conversion']),
        contractId: _text(j['contractId']),
        policyNumber: _text(j['policyNumber']),
        voyage: VoyageInfo.fromJson(j['voyage']),
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

// ---------------------------------------------------------------------------
// Assurance Voyage
// ---------------------------------------------------------------------------

String _ymd(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Âge en années entières à la date [on] (anniversaire inclus). Même règle que le serveur, utilisée
/// seulement pour la simulation publique, qui attend un âge ; le devis enregistré est calculé par le serveur.
int ageAt(DateTime birth, DateTime on) {
  var age = on.year - birth.year;
  if (on.month < birth.month || (on.month == birth.month && on.day < birth.day)) age--;
  return age < 0 ? 0 : age;
}

/// Nombre de jours du départ au retour, les deux jours inclus (du 1er au 7 : 7 jours).
int inclusiveDays(DateTime departure, DateTime back) {
  final a = DateTime.utc(departure.year, departure.month, departure.day);
  final b = DateTime.utc(back.year, back.month, back.day);
  return b.difference(a).inDays + 1;
}

/// Un pays de destination. `name` et `spaceLabel` sont affichables ; `space` est un code technique.
class VoyageCountry {
  const VoyageCountry({required this.id, required this.name, required this.space, required this.spaceLabel});

  final String id;
  final String name;
  final String space;
  final String spaceLabel;

  static VoyageCountry? fromJson(Object? v) {
    if (v is! Map) return null;
    final id = _text(v['id']);
    final name = _text(v['name']);
    if (id == null || name == null) return null;
    return VoyageCountry(id: id, name: name, space: (_text(v['space']) ?? ''), spaceLabel: _text(v['spaceLabel']) ?? '');
  }
}

/// Paramètres du formulaire Voyage, pilotés par le serveur (`GET /public/quotes/voyage/form`).
class VoyageForm {
  const VoyageForm({
    required this.countries,
    required this.minAge,
    required this.maxAge,
    required this.maxDurationDays,
    this.minDepartureDate,
  });

  final List<VoyageCountry> countries;
  final int minAge;
  final int maxAge;
  final int maxDurationDays;

  /// Premier départ possible : date du jour du serveur (Abidjan), pas l'horloge du téléphone.
  final DateTime? minDepartureDate;

  /// Pays regroupés par espace, dans l'ordre alphabétique des libellés d'espace puis des noms.
  Map<String, List<VoyageCountry>> get bySpace {
    final map = <String, List<VoyageCountry>>{};
    for (final c in countries) {
      (map[c.spaceLabel.isEmpty ? c.space : c.spaceLabel] ??= []).add(c);
    }
    final keys = map.keys.toList()..sort();
    return {for (final k in keys) k: map[k]!..sort((a, b) => a.name.compareTo(b.name))};
  }

  factory VoyageForm.fromJson(Map<String, dynamic> j) => VoyageForm(
        countries: [
          for (final c in (j['countries'] as List? ?? const []))
            if (VoyageCountry.fromJson(c) case final country?) country,
        ],
        minAge: _int(j['minAge']),
        maxAge: _int(j['maxAge']),
        maxDurationDays: _int(j['maxDurationDays']),
        minDepartureDate: _date(j['minDepartureDate']),
      );
}

/// Saisie du client pour un devis Voyage enregistré.
class VoyageRequest {
  const VoyageRequest({
    required this.countryId,
    required this.birthDate,
    required this.departureDate,
    required this.returnDate,
    this.travelerName,
    this.passportNumber,
  });

  final String countryId;
  final DateTime birthDate;
  final DateTime departureDate;
  final DateTime returnDate;

  /// Seulement si le voyageur n'est pas le titulaire du compte.
  final String? travelerName;
  final String? passportNumber;

  /// Âge au départ et durée : pour la simulation publique uniquement.
  int get age => ageAt(birthDate, departureDate);
  int get durationDays => inclusiveDays(departureDate, returnDate);

  Map<String, Object?> toJson() => {
        'countryId': int.tryParse(countryId) ?? countryId,
        'birthDate': _ymd(birthDate),
        'departureDate': _ymd(departureDate),
        'returnDate': _ymd(returnDate),
        if (travelerName != null && travelerName!.trim().isNotEmpty) 'travelerName': travelerName!.trim(),
        if (passportNumber != null && passportNumber!.trim().isNotEmpty) 'passportNumber': passportNumber!.trim(),
      };

  /// Corps de la simulation publique : le serveur attend un âge et une durée.
  Map<String, Object?> toSimulationJson() => {
        'countryId': int.tryParse(countryId) ?? countryId,
        'age': age,
        'durationDays': durationDays,
      };
}

/// Détail d'un devis Voyage (objet `voyage` de la réponse).
class VoyageInfo {
  const VoyageInfo({
    required this.countryName,
    this.spaceLabel,
    this.travelerBirthDate,
    this.travelerAge,
    this.passportNumber,
    this.departureDate,
    this.returnDate,
    this.durationDays,
  });

  final String countryName;
  final String? spaceLabel;
  final DateTime? travelerBirthDate;
  final int? travelerAge;

  /// Masqué par le serveur dans les listes (« ****4567 ») ; affiché tel quel.
  final String? passportNumber;
  final DateTime? departureDate;
  final DateTime? returnDate;

  /// LA durée à afficher (la durée en mois du devis est technique et n'est jamais montrée).
  final int? durationDays;

  static VoyageInfo? fromJson(Object? v) {
    if (v is! Map) return null;
    final name = _text(v['countryName']);
    if (name == null) return null;
    return VoyageInfo(
      countryName: name,
      spaceLabel: _text(v['spaceLabel']),
      travelerBirthDate: _date(v['travelerBirthDate']),
      travelerAge: (v['travelerAge'] as num?)?.toInt(),
      passportNumber: _text(v['passportNumber']),
      departureDate: _date(v['departureDate']),
      returnDate: _date(v['returnDate']),
      durationDays: (v['durationDays'] as num?)?.toInt(),
    );
  }

  /// « 7 jours » ; null si le serveur ne donne pas la durée.
  String? get durationLabel => durationDays == null ? null : '$durationDays ${durationDays! > 1 ? 'jours' : 'jour'}';
}

/// Résultat de `POST /public/quotes/voyage/simulate` (rien n'est enregistré).
class VoyageSimulation {
  const VoyageSimulation({
    required this.countryName,
    required this.spaceLabel,
    required this.age,
    required this.durationDays,
    required this.premium,
    this.durationBand,
    this.guarantees = const [],
    this.guaranteesNote,
  });

  final String countryName;
  final String spaceLabel;
  final int age;
  final int durationDays;

  /// Tranche tarifaire, texte du serveur (« jusqu'à 15 jours »).
  final String? durationBand;
  final MotoPremium premium;

  /// Garanties du produit, fournies par AssurZen via le serveur (absentes d'un ancien serveur : liste vide,
  /// la rubrique est alors masquée).
  final List<MotoGuarantee> guarantees;
  final String? guaranteesNote;

  factory VoyageSimulation.fromJson(Map<String, dynamic> j) {
    final country = (j['country'] as Map?)?.cast<String, dynamic>() ?? const {};
    return VoyageSimulation(
      countryName: (country['name'] ?? '').toString(),
      spaceLabel: (country['spaceLabel'] ?? '').toString(),
      age: _int(j['age']),
      durationDays: _int(j['durationDays']),
      durationBand: _text(j['durationBand']),
      premium: MotoPremium(
        netPremium: _int(j['netPremium']),
        taxes: _int(j['taxes']),
        accessories: _int(j['accessories']),
        fga: 0,
        total: _int(j['totalPremium']),
      ),
      guarantees: _guarantees(j['guarantees']),
      guaranteesNote: _text(j['guaranteesNote']),
    );
  }

  /// Même forme que les autres produits, pour réutiliser l'affichage du résultat. Aucune garantie
  /// n'est inventée : seules celles du serveur sont montrées.
  MotoQuoteSimulation toQuoteSimulation({DateTime? departure, DateTime? back}) => MotoQuoteSimulation(
        formulaId: '',
        formulaName: spaceLabel.isEmpty ? '' : 'Assurance Voyage - $spaceLabel',
        vehicleType: '',
        durationMonths: 0,
        effectiveDate: departure,
        expiryDate: back,
        premium: premium,
        guarantees: guarantees,
        guaranteesNote: guaranteesNote,
      );
}
