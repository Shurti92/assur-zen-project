package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PrimeObjectifRequest;
import ci.assurzen.dto.PrimeObjectifResponse;
import ci.assurzen.dto.PrimeObjectifSimulationResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PrimeObjectif;
import ci.assurzen.entities.StatutPrimeObjectif;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.PrimeObjectifRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prime versee a un partenaire ayant atteint/depasse son objectif de production annuel (contrat
 * "Prime d'objectif partenaire", 2026-08-28). Reutilise le calcul objectif/realise/taux deja etabli
 * par PartenaireService (une seule source de verite), et le barema est configurable via 6 cles
 * dynamiques (module Configuration existant, pas de nouvelle table de parametrage) :
 * PRIME_OBJECTIF_SEUIL_BASE/TAUX_BASE/SEUIL_PALIER2/TAUX_PALIER2/SEUIL_PALIER3/TAUX_PALIER3.
 *
 * Formule (taux de realisation = realise / objectif x 100), effet de seuil assume (chaque palier
 * s'applique a TOUT le depassement depuis l'objectif de base, pas seulement depuis son propre seuil -
 * decision produit confirmee le 2026-08-28, pas une progression lissee type bareme d'impot) :
 * - taux &lt; SEUIL_BASE            -&gt; aucune prime
 * - SEUIL_BASE &lt;= taux &lt; SEUIL_PALIER2  -&gt; TAUX_BASE% x objectif
 * - SEUIL_PALIER2 &lt;= taux &lt; SEUIL_PALIER3 -&gt; TAUX_BASE% x objectif + TAUX_PALIER2% x (realise - objectif)
 * - taux &gt;= SEUIL_PALIER3         -&gt; TAUX_BASE% x objectif + TAUX_PALIER3% x (realise - objectif)
 *
 * Reservee aux administrateurs (SecurityConfig : /api-assur-zen/primes-objectif/** en hasRole(ADMIN)),
 * meme protection en double au niveau service (requireAdmin(), meme convention que PartenaireService).
 */
@Service
@RequiredArgsConstructor
public class PrimeObjectifService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final String TEMPLATE_PRIME_OBJECTIF_PAYEE = "PRIME_OBJECTIF_PAYEE";

    private final PrimeObjectifRepository repository;
    private final PartenaireRepository partenaireRepository;
    private final UserRepository userRepository;
    private final PartenaireService partenaireService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final LedgerService ledgerService;
    private final SmsNotificationService smsNotificationService;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PrimeObjectifSimulationResponse simuler(Long partenaireId) {
        requireAdmin();
        Partenaire partenaire = resolvePartenaire(partenaireId);
        ExerciceComptable exercice = partenaire.getExerciceComptable();

        BigDecimal objectif = partenaire.getObjectifProduction();
        BigDecimal realise = partenaireService.computeObjectifRealise(partenaire);
        BigDecimal taux = partenaireService.computeTauxRealisation(realise, objectif);

        boolean primeActivee = partenaire.isPrimeObjectifActivee();
        boolean exerciceCloture = exercice != null && exercice.isCloture();
        boolean objectifAtteint = taux != null && taux.compareTo(seuilBase()) >= 0;
        boolean demandeDejaExistante = exercice != null
                && repository.findByPartenaireIdAndExerciceComptableId(partenaireId, exercice.getId()).isPresent();

        Long montantSimule = (taux == null || objectif == null) ? 0L : calculerMontant(objectif, realise, taux);

        return new PrimeObjectifSimulationResponse(
                partenaireId,
                exercice == null ? null : exercice.getId(),
                exercice == null ? null : exercice.getLibelle(),
                objectif,
                realise,
                taux,
                montantSimule,
                primeActivee,
                exerciceCloture,
                objectifAtteint,
                demandeDejaExistante,
                primeActivee && exerciceCloture && objectifAtteint && !demandeDejaExistante
        );
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "PRIME_OBJECTIF", entityName = "PrimeObjectif")
    public PrimeObjectifResponse demander(PrimeObjectifRequest request) {
        requireAdmin();
        Partenaire partenaire = resolvePartenaire(request.partenaireId());

        if (!partenaire.isPrimeObjectifActivee()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La prime d'objectif n'est pas activee pour ce partenaire");
        }

        ExerciceComptable exercice = partenaire.getExerciceComptable();
        if (exercice == null || !exercice.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "L'exercice comptable de ce partenaire n'est pas encore cloture");
        }

        if (repository.findByPartenaireIdAndExerciceComptableId(partenaire.getId(), exercice.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une demande de prime d'objectif existe deja pour ce partenaire sur l'exercice " + exercice.getLibelle());
        }

        BigDecimal objectif = partenaire.getObjectifProduction();
        BigDecimal realise = partenaireService.computeObjectifRealise(partenaire);
        // Borne AVANT persistance (jamais dans simuler() ci-dessus, purement transitoire) : un
        // objectif fixe anormalement bas produit sinon un taux de plusieurs millions de %, hors de
        // portee de la colonne taux_realisation (DECIMAL(7,2)) - Data truncation SQL, meme bug reel
        // que CommercialPerformance.achievementRate constate le 2026-09-17. Le seuil d'eligibilite
        // (seuilBase(), typiquement 100%) reste toujours atteint apres bornage.
        BigDecimal taux = ci.assurzen.utils.PercentageUtils.borner(
                partenaireService.computeTauxRealisation(realise, objectif), 7, 2);

        if (taux == null || taux.compareTo(seuilBase()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Objectif non atteint" + (taux == null ? "" : " (" + taux + "% < " + seuilBase() + "%)"));
        }

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User demandePar = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        PrimeObjectif prime = PrimeObjectif.builder()
                .reference(codeGeneratorService.generate(CodePrefix.PRIME_OBJECTIF))
                .partenaire(partenaire)
                .exerciceComptable(exercice)
                .objectifProduction(objectif)
                .objectifRealise(realise)
                .tauxRealisation(taux)
                .montant(calculerMontant(objectif, realise, taux))
                .statut(StatutPrimeObjectif.EN_ATTENTE)
                .demandePar(demandePar)
                .build();

        return toResponse(repository.save(prime));
    }

    public PageResponse<PrimeObjectifResponse> list(Long partenaireId, StatutPrimeObjectif statut, Integer page, Integer size) {
        requireAdmin();
        List<PrimeObjectifResponse> items = repository.findAll(Sort.by(Sort.Direction.DESC, "dateDemande")).stream()
                .filter(p -> partenaireId == null || p.getPartenaire().getId().equals(partenaireId))
                .filter(p -> statut == null || p.getStatut() == statut)
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    /**
     * Transitions autorisees : EN_ATTENTE -> VALIDEE|REJETEE, VALIDEE -> PAYEE|REJETEE, PAYEE/REJETEE
     * terminaux (meme structure que FideliteService.validerTransition). PAYEE declenche l'ecriture
     * comptable (LedgerService.enregistrerPrimeObjectif) et un SMS de confirmation au partenaire
     * (echec d'envoi non bloquant, meme convention que CommissionService/PartenaireService).
     */
    @Transactional
    public PrimeObjectifResponse changerStatut(Long id, StatutPrimeObjectif nouveauStatut, String motifRejet) {
        requireAdmin();
        PrimeObjectif prime = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prime d'objectif introuvable: " + id));
        PrimeObjectifResponse avant = toResponse(prime);

        validerTransition(prime.getStatut(), nouveauStatut);

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User utilisateur = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        switch (nouveauStatut) {
            case VALIDEE -> {
                prime.setValidePar(utilisateur);
                prime.setDateValidation(Instant.now());
            }
            case REJETEE -> {
                if (!StringUtils.hasText(motifRejet)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de rejet est obligatoire");
                }
                prime.setMotifRejet(motifRejet);
            }
            case PAYEE -> {
                prime.setPayePar(utilisateur);
                prime.setDatePaiement(Instant.now());
                ledgerService.enregistrerPrimeObjectif(nomPartenaire(prime.getPartenaire()), prime.getMontant());
                prime.setNotificationEnvoyee(notifierPartenaire(prime));
            }
            case EN_ATTENTE -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition vers EN_ATTENTE non autorisee");
        }
        prime.setStatut(nouveauStatut);

        PrimeObjectifResponse apres = toResponse(repository.save(prime));
        functionalAuditRecorder.recordUpdate("PRIME_OBJECTIF", "PrimeObjectif", String.valueOf(id), avant, apres, motifRejet);
        return apres;
    }

    private void validerTransition(StatutPrimeObjectif actuel, StatutPrimeObjectif cible) {
        boolean valide = switch (actuel) {
            case EN_ATTENTE -> cible == StatutPrimeObjectif.VALIDEE || cible == StatutPrimeObjectif.REJETEE;
            case VALIDEE -> cible == StatutPrimeObjectif.PAYEE || cible == StatutPrimeObjectif.REJETEE;
            case PAYEE, REJETEE -> false;
        };
        if (!valide) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition " + actuel + " -> " + cible + " non autorisee");
        }
    }

    /** Echec d'envoi non bloquant (sendFromTemplate ne leve jamais) - la prime reste PAYEE meme si le SMS echoue. */
    private boolean notifierPartenaire(PrimeObjectif prime) {
        Partenaire partenaire = prime.getPartenaire();
        return smsNotificationService.sendFromTemplate(
                partenaire.getTelephone(),
                TEMPLATE_PRIME_OBJECTIF_PAYEE,
                Map.of(
                        "montant", formatMontant(prime.getMontant()),
                        "exercice", prime.getExerciceComptable().getLibelle(),
                        "reference", prime.getReference()
                ),
                TypeMessageSms.prime_objectif_payee
        );
    }

    /** Regroupement par milliers avec un espace simple, meme raison que CommissionService.formatMontant
     * (pas le NBSP de Locale.FRANCE, plus sur pour un SMS GSM-7). */
    private String formatMontant(long montant) {
        String brut = String.valueOf(montant);
        StringBuilder resultat = new StringBuilder();
        int compteur = 0;
        for (int i = brut.length() - 1; i >= 0; i--) {
            resultat.append(brut.charAt(i));
            compteur++;
            if (compteur % 3 == 0 && i != 0) {
                resultat.append(' ');
            }
        }
        return resultat.reverse().toString();
    }

    private String nomPartenaire(Partenaire partenaire) {
        return partenaire.getPrenoms() + " " + partenaire.getNom();
    }

    private Long calculerMontant(BigDecimal objectif, BigDecimal realise, BigDecimal taux) {
        if (objectif == null || realise == null || taux == null || taux.compareTo(seuilBase()) < 0) {
            return 0L;
        }

        BigDecimal tauxBase = tauxBase();
        BigDecimal primeBase = objectif.multiply(tauxBase).divide(CENT, 4, RoundingMode.HALF_UP);

        if (taux.compareTo(seuilPalier2()) < 0) {
            return arrondir(primeBase);
        }

        BigDecimal depassement = realise.subtract(objectif);
        BigDecimal tauxDepassement = taux.compareTo(seuilPalier3()) >= 0 ? tauxPalier3() : tauxPalier2();
        BigDecimal primeDepassement = depassement.multiply(tauxDepassement).divide(CENT, 4, RoundingMode.HALF_UP);

        return arrondir(primeBase.add(primeDepassement));
    }

    private long arrondir(BigDecimal montant) {
        return montant.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal seuilBase() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_SEUIL_BASE");
    }

    private BigDecimal tauxBase() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_TAUX_BASE");
    }

    private BigDecimal seuilPalier2() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_SEUIL_PALIER2");
    }

    private BigDecimal tauxPalier2() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_TAUX_PALIER2");
    }

    private BigDecimal seuilPalier3() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_SEUIL_PALIER3");
    }

    private BigDecimal tauxPalier3() {
        return dynamicConfigurationService.getDecimal("PRIME_OBJECTIF_TAUX_PALIER3");
    }

    private void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private Partenaire resolvePartenaire(Long partenaireId) {
        return partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
    }

    private PrimeObjectifResponse toResponse(PrimeObjectif prime) {
        Partenaire partenaire = prime.getPartenaire();
        ExerciceComptable exercice = prime.getExerciceComptable();
        return new PrimeObjectifResponse(
                prime.getId(),
                prime.getReference(),
                partenaire.getId(),
                nomPartenaire(partenaire),
                exercice.getId(),
                exercice.getLibelle(),
                prime.getObjectifProduction(),
                prime.getObjectifRealise(),
                prime.getTauxRealisation(),
                prime.getMontant(),
                prime.getStatut(),
                prime.getDateDemande(),
                prime.getDemandePar() == null ? null : prime.getDemandePar().getId(),
                prime.getValidePar() == null ? null : prime.getValidePar().getId(),
                prime.getDateValidation(),
                prime.getPayePar() == null ? null : prime.getPayePar().getId(),
                prime.getDatePaiement(),
                prime.getMotifRejet(),
                prime.getNotificationEnvoyee()
        );
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
}
