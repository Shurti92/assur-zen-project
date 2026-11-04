import '../models/models.dart';

/// DONNÉES DE DÉMONSTRATION — FICTIVES.
///
/// Elles reproduisent la maquette validée. Aucune garantie, aucun tarif ni
/// aucune condition contractuelle affichés ici n’engagent AssurZen : en
/// production, tout vient de l’API (décisions 1.3 et 1.6).
abstract final class MockData {
  static final DateTime now = DateTime.now();

  /// Coordonnées FICTIVES de démonstration (en mode réel, elles viennent du serveur).
  static const contactInfo = ContactInfo(
    phones: [ContactNumber('Service client', '0700000001')],
    whatsapp: '0700000001',
    email: 'contact@exemple.ci',
    address: 'Plateau, Immeuble Longchamp, Abidjan',
    hours: '7j/7 · 24h/24',
    emergencyNumbers: [ContactNumber('SAMU', '185'), ContactNumber('Pompiers', '180')],
  );

  static DateTime daysFromNow(int days) => DateTime(now.year, now.month, now.day).add(Duration(days: days));

  static const demoUser = AppUser(id: 'u-demo', fullName: 'Koné Awa', phone: '0708123456', email: 'awa.kone@exemple.ci');

  static const products = <Product>[
    Product(
      id: 'auto',
      assistancePlans: [
        AssistancePlan(
          key: 'demo-a',
          name: 'Niveau démo A',
          price: 0,
          benefit: true,
          sections: [
            AssistanceSection('En cas d’accident', [AssistanceItem('Prestation de démonstration')]),
          ],
        ),
        AssistancePlan(
          key: 'demo-b',
          name: 'Niveau démo B',
          price: 10000,
          maxVehicleAge: 5,
          recommended: true,
          sections: [
            AssistanceSection('En cas de panne', [AssistanceItem('Prestation de démonstration', condition: 'Condition de démonstration')]),
          ],
        ),
      ],
      title: 'Assurance automobile',
      shortName: 'Automobile',
      description: 'Roulez couvert, avec votre attestation toujours à portée de main.',
      family: ProductFamily.vehicule,
      onlineSubscription: true,
      benefits: [
        'Devis immédiat depuis votre téléphone',
        'Attestation numérique avec QR code de vérification',
        'Carte brune CEDEAO si votre contrat y donne droit',
        'Assistance 24h/24 par téléphone et WhatsApp',
      ],
      guarantees: [
        Guarantee('Responsabilité civile', GuaranteeKind.obligatoire),
        Guarantee('Défense et recours', GuaranteeKind.aConfirmer),
        Guarantee('Vol, incendie, bris de glace', GuaranteeKind.selonFormule),
      ],
      howItWorks:
          'Renseignez votre véhicule, choisissez une formule et une durée, puis recevez un devis calculé par AssurZen. Payez par Mobile Money et recevez votre attestation.',
    ),
    Product(
      id: 'moto',
      title: 'Assurance moto',
      shortName: 'Moto',
      description: 'Pour les motos et deux-roues, usage personnel ou professionnel.',
      family: ProductFamily.vehicule,
      onlineSubscription: true,
      benefits: ['Devis en quelques minutes', 'Durées courtes : 1, 3, 6 ou 12 mois', 'Renouvellement en un geste'],
      guarantees: [
        Guarantee('Responsabilité civile', GuaranteeKind.obligatoire),
        Guarantee('Garanties complémentaires', GuaranteeKind.aConfirmer),
      ],
      howItWorks: 'Indiquez la cylindrée et l’usage de votre moto pour obtenir un devis.',
    ),
    Product(
      id: 'vtc',
      title: 'Assurance VTC / Yango',
      shortName: 'VTC / Yango',
      description: 'Une couverture pensée pour les chauffeurs qui transportent des passagers.',
      family: ProductFamily.vehicule,
      onlineSubscription: true,
      benefits: [
        'Adaptée au transport de personnes',
        'Document au format exigé par les plateformes',
        'Renouvellement rapide entre deux courses',
      ],
      guarantees: [
        Guarantee('Responsabilité civile', GuaranteeKind.obligatoire),
        Guarantee('Personnes transportées', GuaranteeKind.aConfirmer),
      ],
      howItWorks:
          'Le parcours VTC reprend celui de l’automobile avec l’usage « transport de personnes » et le choix de votre plateforme.',
    ),
    Product(
      id: 'sante',
      title: 'Assurance santé',
      shortName: 'Santé',
      description: 'Faites face aux frais de santé pour vous et votre famille.',
      family: ProductFamily.personne,
      onlineSubscription: false,
      benefits: ['Étude de vos besoins par un conseiller', 'Devis personnalisé', 'Suivi du contrat dans l’application'],
      howItWorks: 'Un conseiller vous rappelle sous 30 minutes pour définir la couverture adaptée à votre famille.',
    ),
    Product(
      id: 'vie',
      title: 'Assurance vie',
      shortName: 'Vie',
      description: 'Préparez l’avenir et protégez vos proches.',
      family: ProductFamily.personne,
      onlineSubscription: false,
      benefits: ['Accompagnement par un conseiller', 'Solution adaptée à vos objectifs', 'Suivi du contrat dans l’application'],
      howItWorks: 'Un conseiller vous présente les solutions et établit avec vous une proposition.',
    ),
    Product(
      id: 'habitation',
      title: 'Assurance habitation',
      shortName: 'Habitation',
      description: 'Protégez votre logement et ce qu’il contient.',
      family: ProductFamily.bien,
      onlineSubscription: false,
      benefits: ['Pour locataires et propriétaires', 'Devis personnalisé', 'Déclaration de sinistre dans l’application'],
      howItWorks: 'Un conseiller évalue votre logement et vous propose une couverture.',
    ),
    Product(
      id: 'voyage',
      title: 'Assurance voyage',
      shortName: 'Voyage',
      description: 'Voyagez l’esprit tranquille, en Afrique et à l’international.',
      family: ProductFamily.personne,
      onlineSubscription: false,
      benefits: ['Attestation pour vos démarches de visa', 'Couverture selon la destination', 'Assistance joignable 24h/24'],
      howItWorks: 'Indiquez votre destination et vos dates ; un conseiller confirme la couverture.',
    ),
    Product(
      id: 'scolaire',
      title: 'Assurance scolaire',
      shortName: 'Scolaire',
      description: 'Une protection pour vos enfants pendant l’année scolaire.',
      family: ProductFamily.personne,
      onlineSubscription: false,
      benefits: ['Un ou plusieurs enfants', 'Attestation pour l’établissement', 'Suivi dans l’application'],
      howItWorks: 'Un conseiller vous accompagne pour inscrire vos enfants.',
    ),
    Product(
      id: 'entreprise',
      title: 'Assurance entreprise',
      shortName: 'Entreprise',
      description: 'Des solutions sur mesure pour votre activité et vos équipes.',
      family: ProductFamily.professionnel,
      onlineSubscription: false,
      benefits: ['Étude personnalisée de vos risques', 'Interlocuteur dédié', 'Gestion des contrats en ligne'],
      howItWorks: 'Un chargé d’affaires étudie votre activité et vous propose un programme.',
    ),
  ];

