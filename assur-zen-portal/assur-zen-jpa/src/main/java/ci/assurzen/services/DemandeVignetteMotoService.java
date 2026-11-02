package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.dto.ChangerStatutDemandeVignetteMotoRequest;
import ci.assurzen.dto.DemandeVignetteMotoRequest;
import ci.assurzen.dto.DemandeVignetteMotoResponse;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.dto.HistoriqueDemandeVignetteMotoResponse;
import ci.assurzen.dto.LienSuiviVignetteResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SimulationTarifVignetteMotoRequest;
import ci.assurzen.dto.SimulationTarifVignetteMotoResponse;
import ci.assurzen.dto.SuiviPublicVignetteResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.FraisLivraisonZone;
import ci.assurzen.entities.GedConfidentialityLevel;
import ci.assurzen.entities.GedDocumentType;
import ci.assurzen.entities.HistoriqueDemandeVignetteMoto;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TarifVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VignetteCommission;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.GedDocumentTypeRepository;
import ci.assurzen.repositories.HistoriqueDemandeVignetteMotoRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VignetteCommissionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.services.VignetteMotoCategorisation.ResultatAge;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Demandes d'etablissement de vignette moto (nouvelle ou renouvellement) - contrat "Vignette Moto",
 * 2026-09-11. Orchestration : categorisation (VignetteMotoCategorisation), resolution tarif/zone
 * (TarifVignetteMotoService/FraisLivraisonZoneService), figeage des montants a la creation, documents
 * via le module GED existant (pas de mecanisme de stockage dedie).
 *
 * Perimetre (audit de securite du 2026-09-12, ajuste le meme jour suite au retour du frontend : le
 * module est un vrai self-service, pas un flux backoffice comme suppose initialement) : un non-admin
 * ne voit/modifie que les demandes qu'il a lui-meme creees, via AuditableEntity.createdBy (username) -
 * meme mecanisme de "proprietaire" que ContractsService.getContractScoped()/InsuredService, pas une
 * nouvelle notion. 404 (pas 403) sur une demande d'autrui, meme convention que les autres gardes-fous
 * IDOR de ce projet.
 */
@Service
@RequiredArgsConstructor
public class DemandeVignetteMotoService {

    private static final String ENTITY_TYPE_GED = "VIGNETTE_MOTO_DEMANDE";
    private static final String DOC_CNI_ASSURE = "CNI_ASSURE";
    private static final String DOC_RECU_ACHAT_MOTO = "RECU_ACHAT_MOTO";
    private static final String DOC_ANCIENNE_VIGNETTE = "ANCIENNE_VIGNETTE";
    /** Ajoutes le 2026-09-18 (demande utilisateur) - uniquement pour NOUVELLE_DEMANDE, le
     * RENOUVELLEMENT garde sa liste inchangee (CNI/recu/ancienne vignette). */
    private static final String DOC_PHOTO_MOTO = "PHOTO_MOTO";
    private static final String DOC_PHOTO_CHASSIS = "PHOTO_CHASSIS";
    private static final String DOC_ASSURANCE = "ASSURANCE";

    /** Montant zone-dependant (contrat "Commission apporteur variable par zone", 2026-09-18,
     * remplace l'ancien forfait unique de 1000 FCFA) - reference = zoneLivraison de la DEMANDE, pas
     * la ville du partenaire/commercial lui-meme. */
    private static final String CONFIG_COMMISSION_APPORTEUR_MONTANT_ABIDJAN = "VIGNETTE_COMMISSION_APPORTEUR_MONTANT_ABIDJAN";
    private static final long COMMISSION_APPORTEUR_MONTANT_ABIDJAN_DEFAUT = 500L;
    private static final String CONFIG_COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN = "VIGNETTE_COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN";
    private static final long COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN_DEFAUT = 1000L;
    private static final String ZONE_ABIDJAN = "ABIDJAN";

