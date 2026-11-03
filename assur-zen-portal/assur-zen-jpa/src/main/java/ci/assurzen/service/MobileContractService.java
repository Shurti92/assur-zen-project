package ci.assurzen.service;

import ci.assurzen.dto.MobileContractDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.ContractCustomerRequest;
import ci.assurzen.exception.ContractNotActiveException;
import ci.assurzen.exception.ContractNotFoundException;
import ci.assurzen.repositories.ContractCustomerRequestRepository;
import ci.assurzen.services.TelegramNotificationService;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lecture des contrats par l'assure connecte (application mobile, lot b, 2026-11-02). Le client ne voit que les
 * contrats des assures lies a son compte (ClientAccountInsured) ; un contrat d'un tiers est un 404 identique a un
 * contrat inexistant. Le serveur decide du statut, de l'eligibilite au renouvellement et des actions possibles.
 *
 * Statuts : brouillon -&gt; invisible ; en attente -&gt; PENDING ; annule -&gt; TERMINATED ; signe -&gt; ACTIVE, ou
 * EXPIRED si la date d'echeance est depassee (SUSPENDED n'existe pas dans le modele actuel).
 * Non fournis pour l'instant (valeurs vides, pas inventees) : garanties, documents et attestation numerique.
 */
@Service
@RequiredArgsConstructor
public class MobileContractService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final Set<String> STATUTS = Set.of("PENDING", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED");
    private static final long FENETRE_RENOUVELLEMENT_PAR_DEFAUT = 30;

    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final ContractRepository contractRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final ContractCustomerRequestRepository requestRepository;
    private final TelegramNotificationService telegramNotificationService;
    private final MobileProductContentService produitContenu;

    /** Paiements mobile actifs (simulation en dev) : alors seulement, renewal.payableReference est renseigne. */
    @org.springframework.beans.factory.annotation.Value("${mobile.payments.simulated:false}")
    private boolean paiementsActifs;

    @Transactional(readOnly = true)
    public MobileContractDtos.ContractList lister(Long userId, String statutDemande) {
        String filtre = StringUtils.hasText(statutDemande) ? statutDemande.trim().toUpperCase(Locale.ROOT) : null;
        if (filtre != null && !STATUTS.contains(filtre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status doit valoir PENDING, ACTIVE, SUSPENDED, EXPIRED ou TERMINATED");
        }
        List<Long> assures = assuresDuCompte(userId);
        if (assures.isEmpty()) {
            return new MobileContractDtos.ContractList(List.of());
        }
        LocalDate aujourdhui = LocalDate.now(ABIDJAN);
        List<MobileContractDtos.ContractListItem> items = contractRepository.findByInsuredIdIn(assures).stream()
                .filter(c -> c.getStatus() != ContractStatus.draft)
                .filter(c -> filtre == null || filtre.equals(statut(c, aujourdhui)))
                .sorted(Comparator.comparing((Contract c) -> c.getDateEcheance(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> versListe(c, aujourdhui))
                .toList();
        return new MobileContractDtos.ContractList(items);
    }

    @Transactional(readOnly = true)
    public MobileContractDtos.ContractDetail detail(Long userId, Long contractId) {
        List<Long> assures = assuresDuCompte(userId);
        Contract contrat = assures.isEmpty() ? null : contractRepository.findByIdAndInsuredIdIn(contractId, assures).orElse(null);
        if (contrat == null || contrat.getStatus() == ContractStatus.draft) {
            throw new ContractNotFoundException();
        }
        return versDetail(contrat, LocalDate.now(ABIDJAN));
    }

    /**
     * Demande de modification ou de resiliation d'un contrat ACTIF (404 pour un contrat d'un tiers, 409 si le contrat
     * n'est pas actif). Idempotente : une demande du meme type encore en attente renvoie la meme reference. La demande
     * est enregistree pour traitement par l'equipe ; aucune modification du contrat n'est faite ici.
     */
    @Transactional
    public ci.assurzen.dto.MobileContractRequestDtos.CreatedResponse demander(Long userId, Long contractId, String type, String message) {
        List<Long> assures = assuresDuCompte(userId);
        Contract contrat = assures.isEmpty() ? null : contractRepository.findByIdAndInsuredIdIn(contractId, assures).orElse(null);
        if (contrat == null || contrat.getStatus() == ContractStatus.draft) {
            throw new ContractNotFoundException();
        }
        if (!"ACTIVE".equals(statut(contrat, LocalDate.now(ABIDJAN)))) {
            throw new ContractNotActiveException();
        }
        ContractCustomerRequest existante = requestRepository
                .findFirstByContractIdAndTypeAndStatutOrderByCreatedAtDesc(contractId, type, "EN_ATTENTE").orElse(null);
        if (existante != null) {
            return new ci.assurzen.dto.MobileContractRequestDtos.CreatedResponse(existante.getReference(), delaiReponseHeures());
        }
        ContractCustomerRequest demande = new ContractCustomerRequest();
        demande.setContractId(contractId);
        demande.setUserId(userId);
        demande.setType(type);
        demande.setMessage(StringUtils.hasText(message) ? message.trim() : null);
        demande.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        demande = requestRepository.saveAndFlush(demande);
        demande.setReference(String.format("RQ-%d-%06d", java.time.Year.now().getValue(), demande.getId()));
        requestRepository.save(demande);
        try {
            telegramNotificationService.envoyer("<b>Demande de " + ("TERMINATION".equals(type) ? "resiliation" : "modification")
                    + " (mobile)</b>\nContrat " + TelegramNotificationService.echapperHtml(String.valueOf(contrat.getPolicyNumber()))
                    + " - reference " + demande.getReference());
        } catch (RuntimeException e) {
            // notification non bloquante
        }
        return new ci.assurzen.dto.MobileContractRequestDtos.CreatedResponse(demande.getReference(), delaiReponseHeures());
    }

    // ------------------------------------------------------------------

    /** Delai annonce pour une demande de contrat : cle CONTRACT_REQUEST_RESPONSE_HOURS, null si non configure. */
    private Integer delaiReponseHeures() {
        long heures = dynamicConfigurationService.getLong("CONTRACT_REQUEST_RESPONSE_HOURS", 0L);
        return heures > 0 ? (int) heures : null;
    }

    private List<Long> assuresDuCompte(Long userId) {
        return clientAccountInsuredRepository.findByUserId(userId).stream().map(ClientAccountInsured::getInsuredId).toList();
    }

    static String statut(Contract contrat, LocalDate aujourdhui) {
        ContractStatus interne = contrat.getStatus();
        if (interne == ContractStatus.cancelled) {
            return "TERMINATED";
        }
        if (interne == ContractStatus.pending || interne == ContractStatus.draft) {
            return "PENDING";
        }
        if (contrat.getDateEcheance() != null && aujourdhui.isAfter(contrat.getDateEcheance())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private long fenetreRenouvellement() {
        return dynamicConfigurationService.getLong("MOBILE_RENEWAL_WINDOW_DAYS", FENETRE_RENOUVELLEMENT_PAR_DEFAUT);
    }

    private MobileContractDtos.RenewalWindow fenetre(Contract contrat, String statut, LocalDate aujourdhui) {
        LocalDate echeance = contrat.getDateEcheance();
        if (echeance == null) {
            return new MobileContractDtos.RenewalWindow(false, null, null);
        }
        long fenetre = fenetreRenouvellement();
        long joursRestants = ChronoUnit.DAYS.between(aujourdhui, echeance);
        boolean dejaRenouvele = contractRepository.findByContratPrecedentId(contrat.getId()).isPresent();
        boolean eligible = "ACTIVE".equals(statut) && joursRestants >= 0 && joursRestants <= fenetre && !dejaRenouvele;
        return new MobileContractDtos.RenewalWindow(eligible, echeance.minusDays(fenetre), joursRestants);
    }

    private MobileContractDtos.ContractListItem versListe(Contract contrat, LocalDate aujourdhui) {
        String statut = statut(contrat, aujourdhui);
        return new MobileContractDtos.ContractListItem(
                String.valueOf(contrat.getId()),
                contrat.getPolicyNumber(),
                identifiantProduit(contrat),
                contrat.getProduct() == null ? null : contrat.getProduct().getNomProduit(),
                libelleAssure(contrat),
                statut,
                contrat.getDateEffet(),
                contrat.getDateEcheance(),
                contrat.getPremium(),
                fenetre(contrat, statut, aujourdhui),
                miseAJour(contrat));
    }

    private MobileContractDtos.ContractDetail versDetail(Contract contrat, LocalDate aujourdhui) {
        String statut = statut(contrat, aujourdhui);
        MobileContractDtos.RenewalWindow fenetre = fenetre(contrat, statut, aujourdhui);
        MobileContractDtos.RenewalOffer renouvellement = fenetre.eligible()
                ? new MobileContractDtos.RenewalOffer(true, fenetre.opensOn(), fenetre.daysLeft(), contrat.getPremium(),
                dureeEnMois(contrat.getDuree()),
                paiementsActifs ? MobilePaymentService.referenceRenouvellement(contrat) : null)
                : null;
        boolean actif = "ACTIVE".equals(statut);
        // Sinistre : aussi sur un contrat echu (declaration tardive d'un sinistre survenu pendant la couverture, 2026-11-03).
        boolean sinistrePossible = actif || "EXPIRED".equals(statut);
        Insured assure = contrat.getInsured();
        String titulaire = assure == null ? null : (nonNull(assure.getPrenoms()) + " " + nonNull(assure.getNom())).trim();
        return new MobileContractDtos.ContractDetail(
                String.valueOf(contrat.getId()),
                contrat.getPolicyNumber(),
                identifiantProduit(contrat),
                contrat.getProduct() == null ? null : contrat.getProduct().getNomProduit(),
                libelleAssure(contrat),
                titulaire,
                statut,
                contrat.getDateEffet(),
                contrat.getDateEcheance(),
                contrat.getPremium(),
                garantiesDuProduit(contrat),
                List.of(),
                null,
                renouvellement,
                new MobileContractDtos.Actions(sinistrePossible, actif, actif),
                miseAJour(contrat));
    }

    /** Garanties parametrees depuis le back-office pour le produit du contrat (liste vide si non renseignees). */
    private List<MobileContractDtos.Guarantee> garantiesDuProduit(Contract contrat) {
        if (contrat.getProduct() == null) {
            return List.of();
        }
        return produitContenu.garanties(contrat.getProduct().getId()).stream()
                .map(g -> new MobileContractDtos.Guarantee(g.name(), g.kind(), null)).toList();
    }

    /** Vehicule pour un contrat auto/moto ("Toyota Corolla · 1234 AB 01"), sinon nom de l'assure. */
    private String libelleAssure(Contract contrat) {
        Vehicule vehicule = contrat.getVehicule();
        if (vehicule != null) {
            String modele = (nonNull(vehicule.getMarque()) + " " + nonNull(vehicule.getModele())).trim();
            String immat = nonNull(vehicule.getImmatriculation()).trim();
            if (!modele.isEmpty() && !immat.isEmpty()) {
                return modele + " · " + immat;
            }
            if (!modele.isEmpty() || !immat.isEmpty()) {
                return modele.isEmpty() ? immat : modele;
            }
        }
        Insured assure = contrat.getInsured();
        return assure == null ? null : (nonNull(assure.getPrenoms()) + " " + nonNull(assure.getNom())).trim();
    }

    /** Identifiant stable lisible du produit : "Assurance Auto" -&gt; "auto", "Assurance Automobile VTC" -&gt; "automobile-vtc". */
    static String identifiantProduit(Contract contrat) {
        if (contrat.getProduct() == null || !StringUtils.hasText(contrat.getProduct().getNomProduit())) {
            return null;
        }
        String nom = Normalizer.normalize(contrat.getProduct().getNomProduit(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        nom = nom.replaceFirst("^assurance\\s+", "");
        return nom.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private Integer dureeEnMois(String duree) {
        if (duree != null) {
            String chiffres = duree.replaceAll("\\D", "");
            if (!chiffres.isEmpty()) {
                return Integer.valueOf(chiffres);
            }
        }
        return 12;
    }

    private java.time.Instant miseAJour(Contract contrat) {
        return contrat.getLastUpdate() != null ? contrat.getLastUpdate() : contrat.getCreatedAt();
    }

    private String nonNull(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