  static const formulas = <Formula>[
    Formula(
      id: 'tiers',
      name: 'Tiers',
      description: 'Responsabilité civile obligatoire et défense-recours.',
      guarantees: ['Responsabilité civile', 'Défense et recours'],
    ),
    Formula(
      id: 'tiers_plus',
      name: 'Tiers +',
      description: 'Tiers, plus vol, incendie et bris de glace.',
      guarantees: ['Responsabilité civile', 'Défense et recours', 'Vol et incendie', 'Bris de glace'],
    ),
    Formula(
      id: 'tous_risques',
      name: 'Tous risques',
      description: 'La protection la plus complète, y compris vos propres dommages.',
      guarantees: ['Responsabilité civile', 'Défense et recours', 'Vol et incendie', 'Bris de glace', 'Dommages tous accidents'],
    ),
  ];

  static QuoteReferentials referentials(String productId) {
    final moto = productId == 'moto';
    return QuoteReferentials(
      powerLabel: moto ? 'Cylindrée' : 'Puissance fiscale',
      powers: moto
          ? const ['≤ 50 cm³', '51 – 125 cm³', '126 – 250 cm³', '> 250 cm³']
          : const ['2 – 4 CV', '5 – 7 CV', '8 – 10 CV', '11 CV et +'],
      usages: productId == 'vtc' ? const ['Transport de personnes'] : const ['Personnel', 'Professionnel'],
      formulas: formulas,
      durations: const [1, 3, 6, 12],
      platforms: productId == 'vtc' ? const ['Yango', 'Autre plateforme'] : const [],
    );
  }

