/// Modèles métier d’AssurZen Mobile.
///
/// Aucun import Flutter ici : ces classes sont la couche « domaine ».
/// Les `fromJson` sont à aligner sur les DTO réels du backend Java : les noms de
/// champs ci-dessous sont des propositions, pas un contrat d’API.
library;

// ---------------------------------------------------------------------------
// Utilisateur et session
// ---------------------------------------------------------------------------

class AppUser {
  const AppUser({required this.id, required this.fullName, required this.phone, this.email});

  final String id;
  final String fullName;
  final String phone; // 10 chiffres
  final String? email;

  AppUser copyWith({String? fullName, String? email}) =>
      AppUser(id: id, fullName: fullName ?? this.fullName, phone: phone, email: email ?? this.email);
}

class Session {
  const Session({required this.token, required this.user, required this.expiresAt, this.refreshToken, this.linkedCount});

  final String token;
  final AppUser user;
  final DateTime expiresAt;

  /// Jeton de renouvellement (rotatif) : permet de reprendre la session après
  /// déverrouillage sans redemander le PIN. `null` en mode démonstration.
  final String? refreshToken;

  /// Inscription : nombre d’assurés existants rattachés au nouveau compte
  /// (contrats souscrits en agence). `null` hors inscription.
  final int? linkedCount;
}

/// Résultat d’une connexion : une session, ou une double authentification à
/// terminer par un code SMS envoyé au numéro masqué [maskedPhone].
sealed class LoginResult {
  const LoginResult();
}

class LoginSuccess extends LoginResult {
  const LoginSuccess(this.session);

  final Session session;
}

class LoginOtpChallenge extends LoginResult {
  const LoginOtpChallenge({required this.challengeToken, required this.maskedPhone, this.expiresInSeconds = 300});

  final String challengeToken;
  final String maskedPhone;
  final int expiresInSeconds;
}

// ---------------------------------------------------------------------------
// Catalogue
// ---------------------------------------------------------------------------

enum ProductFamily { vehicule, personne, bien, professionnel }

extension ProductFamilyLabel on ProductFamily {
  String get label => switch (this) {
        ProductFamily.vehicule => 'Véhicules',
        ProductFamily.personne => 'Personnes',
        ProductFamily.bien => 'Biens',
        ProductFamily.professionnel => 'Professionnels',
      };
}

enum GuaranteeKind { obligatoire, incluse, selonFormule, aConfirmer }

class Guarantee {
  const Guarantee(this.name, this.kind, {this.description, this.limit, this.formulaIds = const []});

  final String name;
  final GuaranteeKind kind;

  /// Texte du back-office ; `null` = non communiqué (rien n'est affiché).
  final String? description;

  /// Plafond ou franchise en texte libre ; `null` = non communiqué.
  final String? limit;

  /// Identifiants des formules qui incluent cette garantie.
  final List<String> formulaIds;

  String get kindLabel => switch (kind) {
        GuaranteeKind.obligatoire => 'Obligatoire',
        GuaranteeKind.incluse => 'Incluse',
        GuaranteeKind.selonFormule => 'Selon formule',
        GuaranteeKind.aConfirmer => 'À confirmer',
      };
}

class Product {
  const Product({
    required this.id,
    required this.title,
    required this.shortName,
    required this.description,
    required this.family,
    required this.onlineSubscription,
    required this.benefits,
    required this.howItWorks,
    this.guarantees = const [],
    this.eligibility,
    this.iconCode,
    this.formulas = const [],
    this.exclusions = const [],
    this.requiredDocuments = const [],
    this.faq = const [],
    this.contentVersion,
    this.assistancePlans = const [],
    this.quoteType,
  });

  /// Type de cotation que le serveur sait faire pour ce produit (`MOTO`, `VTC`, `VOYAGE`, `ENTREPRISE`) ;
  /// `null` : pas de cotation, le client demande à être rappelé.
  final String? quoteType;

  bool get hasEnterpriseQuote => quoteType == 'ENTREPRISE';

  /// Niveaux d'assistance (Assurance Auto uniquement), actifs et triés par le serveur. Vide : rubrique masquée.
  final List<AssistancePlan> assistancePlans;

  /// Formules du produit (contenu, sans prix), dans l'ordre du serveur.
  final List<Formula> formulas;
  final List<String> exclusions;
  final List<String> requiredDocuments;
  final List<FaqItem> faq;

