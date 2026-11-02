package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.dto.EligibiliteVerificationItem;
import ci.assurzen.dto.HistoriqueParrainageResponse;
import ci.assurzen.dto.ControleAntifraudeParrainageResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ParrainageContratRequest;
import ci.assurzen.dto.ParrainageInscriptionRequest;
import ci.assurzen.dto.ParrainageRequest;
import ci.assurzen.dto.ParrainageResponse;
import ci.assurzen.dto.ParrainageValidationResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CodeParrainage;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ControleAntifraudeParrainage;
import ci.assurzen.entities.HistoriqueParrainage;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Parrainage;
import ci.assurzen.entities.ProgrammeParrainage;
import ci.assurzen.entities.StatutControleFraude;
import ci.assurzen.entities.StatutParrainage;
import ci.assurzen.entities.StatutProgrammeParrainage;
import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.entities.TypeControleFraude;
import ci.assurzen.entities.TypeEvenementParrainage;
import ci.assurzen.entities.User;
import ci.assurzen.dto.MoEnvoiMessage;
import ci.assurzen.dto.SmsSendResult;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.exception.SmsSendException;
import ci.assurzen.repositories.CodeParrainageRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ControleAntifraudeParrainageRepository;
import ci.assurzen.repositories.HistoriqueParrainageRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ParrainageRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.ProgrammeParrainageRepository;
import ci.assurzen.repositories.RecompenseParrainageRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur central de parrainage : creation, invitation, rattachement du filleul/contrat, validation
 * d'eligibilite et anti-fraude. La generation des recompenses est deleguee a
 * RecompenseParrainageEngine ; ce service pilote le cycle de vie du Parrainage lui-meme.
 *
 * valider() est concu pour etre rappele plusieurs fois (aucun evenement contrat n'existe dans ce
 * monolithe) : si un contrat precedemment signe devient annule, un nouvel appel a valider() detecte
 * l'ecart et reverse automatiquement toute recompense deja attribuee.
 */
@Service
@RequiredArgsConstructor
public class ParrainageService {

    private static final List<StatutParrainage> STATUTS_ACTIFS_POUR_QUOTA = List.of(
            StatutParrainage.ELIGIBLE, StatutParrainage.CONTRAT_CREE, StatutParrainage.CONTRAT_ACTIF,
            StatutParrainage.RECOMPENSE_EN_ATTENTE, StatutParrainage.RECOMPENSE_ATTRIBUEE);

    private static final List<StatutParrainage> STATUTS_NON_BLOQUANTS_POUR_QUOTA = List.of(
            StatutParrainage.REJETE, StatutParrainage.ANNULE, StatutParrainage.EXPIRE);

    private final ParrainageRepository parrainageRepository;
    private final ProgrammeParrainageRepository programmeParrainageRepository;
    private final CodeParrainageRepository codeParrainageRepository;
    private final CodeParrainageService codeParrainageService;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final HistoriqueParrainageRepository historiqueParrainageRepository;
    private final ControleAntifraudeParrainageRepository controleAntifraudeParrainageRepository;
    private final RecompenseParrainageRepository recompenseParrainageRepository;
    private final RecompenseParrainageEngine recompenseParrainageEngine;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final SmsSenderService smsSenderService;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final DynamicConfigurationService dynamicConfigurationService;