  static final contracts = <Contract>[
    Contract(
      id: 'c1',
      productId: 'auto',
      name: 'Automobile',
      insuredObject: 'Toyota Corolla · 1234 AB 01',
      number: 'AZ-AUT-26-004512',
      status: ContractStatus.actif,
      holderName: 'Koné Awa',
      startDate: daysFromNow(-238),
      endDate: daysFromNow(127),
      premium: 186400,
      renewal: const ContractRenewal(eligible: false, daysLeft: 127),
      actions: ContractActions.all,
      attestation: ContractAttestation(
        available: true,
        verificationCode: 'AZ-VERIF-AUT-26-004512-DEMO',
        validFrom: daysFromNow(-238),
        validUntil: daysFromNow(127),
      ),
      guarantees: const [
        Guarantee('Responsabilité civile', GuaranteeKind.obligatoire),
        Guarantee('Défense et recours', GuaranteeKind.incluse),
        Guarantee('Bris de glace', GuaranteeKind.selonFormule),
      ],
      documents: const [
        ContractDocument(id: 'd1', name: 'Attestation d’assurance', type: DocumentType.attestation, status: DocumentStatus.disponible),
        ContractDocument(id: 'd2', name: 'Carte brune CEDEAO', type: DocumentType.carteBrune, status: DocumentStatus.enGeneration),
        ContractDocument(id: 'd3', name: 'Conditions particulières', type: DocumentType.conditions, status: DocumentStatus.disponible),
        ContractDocument(id: 'd4', name: 'Reçu de paiement', type: DocumentType.recu, status: DocumentStatus.disponible),
      ],
    ),
    Contract(
      id: 'c2',
      productId: 'moto',
      name: 'Moto',
      insuredObject: 'Haojue · 5678 KL 01',
      number: 'AZ-MOT-26-001187',
      status: ContractStatus.actif,
      holderName: 'Koné Awa',
      startDate: daysFromNow(-353),
      endDate: daysFromNow(12),
      premium: 38500,
      renewal: const ContractRenewal(eligible: true, daysLeft: 12, amount: 38500, durationMonths: 12),
      actions: ContractActions.all,
      attestation: ContractAttestation(
        available: true,
        verificationCode: 'AZ-VERIF-MOT-26-001187-DEMO',
        validFrom: daysFromNow(-353),
        validUntil: daysFromNow(12),
      ),
      guarantees: const [Guarantee('Responsabilité civile', GuaranteeKind.obligatoire)],
      documents: const [
        ContractDocument(id: 'd5', name: 'Attestation d’assurance', type: DocumentType.attestation, status: DocumentStatus.disponible),
        ContractDocument(id: 'd6', name: 'Reçu de paiement', type: DocumentType.recu, status: DocumentStatus.disponible),
      ],
    ),
    const Contract(
      id: 'c3',
      productId: 'habitation',
      name: 'Habitation',
      insuredObject: 'Appartement · Cocody',
      number: 'AZ-HAB-26-000342',
      status: ContractStatus.enAttente,
      guarantees: [Guarantee('À définir après validation', GuaranteeKind.aConfirmer)],
      documents: [
        ContractDocument(id: 'd7', name: 'Conditions particulières', type: DocumentType.conditions, status: DocumentStatus.nonDisponible),
      ],
    ),
    Contract(
      id: 'c4',
      productId: 'voyage',
      name: 'Voyage',
      insuredObject: 'Abidjan – Accra',
      number: 'AZ-VOY-26-000918',
      status: ContractStatus.expire,
      startDate: daysFromNow(-85),
      endDate: daysFromNow(-71),
      premium: 12000,
      guarantees: const [Guarantee('Assistance voyage', GuaranteeKind.incluse)],
      documents: const [
        ContractDocument(id: 'd8', name: 'Attestation de voyage', type: DocumentType.attestation, status: DocumentStatus.disponible),
      ],
    ),
  ];

  static const claimSteps = ['Déclaration reçue', 'Dossier en analyse', 'Expertise', 'Décision', 'Règlement'];

  static final claims = <Claim>[
    Claim(
      id: 'k1',
      type: 'Accident de la circulation',
      contractLabel: 'Automobile · 1234 AB 01',
      number: 'SIN-2026-00871',
      declaredAt: now.subtract(const Duration(days: 13)),
      steps: claimSteps,
      currentStep: 1,
      history: [
        ClaimEvent(now.subtract(const Duration(days: 11, hours: 6)), 'Votre dossier a été attribué à un gestionnaire.'),
        ClaimEvent(now.subtract(const Duration(days: 13)), 'Déclaration reçue avec 4 photos et 1 document.'),
      ],
    ),
    Claim(
      id: 'k2',
      type: 'Bris de glace',
      contractLabel: 'Automobile · 1234 AB 01',
      number: 'SIN-2026-00412',
      declaredAt: now.subtract(const Duration(days: 144)),
      steps: claimSteps,
      currentStep: 4,
      closed: true,
      history: [
        ClaimEvent(now.subtract(const Duration(days: 127)), 'Indemnisation versée. Dossier clôturé.'),
        ClaimEvent(now.subtract(const Duration(days: 144)), 'Déclaration reçue.'),
      ],
    ),
  ];