  /// Horodatage ISO de la dernière modification du contenu (clé de cache).
  final String? contentVersion;

  /// Code d'icône choisi dans le back-office (car, moto, taxi, health, home,
  /// plane…) ; `null` ou code inconnu : icône par défaut selon l'identifiant.
  final String? iconCode;

  final String id; // auto, moto, vtc, sante, vie, habitation, voyage, scolaire, entreprise
  final String title;
  final String shortName;
  final String description;
  final ProductFamily family;

  /// Décision 1.2 : auto, moto, VTC en ligne ; les autres via un conseiller.
  final bool onlineSubscription;
  final List<String> benefits;
  final String howItWorks;

  /// Vide tant qu’AssurZen n’a pas validé les garanties (décision 1.3).
  final List<Guarantee> guarantees;

  /// Texte fourni par AssurZen ; `null` = non communiqué.
  final String? eligibility;

  bool get isVehicle => family == ProductFamily.vehicule;

  /// Produit professionnel dont le devis se fait dans l'application (« Sur devis ») : le parcours s'arrête
  /// au devis, la suite se fait avec un conseiller. Un produit professionnel sans souscription en ligne
  /// (ex. Assurance Entreprise) n'a pas de devis : le client demande à être rappelé.
  bool get isQuoteOnly => family == ProductFamily.professionnel && onlineSubscription;
}

/// Une prestation d'un niveau d'assistance : texte libre, avec une condition éventuelle.
class AssistanceItem {
  const AssistanceItem(this.text, {this.condition});

  final String text;
  final String? condition;
}

/// Prestations regroupées par événement (accident, panne, vol, incendie…), titres fournis par le serveur.
class AssistanceSection {
  const AssistanceSection(this.title, this.items);

  final String title;
  final List<AssistanceItem> items;
}

/// Niveau d'assistance paramétré au back-office (Gratuit, Confort, Relax, Liberté…).
///
/// Aucun contenu n'est écrit dans l'application. L'unité du prix (par an, par contrat…) n'est pas
/// connue : elle n'est donc jamais affichée. Les bornes d'âge du véhicule sont informatives : aucun
/// niveau n'est masqué ni grisé selon elles.
class AssistancePlan {
  const AssistancePlan({
    required this.key,
    required this.name,
    required this.price,
    this.priceLabel,
    this.minVehicleAge,
    this.maxVehicleAge,
    this.benefit = false,
    this.recommended = false,
    this.sections = const [],
  });

  final String key;
  final String name;

  /// Entier en F CFA ; 0 pour « Gratuit ».
  final int price;

  /// Libellé libre du serveur (ex. « +38 000 F »). Absent : formaté depuis [price].
  final String? priceLabel;

  /// Âge du véhicule en années, `null` = sans borne.
  final int? minVehicleAge;
  final int? maxVehicleAge;

  /// « Avantage gratuit » (indicateur du serveur).
  final bool benefit;

  /// Badge « Recommandé ».
  final bool recommended;
  final List<AssistanceSection> sections;
}

class Formula {
  const Formula({required this.id, required this.name, required this.description, required this.guarantees});

  final String id;
  final String name;
  final String description;
  final List<String> guarantees;
}

/// Listes de choix du devis, renvoyées par le serveur pour un produit.
class QuoteReferentials {
  const QuoteReferentials({
    required this.powerLabel,
    required this.powers,
    required this.usages,
    required this.formulas,
    required this.durations,
    this.platforms = const [],
  });

  final String powerLabel; // « Puissance fiscale » ou « Cylindrée »
  final List<String> powers;
  final List<String> usages;
  final List<Formula> formulas; // 3 au maximum (décision 1.4)
  final List<int> durations; // en mois : 1, 3, 6, 12 (décision 1.8)
  final List<String> platforms; // VTC uniquement (décision 1.7)
}

// ---------------------------------------------------------------------------
// Devis
// ---------------------------------------------------------------------------

enum RequiredDocument { carteGrise, permis, pieceIdentite }

extension RequiredDocumentLabel on RequiredDocument {
  String get label => switch (this) {
        RequiredDocument.carteGrise => 'Carte grise',
        RequiredDocument.permis => 'Permis de conduire',
        RequiredDocument.pieceIdentite => 'Pièce d’identité',
      };
}