    public PageResponse<ParrainageResponse> search(StatutParrainage statut, Long programmeId, Long parrainId, Integer page, Integer size) {
        List<ParrainageResponse> items = parrainageRepository.findAll().stream()
                .filter(p -> statut == null || p.getStatut() == statut)
                .filter(p -> programmeId == null || (p.getProgramme() != null && programmeId.equals(p.getProgramme().getId())))
                .filter(p -> parrainId == null || (p.getParrain() != null && parrainId.equals(p.getParrain().getId())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public ParrainageResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public ParrainageResponse creer(ParrainageRequest request) {
        CodeParrainage codeParrainage = null;
        Insured parrain;
        ProgrammeParrainage programme;

        if (StringUtils.hasText(request.code())) {
            codeParrainage = codeParrainageService.getEntity(request.code());
            codeParrainageService.verifierUtilisable(codeParrainage);
            parrain = codeParrainage.getParrain();
            programme = codeParrainage.getProgramme();
        } else {
            if (request.parrainInsuredId() == null || request.programmeId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fournir soit un code de parrainage, soit parrainInsuredId + programmeId");
            }
            parrain = resolveInsured(request.parrainInsuredId());
            programme = resolveProgramme(request.programmeId());
        }

        verifierProgrammeActif(programme);
        verifierQuotaEtCarence(parrain, programme);

        Instant maintenant = Instant.now();
        Parrainage parrainage = Parrainage.builder()
                .reference(codeGeneratorService.generate(CodePrefix.PARRAINAGE))
                .programme(programme)
                .codeParrainage(codeParrainage)
                .parrain(parrain)
                .filleulNom(request.filleulNom())
                .filleulEmail(request.filleulEmail())
                .filleulTelephone(request.filleulTelephone())
                .statut(StatutParrainage.CREE)
                .dateExpiration(programme.getDureeValiditeJours() != null
                        ? maintenant.plus(programme.getDureeValiditeJours(), ChronoUnit.DAYS) : null)
                .build();
        parrainage = parrainageRepository.save(parrainage);

        if (codeParrainage != null) {
            codeParrainageService.enregistrerUtilisation(codeParrainage);
        }

        enregistrerHistorique(parrainage, TypeEvenementParrainage.CREATION, null, StatutParrainage.CREE, "Parrainage cree");

        boolean rejete = executerControlesAntifraude(parrainage).stream()
                .anyMatch(c -> c.getStatut() == StatutControleFraude.ECHEC);
        if (rejete) {
            StatutParrainage ancien = parrainage.getStatut();
            parrainage.setStatut(StatutParrainage.REJETE);
            parrainage.setMotifRejet("Rejete par le controle anti-fraude a la creation");
            parrainage = parrainageRepository.save(parrainage);
            enregistrerHistorique(parrainage, TypeEvenementParrainage.REJET, ancien, StatutParrainage.REJETE, "Controle anti-fraude echoue");
        }

        return toResponse(parrainage);
    }

    /**
     * Pas de @Transactional ici : sur echec SMS, l'historique doit persister meme si la methode se
     * termine par une exception. Sous @Transactional, le rollback implicite sur RuntimeException
     * effacerait silencieusement l'entree d'historique enregistree juste avant le throw.
     */
    public ParrainageResponse inviter(Long id) {
        Parrainage parrainage = getEntity(id);
        if (parrainage.getStatut() == StatutParrainage.REJETE || parrainage.getStatut() == StatutParrainage.ANNULE
                || parrainage.getStatut() == StatutParrainage.EXPIRE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce parrainage n'est plus actif: " + parrainage.getStatut());
        }
        if (!StringUtils.hasText(parrainage.getFilleulTelephone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun numero de telephone connu pour le filleul");
        }
        if (!dynamicConfigurationService.getBoolean("SMS_ENABLED", true)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envoi de SMS desactive (configuration SMS_ENABLED)");
        }

        String message = "AssurZen : " + insuredName(parrainage.getParrain()) + " vous invite a le/la parrainer. "
                + "Souscrivez avec le code " + (parrainage.getCodeParrainage() != null ? parrainage.getCodeParrainage().getCode() : parrainage.getReference())
                + " et profitez d'une offre de bienvenue.";

        try {
            SmsSendResult result = smsSenderService.envoiMessageAuNumero(new MoEnvoiMessage(parrainage.getFilleulTelephone(), message));
            if (!result.success()) {
                enregistrerHistorique(parrainage, TypeEvenementParrainage.INVITATION_ENVOYEE, parrainage.getStatut(), parrainage.getStatut(),
                        "Echec fournisseur SMS (HTTP " + result.httpStatus() + ")");
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Echec de l'envoi de l'invitation SMS");
            }
        } catch (SmsSendException e) {
            enregistrerHistorique(parrainage, TypeEvenementParrainage.INVITATION_ENVOYEE, parrainage.getStatut(), parrainage.getStatut(),
                    "Erreur technique SMS: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Echec technique de l'envoi de l'invitation SMS");
        }

        StatutParrainage ancien = parrainage.getStatut();
        parrainage.setDateInvitation(Instant.now());
        if (parrainage.getStatut() == StatutParrainage.CREE) {
            parrainage.setStatut(StatutParrainage.INVITE);
        }
        parrainage = parrainageRepository.save(parrainage);
        enregistrerHistorique(parrainage, TypeEvenementParrainage.INVITATION_ENVOYEE, ancien, parrainage.getStatut(), "Invitation SMS envoyee");

        return toResponse(parrainage);
    }

    @Transactional
    public ParrainageResponse inscrireFilleul(Long id, ParrainageInscriptionRequest request) {
        Parrainage parrainage = getEntity(id);
        Insured filleul = resolveInsured(request.filleulInsuredId());

        if (filleul.getId().equals(parrainage.getParrain().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un client ne peut pas se parrainer lui-meme");
        }

        StatutParrainage ancien = parrainage.getStatut();
        parrainage.setFilleul(filleul);
        parrainage.setDateInscription(Instant.now());
        if (ancien == StatutParrainage.CREE || ancien == StatutParrainage.INVITE) {
            parrainage.setStatut(StatutParrainage.INSCRIT);
        }
        parrainage = parrainageRepository.save(parrainage);
        enregistrerHistorique(parrainage, TypeEvenementParrainage.FILLEUL_INSCRIT, ancien, parrainage.getStatut(),
                "Filleul inscrit : " + insuredName(filleul));

        executerControlesAntifraude(parrainage);

        return toResponse(parrainage);
    }

    @Transactional
    public ParrainageResponse rattacherContrat(Long id, ParrainageContratRequest request) {
        Parrainage parrainage = getEntity(id);
        Contract contrat = contractRepository.findById(request.contratId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + request.contratId()));

        if (parrainage.getFilleul() == null) {
            parrainage.setFilleul(contrat.getInsured());
        } else if (contrat.getInsured() != null && !contrat.getInsured().getId().equals(parrainage.getFilleul().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce contrat n'appartient pas au filleul de ce parrainage");
        }

        StatutParrainage ancien = parrainage.getStatut();
        parrainage.setContrat(contrat);
        parrainage.setStatut(contrat.getStatus() == ContractStatus.signed ? StatutParrainage.CONTRAT_ACTIF : StatutParrainage.CONTRAT_CREE);
        parrainage = parrainageRepository.save(parrainage);
        enregistrerHistorique(parrainage, TypeEvenementParrainage.CONTRAT_RATTACHE, ancien, parrainage.getStatut(),
                "Contrat rattache : " + contrat.getPolicyNumber());

        return toResponse(parrainage);
    }

    /** Reevalue l'eligibilite complete d'un parrainage. Idempotent : peut etre rappele apres annulation d'un contrat pour reverser une recompense deja attribuee. */
    @Transactional
    public ParrainageValidationResponse valider(Long id) {
        Parrainage parrainage = getEntity(id);
        List<EligibiliteVerificationItem> verifications = new ArrayList<>();

        boolean programmeActif = estProgrammeActif(parrainage.getProgramme());
        verifications.add(new EligibiliteVerificationItem("Programme actif", programmeActif,
                programmeActif ? "Le programme est actif et dans sa periode de validite" : "Programme inactif ou hors periode"));

        boolean parrainActif = estClientActif(parrainage.getParrain());
        verifications.add(new EligibiliteVerificationItem("Parrain actif", parrainActif,
                parrainActif ? "Le parrain possede au moins un contrat signe" : "Le parrain n'a aucun contrat signe"));

        boolean filleulNouveauClient = parrainage.getFilleul() != null && estNouveauClient(parrainage);
        verifications.add(new EligibiliteVerificationItem("Filleul nouveau client", filleulNouveauClient,
                filleulNouveauClient ? "Le filleul n'avait aucun contrat signe avant ce parrainage" : "Filleul inconnu ou deja client avant ce parrainage"));

        boolean contratActif = parrainage.getContrat() != null && parrainage.getContrat().getStatus() == ContractStatus.signed;
        verifications.add(new EligibiliteVerificationItem("Contrat actif", contratActif,
                contratActif ? "Le contrat du filleul est signe" : "Aucun contrat signe rattache"));

        boolean produitEligible = contratActif && produitEligible(parrainage.getProgramme(), parrainage.getContrat());
        verifications.add(new EligibiliteVerificationItem("Produit eligible", produitEligible,
                produitEligible ? "Le produit souscrit est eligible au programme" : "Produit non eligible au programme"));

        boolean primeMinimumRespectee = !contratActif || parrainage.getProgramme().getPrimeMinimum() == null
                || (parrainage.getContrat().getPremium() != null && parrainage.getContrat().getPremium() >= parrainage.getProgramme().getPrimeMinimum());
        verifications.add(new EligibiliteVerificationItem("Prime minimum respectee", primeMinimumRespectee,
                primeMinimumRespectee ? "Seuil de prime respecte" : "Prime du contrat sous le seuil requis"));

        boolean quotaRespecte = quotaRespecte(parrainage.getParrain(), parrainage.getProgramme(), parrainage.getId());
        verifications.add(new EligibiliteVerificationItem("Quota parrain non depasse", quotaRespecte,
                quotaRespecte ? "Le parrain n'a pas depasse son quota" : "Quota de parrainages atteint pour ce parrain"));

        boolean eligible = verifications.stream().allMatch(EligibiliteVerificationItem::satisfait);

        if (eligible && estStatutReevaluable(parrainage.getStatut())) {
            marquerEligibleEtGenererRecompenses(parrainage);
        } else if (!contratActif && parrainage.getContrat() != null
                && (parrainage.getStatut() == StatutParrainage.RECOMPENSE_EN_ATTENTE || parrainage.getStatut() == StatutParrainage.RECOMPENSE_ATTRIBUEE)) {
            reverserRecompensesEtAnnuler(parrainage, "Contrat du filleul annule ou inactif");
        }

        return new ParrainageValidationResponse(toResponse(parrainage), eligible, verifications);
    }

    @Transactional
    @Auditable(action = ActionAudit.CANCEL, module = "PARRAINAGE", entityName = "Parrainage")
    public ParrainageResponse annuler(Long id, String motif) {
        Parrainage parrainage = getEntity(id);
        reverserRecompensesEtAnnuler(parrainage, StringUtils.hasText(motif) ? motif : "Annule manuellement");
        return toResponse(parrainage);
    }

    @Transactional
    public ParrainageResponse expirer(Long id) {
        Parrainage parrainage = getEntity(id);
        if (parrainage.getStatut() == StatutParrainage.RECOMPENSE_ATTRIBUEE || parrainage.getStatut() == StatutParrainage.ANNULE
                || parrainage.getStatut() == StatutParrainage.EXPIRE || parrainage.getStatut() == StatutParrainage.REJETE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce parrainage ne peut plus expirer depuis son statut actuel: " + parrainage.getStatut());
        }
        StatutParrainage ancien = parrainage.getStatut();
        parrainage.setStatut(StatutParrainage.EXPIRE);
        parrainage.setDateExpiration(Instant.now());
        parrainage = parrainageRepository.save(parrainage);
        enregistrerHistorique(parrainage, TypeEvenementParrainage.EXPIRATION, ancien, StatutParrainage.EXPIRE, "Parrainage expire");
        return toResponse(parrainage);
    }

    public List<HistoriqueParrainageResponse> historique(Long id) {
        getEntity(id);
        return historiqueParrainageRepository.findByParrainageIdOrderByCreatedAtAsc(id).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public List<ControleAntifraudeParrainageResponse> controlesAntifraude(Long id) {
        getEntity(id);
        return controleAntifraudeParrainageRepository.findByParrainageIdOrderByCreatedAtAsc(id).stream()
                .map(this::toFraudResponse)
                .toList();
    }

    @Transactional
    public List<ControleAntifraudeParrainageResponse> declencherControleAntifraude(Long id) {
        Parrainage parrainage = getEntity(id);
        return executerControlesAntifraude(parrainage).stream().map(this::toFraudResponse).toList();
    }

    // ------------------------------------------------------------------
    // Eligibilite / recompenses
    // ------------------------------------------------------------------

    private void marquerEligibleEtGenererRecompenses(Parrainage parrainage) {
        StatutParrainage ancien = parrainage.getStatut();
        boolean dejaRecompense = !recompenseParrainageRepository.findByParrainageId(parrainage.getId()).isEmpty();

        parrainage.setStatut(dejaRecompense ? parrainage.getStatut() : StatutParrainage.ELIGIBLE);
        if (parrainage.getDateEligibilite() == null) {
            parrainage.setDateEligibilite(Instant.now());
        }

        if (!dejaRecompense) {
            parrainage = parrainageRepository.save(parrainage);
            enregistrerHistorique(parrainage, TypeEvenementParrainage.ELIGIBILITE_VERIFIEE, ancien, StatutParrainage.ELIGIBLE, "Parrainage devenu eligible");

            int nombreRecompenses = recompenseParrainageEngine.genererRecompenses(parrainage);
            if (nombreRecompenses > 0) {
                StatutParrainage avant = parrainage.getStatut();
                parrainage.setStatut(StatutParrainage.RECOMPENSE_EN_ATTENTE);
                parrainage = parrainageRepository.save(parrainage);
                enregistrerHistorique(parrainage, TypeEvenementParrainage.RECOMPENSE_CREEE, avant, StatutParrainage.RECOMPENSE_EN_ATTENTE,
                        nombreRecompenses + " recompense(s) generee(s)");
            }
        }
    }

    private void reverserRecompensesEtAnnuler(Parrainage parrainage, String motif) {
        int reversees = recompenseParrainageEngine.reverserRecompenses(parrainage, motif);
        StatutParrainage ancien = parrainage.getStatut();
        parrainage.setStatut(StatutParrainage.ANNULE);
        parrainage.setDateAnnulation(Instant.now());
        parrainage.setMotifRejet(motif);
        parrainageRepository.save(parrainage);
        if (reversees > 0) {
            enregistrerHistorique(parrainage, TypeEvenementParrainage.RECOMPENSE_ANNULEE, ancien, StatutParrainage.ANNULE,
                    reversees + " recompense(s) reversee(s) : " + motif);
        }
        enregistrerHistorique(parrainage, TypeEvenementParrainage.ANNULATION, ancien, StatutParrainage.ANNULE, motif);
    }

    private boolean estStatutReevaluable(StatutParrainage statut) {
        return statut != StatutParrainage.REJETE && statut != StatutParrainage.ANNULE
                && statut != StatutParrainage.EXPIRE && statut != StatutParrainage.RECOMPENSE_ATTRIBUEE;
    }

    private boolean estProgrammeActif(ProgrammeParrainage programme) {
        if (programme.getStatut() != StatutProgrammeParrainage.ACTIF) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (programme.getDateDebut() != null && today.isBefore(programme.getDateDebut())) {
            return false;
        }
        return programme.getDateFin() == null || !today.isAfter(programme.getDateFin());
    }

    private boolean estClientActif(Insured insured) {
        if (insured == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .anyMatch(c -> c.getStatus() == ContractStatus.signed);
    }

    private boolean estNouveauClient(Parrainage parrainage) {
        Insured filleul = parrainage.getFilleul();
        if (filleul == null) {
            return false;
        }
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(filleul.getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.signed)
                .allMatch(c -> parrainage.getContrat() != null && c.getId().equals(parrainage.getContrat().getId())
                        || c.getCreatedAt() != null && !c.getCreatedAt().isBefore(parrainage.getCreatedAt()));
    }

    private boolean produitEligible(ProgrammeParrainage programme, Contract contrat) {
        if (programme.getProduitsEligibles() == null || programme.getProduitsEligibles().isEmpty()) {
            return true;
        }
        return contrat.getProduct() != null && programme.getProduitsEligibles().contains(contrat.getProduct().getId());
    }

    private boolean quotaRespecte(Insured parrain, ProgrammeParrainage programme, Long excludeParrainageId) {
        if (programme.getMaxParrainagesParClient() == null) {
            return true;
        }
        long nombre = parrainageRepository.findByParrainIdAndProgrammeIdAndStatutIn(parrain.getId(), programme.getId(), STATUTS_ACTIFS_POUR_QUOTA)
                .stream()
                .filter(p -> !p.getId().equals(excludeParrainageId))
                .count();
        return nombre < programme.getMaxParrainagesParClient();
    }

    private void verifierProgrammeActif(ProgrammeParrainage programme) {
        if (!estProgrammeActif(programme)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce programme de parrainage n'est pas actif");
        }
    }

    private void verifierQuotaEtCarence(Insured parrain, ProgrammeParrainage programme) {
        List<Parrainage> existants = parrainageRepository.findByParrainIdAndProgrammeId(parrain.getId(), programme.getId());

        if (programme.getMaxParrainagesParClient() != null) {
            long actifs = existants.stream().filter(p -> !STATUTS_NON_BLOQUANTS_POUR_QUOTA.contains(p.getStatut())).count();
            if (actifs >= programme.getMaxParrainagesParClient()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce parrain a atteint son quota de parrainages pour ce programme");
            }
        }

        if (programme.getDelaiCarenceJours() != null && programme.getDelaiCarenceJours() > 0) {
            Instant limite = Instant.now().minus(programme.getDelaiCarenceJours(), ChronoUnit.DAYS);
            boolean recent = existants.stream().anyMatch(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(limite));
            if (recent) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Delai de carence entre deux parrainages non respecte pour ce parrain");
            }
        }
    }

    // ------------------------------------------------------------------
    // Anti-fraude
    // ------------------------------------------------------------------

    private List<ControleAntifraudeParrainage> executerControlesAntifraude(Parrainage parrainage) {
        List<ControleAntifraudeParrainage> controles = new ArrayList<>();

        controles.add(controlerAutoParrainage(parrainage));
        controles.add(controlerTelephoneDuplique(parrainage));
        controles.add(controlerEmailDuplique(parrainage));
        controles.add(controlerClientExistant(parrainage));
        controles.add(controlerParrainagesMultiples(parrainage));

        return controleAntifraudeParrainageRepository.saveAll(controles);
    }

    private ControleAntifraudeParrainage controlerAutoParrainage(Parrainage parrainage) {
        Insured parrain = parrainage.getParrain();
        boolean autoParrainage = false;
        if (parrainage.getFilleul() != null && parrainage.getFilleul().getId().equals(parrain.getId())) {
            autoParrainage = true;
        }
        if (StringUtils.hasText(parrainage.getFilleulTelephone()) && parrainage.getFilleulTelephone().equals(parrain.getClientPhone())) {
            autoParrainage = true;
        }
        if (StringUtils.hasText(parrainage.getFilleulEmail()) && parrain.getClientEmail() != null
                && parrainage.getFilleulEmail().equalsIgnoreCase(parrain.getClientEmail())) {
            autoParrainage = true;
        }
        return construireControle(parrainage, TypeControleFraude.AUTO_PARRAINAGE,
                autoParrainage ? StatutControleFraude.ECHEC : StatutControleFraude.REUSSI,
                autoParrainage ? "Le parrain et le filleul semblent etre la meme personne" : "Parrain et filleul distincts");
    }

    private ControleAntifraudeParrainage controlerTelephoneDuplique(Parrainage parrainage) {
        if (!StringUtils.hasText(parrainage.getFilleulTelephone())) {
            return construireControle(parrainage, TypeControleFraude.TELEPHONE_DUPLIQUE, StatutControleFraude.REUSSI, "Aucun telephone filleul fourni");
        }
        boolean existeDeja = insuredRepository.findByClientPhone(parrainage.getFilleulTelephone()).isPresent();
        return construireControle(parrainage, TypeControleFraude.TELEPHONE_DUPLIQUE,
                existeDeja ? StatutControleFraude.REVUE_REQUISE : StatutControleFraude.REUSSI,
                existeDeja ? "Un assure existe deja avec ce numero de telephone" : "Numero de telephone inedit");
    }

    private ControleAntifraudeParrainage controlerEmailDuplique(Parrainage parrainage) {
        if (!StringUtils.hasText(parrainage.getFilleulEmail())) {
            return construireControle(parrainage, TypeControleFraude.EMAIL_DUPLIQUE, StatutControleFraude.REUSSI, "Aucun email filleul fourni");
        }
        boolean existeDeja = insuredRepository.findAll().stream()
                .anyMatch(i -> parrainage.getFilleulEmail().equalsIgnoreCase(i.getClientEmail()));
        return construireControle(parrainage, TypeControleFraude.EMAIL_DUPLIQUE,
                existeDeja ? StatutControleFraude.REVUE_REQUISE : StatutControleFraude.REUSSI,
                existeDeja ? "Un assure existe deja avec cet email" : "Email inedit");
    }

    private ControleAntifraudeParrainage controlerClientExistant(Parrainage parrainage) {
        if (parrainage.getFilleul() == null) {
            return construireControle(parrainage, TypeControleFraude.CLIENT_EXISTANT, StatutControleFraude.REUSSI, "Filleul pas encore identifie");
        }
        boolean dejaClient = contractRepository.findByInsuredIdOrderByCreatedAtDesc(parrainage.getFilleul().getId()).stream()
                .anyMatch(c -> c.getStatus() == ContractStatus.signed
                        && (c.getCreatedAt() == null || c.getCreatedAt().isBefore(parrainage.getCreatedAt())));
        return construireControle(parrainage, TypeControleFraude.CLIENT_EXISTANT,
                dejaClient ? StatutControleFraude.ECHEC : StatutControleFraude.REUSSI,
                dejaClient ? "Le filleul etait deja client avant ce parrainage" : "Le filleul est un nouveau client");
    }

    private ControleAntifraudeParrainage controlerParrainagesMultiples(Parrainage parrainage) {
        if (!StringUtils.hasText(parrainage.getFilleulEmail()) && !StringUtils.hasText(parrainage.getFilleulTelephone())) {
            return construireControle(parrainage, TypeControleFraude.PARRAINAGES_MULTIPLES, StatutControleFraude.REUSSI, "Filleul non identifie");
        }
        long nombre = parrainageRepository.findByFilleulEmailIgnoreCaseOrFilleulTelephone(
                        parrainage.getFilleulEmail(), parrainage.getFilleulTelephone()).stream()
                .filter(p -> !p.getId().equals(parrainage.getId()))
                .count();
        return construireControle(parrainage, TypeControleFraude.PARRAINAGES_MULTIPLES,
                nombre > 0 ? StatutControleFraude.REVUE_REQUISE : StatutControleFraude.REUSSI,
                nombre > 0 ? "Ce filleul est deja associe a " + nombre + " autre(s) parrainage(s)" : "Aucun autre parrainage pour ce filleul");
    }

    private ControleAntifraudeParrainage construireControle(Parrainage parrainage, TypeControleFraude type, StatutControleFraude statut, String motif) {
        return ControleAntifraudeParrainage.builder()
                .parrainage(parrainage)
                .typeControle(type)
                .statut(statut)
                .motif(motif)
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers communs
    // ------------------------------------------------------------------

    private void enregistrerHistorique(Parrainage parrainage, TypeEvenementParrainage type, StatutParrainage ancien, StatutParrainage nouveau, String description) {
        User auteur = resolveUserSiPresent();
        HistoriqueParrainage evenement = HistoriqueParrainage.builder()
                .parrainage(parrainage)
                .typeEvenement(type)
                .ancienStatut(ancien)
                .nouveauStatut(nouveau)
                .description(description)
                .auteur(auteur)
                .build();
        historiqueParrainageRepository.save(evenement);
    }

    private User resolveUserSiPresent() {
        try {
            AuthenticatedUserPrincipal principal = authService.currentPrincipal();
            return principal == null ? null : userRepository.findById(principal.userId()).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    Parrainage getEntity(Long id) {
        return parrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parrainage introuvable: " + id));
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId));
    }

    private ProgrammeParrainage resolveProgramme(Long programmeId) {
        return programmeParrainageRepository.findById(programmeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme de parrainage introuvable: " + programmeId));
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElse(user.getUsername());
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

    ParrainageResponse toResponse(Parrainage p) {
        return new ParrainageResponse(
                p.getId(),
                p.getReference(),
                p.getProgramme() == null ? null : p.getProgramme().getId(),
                p.getProgramme() == null ? null : p.getProgramme().getNom(),
                p.getCodeParrainage() == null ? null : p.getCodeParrainage().getId(),
                p.getCodeParrainage() == null ? null : p.getCodeParrainage().getCode(),
                p.getParrain() == null ? null : p.getParrain().getId(),
                insuredName(p.getParrain()),
                p.getFilleul() == null ? null : p.getFilleul().getId(),
                p.getFilleul() != null ? insuredName(p.getFilleul()) : p.getFilleulNom(),
                p.getFilleulEmail(),
                p.getFilleulTelephone(),
                p.getContrat() == null ? null : p.getContrat().getId(),
                p.getContrat() == null ? null : p.getContrat().getPolicyNumber(),
                p.getStatut(),
                p.getMotifRejet(),
                p.getDateInvitation(),
                p.getDateInscription(),
                p.getDateEligibilite(),
                p.getDateCompletion(),
                p.getDateAnnulation(),
                p.getDateExpiration(),
                p.getCreatedAt(),
                p.getLastUpdate()
        );
    }

    private HistoriqueParrainageResponse toHistoryResponse(HistoriqueParrainage h) {
        return new HistoriqueParrainageResponse(
                h.getId(),
                h.getParrainage() == null ? null : h.getParrainage().getId(),
                h.getTypeEvenement(),
                h.getAncienStatut(),
                h.getNouveauStatut(),
                h.getDescription(),
                h.getAuteur() == null ? null : h.getAuteur().getId(),
                displayName(h.getAuteur()),
                h.getCreatedAt()
        );
    }

    private ControleAntifraudeParrainageResponse toFraudResponse(ControleAntifraudeParrainage c) {
        return new ControleAntifraudeParrainageResponse(
                c.getId(),
                c.getParrainage() == null ? null : c.getParrainage().getId(),
                c.getTypeControle(),
                c.getStatut(),
                c.getScore(),
                c.getMotif(),
                c.getCreatedAt()
        );
    }
}