  static const claimTypes = ['Accident de la circulation', 'Vol', 'Bris de glace', 'Incendie', 'Autre'];

  static final notifications = <AppNotification>[
    AppNotification(
      id: 'n1',
      category: NotificationCategory.echeance,
      title: 'Votre assurance Moto arrive à échéance',
      body: 'Renouvelez avant l’échéance pour rester couvert.',
      date: now.subtract(const Duration(hours: 2)),
      contractId: 'c2',
    ),
    AppNotification(
      id: 'n2',
      category: NotificationCategory.document,
      title: 'Attestation disponible',
      body: 'L’attestation de votre contrat Automobile est prête.',
      date: now.subtract(const Duration(days: 1)),
      contractId: 'c1',
    ),
    AppNotification(
      id: 'n3',
      category: NotificationCategory.sinistre,
      title: 'Dossier SIN-2026-00871 mis à jour',
      body: 'Votre dossier est en cours d’analyse.',
      date: now.subtract(const Duration(days: 11)),
      claimId: 'k1',
    ),
    AppNotification(
      id: 'n4',
      category: NotificationCategory.paiement,
      title: 'Paiement confirmé',
      body: '186 400 FCFA reçus pour le contrat AZ-AUT-26-004512.',
      date: now.subtract(const Duration(days: 238)),
      read: true,
      contractId: 'c1',
    ),
    AppNotification(
      id: 'n5',
      category: NotificationCategory.service,
      title: 'Maintenance programmée',
      body: 'Service indisponible dimanche de 2 h à 4 h.',
      date: now.subtract(const Duration(days: 240)),
      read: true,
    ),
  ];

  static const paymentMethods = <PaymentMethod>[
    PaymentMethod(id: 'orange_money', name: 'Orange Money', shortLabel: 'OM', subtitle: 'Validation par code sur votre téléphone', brandColor: 0xFFE4701E),
    PaymentMethod(id: 'mtn_momo', name: 'MTN MoMo', shortLabel: 'MTN', subtitle: 'Validation par code sur votre téléphone', brandColor: 0xFF8A6D00),
    PaymentMethod(id: 'moov_money', name: 'Moov Money', shortLabel: 'MV', subtitle: 'Validation par code sur votre téléphone', brandColor: 0xFF1D5BB4),
    PaymentMethod(id: 'wave', name: 'Wave', shortLabel: 'W', subtitle: 'Validation dans l’application Wave', brandColor: 0xFF1A78C2),
    PaymentMethod(id: 'carte', name: 'Carte bancaire', shortLabel: 'CB', subtitle: 'Visa ou Mastercard', brandColor: 0xFF3A4660, needsPhoneNumber: false),
    PaymentMethod(id: 'agence', name: 'Payer en agence', shortLabel: 'AG', subtitle: 'Une référence vous est envoyée par SMS', brandColor: 0xFF4A5752, needsPhoneNumber: false),
  ];

  static const faq = <FaqItem>[
    FaqItem('Quand puis-je télécharger mon attestation ?',
        'Dès que le paiement est confirmé et que l’attestation est générée par AssurZen. Vous recevez une notification à ce moment-là, et elle reste consultable sans connexion.'),
    FaqItem('Comment déclarer un sinistre ?',
        'Depuis l’onglet Sinistres, touchez « Déclarer un sinistre », puis suivez les 4 étapes. Gardez vos photos et votre constat à portée de main. Un gestionnaire vous contacte sous 24 h.'),
    FaqItem('Quels moyens de paiement sont acceptés ?',
        'Orange Money, MTN MoMo, Moov Money, Wave, la carte bancaire et le paiement en agence. Aucun frais n’est ajouté au moment du paiement.'),
    FaqItem('Puis-je payer en plusieurs fois ?',
        'Oui, en 4 fois pour les formules Tiers + et Tous risques souscrites pour 12 mois.'),
    FaqItem('J’ai oublié mon code PIN, que faire ?',
        'Sur l’écran de connexion, touchez « Code PIN oublié ». Nous vous envoyons un code par SMS pour en créer un nouveau.'),
  ];
}