/// Brouillon de devis : conservé 30 jours sur le téléphone (décision 2.5).
class QuoteDraft {
  QuoteDraft({required this.productId});

  final String productId;
  int step = 1;
  String? power;
  String? usage;
  String? platform;
  String plate = '';
  String model = '';
  DateTime? firstCirculation;
  int seats = 5;
  String? formulaId;
  int durationMonths = 12;
  DateTime? startDate;
  String holderName = '';
  String holderPhone = '';
  String holderEmail = '';
  String promoCode = '';
  bool certified = false;
  int installments = 1; // 1 ou 4 (décision 3.6)
  final Map<RequiredDocument, String> documents = {}; // chemin local du fichier
  DateTime savedAt = DateTime.now();

  Map<String, Object?> toJson() => {
        'productId': productId,
        'step': step,
        'power': power,
        'usage': usage,
        'platform': platform,
        'plate': plate,
        'model': model,
        'firstCirculation': firstCirculation?.toIso8601String(),
        'seats': seats,
        'formulaId': formulaId,
        'durationMonths': durationMonths,
        'startDate': startDate?.toIso8601String(),
        'holderName': holderName,
        'holderPhone': holderPhone,
        'holderEmail': holderEmail,
        'promoCode': promoCode,
        'installments': installments,
        'savedAt': savedAt.toIso8601String(),
      };

  static QuoteDraft fromJson(Map<String, dynamic> json) {
    DateTime? date(Object? v) => v is String ? DateTime.tryParse(v) : null;
    return QuoteDraft(productId: json['productId'] as String)
      ..step = (json['step'] as int?) ?? 1
      ..power = json['power'] as String?
      ..usage = json['usage'] as String?
      ..platform = json['platform'] as String?
      ..plate = (json['plate'] as String?) ?? ''
      ..model = (json['model'] as String?) ?? ''
      ..firstCirculation = date(json['firstCirculation'])
      ..seats = (json['seats'] as int?) ?? 5
      ..formulaId = json['formulaId'] as String?
      ..durationMonths = (json['durationMonths'] as int?) ?? 12
      ..startDate = date(json['startDate'])
      ..holderName = (json['holderName'] as String?) ?? ''
      ..holderPhone = (json['holderPhone'] as String?) ?? ''
      ..holderEmail = (json['holderEmail'] as String?) ?? ''
      ..promoCode = (json['promoCode'] as String?) ?? ''
      ..installments = (json['installments'] as int?) ?? 1
      ..savedAt = date(json['savedAt']) ?? DateTime.now();
  }
}

/// Accusé d'une demande de rappel. [expectedWithinMinutes] est `null` tant que
/// le serveur n'a pas de délai configuré : aucun engagement n'est alors annoncé.
class CallbackReceipt {
  const CallbackReceipt({required this.reference, this.expectedWithinMinutes});

  final String reference;
  final int? expectedWithinMinutes;
}

/// Devis calculé par le serveur — jamais calculé dans l’application.
class Quote {
  const Quote({
    required this.number,
    required this.productId,
    required this.netPremium,
    required this.fees,
    required this.taxes,
    required this.guarantees,
    required this.validUntil,
    required this.requiredDocuments,
    this.installmentAmount,
    this.isDemo = false,
  });

  final String number;
  final String productId;
  final int netPremium;
  final int fees;
  final int taxes;
  final List<String> guarantees;
  final DateTime validUntil; // 30 jours (décision 2.2)
  final List<RequiredDocument> requiredDocuments;
  final int? installmentAmount; // montant d’une échéance si paiement en 4 fois
  final bool isDemo;

  int get total => netPremium + fees + taxes;
}

// ---------------------------------------------------------------------------
// Paiement
// ---------------------------------------------------------------------------

class PaymentMethod {
  const PaymentMethod({
    required this.id,
    required this.name,
    required this.shortLabel,
    required this.subtitle,
    required this.brandColor,
    this.needsPhoneNumber = true,
    this.enabled = true,
    this.simulated = false,
  });

  final String id; // orange_money, mtn_momo, moov_money, wave, carte, agence
  final String name;
  final String shortLabel;
  final String subtitle;
  final int brandColor; // ARGB, pour la pastille
  final bool needsPhoneNumber;

  /// Décidé par le serveur : un moyen désactivé n'est jamais proposé.
  final bool enabled;

