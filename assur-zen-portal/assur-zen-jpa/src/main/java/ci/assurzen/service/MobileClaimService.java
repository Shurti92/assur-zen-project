package ci.assurzen.service;

import ci.assurzen.dto.MobileClaimDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Sinistre;
import ci.assurzen.entities.SinistrePhoto;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.entities.SinistreSuivi;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.exception.ContractNotFoundException;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.SinistrePhotoRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.repositories.SinistreSuiviRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.SinistrePhotoStorageService;
import ci.assurzen.services.SmsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Declaration et suivi des sinistres par l'assure connecte (application mobile, demande n°6, 2026-11-02). Reutilise le
 * circuit du back-office : meme numerotation, meme entite Sinistre, meme SMS de declaration a l'assure, memes suivis
 * (SinistreSuivi) et meme stockage des pieces. Le client ne voit que les sinistres des assures lies a son compte (404
 * sinon). Une declaration est valide sans pieces ; les pieces s'ajoutent ensuite (6 photos et 3 documents au maximum).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileClaimService {

    public static final int MAX_PHOTOS = 6;
    public static final int MAX_DOCUMENTS = 3;
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final long TAILLE_MAX_PAR_DEFAUT = 10L * 1024 * 1024;
    private static final List<String> ETAPES = List.of("Déclaration reçue", "Analyse du dossier", "Expertise", "Décision",
            "Indemnisation et clôture");

    private static final List<MobileClaimDtos.ClaimType> TYPES_VEHICULE = List.of(
            new MobileClaimDtos.ClaimType("COLLISION", "Accident / collision"),
            new MobileClaimDtos.ClaimType("THEFT", "Vol du véhicule"),
            new MobileClaimDtos.ClaimType("FIRE", "Incendie"),
            new MobileClaimDtos.ClaimType("GLASS", "Bris de glace"),
            new MobileClaimDtos.ClaimType("OTHER", "Autre sinistre"));
    private static final List<MobileClaimDtos.ClaimType> TYPES_SANTE = List.of(
            new MobileClaimDtos.ClaimType("HOSPITALIZATION", "Hospitalisation"),
            new MobileClaimDtos.ClaimType("CARE", "Soins / consultation"),
            new MobileClaimDtos.ClaimType("MEDICINES", "Médicaments"),
            new MobileClaimDtos.ClaimType("OTHER", "Autre sinistre"));
    private static final List<MobileClaimDtos.ClaimType> TYPES_HABITATION = List.of(
            new MobileClaimDtos.ClaimType("FIRE", "Incendie"),
            new MobileClaimDtos.ClaimType("WATER_DAMAGE", "Dégât des eaux"),
            new MobileClaimDtos.ClaimType("BURGLARY", "Vol / cambriolage"),
            new MobileClaimDtos.ClaimType("OTHER", "Autre sinistre"));
    private static final List<MobileClaimDtos.ClaimType> TYPES_VOYAGE = List.of(
            new MobileClaimDtos.ClaimType("MEDICAL", "Frais médicaux à l'étranger"),
            new MobileClaimDtos.ClaimType("BAGGAGE", "Bagages perdus ou volés"),
            new MobileClaimDtos.ClaimType("CANCELLATION", "Annulation de voyage"),
            new MobileClaimDtos.ClaimType("OTHER", "Autre sinistre"));
    private static final List<MobileClaimDtos.ClaimType> TYPES_PAR_DEFAUT = List.of(
            new MobileClaimDtos.ClaimType("OTHER", "Sinistre"));

    private static final Map<SinistreStatus, String> CODES = Map.of(
            SinistreStatus.declare, "DECLARED",
            SinistreStatus.en_cours_analyse, "IN_ANALYSIS",
            SinistreStatus.pieces_complementaires_requises, "DOCUMENTS_REQUIRED",
            SinistreStatus.expertise_en_cours, "EXPERTISE",
            SinistreStatus.accepte, "ACCEPTED",
            SinistreStatus.rejete, "REJECTED",
            SinistreStatus.indemnise, "PAID",
            SinistreStatus.cloture, "CLOSED");
    private static final Map<SinistreStatus, String> LIBELLES = Map.of(
            SinistreStatus.declare, "Déclaré",
            SinistreStatus.en_cours_analyse, "En cours d'analyse",
            SinistreStatus.pieces_complementaires_requises, "Pièces complémentaires demandées",
            SinistreStatus.expertise_en_cours, "Expertise en cours",
            SinistreStatus.accepte, "Accepté",
            SinistreStatus.rejete, "Rejeté",
            SinistreStatus.indemnise, "Indemnisé",
            SinistreStatus.cloture, "Clôturé");

    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final ContractRepository contractRepository;
    private final SinistreRepository sinistreRepository;
    private final SinistreSuiviRepository suiviRepository;
    private final SinistrePhotoRepository photoRepository;
    private final SinistrePhotoStorageService storageService;
    private final UserRepository userRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final SmsNotificationService smsNotificationService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final BusinessMetrics businessMetrics;

    // ------------------------------------------------------------------ types

    public MobileClaimDtos.ClaimTypeList types(String productId) {
        return new MobileClaimDtos.ClaimTypeList(typesPourProduit(productId));
    }

    static List<MobileClaimDtos.ClaimType> typesPourProduit(String produit) {
        String p = produit == null ? "" : produit.toLowerCase(Locale.ROOT);
        if (p.contains("auto") || p.contains("moto") || p.contains("vtc") || p.contains("vehicule")) {
            return TYPES_VEHICULE;
        }
        if (p.contains("sante")) {
            return TYPES_SANTE;
        }
        if (p.contains("habitation")) {
            return TYPES_HABITATION;
        }
        if (p.contains("voyage")) {
            return TYPES_VOYAGE;
        }
        return TYPES_PAR_DEFAUT;
    }

    // ------------------------------------------------------------------ declaration

    @Transactional
    public MobileClaimDtos.Created declarer(Long userId, MobileClaimDtos.CreateRequest requete) {
        Contract contrat = contratDuClient(userId, requete.contractId());
        LocalDate aujourdhui = LocalDate.now(ABIDJAN);
        String statutContrat = MobileContractService.statut(contrat, aujourdhui);
        // Decision du 2026-11-03 : un sinistre survenu pendant la periode de couverture peut etre declare meme apres
        // l'echeance du contrat (la date est controlee plus bas). Un contrat en attente ou resilie reste refuse.
        if (!"ACTIVE".equals(statutContrat) && !"EXPIRED".equals(statutContrat)) {
            throw new MobileApiException(409, "CLAIM_NOT_ALLOWED", "Un sinistre ne peut être déclaré que sur un contrat actif ou échu.", "contractId");
        }
        List<MobileClaimDtos.ClaimType> types = typesPourProduit(MobileContractService.identifiantProduit(contrat));
        MobileClaimDtos.ClaimType type = types.stream().filter(t -> t.id().equalsIgnoreCase(requete.typeId())).findFirst()
                .orElseThrow(() -> new MobileApiException(400, "VALIDATION_FAILED",
                        "Ce type de sinistre n'existe pas pour ce produit.", "typeId"));
        LocalDate survenance = requete.occurredAt();
        if (survenance.isAfter(aujourdhui)
                || (contrat.getDateEffet() != null && survenance.isBefore(contrat.getDateEffet()))
                || (contrat.getDateEcheance() != null && survenance.isAfter(contrat.getDateEcheance()))) {
            throw new MobileApiException(400, "OCCURRED_AT_OUT_OF_RANGE",
                    "La date du sinistre doit être passée et comprise dans la période du contrat.", "occurredAt");
        }
        long max = dynamicConfigurationService.getLong("MOBILE_MAX_OPEN_CLAIMS_PER_CONTRACT", 5L);
        long ouverts = sinistreRepository.countByContractIdAndStatusNotIn(contrat.getId(),
                List.of(SinistreStatus.cloture, SinistreStatus.rejete, SinistreStatus.indemnise));
        if (ouverts >= max) {
            throw new MobileApiException(409, "CLAIM_LIMIT_REACHED",
                    "Vous avez déjà " + ouverts + " sinistres en cours sur ce contrat.", "contractId");
        }

        User declarant = userRepository.findById(userId).orElseThrow(ContractNotFoundException::new);
        String numero = codeGeneratorService.generate(CodePrefix.SINISTRE);
        Sinistre sinistre = Sinistre.builder()
                .numero(numero)
                .contract(contrat)
                .insured(contrat.getInsured())
                .nature(type.label())
                .dateSurvenance(survenance)
                .dateDeclaration(aujourdhui)
                .status(SinistreStatus.declare)
                .declarant(declarant)
                .compagnie(contrat.getCompagnie())
                .description(StringUtils.hasText(requete.description()) ? requete.description().trim() : null)
                .notes(notes(requete))
                .build();
        sinistre = sinistreRepository.save(sinistre);

        String telephone = contrat.getInsured() == null ? null : contrat.getInsured().getClientPhone();
        boolean notifie = smsNotificationService.sendClaimDeclaredSms(telephone, numero);
        suiviRepository.save(SinistreSuivi.builder()
                .sinistre(sinistre)
                .statut(SinistreStatus.declare)
                .commentaire("Sinistre declare depuis l'application mobile")
                .auteur(declarant)
                .notificationEnvoyee(notifie)
                .build());
        businessMetrics.incrementClaimsDeclared();
        log.info("[MOBILE-SINISTRE] sinistre {} declare sur le contrat {}", numero, contrat.getPolicyNumber());
        long heures = dynamicConfigurationService.getLong("CLAIM_HANDLING_HOURS", 0L);
        return new MobileClaimDtos.Created(String.valueOf(sinistre.getId()), numero, CODES.get(SinistreStatus.declare), aujourdhui,
                heures > 0 ? (int) heures : null);
    }

    private String notes(MobileClaimDtos.CreateRequest r) {
        StringBuilder sb = new StringBuilder("Declaration mobile.");
        if (StringUtils.hasText(r.place())) {
            sb.append("\nLieu : ").append(r.place().trim());
        }
        if (Boolean.TRUE.equals(r.thirdPartyInvolved())) {
            sb.append("\nTiers implique : oui");
            if (r.thirdParty() != null) {
                if (StringUtils.hasText(r.thirdParty().name())) {
                    sb.append("\n  Nom : ").append(r.thirdParty().name().trim());
                }
                if (StringUtils.hasText(r.thirdParty().plate())) {
                    sb.append("\n  Immatriculation : ").append(r.thirdParty().plate().trim());
                }
                if (StringUtils.hasText(r.thirdParty().insurer())) {
                    sb.append("\n  Assureur : ").append(r.thirdParty().insurer().trim());
                }
            }
        } else if (Boolean.FALSE.equals(r.thirdPartyInvolved())) {
            sb.append("\nTiers implique : non");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public MobileClaimDtos.ClaimList lister(Long userId) {
        List<Long> assures = assuresDuCompte(userId);
        if (assures.isEmpty()) {
            return new MobileClaimDtos.ClaimList(List.of());
        }
        return new MobileClaimDtos.ClaimList(sinistreRepository.findByInsuredIdInOrderByCreatedAtDesc(assures).stream()
                .map(this::vue).toList());
    }

    @Transactional(readOnly = true)
    public MobileClaimDtos.Claim detail(Long userId, Long id) {
        return vue(sinistreDuClient(userId, id));
    }

    @Transactional(readOnly = true)
    public MobileClaimDtos.FileList fichiers(Long userId, Long id) {
        Sinistre sinistre = sinistreDuClient(userId, id);
        return new MobileClaimDtos.FileList(photoRepository.findBySinistreIdOrderByCreatedAtAsc(sinistre.getId()).stream()
                .map(p -> new MobileClaimDtos.FileRef(String.valueOf(p.getId()), p.getKind() == null ? "PHOTO" : p.getKind(),
                        p.getFileName(), p.getSizeBytes(), p.getCreatedAt())).toList(), MAX_PHOTOS, MAX_DOCUMENTS);
    }

    // ------------------------------------------------------------------ pieces

    @Transactional
    public MobileClaimDtos.FileRef ajouterFichier(Long userId, Long id, String genre, MultipartFile fichier) {
        String kind = genre == null ? "" : genre.trim().toUpperCase(Locale.ROOT);
        if (!kind.equals("PHOTO") && !kind.equals("DOCUMENT")) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "kind doit valoir PHOTO ou DOCUMENT.", "kind");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Aucun fichier fourni.", "file");
        }
        Sinistre sinistre = sinistreDuClient(userId, id);
        if (sinistre.getStatus() == SinistreStatus.cloture || sinistre.getStatus() == SinistreStatus.rejete
                || sinistre.getStatus() == SinistreStatus.indemnise) {
            throw new MobileApiException(409, "CLAIM_CLOSED", "Ce dossier est clos : il n'accepte plus de pièces.", null);
        }
        long tailleMax = dynamicConfigurationService.getLong("MAX_FILE_SIZE", TAILLE_MAX_PAR_DEFAUT);
        if (fichier.getSize() > tailleMax) {
            throw new MobileApiException(400, "FILE_TOO_LARGE",
                    "Fichier trop volumineux (maximum " + (tailleMax / (1024 * 1024)) + " Mo).", "file");
        }
        String type = detecterType(fichier);
        boolean autorise = kind.equals("PHOTO") ? ("JPEG".equals(type) || "PNG".equals(type))
                : ("JPEG".equals(type) || "PNG".equals(type) || "PDF".equals(type));
        if (!autorise) {
            throw new MobileApiException(400, "UNSUPPORTED_FILE_TYPE", kind.equals("PHOTO")
                    ? "Les photos doivent être au format JPEG ou PNG." : "Les documents doivent être au format PDF, JPEG ou PNG.", "file");
        }
        long deja = kind.equals("PHOTO") ? comptePhotos(sinistre.getId()) : photoRepository.countBySinistreIdAndKind(sinistre.getId(), "DOCUMENT");
        int plafond = kind.equals("PHOTO") ? MAX_PHOTOS : MAX_DOCUMENTS;
        if (deja >= plafond) {
            throw new MobileApiException(409, "FILE_LIMIT_REACHED", "Maximum atteint : " + plafond
                    + (kind.equals("PHOTO") ? " photos" : " documents") + " par sinistre.", "file");
        }
        String chemin;
        try (InputStream flux = fichier.getInputStream()) {
            chemin = storageService.store(sinistre.getId(), fichier.getOriginalFilename(), flux);
        } catch (IOException e) {
            throw new MobileApiException(500, "FILE_STORAGE_FAILED", "Impossible d'enregistrer le fichier. Réessayez.", "file");
        }
        SinistrePhoto piece = photoRepository.save(SinistrePhoto.builder()
                .sinistre(sinistre)
                .kind(kind)
                .fileName(StringUtils.hasText(fichier.getOriginalFilename()) ? fichier.getOriginalFilename() : (kind.equals("PHOTO") ? "photo" : "document"))
                .storedPath(chemin)
                .contentType(type.equals("PDF") ? "application/pdf" : type.equals("PNG") ? "image/png" : "image/jpeg")
                .sizeBytes(fichier.getSize())
                .uploadedBy(userRepository.findById(userId).orElse(null))
                .build());
        return new MobileClaimDtos.FileRef(String.valueOf(piece.getId()), kind, piece.getFileName(), piece.getSizeBytes(), piece.getCreatedAt());
    }

    /** Les photos du mobile portent kind = PHOTO ; les anciennes (kind null) du back-office comptent comme photos. */
    private long comptePhotos(Long sinistreId) {
        return photoRepository.findBySinistreIdOrderByCreatedAtAsc(sinistreId).stream()
                .filter(p -> p.getKind() == null || "PHOTO".equals(p.getKind())).count();
    }

    /** Type reel du fichier d'apres ses premiers octets (on ne fait pas confiance au Content-Type declare). */
    static String detecterType(MultipartFile fichier) {
        try (InputStream flux = fichier.getInputStream()) {
            byte[] tete = flux.readNBytes(8);
            if (tete.length >= 3 && (tete[0] & 0xFF) == 0xFF && (tete[1] & 0xFF) == 0xD8 && (tete[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }
            if (tete.length >= 8 && (tete[0] & 0xFF) == 0x89 && tete[1] == 'P' && tete[2] == 'N' && tete[3] == 'G') {
                return "PNG";
            }
            if (tete.length >= 4 && tete[0] == '%' && tete[1] == 'P' && tete[2] == 'D' && tete[3] == 'F') {
                return "PDF";
            }
        } catch (IOException e) {
            // illisible : traite comme non supporte
        }
        return "INCONNU";
    }

    // ------------------------------------------------------------------

    private List<Long> assuresDuCompte(Long userId) {
        return clientAccountInsuredRepository.findByUserId(userId).stream().map(ClientAccountInsured::getInsuredId).toList();
    }

    private Contract contratDuClient(Long userId, String contractId) {
        Long id;
        try {
            id = Long.valueOf(contractId.trim());
        } catch (NumberFormatException e) {
            throw new ContractNotFoundException();
        }
        List<Long> assures = assuresDuCompte(userId);
        return (assures.isEmpty() ? java.util.Optional.<Contract>empty() : contractRepository.findByIdAndInsuredIdIn(id, assures))
                .filter(c -> c.getStatus() != ci.assurzen.entities.ContractStatus.draft)
                .orElseThrow(ContractNotFoundException::new);
    }

    private Sinistre sinistreDuClient(Long userId, Long id) {
        List<Long> assures = assuresDuCompte(userId);
        return (assures.isEmpty() ? java.util.Optional.<Sinistre>empty() : sinistreRepository.findWithContractByIdAndInsuredIdIn(id, assures))
                .orElseThrow(() -> new MobileApiException(404, "CLAIM_NOT_FOUND", "Sinistre introuvable."));
    }

    /** Libelle francais d'un statut de sinistre (historique du suivi et notifications). */
    public static String libelleStatut(SinistreStatus statut) {
        return LIBELLES.get(statut);
    }

    static int etapeCourante(SinistreStatus statut) {
        return switch (statut) {
            case declare -> 0;
            case en_cours_analyse, pieces_complementaires_requises -> 1;
            case expertise_en_cours -> 2;
            case accepte, rejete -> 3;
            case indemnise, cloture -> 4;
        };
    }

    private MobileClaimDtos.Claim vue(Sinistre s) {
        int courante = etapeCourante(s.getStatus());
        boolean termine = s.getStatus() == SinistreStatus.indemnise || s.getStatus() == SinistreStatus.cloture;
        List<MobileClaimDtos.Step> etapes = new ArrayList<>();
        for (int i = 0; i < ETAPES.size(); i++) {
            String etat = termine || i < courante ? "DONE" : i == courante ? "CURRENT" : "TODO";
            etapes.add(new MobileClaimDtos.Step(ETAPES.get(i), etat));
        }
        List<MobileClaimDtos.HistoryEntry> historique = suiviRepository.findBySinistreIdOrderByCreatedAtAsc(s.getId()).stream()
                .map(h -> new MobileClaimDtos.HistoryEntry(h.getCreatedAt(), LIBELLES.get(h.getStatut())
                        + (StringUtils.hasText(h.getCommentaire()) && !"Sinistre declare".equals(h.getCommentaire())
                        && !h.getCommentaire().startsWith("Sinistre declare depuis") ? " - " + h.getCommentaire() : "")))
                .toList();
        Contract contrat = s.getContract();
        String libelleContrat = contrat == null ? null
                : (contrat.getProduct() == null ? "" : contrat.getProduct().getNomProduit() + " - ") + contrat.getPolicyNumber();
        boolean clos = s.getStatus() == SinistreStatus.cloture || s.getStatus() == SinistreStatus.rejete;
        long photos = comptePhotos(s.getId());
        long documents = photoRepository.countBySinistreIdAndKind(s.getId(), "DOCUMENT");
        return new MobileClaimDtos.Claim(String.valueOf(s.getId()), s.getNumero(), s.getNature(),
                contrat == null ? null : String.valueOf(contrat.getId()), libelleContrat, s.getDateDeclaration(), s.getDateSurvenance(),
                CODES.get(s.getStatus()), clos, courante, etapes, historique,
                !clos && s.getStatus() != SinistreStatus.indemnise, (int) photos, (int) documents);
    }
}