    /** Matrice des transitions autorisees (contrat "Suivi d'evolution des demandes de vignettes",
     * 2026-09-17) - centralise ce qui etait auparavant reparti en verifications ad-hoc dans
     * soumettre()/annuler()/rejeter(). LIVREE/ANNULEE/REJETEE sont terminaux (absents de la map =
     * aucune transition sortante autorisee) - corrige au passage un gap latent ou annuler() acceptait
     * a tort REJETEE comme statut de depart. REJETEE reste volontairement generique (pas de nouveaux
     * statuts d'echec distincts type PAIEMENT_ECHOUE/EDITION_ECHOUEE) : le motif texte libre suffit a
     * distinguer la cause, decision produit confirmee le 2026-09-17. */
    private static final Map<StatutDemandeVignetteMoto, Set<StatutDemandeVignetteMoto>> TRANSITIONS_AUTORISEES = new EnumMap<>(StatutDemandeVignetteMoto.class);
    static {
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.BROUILLON, Set.of(
                StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, Set.of(
                StatutDemandeVignetteMoto.PAYEE, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.PAYEE, Set.of(
                StatutDemandeVignetteMoto.EN_TRAITEMENT, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.EN_TRAITEMENT, Set.of(
                StatutDemandeVignetteMoto.EN_EDITION, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.EN_EDITION, Set.of(
                StatutDemandeVignetteMoto.EDITEE, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.EDITEE, Set.of(
                StatutDemandeVignetteMoto.EN_LIVRAISON, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeVignetteMoto.EN_LIVRAISON, Set.of(
                StatutDemandeVignetteMoto.LIVREE, StatutDemandeVignetteMoto.ANNULEE, StatutDemandeVignetteMoto.REJETEE));
    }

    /** Cibles atteignables uniquement via changerStatut() (admin, progression manuelle) - BROUILLON/
     * EN_ATTENTE_PAIEMENT/ANNULEE ont deja leurs propres endpoints dedies (creer/soumettre/annuler)
     * avec leur propre semantique (ex. IDOR owner-scope sur annuler()), pas dupliques ici. */
    private static final Set<StatutDemandeVignetteMoto> CIBLES_CHANGER_STATUT = Set.of(
            StatutDemandeVignetteMoto.PAYEE, StatutDemandeVignetteMoto.EN_TRAITEMENT, StatutDemandeVignetteMoto.EN_EDITION,
            StatutDemandeVignetteMoto.EDITEE, StatutDemandeVignetteMoto.EN_LIVRAISON, StatutDemandeVignetteMoto.LIVREE,
            StatutDemandeVignetteMoto.REJETEE);

    /** "VGN_MOTO_" et non "VIGNETTE_MOTO_" (14 caracteres) : concatene a StatutDemandeVignetteMoto.name()
     * (jusqu'a 20 caracteres pour EN_ATTENTE_PAIEMENT), un prefixe trop long depasserait la limite de
     * 30 caracteres de CommunicationTemplate.code - bug reel trouve en seedant sur recette le
     * 2026-09-17 (VIGNETTE_MOTO_EN_ATTENTE_PAIEMENT = 34 caracteres, rejete en 400). */
    private static final String TEMPLATE_PREFIX_STATUT = "VGN_MOTO_";

    private final DemandeVignetteMotoRepository repository;
    private final TarifVignetteMotoService tarifVignetteMotoService;
    private final FraisLivraisonZoneService fraisLivraisonZoneService;
    private final GedDocumentService gedDocumentService;
    private final GedDocumentTypeRepository gedDocumentTypeRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final PartenaireRepository partenaireRepository;
    private final CommercialRepository commercialRepository;
    private final VignetteCommissionRepository vignetteCommissionRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final HistoriqueDemandeVignetteMotoRepository historiqueRepository;
    private final UserRepository userRepository;
    private final SmsNotificationService smsNotificationService;
    private final LienSuiviVignetteService lienSuiviVignetteService;

    public SimulationTarifVignetteMotoResponse simuler(SimulationTarifVignetteMotoRequest request) {
        if (request.partenaireApporteurId() != null && request.commercialApporteurId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une demande ne peut avoir qu'un seul apporteur : partenaire OU commercial, pas les deux");
        }
        // Resolus (pas juste verifies "non-null") pour que l'apercu echoue en 404 sur un id invalide,
        // plutot que de laisser cette surprise pour la creation reelle - meme discipline que creer().
        boolean avecApporteur = request.partenaireApporteurId() != null || request.commercialApporteurId() != null;
        if (request.partenaireApporteurId() != null) {
            resolvePartenaire(request.partenaireApporteurId());
        }
        if (request.commercialApporteurId() != null) {
            resolveCommercial(request.commercialApporteurId());
        }

        ResultatTarification resultat = calculerTarification(
                request.datePremiereMiseCirculation(), request.cylindree(), request.zoneLivraison());

        BigDecimal reductionApporteur = avecApporteur
                ? BigDecimal.valueOf(resolveMontantCommissionApporteur(resultat.zone().getCode()))
                : null;
        BigDecimal montantTotal = avecApporteur ? resultat.montantTotal().subtract(reductionApporteur) : resultat.montantTotal();

        return new SimulationTarifVignetteMotoResponse(
                resultat.categorieAge(), resultat.ageMoto(), resultat.categorieCylindree(),
                resultat.montantVignette(), resultat.fraisOperation(), resultat.fraisLivraison(),
                resultat.fraisAccessoires(), montantTotal, reductionApporteur);
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "VIGNETTES_MOTO", entityName = "DemandeVignetteMoto")
    public DemandeVignetteMotoResponse creer(DemandeVignetteMotoRequest request) {
        if (request.cylindreeMoto() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cylindree doit etre superieure a 0");
        }
        if (request.partenaireApporteurId() != null && request.commercialApporteurId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une demande ne peut avoir qu'un seul apporteur : partenaire OU commercial, pas les deux");
        }
        Partenaire partenaireApporteur = request.partenaireApporteurId() == null ? null : resolvePartenaire(request.partenaireApporteurId());
        Commercial commercialApporteur = request.commercialApporteurId() == null ? null : resolveCommercial(request.commercialApporteurId());

        ResultatTarification resultat = calculerTarification(
                request.datePremiereMiseCirculation(), request.cylindreeMoto(), request.zoneLivraison());

        DemandeVignetteMoto.DemandeVignetteMotoBuilder demandeBuilder = DemandeVignetteMoto.builder()
                .reference(codeGeneratorService.generate(CodePrefix.VIGNETTE_MOTO))
                .typeDemande(request.typeDemande())
                .nomAssure(request.nomAssure())
                .prenomsAssure(request.prenomsAssure())
                .telephoneAssure(request.telephoneAssure())
                .telephoneAssureSecondaire(request.telephoneAssureSecondaire())
                .adresseLivraison(request.adresseLivraison())
                .villeLivraison(request.villeLivraison())
                .marqueMoto(request.marqueMoto())
                .modeleMoto(request.modeleMoto())
                .numeroChassisMoto(request.numeroChassisMoto())
                .cylindreeMoto(request.cylindreeMoto())
                .datePremiereMiseCirculation(request.datePremiereMiseCirculation())
                .tarifVignetteMoto(resultat.tarif())
                .fraisLivraisonZone(resultat.zone())
                .categorieAgeAppliquee(resultat.categorieAge())
                .categorieCylindreeAppliquee(resultat.categorieCylindree())
                .ageMotoCalcule(resultat.ageMoto())
                .montantVignette(resultat.montantVignette())
                .fraisOperation(resultat.fraisOperation())
                .fraisLivraison(resultat.fraisLivraison())
                .fraisAccessoires(resultat.fraisAccessoires())
                .partenaireApporteur(partenaireApporteur)
                .commercialApporteur(commercialApporteur);

        long montantCommission = 0L;
        boolean avecApporteur = partenaireApporteur != null || commercialApporteur != null;
        if (avecApporteur) {
            montantCommission = resolveMontantCommissionApporteur(resultat.zone().getCode());
            demandeBuilder.reductionApporteur(BigDecimal.valueOf(montantCommission));
            demandeBuilder.montantTotal(resultat.montantTotal().subtract(BigDecimal.valueOf(montantCommission)));
        } else {
            demandeBuilder.montantTotal(resultat.montantTotal());
        }

        DemandeVignetteMoto demande = demandeBuilder.build();
        demande.setCreatedBy(authService.currentPrincipal().username());
        demande = repository.save(demande);

        if (avecApporteur) {
            creerCommissionApporteur(demande, partenaireApporteur, commercialApporteur, montantCommission);
        }

        User auteur = resolveUtilisateurCourant();
        HistoriqueDemandeVignetteMoto premierEvenement = HistoriqueDemandeVignetteMoto.builder()
                .demandeVignetteMoto(demande)
                .ancienStatut(null)
                .nouveauStatut(demande.getStatut())
                .auteur(auteur)
                .build();

        LienSuiviVignetteResponse lien = lienSuiviVignetteService.genererPourDemande(demande);
        boolean notifie = smsNotificationService.sendFromTemplate(
                demande.getTelephoneAssure(),
                TEMPLATE_PREFIX_STATUT + demande.getStatut().name(),
                Map.of("reference", demande.getReference(), "lienSuivi", lien.lienComplet()),
                TypeMessageSms.vignette_moto_changement_statut);
        premierEvenement.setNotificationEnvoyee(notifie);
        historiqueRepository.save(premierEvenement);

        return toResponse(demande);
    }

    /** Commission due a l'apporteur, credited des la creation de la demande (le workflow paiement/
     * livraison de la vignette elle-meme n'est pas encore construit dans ce module - decision produit
     * du 2026-09-15). Montant fige au moment de la creation, jamais recalcule si la configuration
     * change ensuite - meme discipline que le tarif de la demande elle-meme. Le montant est celui deja
     * deduit du prix client (cf. creer()) - jamais recalcule ici separement, pour garantir que
     * "ce que perd le client" == "ce que recoit l'apporteur", au FCFA pres. */
    private void creerCommissionApporteur(DemandeVignetteMoto demande, Partenaire partenaire, Commercial commercial, long montant) {
        VignetteCommission commission = VignetteCommission.builder()
                .demandeVignetteMoto(demande)
                .partenaire(partenaire)
                .commercial(commercial)
                .montant(montant)
                .statut(CommissionStatus.pending)
                .build();
        vignetteCommissionRepository.save(commission);
    }

    /** Contrat "Commission apporteur variable par zone", 2026-09-18 : la reference est la zone de
     * LIVRAISON de la demande (zoneLivraison), pas la ville du partenaire/commercial apporteur
     * lui-meme - decision produit confirmee. */
    private long resolveMontantCommissionApporteur(String zoneLivraisonCode) {
        return ZONE_ABIDJAN.equals(zoneLivraisonCode)
                ? dynamicConfigurationService.getLong(CONFIG_COMMISSION_APPORTEUR_MONTANT_ABIDJAN, COMMISSION_APPORTEUR_MONTANT_ABIDJAN_DEFAUT)
                : dynamicConfigurationService.getLong(CONFIG_COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN, COMMISSION_APPORTEUR_MONTANT_HORS_ABIDJAN_DEFAUT);
    }

    public PageResponse<DemandeVignetteMotoResponse> list(StatutDemandeVignetteMoto statut, Integer page, Integer size) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        List<DemandeVignetteMotoResponse> items = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> admin || principal.username().equals(d.getCreatedBy()))
                .filter(d -> statut == null || d.getStatut() == statut)
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public DemandeVignetteMotoResponse get(Long id) {
        DemandeVignetteMoto demande = getEntity(id);
        verifierAccesDemandeCible(demande);
        return toResponse(demande);
    }

    @Transactional
    public GedDocumentResponse ajouterDocument(Long demandeId, String typeCode, MultipartFile file) {
        DemandeVignetteMoto demande = getEntity(demandeId);
        verifierAccesDemandeCible(demande);
        GedDocumentType documentType = gedDocumentTypeRepository.findByCodeIgnoreCase(typeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de document inconnu: " + typeCode));

        return gedDocumentService.create(documentType.getId(), null,
                documentType.getNom() + " - " + demande.getReference(), null,
                ENTITY_TYPE_GED, demande.getId(), GedConfidentialityLevel.CONFIDENTIAL, "VIGNETTE_MOTO", file);
    }

    public PageResponse<GedDocumentResponse> listerDocuments(Long demandeId, Integer page, Integer size) {
        verifierAccesDemandeCible(getEntity(demandeId));
        return gedDocumentService.listByEntity(ENTITY_TYPE_GED, demandeId, page, size);
    }

    /** Endpoint scope dedie (audit de securite du 2026-09-12) : GET /ged/documents/{id}/download
     * general reste ADMIN-only (aucune verification de perimetre generique par type d'entite liee
     * n'existe), mais un createur de demande doit pouvoir revoir ses propres documents deposes -
     * verifie ici a la fois que la demande lui appartient ET que le document demande est bien lie a
     * CETTE demande (sinon un id de document arbitraire d'une autre demande serait accepte). */
    public GedDocumentVersionContent downloadDocument(Long demandeId, Long documentId) {
        DemandeVignetteMoto demande = getEntity(demandeId);
        verifierAccesDemandeCible(demande);

        boolean documentLieACetteDemande = gedDocumentService.listByEntity(ENTITY_TYPE_GED, demandeId, 0, 200)
                .content().stream()
                .anyMatch(doc -> doc.id().equals(documentId));
        if (!documentLieACetteDemande) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable pour cette demande: " + documentId);
        }
        return gedDocumentService.download(documentId);
    }

    @Transactional
    @Auditable(action = ActionAudit.SUBMIT, module = "VIGNETTES_MOTO", entityName = "DemandeVignetteMoto")
    public DemandeVignetteMotoResponse soumettre(Long id) {
        DemandeVignetteMoto demande = getEntity(id);
        verifierAccesDemandeCible(demande);
        validerTransition(demande.getStatut(), StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT);
        validerDocumentsRequis(demande);

        return appliquerTransition(demande, StatutDemandeVignetteMoto.EN_ATTENTE_PAIEMENT, null, null);
    }

    @Transactional
    @Auditable(action = ActionAudit.CANCEL, module = "VIGNETTES_MOTO", entityName = "DemandeVignetteMoto")
    public DemandeVignetteMotoResponse annuler(Long id) {
        DemandeVignetteMoto demande = getEntity(id);
        verifierAccesDemandeCible(demande);
        validerTransition(demande.getStatut(), StatutDemandeVignetteMoto.ANNULEE);

        return appliquerTransition(demande, StatutDemandeVignetteMoto.ANNULEE, null, null);
    }

    /** Reserve a l'admin (pas au createur) : confirme par le frontend le 2026-09-12, rejeter sa propre
     * demande n'a pas de sens pour un createur, contrairement a annuler() qui reste ouvert au createur.
     * Double garde-fou, meme convention que TarifVignetteMotoService/FraisLivraisonZoneService :
     * SecurityConfig bloque deja PATCH .../rejeter au niveau gateway, ce controle cote service est la
     * seconde couche. Elargi le 2026-09-17 (decision produit) : rejetable depuis N'IMPORTE QUEL statut
     * non terminal (avant : uniquement BROUILLON/EN_ATTENTE_PAIEMENT) - REJETEE reste generique pour
     * tout echec ulterieur (paiement/edition/livraison), le motif texte libre precise l'etape. */
    @Transactional
    @Auditable(action = ActionAudit.REJECT, module = "VIGNETTES_MOTO", entityName = "DemandeVignetteMoto")
    public DemandeVignetteMotoResponse rejeter(Long id, String motif) {
        requireAdmin();
        DemandeVignetteMoto demande = getEntity(id);
        validerTransition(demande.getStatut(), StatutDemandeVignetteMoto.REJETEE);

        return appliquerTransition(demande, StatutDemandeVignetteMoto.REJETEE, motif, null);
    }

    /** Progression manuelle du cycle de vie (contrat "Suivi d'evolution des demandes de vignettes",
     * 2026-09-17) - reserve a l'admin/gestionnaire (aucune integration reelle paiement/edition/
     * livraison n'existe encore dans ce module, decision produit confirmee : infrastructure de suivi
     * manuel uniquement pour l'instant). Restreint a CIBLES_CHANGER_STATUT : BROUILLON/
     * EN_ATTENTE_PAIEMENT/ANNULEE gardent leurs endpoints dedies (creer/soumettre/annuler), pas
     * dupliques ici. */
    @Transactional
    @Auditable(action = ActionAudit.UPDATE, module = "VIGNETTES_MOTO", entityName = "DemandeVignetteMoto")
    public DemandeVignetteMotoResponse changerStatut(Long id, StatutDemandeVignetteMoto nouveauStatut, String motif, String referenceOperation) {
        requireAdmin();
        if (!CIBLES_CHANGER_STATUT.contains(nouveauStatut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Statut non atteignable via cet endpoint: " + nouveauStatut);
        }
        DemandeVignetteMoto demande = getEntity(id);
        validerTransition(demande.getStatut(), nouveauStatut);

        return appliquerTransition(demande, nouveauStatut, motif, referenceOperation);
    }

    public List<HistoriqueDemandeVignetteMotoResponse> getHistorique(Long id) {
        DemandeVignetteMoto demande = getEntity(id);
        verifierAccesDemandeCible(demande);
        return historiqueRepository.findByDemandeVignetteMotoIdOrderByCreatedAtAsc(id).stream()
                .map(this::toHistoriqueResponse)
                .toList();
    }

    /** Public - aucune authentification, aucun controle IDOR (le token en tient lieu). Reponse
     * deliberement minimale : ni montant, ni telephone, ni document (cf. javadoc
     * SuiviPublicVignetteResponse). */
    public SuiviPublicVignetteResponse getSuiviPublic(String token) {
        DemandeVignetteMoto demande = lienSuiviVignetteService.resoudreDemandeActive(token);
        List<HistoriqueDemandeVignetteMoto> historique = historiqueRepository.findByDemandeVignetteMotoIdOrderByCreatedAtAsc(demande.getId());

        List<SuiviPublicVignetteResponse.Entree> entrees = historique.stream()
                .map(h -> new SuiviPublicVignetteResponse.Entree(h.getNouveauStatut(), libelleStatut(h.getNouveauStatut()), h.getCreatedAt()))
                .toList();

        return new SuiviPublicVignetteResponse(
                demande.getReference(), demande.getStatut(), libelleStatut(demande.getStatut()), demande.getMotifRejet(),
                demande.getUpdatedAt(), entrees);
    }

    // ------------------------------------------------------------------

    private void validerTransition(StatutDemandeVignetteMoto actuel, StatutDemandeVignetteMoto cible) {
        Set<StatutDemandeVignetteMoto> autorises = TRANSITIONS_AUTORISEES.getOrDefault(actuel, Set.of());
        if (!autorises.contains(cible)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transition " + actuel + " -> " + cible + " non autorisee (statut actuel: " + actuel + ")");
        }
    }

    /** Point unique d'ecriture d'une transition : statut + motif (REJETEE uniquement) + historique +
     * SMS - jamais duplique entre soumettre/annuler/rejeter/changerStatut. */
    private DemandeVignetteMotoResponse appliquerTransition(DemandeVignetteMoto demande, StatutDemandeVignetteMoto nouveauStatut,
                                                             String motif, String referenceOperation) {
        StatutDemandeVignetteMoto ancienStatut = demande.getStatut();
        demande.setStatut(nouveauStatut);
        if (nouveauStatut == StatutDemandeVignetteMoto.REJETEE) {
            demande.setMotifRejet(motif);
        }
        demande = repository.save(demande);

        boolean notifie = declencherSmsChangementStatut(demande, nouveauStatut, motif);

        HistoriqueDemandeVignetteMoto evenement = HistoriqueDemandeVignetteMoto.builder()
                .demandeVignetteMoto(demande)
                .ancienStatut(ancienStatut)
                .nouveauStatut(nouveauStatut)
                .motif(motif)
                .referenceOperation(referenceOperation)
                .auteur(resolveUtilisateurCourant())
                .notificationEnvoyee(notifie)
                .build();
        historiqueRepository.save(evenement);

        return toResponse(demande);
    }

    /** Echec d'envoi non bloquant (sendFromTemplate ne leve jamais) - meme convention que les autres
     * modules de ce projet. Le lien de suivi n'est PAS reinclus ici (seul le token brut permettrait de
     * reconstituer l'URL, or il n'est jamais persiste - cf. javadoc LienSuiviVignetteService) : seuls
     * le SMS de creation et celui de regeneration du lien portent l'URL complete. */
    private boolean declencherSmsChangementStatut(DemandeVignetteMoto demande, StatutDemandeVignetteMoto statut, String motif) {
        if (!StringUtils.hasText(demande.getTelephoneAssure())) {
            return false;
        }
        return smsNotificationService.sendFromTemplate(
                demande.getTelephoneAssure(),
                TEMPLATE_PREFIX_STATUT + statut.name(),
                Map.of("reference", demande.getReference(), "motif", StringUtils.hasText(motif) ? motif : "N/A"),
                TypeMessageSms.vignette_moto_changement_statut);
    }

    private User resolveUtilisateurCourant() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        return userRepository.findById(principal.userId()).orElse(null);
    }

    private HistoriqueDemandeVignetteMotoResponse toHistoriqueResponse(HistoriqueDemandeVignetteMoto h) {
        return new HistoriqueDemandeVignetteMotoResponse(
                h.getId(), h.getDemandeVignetteMoto().getId(), h.getAncienStatut(), h.getNouveauStatut(),
                h.getMotif(), h.getReferenceOperation(),
                h.getAuteur() != null ? h.getAuteur().getId() : null,
                h.getAuteur() != null ? h.getAuteur().getUsername() : null,
                h.getNotificationEnvoyee(), h.getCreatedAt());
    }

    private String libelleStatut(StatutDemandeVignetteMoto statut) {
        return switch (statut) {
            case BROUILLON -> "Demande enregistree";
            case EN_ATTENTE_PAIEMENT -> "En attente de paiement";
            case PAYEE -> "Paiement confirme";
            case EN_TRAITEMENT -> "En cours de traitement";
            case EN_EDITION -> "Vignette en cours d'edition";
            case EDITEE -> "Vignette editee";
            case EN_LIVRAISON -> "En attente de remise ou de livraison";
            case LIVREE -> "Vignette remise";
            case ANNULEE -> "Demande annulee";
            case REJETEE -> "Demande rejetee";
        };
    }

    private void validerDocumentsRequis(DemandeVignetteMoto demande) {
        Set<String> typesPresents = gedDocumentService.listByEntity(ENTITY_TYPE_GED, demande.getId(), 0, 100)
                .content().stream()
                .map(GedDocumentResponse::documentTypeCode)
                .collect(java.util.stream.Collectors.toSet());

        List<String> typesRequis = demande.getTypeDemande() == TypeDemandeVignette.RENOUVELLEMENT
                ? List.of(DOC_CNI_ASSURE, DOC_RECU_ACHAT_MOTO, DOC_ANCIENNE_VIGNETTE)
                : List.of(DOC_CNI_ASSURE, DOC_RECU_ACHAT_MOTO, DOC_PHOTO_MOTO, DOC_PHOTO_CHASSIS, DOC_ASSURANCE);

        List<String> manquants = typesRequis.stream().filter(t -> !typesPresents.contains(t)).toList();
        if (!manquants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Document(s) obligatoire(s) manquant(s) avant soumission: " + String.join(", ", manquants));
        }
    }

    private record ResultatTarification(
            TarifVignetteMoto tarif, FraisLivraisonZone zone,
            ci.assurzen.entities.CategorieAgeMoto categorieAge, int ageMoto,
            ci.assurzen.entities.CategorieCylindree categorieCylindree,
            BigDecimal montantVignette, BigDecimal fraisOperation, BigDecimal fraisLivraison,
            BigDecimal fraisAccessoires, BigDecimal montantTotal) {
    }

    /** Calcul partage par simuler() et creer() (§10/§11 du contrat) - jamais duplique entre les deux. */
    private ResultatTarification calculerTarification(LocalDate datePremiereMiseCirculation, int cylindree, String zoneCode) {
        ResultatAge resultatAge = VignetteMotoCategorisation.calculerAge(datePremiereMiseCirculation, LocalDate.now());
        ci.assurzen.entities.CategorieCylindree categorieCylindree = VignetteMotoCategorisation.categoriserCylindree(cylindree);

        TarifVignetteMoto tarif = tarifVignetteMotoService.resoudre(resultatAge.categorieAge(), categorieCylindree, LocalDate.now());
        FraisLivraisonZone zone = fraisLivraisonZoneService.resoudre(zoneCode);

        BigDecimal fraisAccessoires = tarif.getFraisOperation().add(zone.getMontant());
        BigDecimal montantTotal = tarif.getMontantVignette().add(fraisAccessoires);

        return new ResultatTarification(tarif, zone, resultatAge.categorieAge(), resultatAge.ageAnnees(), categorieCylindree,
                tarif.getMontantVignette(), tarif.getFraisOperation(), zone.getMontant(), fraisAccessoires, montantTotal);
    }

    private Partenaire resolvePartenaire(Long id) {
        return partenaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire apporteur introuvable: " + id));
    }

    private Commercial resolveCommercial(Long id) {
        return commercialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commercial apporteur introuvable: " + id));
    }

    private DemandeVignetteMoto getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de vignette moto introuvable: " + id));
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    /** 404 (pas 403) sur une demande d'autrui - meme convention que les autres gardes-fous IDOR de ce
     * projet (ContractsService, InsuredService) : ne pas confirmer l'existence d'un id a un tiers non
     * autorise. createdBy peut etre null pour d'anciennes demandes creees avant ce correctif
     * (2026-09-12) - dans ce cas, seul un admin y a acces (pas de proprietaire identifiable). */
    private void verifierAccesDemandeCible(DemandeVignetteMoto demande) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        if (demande.getCreatedBy() == null || !demande.getCreatedBy().equals(principal.username())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de vignette moto introuvable: " + demande.getId());
        }
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private DemandeVignetteMotoResponse toResponse(DemandeVignetteMoto d) {
        return new DemandeVignetteMotoResponse(
                d.getId(), d.getUuid(), d.getReference(), d.getTypeDemande(), d.getStatut(),
                d.getNomAssure(), d.getPrenomsAssure(), d.getTelephoneAssure(), d.getTelephoneAssureSecondaire(), d.getAdresseLivraison(), d.getVilleLivraison(),
                d.getFraisLivraisonZone() != null ? d.getFraisLivraisonZone().getCode() : null,
                d.getMarqueMoto(), d.getModeleMoto(), d.getNumeroChassisMoto(), d.getCylindreeMoto(), d.getDatePremiereMiseCirculation(),
                d.getCategorieAgeAppliquee(), d.getCategorieCylindreeAppliquee(), d.getAgeMotoCalcule(),
                d.getTarifVignetteMoto() != null ? d.getTarifVignetteMoto().getId() : null,
                d.getMontantVignette(), d.getFraisOperation(), d.getFraisLivraison(), d.getFraisAccessoires(), d.getMontantTotal(), d.getReductionApporteur(),
                d.getMotifRejet(),
                d.getPartenaireApporteur() != null ? d.getPartenaireApporteur().getId() : null,
                d.getPartenaireApporteur() != null ? d.getPartenaireApporteur().getNom() + " " + d.getPartenaireApporteur().getPrenoms() : null,
                d.getCommercialApporteur() != null ? d.getCommercialApporteur().getId() : null,
                d.getCommercialApporteur() != null ? d.getCommercialApporteur().getProfile().getFirstname() + " " + d.getCommercialApporteur().getProfile().getLastname() : null,
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