  /// Le serveur simule le paiement (dev) : aucun argent ne bouge.
  final bool simulated;
}

enum PaymentStatus { pending, confirmed, failed }

/// Référence `PAY-AAAA-NNNNNN` citée dans un message du serveur (`PAYMENT_IN_PROGRESS`), ou null.
String? paymentReferenceIn(String? text) => RegExp(r'PAY-\d{4}-\d{6}').firstMatch(text ?? '')?.group(0);

class PaymentAttempt {
  const PaymentAttempt({
    required this.reference,
    required this.status,
    this.failureReason,
    this.receiptUrl,
    this.simulated = false,
    this.redirectUrl,
    this.expiresAt,
  });

  /// Page de l'opérateur (ou de Jeko) où le client valide le paiement.
  final String? redirectUrl;

  /// Fin de validité de la tentative, fixée par le serveur.
  final DateTime? expiresAt;

  final String reference; // référence unique par tentative (décision 3.4)
  final PaymentStatus status;
  final String? failureReason;
  final String? receiptUrl;
  final bool simulated;
}

// ---------------------------------------------------------------------------
// Contrats et documents
// ---------------------------------------------------------------------------

/// Décision 5.1 : statuts minimaux renvoyés par le backend.
enum ContractStatus { enAttente, actif, suspendu, expire, resilie }

extension ContractStatusLabel on ContractStatus {
  String get label => switch (this) {
        ContractStatus.enAttente => 'En attente',
        ContractStatus.actif => 'Actif',
        ContractStatus.suspendu => 'Suspendu',
        ContractStatus.expire => 'Expiré',
        ContractStatus.resilie => 'Résilié',
      };
}

enum DocumentType { attestation, vignette, carteBrune, carteGrise, conditions, recu, contrat, autre }

enum DocumentStatus { disponible, enGeneration, nonDisponible, enVerification, refuse }

extension DocumentStatusLabel on DocumentStatus {
  String get label => switch (this) {
        DocumentStatus.disponible => 'Disponible',
        DocumentStatus.enGeneration => 'En cours de génération',
        DocumentStatus.nonDisponible => 'Pas encore disponible',
        DocumentStatus.enVerification => 'En attente',
        DocumentStatus.refuse => 'Refusé',
      };
}

class ContractDocument {
  const ContractDocument({
    required this.id,
    required this.name,
    required this.type,
    required this.status,
    this.url,
    this.addedByClient = false,
    this.rejectReason,
    this.removable = false,
  });

  final String id;
  final String name;
  final DocumentType type;
  final DocumentStatus status;
  final String? url; // URL sécurisée fournie par le serveur

  /// Document ajouté par l'assuré lui-même (source `CLIENT`) ; AssurZen le valide ou le refuse.
  final bool addedByClient;

  /// Motif de refus rédigé par AssurZen, jamais par l'application.
  final String? rejectReason;

  /// Suppression autorisée par le serveur (documents de l'assuré non validés).
  final bool removable;

  bool get isAvailable => status == DocumentStatus.disponible;
}

/// Renouvellement proposé par le serveur : fenêtre, montant et durée viennent
/// du backend, le mobile n’applique aucune règle de délai ni de tarif.
class ContractRenewal {
  const ContractRenewal({
    required this.eligible,
    this.opensOn,
    this.daysLeft,
    this.amount,
    this.durationMonths,
    this.payableReference,
    this.discountPercent,
    this.discountAmount,
    this.fullAmount,
  });

  final bool eligible;
  final DateTime? opensOn;
  final int? daysLeft;

  /// Prime du renouvellement, remise Zen Points déjà déduite : c'est le montant à payer.
  final int? amount;

  /// Remise Zen Points du renouvellement ; absents quand il n'y en a pas.
  final num? discountPercent;
  final int? discountAmount;

  /// Prix public avant remise.
  final int? fullAmount;

  bool get hasDiscount => (discountPercent ?? 0) > 0 && (discountAmount ?? 0) > 0 && fullAmount != null;
  final int? durationMonths;

  /// Référence à payer ; fournie avec le lot paiement (demande n°4).
  final String? payableReference;
}

/// Actions autorisées par le serveur sur un contrat.
class ContractActions {
  const ContractActions({this.canDeclareClaim = false, this.canRequestChange = false, this.canRequestTermination = false});

