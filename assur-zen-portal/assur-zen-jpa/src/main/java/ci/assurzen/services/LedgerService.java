package ci.assurzen.services;

import ci.assurzen.dto.BalanceCompteResponse;
import ci.assurzen.dto.CompteComptableResponse;
import ci.assurzen.dto.EcritureComptableResponse;
import ci.assurzen.dto.GrandLivreLineResponse;
import ci.assurzen.dto.LigneEcritureResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.CompteComptable;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.EcritureComptable;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.LigneEcriture;
import ci.assurzen.entities.Paiement;
import ci.assurzen.entities.TypeCompte;
import ci.assurzen.entities.TypeOperationLedger;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CompteComptableRepository;
import ci.assurzen.repositories.EcritureComptableRepository;
import ci.assurzen.repositories.LigneEcritureRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module Ledger (grand livre comptable, partie double). Independant du reste du domaine metier :
 * les autres services l'appellent apres avoir persiste leur propre operation (souscription, paiement,
 * commission, annulation) ; ce service ne fait que journaliser des ecritures equilibrees. Chaque methode
 * enregistrerXxx est appelee depuis la transaction du service appelant, donc les ecritures sont commitees
 * atomiquement avec l'operation metier qui les declenche.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private static final String COMPTE_CLIENTS = "411000";
    private static final String COMPTE_BANQUE = "512000";
    private static final String COMPTE_PRIMES = "706000";
    private static final String COMPTE_COMMISSIONS = "622000";
    /** "Primes et gratifications" (SYSCOHADA 6613) : bonus mensuel des commerciaux internes (2026-10-02) -
     * remuneration du personnel, distincte des commissions (622000) et des primes d'objectif partenaires. */
    private static final String COMPTE_BONUS_COMMERCIAL = "661300";
    /** Prime de transport et indemnite de communication des commerciaux internes (2026-11-01). */
    private static final String COMPTE_TRANSPORT_COMMERCIAL = "661400";
    private static final String COMPTE_COMMUNICATION_COMMERCIAL = "661500";
    /** "Primes objectif partenaires" (bonus de depassement d'objectif commercial) - a ne PAS confondre
     * avec COMPTE_PRIMES (706000, "Primes emises" = primes d'assurance encaissees, sens totalement
     * different du mot "prime" ici). Charge, comme COMPTE_COMMISSIONS. */
    private static final String COMPTE_PRIME_OBJECTIF = "658100";

    private final CompteComptableRepository compteRepository;
    private final EcritureComptableRepository ecritureRepository;
    private final LigneEcritureRepository ligneRepository;
    private final CodeGeneratorService codeGeneratorService;

    @PostConstruct
    @Transactional
    public void seedPlanComptable() {
        seedCompte(COMPTE_CLIENTS, "Clients / Assures", TypeCompte.ACTIF);
        seedCompte(COMPTE_BANQUE, "Banque", TypeCompte.ACTIF);
        seedCompte(COMPTE_PRIMES, "Primes emises", TypeCompte.PRODUIT);
        seedCompte(COMPTE_COMMISSIONS, "Commissions versees", TypeCompte.CHARGE);
        seedCompte(COMPTE_PRIME_OBJECTIF, "Primes objectif partenaires", TypeCompte.CHARGE);
        seedCompte(COMPTE_BONUS_COMMERCIAL, "Primes et gratifications (bonus commerciaux)", TypeCompte.CHARGE);
        seedCompte(COMPTE_TRANSPORT_COMMERCIAL, "Primes de transport (commerciaux)", TypeCompte.CHARGE);
        seedCompte(COMPTE_COMMUNICATION_COMMERCIAL, "Indemnites de communication (commerciaux)", TypeCompte.CHARGE);
    }

    private void seedCompte(String code, String libelle, TypeCompte type) {
        if (compteRepository.findByCode(code).isPresent()) {
            return;
        }
        compteRepository.save(CompteComptable.builder()
                .code(code)
                .libelle(libelle)
                .type(type)
                .active(true)
                .build());
        log.info("Compte comptable initialise: {} - {}", code, libelle);
    }

    // ==================== Generation automatique des ecritures ====================

    @Transactional
    public void enregistrerSouscription(Contract contract) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.SOUSCRIPTION,
                LocalDate.now(),
                "Souscription contrat " + contract.getPolicyNumber(),
                contract);

        ecriture.addLigne(ligne(compte(COMPTE_CLIENTS), contract.getPremium(), 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_PRIMES), 0L, contract.getPremium(), null));

        ecritureRepository.save(ecriture);
    }

    @Transactional
    public void enregistrerPaiement(Paiement paiement) {
        Contract contract = paiement.getContract();
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.PAIEMENT,
                paiement.getDatePaiement(),
                "Paiement recu contrat " + contract.getPolicyNumber(),
                contract);

        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), paiement.getMontant(), 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_CLIENTS), 0L, paiement.getMontant(), null));

        ecritureRepository.save(ecriture);
    }

    @Transactional
    public void enregistrerCommission(Contract contract, Long montant) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.COMMISSION,
                LocalDate.now(),
                "Commission partenaire contrat " + contract.getPolicyNumber(),
                contract);

        ecriture.addLigne(ligne(compte(COMPTE_COMMISSIONS), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));

        ecritureRepository.save(ecriture);
    }

    /**
     * Avance sur commission versee par une demande de paiement (partenaire ou commercial), 2026-09-26 -
     * ecrite quand l'avance est REELLEMENT payee (virement confirme ou versement hors application). Memes
     * comptes que enregistrerCommission() ; aucun contrat (l'avance porte sur le solde). Au paiement
     * ulterieur des contrats, seule la part non couverte par une avance deja comptabilisee est ecrite
     * (CommissionService) : jamais de double comptage.
     */
    @Transactional
    public void enregistrerAvanceCommission(String beneficiaire, Long montant, Long demandeId, boolean commercial) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.COMMISSION,
                LocalDate.now(),
                "Avance sur commission " + (commercial ? "commerciale" : "partenaire") + " - demande n" + demandeId
                        + (beneficiaire == null ? "" : " (" + beneficiaire + ")"),
                null);
        ecriture.addLigne(ligne(compte(COMPTE_COMMISSIONS), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));
        ecritureRepository.save(ecriture);
    }

    /** Commission versee a un commercial interne (contrat "Commission commerciale interne",
     * 2026-09-08) - meme comptes que enregistrerCommission() (COMPTE_COMMISSIONS/COMPTE_BANQUE),
     * seul le libelle differe pour rester lisible au grand livre. */
    @Transactional
    public void enregistrerCommissionCommerciale(Contract contract, Long montant) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.COMMISSION,
                LocalDate.now(),
                "Commission commerciale contrat " + contract.getPolicyNumber(),
                contract);

        ecriture.addLigne(ligne(compte(COMPTE_COMMISSIONS), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));

        ecritureRepository.save(ecriture);
    }

    /**
     * Paiement d'une prime d'objectif partenaire (contrat "Prime d'objectif partenaire", 2026-08-28).
     * Volontairement PAS rattachee a l'ExerciceComptable recompense par la prime : cet exercice est par
     * definition deja cloture au moment ou une prime peut y etre demandee (condition d'eligibilite),
     * et nouvelleEcriture() rejette toute ecriture sur un exercice cloture (verrouillage definitif du
     * grand livre historique). L'ecriture est donc journalisee sans exercice associe (comme une
     * operation "hors exercice"), correspondant a la pratique comptable usuelle : la charge est
     * constatee a la date reelle du versement, pas retroactivement dans les livres deja clos de
     * l'annee recompensee.
     */
    @Transactional
    public void enregistrerPrimeObjectif(String libellePartenaire, Long montant) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.PRIME_OBJECTIF,
                LocalDate.now(),
                "Prime d'objectif versee - " + libellePartenaire,
                null,
                null);

        ecriture.addLigne(ligne(compte(COMPTE_PRIME_OBJECTIF), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));

        ecritureRepository.save(ecriture);
    }

    /**
     * Versement du bonus mensuel d'un commercial interne (2026-10-02), ecrit uniquement quand le bonus est
     * REELLEMENT paye (virement SycaPay confirme ou versement hors application). Type d'operation
     * PRIME_OBJECTIF (bonus d'atteinte d'objectif) : la colonne type_operation est un ENUM MySQL
     * qu'Hibernate ne modifie jamais, une nouvelle valeur aurait fait echouer l'ecriture d'un bonus deja
     * vire tant que le script SQL n'est pas applique. Hors exercice, meme raison que enregistrerPrimeObjectif().
     */
    @Transactional
    public void enregistrerBonusCommercial(String libelleCommercial, String moisLibelle, Long montant) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.PRIME_OBJECTIF,
                LocalDate.now(),
                "Bonus commercial " + moisLibelle + " - " + libelleCommercial,
                null,
                null);

        ecriture.addLigne(ligne(compte(COMPTE_BONUS_COMMERCIAL), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));

        ecritureRepository.save(ecriture);
    }

    /** Prime de transport ou indemnite de communication payee a un commercial (2026-11-01). Meme type
     * d'operation que le bonus (PRIME_OBJECTIF, aucun ENUM a modifier), compte de charge dedie. */
    @Transactional
    public void enregistrerAvantageCommercial(boolean transport, String libelleCommercial, String moisLibelle, Long montant) {
        EcritureComptable ecriture = nouvelleEcriture(
                TypeOperationLedger.PRIME_OBJECTIF,
                LocalDate.now(),
                (transport ? "Prime de transport " : "Indemnite de communication ") + moisLibelle + " - " + libelleCommercial,
                null,
                null);

        ecriture.addLigne(ligne(compte(transport ? COMPTE_TRANSPORT_COMMERCIAL : COMPTE_COMMUNICATION_COMMERCIAL), montant, 0L, null));
        ecriture.addLigne(ligne(compte(COMPTE_BANQUE), 0L, montant, null));

        ecritureRepository.save(ecriture);
    }

    @Transactional
    public void enregistrerAnnulation(Contract contract) {
        EcritureComptable origine = ecritureRepository
                .findFirstByContractIdAndTypeOperationAndContrepasseeFalseOrderByDateEcritureDesc(
                        contract.getId(), TypeOperationLedger.SOUSCRIPTION)
                .orElse(null);

        if (origine == null) {
            log.warn("Annulation du contrat {} : aucune ecriture de souscription active a contrepasser", contract.getPolicyNumber());
            return;
        }

        EcritureComptable contrepassation = nouvelleEcriture(
                TypeOperationLedger.ANNULATION,
                LocalDate.now(),
                "Annulation contrat " + contract.getPolicyNumber() + " (contrepassation " + origine.getReference() + ")",
                contract);
        contrepassation.setEcritureOrigine(origine);

        for (LigneEcriture ligneOrigine : origine.getLignes()) {
            contrepassation.addLigne(ligne(
                    ligneOrigine.getCompte(),
                    ligneOrigine.getMontantCredit(),
                    ligneOrigine.getMontantDebit(),
                    ligneOrigine.getLibelle()));
        }

        origine.setContrepassee(true);
        ecritureRepository.save(origine);
        ecritureRepository.save(contrepassation);
    }

    // ==================== Consultation ====================

    public List<CompteComptableResponse> listComptes() {
        return compteRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(c -> new CompteComptableResponse(c.getId(), c.getCode(), c.getLibelle(), c.getType(), c.isActive()))
                .toList();
    }

    public PageResponse<EcritureComptableResponse> listEcritures(Long contractId, TypeOperationLedger typeOperation, Integer page, Integer size) {
        List<EcritureComptableResponse> items = ecritureRepository.findAll(Sort.by(Sort.Direction.DESC, "dateEcriture", "id")).stream()
                .filter(e -> contractId == null || (e.getContract() != null && contractId.equals(e.getContract().getId())))
                .filter(e -> typeOperation == null || e.getTypeOperation() == typeOperation)
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public EcritureComptableResponse getEcriture(Long id) {
        EcritureComptable ecriture = ecritureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ecriture comptable introuvable: " + id));
        return toResponse(ecriture);
    }

    /**
     * Grand livre d'un compte : toutes les lignes qui l'affectent, triees chronologiquement, avec solde progressif.
     * Convention de signe : pour un compte ACTIF/CHARGE, le debit augmente le solde ; pour un compte PASSIF/PRODUIT,
     * c'est le credit qui l'augmente.
     */
    public List<GrandLivreLineResponse> getGrandLivre(Long compteId) {
        CompteComptable compte = compteRepository.findById(compteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte comptable introuvable: " + compteId));

        boolean debitAugmente = debitAugmenteSolde(compte.getType());
        long[] solde = {0L};

        return ligneRepository.findByCompteIdOrderChronologique(compteId).stream()
                .map(ligne -> {
                    long mouvement = debitAugmente
                            ? ligne.getMontantDebit() - ligne.getMontantCredit()
                            : ligne.getMontantCredit() - ligne.getMontantDebit();
                    solde[0] += mouvement;

                    EcritureComptable ecriture = ligne.getEcriture();
                    return new GrandLivreLineResponse(
                            ecriture.getDateEcriture(),
                            ecriture.getReference(),
                            ecriture.getTypeOperation(),
                            ligne.getLibelle() != null ? ligne.getLibelle() : ecriture.getLibelle(),
                            ligne.getMontantDebit(),
                            ligne.getMontantCredit(),
                            solde[0]);
                })
                .toList();
    }

    /**
     * Balance generale : total debit/credit et solde de chaque compte du plan comptable.
     */
    public List<BalanceCompteResponse> getBalance() {
        List<LigneEcriture> toutesLesLignes = ligneRepository.findAll();
        Map<Long, List<LigneEcriture>> lignesParCompte = toutesLesLignes.stream()
                .collect(Collectors.groupingBy(l -> l.getCompte().getId()));

        return compteRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(compte -> {
                    List<LigneEcriture> lignes = lignesParCompte.getOrDefault(compte.getId(), List.of());
                    long totalDebit = lignes.stream().mapToLong(LigneEcriture::getMontantDebit).sum();
                    long totalCredit = lignes.stream().mapToLong(LigneEcriture::getMontantCredit).sum();
                    long solde = debitAugmenteSolde(compte.getType()) ? totalDebit - totalCredit : totalCredit - totalDebit;

                    return new BalanceCompteResponse(compte.getId(), compte.getCode(), compte.getLibelle(), compte.getType(),
                            totalDebit, totalCredit, solde);
                })
                .toList();
    }

    private boolean debitAugmenteSolde(TypeCompte type) {
        return type == TypeCompte.ACTIF || type == TypeCompte.CHARGE;
    }

    // ==================== Helpers ====================

    private EcritureComptable nouvelleEcriture(TypeOperationLedger typeOperation, LocalDate date, String libelle, Contract contract) {
        ExerciceComptable exercice = contract == null ? null : contract.getExerciceComptable();
        return nouvelleEcriture(typeOperation, date, libelle, contract, exercice);
    }

    private EcritureComptable nouvelleEcriture(TypeOperationLedger typeOperation, LocalDate date, String libelle, Contract contract, ExerciceComptable exercice) {
        if (exercice != null && exercice.isCloture()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de journaliser une ecriture : l'exercice comptable " + exercice.getLibelle() + " est cloture");
        }

        return EcritureComptable.builder()
                .reference(codeGeneratorService.generate(CodePrefix.ECRITURE))
                .dateEcriture(date)
                .libelle(libelle)
                .typeOperation(typeOperation)
                .contract(contract)
                .exerciceComptable(exercice)
                .build();
    }

    private LigneEcriture ligne(CompteComptable compte, Long debit, Long credit, String libelle) {
        return LigneEcriture.builder()
                .compte(compte)
                .montantDebit(debit == null ? 0L : debit)
                .montantCredit(credit == null ? 0L : credit)
                .libelle(libelle)
                .build();
    }

    private CompteComptable compte(String code) {
        return compteRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compte comptable manquant: " + code));
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

    private EcritureComptableResponse toResponse(EcritureComptable ecriture) {
        List<LigneEcritureResponse> lignes = ecriture.getLignes().stream()
                .map(l -> new LigneEcritureResponse(
                        l.getId(),
                        l.getCompte().getId(),
                        l.getCompte().getCode(),
                        l.getCompte().getLibelle(),
                        l.getMontantDebit(),
                        l.getMontantCredit(),
                        l.getLibelle()))
                .toList();

        return new EcritureComptableResponse(
                ecriture.getId(),
                ecriture.getReference(),
                ecriture.getDateEcriture(),
                ecriture.getLibelle(),
                ecriture.getTypeOperation(),
                ecriture.getContract() == null ? null : ecriture.getContract().getId(),
                ecriture.getContract() == null ? null : ecriture.getContract().getPolicyNumber(),
                ecriture.getExerciceComptable() == null ? null : ecriture.getExerciceComptable().getId(),
                ecriture.getExerciceComptable() == null ? null : ecriture.getExerciceComptable().getLibelle(),
                ecriture.getEcritureOrigine() == null ? null : ecriture.getEcritureOrigine().getId(),
                ecriture.isContrepassee(),
                lignes,
                ecriture.getCreation());
    }
}