  static const none = ContractActions();
  static const all = ContractActions(canDeclareClaim: true, canRequestChange: true, canRequestTermination: true);

  final bool canDeclareClaim;
  final bool canRequestChange;
  final bool canRequestTermination;
}

/// Attestation numérique : absente tant que le Back-Office n’en fournit pas.
class ContractAttestation {
  const ContractAttestation({required this.available, this.verificationCode, this.validFrom, this.validUntil});

  final bool available;
  final String? verificationCode;
  final DateTime? validFrom;
  final DateTime? validUntil;
}

enum ContractRequestType { modification, termination }

class ContractRequestReceipt {
  const ContractRequestReceipt({required this.reference, this.expectedWithinHours});

  final String reference;
  final int? expectedWithinHours;
}

/// Véhicule du contrat (auto, moto) fourni par le serveur ; tout champ peut manquer.
class ContractVehicle {
  const ContractVehicle({this.type, this.brand, this.model, this.plate});

  final String? type; // MOTO, AUTO, ...
  final String? brand;
  final String? model;
  final String? plate;

  bool get isEmpty => type == null && brand == null && model == null && plate == null;

  bool get isMoto => type?.toUpperCase() == 'MOTO';

  /// « Yamaha Crypton » (marque et modèle présents seulement).
  String get title => [brand, model].whereType<String>().join(' ');
}

class Contract {
  const Contract({
    required this.id,
    required this.productId,
    required this.name,
    required this.insuredObject,
    required this.number,
    required this.status,
    required this.guarantees,
    required this.documents,
    this.holderName,
    this.startDate,
    this.endDate,
    this.premium,
    this.renewal,
    this.actions = ContractActions.none,
    this.attestation,
    this.vehicle,
  });

  final ContractVehicle? vehicle;
  final String id;
  final String productId;
  final String name;
  final String insuredObject; // « Toyota Corolla · 1234 AB 01 » ou nom de l’assuré
  final String number;
  final ContractStatus status;
  final List<Guarantee> guarantees;
  final List<ContractDocument> documents;
  final String? holderName;
  final DateTime? startDate;
  final DateTime? endDate;
  final int? premium;
  final ContractRenewal? renewal;
  final ContractActions actions;
  final ContractAttestation? attestation;

  /// Contenu du QR code de vérification de l’attestation (décision 5.2).
  String? get verificationCode => attestation?.verificationCode;

  bool get hasDigitalAttestation => status == ContractStatus.actif && attestation?.available == true && verificationCode != null;

  /// Échéance proche : décidée par le serveur (`renewal.eligible`).
  bool get renewalOpen => renewal?.eligible == true;
}

// ---------------------------------------------------------------------------
// Sinistres
// ---------------------------------------------------------------------------

class ClaimEvent {
  const ClaimEvent(this.date, this.text);

  final DateTime date;
  final String text;
}

class Claim {
  const Claim({
    required this.id,
    required this.type,
    required this.contractLabel,
    required this.number,
    required this.declaredAt,
    required this.steps,
    required this.currentStep,
    required this.history,
    this.closed = false,
    this.failedUploads = 0,
    this.expectedHandlingHours,
  });

  /// Délai de prise en charge annoncé par le serveur à la déclaration ; `null` =
  /// aucun engagement (clé de configuration vide).
  final int? expectedHandlingHours;

  final String id;
  final String type;

  /// Pièces (photos, documents) que le serveur n'a pas pu recevoir après la
  /// déclaration, qui elle est enregistrée. 0 hors cas d'erreur.
  final int failedUploads;
  final String contractLabel;
  final String number;
  final DateTime declaredAt;

  /// Décision 6.2 : 5 étapes lisibles, fournies par le serveur.
  final List<String> steps;
  final int currentStep; // index dans [steps]
  final List<ClaimEvent> history;
  final bool closed;

  String get statusLabel => closed ? 'Clôturé' : (steps.isEmpty ? 'En cours' : steps[currentStep]);

  Claim withFailedUploads(int failed) => Claim(
        id: id,
        type: type,
        contractLabel: contractLabel,
        number: number,
        declaredAt: declaredAt,
        steps: steps,
        currentStep: currentStep,
        history: history,
        closed: closed,
        failedUploads: failed,
        expectedHandlingHours: expectedHandlingHours,
      );

  Claim withExpectedHandlingHours(int? hours) => Claim(
        id: id,
        type: type,
        contractLabel: contractLabel,
        number: number,
        declaredAt: declaredAt,
        steps: steps,
        currentStep: currentStep,
        history: history,
        closed: closed,
        failedUploads: failedUploads,
        expectedHandlingHours: hours,
      );
}

/// Type de sinistre proposé par le serveur pour un produit (libellé à afficher
/// tel quel, identifiant à renvoyer).
class ClaimType {
  const ClaimType(this.id, this.label);

  final String id;
  final String label;
}

/// Déclaration en cours de saisie.
class ClaimDraft {
  String? contractId;
  String? type; // libellé affiché
  String? typeId; // identifiant renvoyé au serveur
  DateTime occurredAt = DateTime.now();
  String place = '';
  bool? thirdPartyInvolved;
  String thirdPartyName = '';
  String thirdPartyPlate = '';
  String thirdPartyInsurer = '';
  String description = '';
  final List<String> photoPaths = []; // 6 au maximum (décision 6.4)
  final List<String> documentPaths = []; // 3 au maximum
  bool certified = false;
}

// ---------------------------------------------------------------------------
// Notifications et assistance
// ---------------------------------------------------------------------------

enum NotificationCategory { echeance, souscription, paiement, sinistre, document, service, loyalty }

extension NotificationCategoryLabel on NotificationCategory {
  String get label => switch (this) {
        NotificationCategory.echeance => 'Échéance',
        NotificationCategory.souscription => 'Souscription',
        NotificationCategory.paiement => 'Paiement',
        NotificationCategory.sinistre => 'Sinistre',
        NotificationCategory.document => 'Document',
        NotificationCategory.service => 'Service',
        NotificationCategory.loyalty => 'Zen Points',
      };
}

class AppNotification {
  const AppNotification({
    required this.id,
    required this.category,
    required this.title,
    required this.body,
    required this.date,
    this.read = false,
    this.contractId,
    this.claimId,
    this.quoteReference,
    this.target,
  });

  final String id;
  final NotificationCategory category;
  final String title;
  final String body;
  final DateTime date;
  final bool read;
  final String? contractId; // lien profond
  final String? claimId;

  /// Devis concerné (« Paiement reçu ») : à la touche, ouvre ce devis tant que le contrat n'existe pas.
  final String? quoteReference;

  /// Écran visé par la notification quand elle n'a ni contrat, ni sinistre, ni devis (ex. `BADGES`).
  final String? target;

  AppNotification markRead() => AppNotification(
      id: id,
      category: category,
      title: title,
      body: body,
      date: date,
      read: true,
      contractId: contractId,
      claimId: claimId,
      quoteReference: quoteReference,
      target: target);
}

/// Préférence de notification push par catégorie, tenue par le serveur. La
/// notification reste toujours listée dans l'écran, seul le push est concerné.
class NotificationPreference {
  const NotificationPreference(this.category, this.enabled);

  final NotificationCategory category;
  final bool enabled;
}

class FaqItem {
  const FaqItem(this.question, this.answer);

  final String question;
  final String answer;
}

/// Sujet de demande au support, fourni par le serveur (`GET /public/support/topics`).
class SupportTopic {
  const SupportTopic(this.id, this.label);

  final String id;
  final String label;
}

enum SupportStatus { enAttente, enCours, repondu, cloture }

/// Demande au support d'un client, avec la réponse du conseiller quand elle existe.
class SupportRequestItem {
  const SupportRequestItem({
    required this.id,
    required this.reference,
    required this.topicLabel,
    required this.message,
    required this.status,
    required this.createdAt,
    this.response,
    this.respondedAt,
  });

  final String id;
  final String reference;
  final String topicLabel;
  final String message;
  final SupportStatus status;
  final DateTime createdAt;
  final String? response;
  final DateTime? respondedAt;
}

/// Liens légaux paramétrés dans le back-office ; `null` = lien non communiqué.
class LegalLinks {
  const LegalLinks({this.termsUrl, this.privacyUrl, this.updatedAt});

  static const empty = LegalLinks();

  final String? termsUrl;
  final String? privacyUrl;
  final String? updatedAt;
}

/// Numéro de téléphone avec son libellé (ex. « Support Technique »).
class ContactNumber {
  const ContactNumber(this.label, this.number);

  final String label;
  final String number;
}

/// Coordonnées AssurZen fournies par le serveur (`GET /public/contact-info`),
/// paramétrées dans le back-office. Un champ `null` ou vide = non communiqué :
/// la rubrique et le bouton correspondants sont masqués, rien n'est inventé.
class ContactInfo {
  const ContactInfo({
    this.phones = const [],
    this.whatsapp,
    this.email,
    this.address,
    this.hours,
    this.emergencyNumbers = const [],
    this.supportAvailability,
  });

  static const empty = ContactInfo();

  final List<ContactNumber> phones;
  final String? whatsapp;
  final String? email;
  final String? address;

  /// Horaires en texte libre (ex. « Lun-Sam 8h-17h »).
  final String? hours;
  final List<ContactNumber> emergencyNumbers;
  final String? supportAvailability;

  String? get primaryPhone => phones.isEmpty ? null : phones.first.number;

  /// Disponibilité à afficher : texte de disponibilité du support, sinon horaires.
  String? get availabilityText => supportAvailability ?? hours;

  bool get isEmpty =>
      phones.isEmpty && whatsapp == null && email == null && address == null && hours == null && emergencyNumbers.isEmpty && supportAvailability == null;
}

/// Accusé d'une demande au support. [expectedWithinMinutes] vient du serveur ;
/// `null` = aucun délai annoncé.
class SupportReceipt {
  const SupportReceipt({required this.reference, this.expectedWithinMinutes});

  final String reference;
  final int? expectedWithinMinutes;
}

/// Types de documents que l'assuré peut ajouter lui-même à un contrat.
enum ClientDocumentType { contrat, recu, attestation }

extension ClientDocumentTypeInfo on ClientDocumentType {
  /// Code attendu par le serveur.
  String get code => switch (this) {
        ClientDocumentType.contrat => 'CONTRAT_ASSURANCE',
        ClientDocumentType.recu => 'RECU_ACHAT',
        ClientDocumentType.attestation => 'ATTESTATION_ASSURANCE',
      };

  String get label => switch (this) {
        ClientDocumentType.contrat => 'Contrat d’assurance',
        ClientDocumentType.recu => 'Reçu d’achat',
        ClientDocumentType.attestation => 'Attestation d’assurance',
      };

  String get hint => switch (this) {
        ClientDocumentType.contrat => 'Votre contrat signé',
        ClientDocumentType.recu => 'Preuve de paiement de la prime',
        ClientDocumentType.attestation => 'Le document remis à l’achat',
      };
}

/// Choix d'une catégorie de notification par canal.
class CategoryChannels {
  const CategoryChannels(this.category, {required this.push, required this.sms});

  final NotificationCategory category;
  final bool push;
  final bool sms;

  CategoryChannels copyWith({bool? push, bool? sms}) => CategoryChannels(category, push: push ?? this.push, sms: sms ?? this.sms);
}

/// Préférences de notification par canal (push, SMS) et par catégorie, tenues par le serveur.
/// `hasChannels` est faux tant que le serveur ne les fournit pas : l'écran ne montre alors rien de plus.
class NotificationSettings {
  const NotificationSettings({
    required this.hasChannels,
    required this.pushEnabled,
    required this.smsEnabled,
    required this.categories,
    this.smsConsentAt,
    this.smsConsentVersion,
    this.smsConsentText,
    this.smsPhone,
  });

  final bool hasChannels;
  final bool pushEnabled;
  final bool smsEnabled;
  final DateTime? smsConsentAt;
  final String? smsConsentVersion;

  /// Texte de l'accord fourni par le serveur ; absent : l'écran n'affiche pas de texte inventé.
  final String? smsConsentText;

  /// Numéro déjà masqué par le serveur (« 07 ** ** ** 86 »).
  final String? smsPhone;
  final List<CategoryChannels> categories;

  NotificationSettings copyWith({bool? pushEnabled, bool? smsEnabled, List<CategoryChannels>? categories}) => NotificationSettings(
        hasChannels: hasChannels,
        pushEnabled: pushEnabled ?? this.pushEnabled,
        smsEnabled: smsEnabled ?? this.smsEnabled,
        categories: categories ?? this.categories,
        smsConsentAt: smsConsentAt,
        smsConsentVersion: smsConsentVersion,
        smsConsentText: smsConsentText,
        smsPhone: smsPhone,
      );
}
