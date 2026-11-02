package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.ContractRequest;
import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ImportRowError;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Compagnie;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Echeance;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.ModePaiement;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.PartenaireProduitCommission;
import ci.assurzen.entities.ProduitCommissionCommercial;
import ci.assurzen.entities.ProduitCommissionTaux;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.entities.User;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.events.ContractSignedEvent;
import ci.assurzen.repositories.CompagnieRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProduitCommissionCommercialRepository;
import ci.assurzen.repositories.ProduitCommissionTauxRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VehiculeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import static ci.assurzen.utils.SafeAccess.safe;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractsService {

    private final ContractRepository contractRepository;
    private final ProduitRepository produitRepository;
    private final InsuredRepository insuredRepository;
    private final VehiculeRepository vehiculeRepository;
    private final PartenaireRepository partenaireRepository;
    private final CompagnieRepository compagnieRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ExerciceComptableRepository exerciceComptableRepository;
    private final ProduitCommissionTauxRepository produitCommissionTauxRepository;
    private final ProduitCommissionCommercialRepository produitCommissionCommercialRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final BusinessMetrics businessMetrics;
    private final LedgerService ledgerService;
    private final EcheancierService echeancierService;
    private final SmsNotificationService smsNotificationService;
    private final FideliteService fideliteService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PartenaireProduitCommissionService partenaireProduitCommissionService;

    /**
     * Historique complet, non filtre par la portee du commercial connecte, de tous les contrats
     * jamais souscrits par un assure (depuis le debut). Reserve aux usages back-office (ex. fiche de
     * sinistralite) qui ont besoin d'une vue exhaustive, pas de la vue scopee de search().
     */
    public List<ContractResponse> findAllByInsuredId(Long insuredId) {
        return contractRepository.findByInsuredIdOrderByCreatedAtDesc(insuredId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PageResponse<ContractResponse> search(String q, ContractStatus status, CommissionStatus commissionStatus,
                                                  Long productId, Long insuredId, Long salesRepId, Long exerciceComptableId,
                                                  Integer page, Integer size) {
        return paginate(filter(q, status, commissionStatus, productId, insuredId, salesRepId, exerciceComptableId), page, size);
    }

    /**
     * Contrats signes dont l'echeance tombe dans les prochains "days" jours (30 par defaut), echeance
     * non encore depassee. Meme portee admin/commercial que search().
     */
    public PageResponse<ContractResponse> findEcheanceProche(Integer days, Integer page, Integer size) {
        int horizonJours = (days == null || days < 1) ? 30 : days;
        return paginate(filterEcheanceProche(horizonJours), page, size);
    }

    /**
     * Contrats signes dont l'echeance est deja depassee et qui n'ont pas ete renouveles, c'est-a-dire
     * qu'aucun autre contrat ne les reference comme contratPrecedent (meme logique que
     * DashboardBackOfficeService.buildCycleDeVie). Meme portee admin/commercial que search().
     */
    public PageResponse<ContractResponse> findNonRenouveles(Integer page, Integer size) {
        return paginate(filterNonRenouveles(), page, size);
    }

    /**
     * Contrats d'un partenaire donne, paginé. 404 si le partenaire n'existe pas. Portee :
     * - admin : tout voit.
     * - le partenaire consulte EST le compte connecte (via son Profile lie) : voit TOUS ses contrats,
     *   peu importe quel commercial interne les a traites - Partenaire et salesRep sont deux notions
     *   independantes (cf. Contract.partenaire vs Contract.salesRep). C'est le cas d'usage reel de cet
     *   endpoint (app partenaire consultant son propre historique) - initialement scope comme search()
     *   par erreur, corrige le 2026-08-21 apres un dashboard partenaire vide malgre des contrats reels
     *   (tous traites par un commercial autre que le partenaire lui-meme).
     * - tout autre commercial non-admin : meme portee que search(), ne voit que ses propres contrats
     *   (salesRep) meme au sein de ce partenaire.
     *
     * inclureAnciensNonRenouveles (defaut false) : par defaut, les contrats signes, echus, non
     * renouveles (aucun contrat ne les reference comme contratPrecedent) depuis plus de
     * CONTRACT_NON_RENOUVELE_MASQUAGE_MOIS mois (config dynamique, defaut 2) sont masques de cette
     * liste - decision produit du 2026-08-23, cote app partenaire uniquement (search()/le reste du
     * back-office restent inchanges). Passer true pour les revoir explicitement.
     */
    public PageResponse<ContractResponse> findByPartenaire(Long partenaireId, String search, ContractStatus status,
                                                             Boolean inclureAnciensNonRenouveles, Integer page, Integer size) {
        verifierAccesPartenaireCible(partenaireId);
        partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        return paginate(filterByPartenaire(partenaireId, search, status, inclureAnciensNonRenouveles), page, size);
    }

    public byte[] exportCsv(String q, ContractStatus status, CommissionStatus commissionStatus, Long productId, Long insuredId, Long salesRepId) {
        List<ContractResponse> rows = filter(q, status, commissionStatus, productId, insuredId, salesRepId, null);

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Id;PolicyNumber;DateEffet;Duree;DateEcheance;Produit;Assure;Partenaire;Compagnie;Premium;TauxCommission;MontantCommission;PartPartenaire;Statut;StatutCommission;Commercial;Notes\n");
        for (ContractResponse row : rows) {
            csv.append(CsvUtils.field(row.id()))
                    .append(';').append(CsvUtils.field(row.policyNumber()))
                    .append(';').append(CsvUtils.field(row.dateEffet()))
                    .append(';').append(CsvUtils.field(row.duree()))
                    .append(';').append(CsvUtils.field(row.dateEcheance()))
                    .append(';').append(CsvUtils.field(row.productName()))
                    .append(';').append(CsvUtils.field(row.insuredName()))
                    .append(';').append(CsvUtils.field(row.partenaireName()))
                    .append(';').append(CsvUtils.field(row.compagnieName()))
                    .append(';').append(CsvUtils.field(row.premium()))
                    .append(';').append(CsvUtils.field(row.commissionRate()))
                    .append(';').append(CsvUtils.field(row.commissionAmount()))
                    .append(';').append(CsvUtils.field(row.partnerShare()))
                    .append(';').append(CsvUtils.field(row.status()))
                    .append(';').append(CsvUtils.field(row.commissionStatus()))
                    .append(';').append(CsvUtils.field(row.salesRep()))
                    .append(';').append(CsvUtils.field(row.notes()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportResult importCsv(MultipartFile file) {
        requireAdmin();

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV manquant ou vide");
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier CSV");
        }

        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        for (int i = 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (!StringUtils.hasText(rawLine)) {
                continue;
            }
            totalRows++;
            int lineNumber = i + 1;

            List<String> fields = CsvUtils.parseLine(rawLine);
            String policyNumber = CsvUtils.value(fields, 0);
            String dateEffetRaw = CsvUtils.value(fields, 1);
            String duree = CsvUtils.value(fields, 2);
            String dateEcheanceRaw = CsvUtils.value(fields, 3);
            String produitName = CsvUtils.value(fields, 4);
            String insuredIdRaw = CsvUtils.value(fields, 5);
            String compagnieName = CsvUtils.value(fields, 6);
            String premiumRaw = CsvUtils.value(fields, 7);
            String tauxRaw = CsvUtils.value(fields, 8);
            String montantRaw = CsvUtils.value(fields, 9);
            String partRaw = CsvUtils.value(fields, 10);
            String statutRaw = CsvUtils.value(fields, 11);
            String statutCommissionRaw = CsvUtils.value(fields, 12);
            String commercialUsername = CsvUtils.value(fields, 13);
            String notes = CsvUtils.value(fields, 14);

            if (!StringUtils.hasText(duree) || !StringUtils.hasText(produitName) || !StringUtils.hasText(insuredIdRaw)
                    || !StringUtils.hasText(compagnieName) || !StringUtils.hasText(premiumRaw)
                    || !StringUtils.hasText(tauxRaw) || !StringUtils.hasText(montantRaw) || !StringUtils.hasText(partRaw)
                    || !StringUtils.hasText(commercialUsername)) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Champs obligatoires manquants (duree, produit, insuredId, compagnie, premium, tauxCommission, montantCommission, partPartenaire, commercial)"));
                continue;
            }

            Produit produit = produitRepository.findByNomProduitIgnoreCase(produitName).orElse(null);
            if (produit == null) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Produit introuvable: " + produitName));
                continue;
            }

            User salesRep = userRepository.findByUsername(commercialUsername).orElse(null);
            if (salesRep == null) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Commercial introuvable: " + commercialUsername));
                continue;
            }

            try {
                Long insuredId = Long.valueOf(insuredIdRaw);
                if (insuredRepository.findById(insuredId).isEmpty()) {
                    errors.add(new ImportRowError(lineNumber, policyNumber, "Assure introuvable: " + insuredId));
                    continue;
                }

                Compagnie compagnie = compagnieRepository.findByCodeCompagnie(compagnieName)
                        .or(() -> compagnieRepository.findAll().stream().filter(c -> c.getNom().equalsIgnoreCase(compagnieName)).findFirst())
                        .orElse(null);
                if (compagnie == null) {
                    errors.add(new ImportRowError(lineNumber, policyNumber, "Compagnie introuvable: " + compagnieName));
                    continue;
                }

                LocalDate dateEffet = parseDate(dateEffetRaw);
                LocalDate dateEcheance = parseDate(dateEcheanceRaw);
                Long premium = Long.valueOf(premiumRaw);
                BigDecimal tauxCommission = new BigDecimal(tauxRaw.replace(",", "."));
                Long montantCommission = Long.valueOf(montantRaw);
                Long partPartenaire = Long.valueOf(partRaw);
                ContractStatus status = StringUtils.hasText(statutRaw) ? ContractStatus.valueOf(statutRaw.toLowerCase(Locale.ROOT)) : null;
                CommissionStatus commissionStatus = StringUtils.hasText(statutCommissionRaw) ? CommissionStatus.valueOf(statutCommissionRaw.toLowerCase(Locale.ROOT)) : null;

                create(new ContractRequest(policyNumber, dateEffet, duree, dateEcheance, produit.getId(), insuredId, null, null,
                        compagnie.getId(), premium, tauxCommission, montantCommission, partPartenaire, status, commissionStatus,
                        null, salesRep.getId(), notes, null, null, null, null, null, null, null, null));
                successCount++;
            } catch (NumberFormatException e) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Valeur numerique invalide"));
            } catch (DateTimeParseException e) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Date invalide (format attendu: AAAA-MM-JJ)"));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Statut invalide (attendu: signed/pending/draft/cancelled ou paid/pending)"));
            } catch (ResponseStatusException e) {
                errors.add(new ImportRowError(lineNumber, policyNumber, e.getReason()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(lineNumber, policyNumber, "Erreur inattendue: " + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, successCount, errors.size(), errors);
    }

    private LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
    }

    public ContractResponse getContract(Long id) {
        return toResponse(getContractScoped(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "CONTRATS", entityName = "Contract")
    public ContractResponse create(ContractRequest request) {
        return businessMetrics.recordSubscriptionProcessing(() -> {
            AuthenticatedUserPrincipal principal = authService.currentPrincipal();
            boolean admin = isAdmin(principal);

            String policyNumber = StringUtils.hasText(request.policyNumber())
                    ? request.policyNumber()
                    : codeGeneratorService.generate(CodePrefix.CONTRAT);

            contractRepository.findByPolicyNumber(policyNumber)
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Un contrat existe deja pour ce numero de police: " + policyNumber);
                    });

            User salesRep = (admin && request.salesRepId() != null)
                    ? resolveUser(request.salesRepId())
                    : resolveUser(principal.userId());

            Partenaire partenaire = resolvePartenaire(request.partenaireId());
            validerMontantsSaisis(request);
            CommissionResolution commission = resolveCommission(request.productId(), partenaire, request.premium(),
                    request.commissionRate(), request.commissionAmount());
            LocalDate dateReference = request.dateEffet() != null ? request.dateEffet() : LocalDate.now();
            CommissionResolution commercialCommission = resolveCommercialCommission(request.productId(), partenaire, request.premium(),
                    request.commercialCommissionAmount(), salesRep.getId(), null, dateReference);
            Long partnerShare = resolvePartnerShare(request.productId(), partenaire, request.partnerShare(), null, dateReference);
            verifierPartSousCommission(partnerShare, commission.commissionAmount(), originePart(request.partnerShare(), null));

            Contract contract = Contract.builder()
                    .policyNumber(policyNumber)
                    .dateEffet(request.dateEffet())
                    .duree(request.duree())
                    .dateEcheance(request.dateEcheance())
                    .product(resolveProduit(request.productId()))
                    .insured(resolveInsured(request.insuredId()))
                    .vehicule(request.vehiculeId() != null ? resolveVehicule(request.vehiculeId()) : null)
                    .partenaire(partenaire)
                    .compagnie(resolveCompagnie(request.compagnieId()))
                    .premium(request.premium())
                    .commissionRate(commission.commissionRate())
                    .commissionAmount(commission.commissionAmount())
                    .partnerShare(partnerShare)
                    .commercialCommissionRate(commercialCommission.commissionRate())
                    .commercialCommissionAmount(commercialCommission.commissionAmount())
                    .status(request.status() != null ? request.status() : ContractStatus.draft)
                    .commissionStatus(request.commissionStatus() != null ? request.commissionStatus() : CommissionStatus.pending)
                    .signedAt(signedAtEffectif(request.status() != null ? request.status() : ContractStatus.draft,
                            request.signedAt(), null))
                    .salesRep(salesRep)
                    .notes(request.notes())
                    .exerciceComptable(resolveExerciceComptable(request.exerciceComptableId(), referenceDateForExercice(request.signedAt(), request.dateEffet())))
                    .modePaiement(request.modePaiement() != null ? request.modePaiement() : ModePaiement.COMPTANT)
                    .nombreEcheances(request.nombreEcheances())
                    .periodiciteEcheances(request.periodiciteEcheances())
                    .montantDejaVerse(request.montantDejaVerse())
                    .contratPrecedent(resolveContratPrecedent(request.contratPrecedentId(), null))
                    .build();
            contract.setPrimeReversementStatus(request.primeReversementStatus() != null
                    ? request.primeReversementStatus()
                    : statutReversementParDefaut(contract));
            validateFractionnement(contract);

            Contract saved = contractRepository.save(contract);
            if (saved.getStatus() == ContractStatus.signed) {
                ledgerService.enregistrerSouscription(saved);
                // Commission partenaire selon sa frequence (paiement IMMEDIAT, notification A_LA_DEMANDE) -
                // traitee apres commit, en asynchrone (PartenaireCommissionEventListener).
                eventPublisher.publishEvent(new ContractSignedEvent(saved.getId()));
            }
            if (saved.getContratPrecedent() != null) {
                fideliteService.crediterBonusRenouvellement(saved);
            }
            // Cycle de rappels SMS avant echeance (avis 45/15/5 jours, fin de contrat) - jusqu'au
            // 2026-09-13 uniquement planifie par InsuredRegistrationService (flux /souscriptions),
            // jamais par ce endpoint generique alors qu'il sert aussi a creer un contrat pour un
            // vehicule/moto de flotte deja rattache a un client existant. scheduleContractReminders()
            // est deja no-op silencieux si aucun telephone/date d'echeance n'est disponible.
            smsNotificationService.scheduleContractReminders(
                    saved.getId(), insuredPhone(saved.getInsured()), insuredPhone2(saved.getInsured()),
                    saved.getPolicyNumber(), saved.getDuree(), saved.getDateEcheance());
            if (saved.getModePaiement() == ModePaiement.FRACTIONNE) {
                List<Echeance> echeances = echeancierService.genererEcheancier(saved);
                smsNotificationService.scheduleEcheanceReminders(saved, insuredPhone(saved.getInsured()), insuredPhone2(saved.getInsured()), saved.getPolicyNumber(), echeances);
            }

            return toResponse(saved);
        });
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest request) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        Contract contract = getContractScoped(id);
        ContractResponse avant = toResponse(contract);
        ContractStatus statutAvant = contract.getStatus();
        ModePaiement modePaiementAvant = contract.getModePaiement();
        boolean avaitDejaUnRenouvellement = contract.getContratPrecedent() != null;

        String policyNumber = StringUtils.hasText(request.policyNumber()) ? request.policyNumber() : contract.getPolicyNumber();
        contractRepository.findByPolicyNumber(policyNumber)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un contrat existe deja pour ce numero de police: " + policyNumber);
                });

        Partenaire partenaire = resolvePartenaire(request.partenaireId());
        Compagnie compagnie = resolveCompagnie(request.compagnieId());
        validerMontantsSaisis(request);
        CommissionResolution commission = resolveCommission(request.productId(), partenaire, request.premium(),
                request.commissionRate(), request.commissionAmount());
        // Modification : une commission deja enregistree est conservee si l'appelant ne la renvoie pas
        // et que le partenaire ne change pas (jamais d'ecrasement silencieux d'une saisie).
        boolean memePartenaire = Objects.equals(partenaire == null ? null : partenaire.getId(),
                contract.getPartenaire() == null ? null : safe(contract.getPartenaire()::getId));
        LocalDate dateReference = request.dateEffet() != null ? request.dateEffet() : LocalDate.now();
        Long salesRepId = contract.getSalesRep() == null ? null : safe(contract.getSalesRep()::getId);
        CommissionResolution commercialCommission = resolveCommercialCommission(request.productId(), partenaire, request.premium(),
                request.commercialCommissionAmount(), salesRepId,
                memePartenaire ? contract.getCommercialCommissionAmount() : null, dateReference);
        Long partnerShare = resolvePartnerShare(request.productId(), partenaire, request.partnerShare(),
                memePartenaire ? contract.getPartnerShare() : null, dateReference);
        // Un contrat existant dont ni la part ni la commission ne changent reste modifiable (anciennes
        // donnees anterieures a la regle) ; sinon la part doit tenir dans la commission.
        if (!Objects.equals(partnerShare, contract.getPartnerShare())
                || !Objects.equals(commission.commissionAmount(), contract.getCommissionAmount())) {
            verifierPartSousCommission(partnerShare, commission.commissionAmount(),
                    originePart(request.partnerShare(), memePartenaire ? contract.getPartnerShare() : null));
        }
        if (statutAvant == ContractStatus.signed) {
            verifierChampsSensiblesInchanges(contract, request.premium(), commission, commercialCommission, partnerShare, partenaire, compagnie);
        }

        contract.setPolicyNumber(policyNumber);
        contract.setDateEffet(request.dateEffet());
        contract.setDuree(request.duree());
        contract.setDateEcheance(request.dateEcheance());
        contract.setProduct(resolveProduit(request.productId()));
        contract.setInsured(resolveInsured(request.insuredId()));
        contract.setVehicule(request.vehiculeId() != null ? resolveVehicule(request.vehiculeId()) : null);
        contract.setPartenaire(partenaire);
        contract.setCompagnie(compagnie);
        contract.setPremium(request.premium());
        contract.setCommissionRate(commission.commissionRate());
        contract.setCommissionAmount(commission.commissionAmount());
        contract.setPartnerShare(partnerShare);
        contract.setCommercialCommissionRate(commercialCommission.commissionRate());
        contract.setCommercialCommissionAmount(commercialCommission.commissionAmount());
        contract.setStatus(request.status() != null ? request.status() : contract.getStatus());
        contract.setCommissionStatus(request.commissionStatus() != null ? request.commissionStatus() : contract.getCommissionStatus());
        contract.setPrimeReversementStatus(request.primeReversementStatus() != null ? request.primeReversementStatus() : contract.getPrimeReversementStatus());
        contract.setSignedAt(signedAtEffectif(contract.getStatus(), request.signedAt(), contract.getSignedAt()));
        contract.setNotes(request.notes());
        contract.setExerciceComptable(resolveExerciceComptable(request.exerciceComptableId(), referenceDateForExercice(request.signedAt(), request.dateEffet())));
        contract.setModePaiement(request.modePaiement() != null ? request.modePaiement() : contract.getModePaiement());
        contract.setNombreEcheances(request.nombreEcheances() != null ? request.nombreEcheances() : contract.getNombreEcheances());
        contract.setPeriodiciteEcheances(request.periodiciteEcheances() != null ? request.periodiciteEcheances() : contract.getPeriodiciteEcheances());
        contract.setMontantDejaVerse(request.montantDejaVerse() != null ? request.montantDejaVerse() : contract.getMontantDejaVerse());
        contract.setContratPrecedent(resolveContratPrecedent(request.contratPrecedentId(), id));
        validateFractionnement(contract);

        if (admin && request.salesRepId() != null) {
            contract.setSalesRep(resolveUser(request.salesRepId()));
        }

        Contract saved = contractRepository.save(contract);

        if (statutAvant != ContractStatus.signed && saved.getStatus() == ContractStatus.signed) {
            ledgerService.enregistrerSouscription(saved);
            eventPublisher.publishEvent(new ContractSignedEvent(saved.getId()));
        } else if (statutAvant == ContractStatus.signed && saved.getStatus() == ContractStatus.cancelled) {
            ledgerService.enregistrerAnnulation(saved);
        }

        if (!avaitDejaUnRenouvellement && saved.getContratPrecedent() != null) {
            fideliteService.crediterBonusRenouvellement(saved);
        }

        if (modePaiementAvant != ModePaiement.FRACTIONNE && saved.getModePaiement() == ModePaiement.FRACTIONNE
                && echeancierService.listForContract(saved.getId()).isEmpty()) {
            List<Echeance> echeances = echeancierService.genererEcheancier(saved);
            smsNotificationService.scheduleEcheanceReminders(saved, insuredPhone(saved.getInsured()), insuredPhone2(saved.getInsured()), saved.getPolicyNumber(), echeances);
        }

        ContractResponse apres = toResponse(saved);
        functionalAuditRecorder.recordUpdate("CONTRATS", "Contract", String.valueOf(saved.getId()), avant, apres, request.notes());
        return apres;
    }

    /**
     * Une fois un contrat signe, prime/commission/partenaire/compagnie ne sont plus modifiables via ce
     * PUT silencieux - seul un avenant explicite (POST /contracts/{id}/avenants, motif + numerotation +
     * classification obligatoires) peut desormais les faire evoluer (decision produit du 2026-08-23).
     * Compare aux valeurs FINALES deja resolues (commission auto-calculee incluse) plutot qu'aux
     * champs bruts de la requete, pour fermer aussi la brecha ou omettre commissionRate/commissionAmount
     * declencherait un recalcul automatique silencieux depuis la table de taux.
     */
    private void verifierChampsSensiblesInchanges(Contract contract, Long nouveauPremium, CommissionResolution commission,
                                                    CommissionResolution commercialCommission, Long nouveauPartnerShare,
                                                    Partenaire nouveauPartenaire, Compagnie nouvelleCompagnie) {
        List<String> champsBloques = new ArrayList<>();
        if (!Objects.equals(nouveauPremium, contract.getPremium())) {
            champsBloques.add("premium");
        }
        boolean commissionRateInchangee = contract.getCommissionRate() == null
                ? commission.commissionRate() == null
                : commission.commissionRate() != null && contract.getCommissionRate().compareTo(commission.commissionRate()) == 0;
        if (!commissionRateInchangee) {
            champsBloques.add("commissionRate");
        }
        if (!Objects.equals(commission.commissionAmount(), contract.getCommissionAmount())) {
            champsBloques.add("commissionAmount");
        }
        if (!Objects.equals(commercialCommission.commissionAmount(), contract.getCommercialCommissionAmount())) {
            champsBloques.add("commercialCommissionAmount");
        }
        if (!Objects.equals(nouveauPartnerShare, contract.getPartnerShare())) {
            champsBloques.add("partnerShare");
        }
        Long partenaireActuelId = contract.getPartenaire() == null ? null : safe(contract.getPartenaire()::getId);
        Long nouveauPartenaireId = nouveauPartenaire == null ? null : nouveauPartenaire.getId();
        if (!Objects.equals(partenaireActuelId, nouveauPartenaireId)) {
            champsBloques.add("partenaireId");
        }
        Long compagnieActuelleId = contract.getCompagnie() == null ? null : safe(contract.getCompagnie()::getId);
        Long nouvelleCompagnieId = nouvelleCompagnie == null ? null : nouvelleCompagnie.getId();
        if (!Objects.equals(compagnieActuelleId, nouvelleCompagnieId)) {
            champsBloques.add("compagnieId");
        }

        if (!champsBloques.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un contrat signe ne permet plus de modifier directement " + String.join(", ", champsBloques)
                            + " - passez par POST /contracts/" + contract.getId() + "/avenants");
        }
    }

    /**
     * Bug reel constate le 2026-09-14 (nettoyage de donnees de test) : tout contrat ayant des
     * rappels SMS programmes (avis_echeance/fin_contrat, cf. scheduleContractReminders) faisait
     * echouer cette suppression en DataIntegrityViolationException (fk_notificateur_sms_contract,
     * pas de cascade en base) - remonte comme un 500 brut au lieu d'une erreur exploitable.
     * Nettoyage explicite avant suppression : ces rappels sont un simple historique d'envoi de SMS
     * lie au cycle de vie du contrat, pas une donnee metier/comptable a preserver independamment
     * (contrairement aux paiements/avenants/sinistres/ecritures comptables/commissions/mouvements de
     * flotte, qui eux continuent de bloquer legitimement la suppression via leur propre FK -
     * delibere, ce ne sont pas de simples logs mais de vraies donnees metier/comptables : plutot que
     * de les nettoyer aussi, GlobalExceptionHandler.contrainteBaseViolee() traduit desormais ce
     * blocage en 409 propre au lieu du 500 brut d'avant).
     */
    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "CONTRATS", entityName = "Contract")
    public void delete(Long id) {
        Contract contract = getContractScoped(id);
        smsNotificationService.deleteRemindersForContract(contract.getId());
        contractRepository.delete(contract);
    }

    private Contract getContractScoped(Long id) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return contractRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + id));
        }
        return contractRepository.findByIdAndSalesRepId(id, principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + id));
    }

    private List<ContractResponse> filter(String q, ContractStatus status, CommissionStatus commissionStatus,
                                           Long productId, Long insuredId, Long salesRepId, Long exerciceComptableId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(contract -> admin || (contract.getSalesRep() != null && principal.userId().equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> salesRepId == null || (contract.getSalesRep() != null && salesRepId.equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> insuredId == null || (contract.getInsured() != null && insuredId.equals(safe(contract.getInsured()::getId))))
                .filter(contract -> status == null || contract.getStatus() == status)
                .filter(contract -> commissionStatus == null || contract.getCommissionStatus() == commissionStatus)
                .filter(contract -> productId == null || (contract.getProduct() != null && productId.equals(safe(contract.getProduct()::getId))))
                .filter(contract -> exerciceComptableId == null || (contract.getExerciceComptable() != null && exerciceComptableId.equals(safe(contract.getExerciceComptable()::getId))))
                .filter(contract -> normalizedQuery == null || matchesQuery(contract, normalizedQuery))
                .map(this::toResponse)
                .toList();
    }

    private List<ContractResponse> filterEcheanceProche(int days) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);

        return contractRepository.findAll(Sort.by(Sort.Direction.ASC, "dateEcheance"))
                .stream()
                .filter(contract -> admin || (contract.getSalesRep() != null && principal.userId().equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> contract.getStatus() == ContractStatus.signed)
                .filter(contract -> contract.getDateEcheance() != null
                        && !contract.getDateEcheance().isBefore(today)
                        && !contract.getDateEcheance().isAfter(horizon))
                .map(this::toResponse)
                .toList();
    }

    private List<ContractResponse> filterNonRenouveles() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        LocalDate today = LocalDate.now();

        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "dateEcheance"))
                .stream()
                .filter(contract -> admin || (contract.getSalesRep() != null && principal.userId().equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> contract.getStatus() == ContractStatus.signed)
                .filter(contract -> contract.getDateEcheance() != null && contract.getDateEcheance().isBefore(today))
                .filter(contract -> contractRepository.findByContratPrecedentId(contract.getId()).isEmpty())
                .map(this::toResponse)
                .toList();
    }

    private List<ContractResponse> filterByPartenaire(Long partenaireId, String search, ContractStatus status, Boolean inclureAnciensNonRenouveles) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        boolean estCePartenaire = !admin && estPartenaireConnecte(principal, partenaireId);
        boolean inclure = Boolean.TRUE.equals(inclureAnciensNonRenouveles);
        String normalizedQuery = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(contract -> admin || estCePartenaire
                        || (contract.getSalesRep() != null && principal.userId().equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> contract.getPartenaire() != null && partenaireId.equals(safe(contract.getPartenaire()::getId)))
                .filter(contract -> status == null || contract.getStatus() == status)
                .filter(contract -> normalizedQuery == null || matchesQuery(contract, normalizedQuery))
                .filter(contract -> inclure || !estAncienNonRenouvele(contract))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Vrai si un contrat signe, echu, sans renouvellement (aucun contrat ne le reference comme
     * contratPrecedent - meme definition que filterNonRenouveles()), dont l'echeance date de plus de
     * CONTRACT_NON_RENOUVELE_MASQUAGE_MOIS mois (config dynamique, defaut 2 mois). Masque par defaut
     * de GET /partenaires/{id}/contracts - decision produit du 2026-08-23, cote app partenaire
     * uniquement.
     */
    private boolean estAncienNonRenouvele(Contract contract) {
        if (contract.getStatus() != ContractStatus.signed || contract.getDateEcheance() == null) {
            return false;
        }
        long seuilMois = dynamicConfigurationService.getLong("CONTRACT_NON_RENOUVELE_MASQUAGE_MOIS", 2L);
        LocalDate limite = LocalDate.now().minusMonths(seuilMois);
        if (!contract.getDateEcheance().isBefore(limite)) {
            return false;
        }
        return contractRepository.findByContratPrecedentId(contract.getId()).isEmpty();
    }

    /** Vrai si le compte connecte EST ce partenaire (son Profile est rattache a ce Partenaire) - meme
     * mecanisme de resolution que AuthService.buildUserResponse() (partenaireId du DTO de connexion). */
    private boolean estPartenaireConnecte(AuthenticatedUserPrincipal principal, Long partenaireId) {
        return profileRepository.findByUserId(principal.userId())
                .map(profile -> partenaireRepository.findByProfile_Id(profile.getId()).stream()
                        .anyMatch(p -> p.getId().equals(partenaireId)))
                .orElse(false);
    }

    /**
     * IDOR confirme en live le 2026-08-23 (audit de securite) sur GET /partenaires/{id}/contracts et
     * GET /partenaires/{id}/contracts/prime-non-reversee : le controle d'acces reposait UNIQUEMENT sur
     * le filtrage (filterByPartenaire/filterPrimeNonReverseeByPartenaire renvoyant une liste vide pour
     * un partenaire etranger), jamais sur un rejet explicite - un compte partenaire pouvait donc
     * interroger n'importe quel {id} et recevoir 200 (liste filtree, pas 403), avec un risque reel de
     * fuite si son propre userId coincidait un jour avec un salesRep sur un contrat d'un AUTRE
     * partenaire (le filtre de secours "commercial interne" ne distingue pas partenaire vs commercial).
     * Rejette desormais explicitement AVANT meme la verification d'existence du partenaire cible (pour
     * qu'un compte partenaire ne puisse jamais distinguer, via 403 vs 404, un {id} existant d'un {id}
     * inexistant - fuite d'information mineure supplementaire signalee dans le meme rapport). Les
     * comptes SANS lien partenaire (commercial interne, admin) ne sont pas concernes par ce verrou -
     * leur portee reste celle deja geree plus loin (salesRep, ou tout si admin).
     */
    private void verifierAccesPartenaireCible(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        List<Partenaire> partenairesLies = profileRepository.findByUserId(principal.userId())
                .map(profile -> partenaireRepository.findByProfile_Id(profile.getId()))
                .orElse(List.of());
        if (partenairesLies.isEmpty()) {
            return;
        }
        boolean estCePartenaire = partenairesLies.stream().anyMatch(p -> p.getId().equals(partenaireId));
        if (!estCePartenaire) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    private boolean matchesQuery(Contract contract, String normalizedQuery) {
        return contains(contract.getPolicyNumber(), normalizedQuery)
                || contains(contract.getNotes(), normalizedQuery)
                || (contract.getProduct() != null && contains(contract.getProduct().getNomProduit(), normalizedQuery))
                || (contract.getInsured() != null && contains(insuredName(contract.getInsured()), normalizedQuery))
                || contains(displayName(contract.getSalesRep()), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
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

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private Produit resolveProduit(Long productId) {
        return produitRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + productId));
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId));
    }

    private Partenaire resolvePartenaire(Long partenaireId) {
        if (partenaireId == null) {
            return null;
        }
        return partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
    }

    private Compagnie resolveCompagnie(Long compagnieId) {
        return compagnieRepository.findById(compagnieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compagnie introuvable: " + compagnieId));
    }

    private Vehicule resolveVehicule(Long vehiculeId) {
        return vehiculeRepository.findById(vehiculeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule introuvable: " + vehiculeId));
    }

    record CommissionResolution(BigDecimal commissionRate, Long commissionAmount) {
    }

    /**
     * Commission partenaire (decision utilisateur du 2026-09-25, "saisie 2 500, base 2 000") -
     * commissionAmount (commission AssurZen) et partnerShare (commission partenaire) sont deux
     * montants distincts, partnerShare n'est plus jamais derive de premium - commissionAmount. Ordre :
     * 1. valeur fournie par l'appelant, conservee telle quelle ;
     * 2. valeur deja enregistree (modification sans changement de partenaire uniquement) ;
     * 3. commission configuree (PartenaireProduitCommission : partenaire precis, sinon son type) ;
     * 4. sinon 400 - aucun montant invente (il serait verse tel quel par SycaPay).
     * Sans partenaire : 0, personne a qui la verser.
     */
    Long resolvePartnerShare(Long productId, Partenaire partenaire, Long requestedPartnerShare,
                             Long valeurExistante, LocalDate dateReference) {
        if (partenaire == null) {
            return 0L;
        }
        if (requestedPartnerShare != null) {
            return requestedPartnerShare;
        }
        if (valeurExistante != null) {
            return valeurExistante;
        }
        return partenaireProduitCommissionService.resoudrePourPartenaire(productId, partenaire, dateReference)
                .map(PartenaireProduitCommission::getMontant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "partnerShare est obligatoire : aucune commission partenaire configuree pour le produit " + productId
                                + " et le partenaire " + partenaire.getId() + " (ni pour son type " + partenaire.getType() + ")"));
    }

    /**
     * Bornes des montants saisis (FCFA entiers) : un montant negatif ou superieur a la prime est une
     * erreur de saisie, rejetee en 400 plutot que stockee puis versee telle quelle (paiements SycaPay
     * automatiques). Seules les valeurs FOURNIES sont controlees, pas celles resolues depuis la
     * configuration.
     */
    void validerMontantsSaisis(ContractRequest request) {
        long premium = request.premium() == null ? 0L : request.premium();
        verifierBorne("commissionAmount", request.commissionAmount(), premium);
        verifierBorne("partnerShare", request.partnerShare(), premium);
        verifierBorne("commercialCommissionAmount", request.commercialCommissionAmount(), premium);
    }

    /**
     * Regle metier du 2026-09-25 : la part partenaire ne peut pas depasser la commission du contrat
     * (commissionAmount). Refus en 400 dans tous les cas, y compris quand la part vient de la
     * commission configuree (import en masse sans part_partenaire) - jamais de plafonnement silencieux.
     * origine : d'ou vient la part, pour un message que l'utilisateur peut corriger.
     */
    static void verifierPartSousCommission(Long partnerShare, Long commissionAmount, OriginePart origine) {
        if (partnerShare == null || commissionAmount == null || partnerShare <= commissionAmount) {
            return;
        }
        String part = CommissionMontants.formater(partnerShare);
        String commission = CommissionMontants.formater(commissionAmount);
        String message = switch (origine) {
            case SAISIE -> "partnerShare (" + part + " FCFA) ne peut pas depasser la commission du contrat ("
                    + commission + " FCFA)";
            case CONFIGUREE -> "La commission partenaire configuree pour ce produit (" + part
                    + " FCFA) depasse la commission du contrat (" + commission
                    + " FCFA) : renseignez partnerShare (au plus " + commission + " FCFA) ou corrigez la configuration";
            case ENREGISTREE -> "La part partenaire enregistree (" + part + " FCFA) depasse la nouvelle commission du contrat ("
                    + commission + " FCFA) : renseignez partnerShare (au plus " + commission + " FCFA)";
        };
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    enum OriginePart { SAISIE, CONFIGUREE, ENREGISTREE }

    private static OriginePart originePart(Long requestedPartnerShare, Long valeurExistante) {
        if (requestedPartnerShare != null) {
            return OriginePart.SAISIE;
        }
        return valeurExistante != null ? OriginePart.ENREGISTREE : OriginePart.CONFIGUREE;
    }

    private static void verifierBorne(String champ, Long valeur, long premium) {
        if (valeur != null && (valeur < 0 || valeur > premium)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    champ + " doit etre compris entre 0 et la prime (" + premium + " FCFA), recu : " + valeur);
        }
    }

    /**
     * Le taux/montant de commission est connu a l'avance en fonction du produit et du type de
     * partenaire (table t_assur_zen_produit_commission_taux) : si l'appelant ne fournit pas
     * commissionRate/commissionAmount, on les resout automatiquement depuis cette table plutot que
     * d'exiger une saisie manuelle redondante. Si l'appelant fournit explicitement une valeur, elle
     * est respectee telle quelle (derogation possible). Selon le produit, le taux peut etre exprime
     * en pourcentage (libTauxCommission = "%") ou en montant fixe FCFA (ex: Assurance Moto) : les deux
     * formes sont gerees ici.
     * Sans partenaire lie au contrat, aucun type de partenaire n'est connu : la saisie manuelle reste
     * obligatoire dans ce cas.
     */
    /**
     * Correctif du 2026-09-09 (bug reel signale par l'utilisateur) : quand un SEUL des deux champs
     * (commissionRate ou commissionAmount) est fourni par l'appelant, l'autre doit se deriver de CELUI
     * fourni - jamais du taux configure en base (t_assur_zen_produit_commission_taux). Avant ce
     * correctif, saisir uniquement un taux personnalise conservait bien ce taux mais recalculait le
     * montant depuis le taux DE LA TABLE (pas depuis le taux saisi), produisant un couple taux/montant
     * incoherent et un montant different de celui attendu. Le taux de la table n'est desormais utilise
     * QUE quand ni l'un ni l'autre champ n'est fourni (resolution 100% automatique).
     */
    private CommissionResolution resolveCommission(Long productId, Partenaire partenaire, Long premium,
                                                     BigDecimal requestedRate, Long requestedAmount) {
        if (requestedRate != null && requestedAmount != null) {
            return new CommissionResolution(requestedRate, requestedAmount);
        }
        long premiumValue = premium == null ? 0L : premium;
        if (requestedRate != null) {
            long derivedAmount = requestedRate.multiply(BigDecimal.valueOf(premiumValue)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return new CommissionResolution(requestedRate, derivedAmount);
        }
        if (requestedAmount != null) {
            BigDecimal derivedRate = premiumValue == 0L
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(requestedAmount).divide(BigDecimal.valueOf(premiumValue), 4, RoundingMode.HALF_UP);
            return new CommissionResolution(derivedRate, requestedAmount);
        }
        if (partenaire == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "commissionRate et commissionAmount sont obligatoires quand aucun partenaire n'est lie au contrat (taux non determinable automatiquement)");
        }

        ProduitCommissionTaux taux = produitCommissionTauxRepository.findByProduitIdAndTypePartenaire(productId, partenaire.getType())
                .filter(ProduitCommissionTaux::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun taux de commission configure pour ce produit et le type de partenaire " + partenaire.getType()));

        BigDecimal computedRate;
        long computedAmount;
        if ("%".equals(taux.getLibTauxCommission())) {
            computedRate = taux.getTauxCommission().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            computedAmount = computedRate.multiply(BigDecimal.valueOf(premium)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } else {
            computedAmount = taux.getTauxCommission().setScale(0, RoundingMode.HALF_UP).longValueExact();
            computedRate = (premium == null || premium == 0L)
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(computedAmount).divide(BigDecimal.valueOf(premium), 4, RoundingMode.HALF_UP);
        }

        return new CommissionResolution(computedRate, computedAmount);
    }

    /**
     * Commission versee au commercial interne (Contract.salesRep) qui a vendu ce contrat, en plus de
     * son salaire+bonus mensuel (contrat "Commission commerciale interne", 2026-09-08). Auto-resolue
     * depuis ProduitCommissionCommercial UNIQUEMENT pour une vente directe (partenaire == null) - vente
     * via partenaire : la commission revient au partenaire (partnerShare), pas au commercial qui l'a
     * simplement traite techniquement (meme logique d'exclusion mutuelle que resolvePartnerShare).
     * Contrairement a resolveCommission() ci-dessus, l'absence de configuration pour ce produit n'est
     * PAS bloquante (0 par defaut) : exiger une saisie manuelle aurait casse tous les flux de creation
     * de contrat direct existants avant ce lot.
     */
    CommissionResolution resolveCommercialCommission(Long productId, Partenaire partenaire, Long premium,
                                                     Long requestedAmount, Long salesRepId, Long valeurExistante,
                                                     LocalDate dateReference) {
        if (partenaire != null) {
            if (requestedAmount != null && requestedAmount != 0L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "commercialCommissionAmount doit etre nul ou absent quand un partenaire est lie au contrat (la commission revient au partenaire)");
            }
            return new CommissionResolution(null, 0L);
        }
        // Decision utilisateur du 2026-09-25 : saisie explicite > valeur deja enregistree (modification)
        // > commission configuree pour ce commercial > commission commerciale par defaut du produit > 0.
        Long montantRetenu = requestedAmount != null ? requestedAmount
                : valeurExistante != null ? valeurExistante
                : partenaireProduitCommissionService.resoudrePourCommercial(productId, salesRepId, dateReference)
                        .map(PartenaireProduitCommission::getMontant)
                        .orElse(null);
        if (montantRetenu != null) {
            BigDecimal derivedRate = (premium == null || premium == 0L)
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(montantRetenu).divide(BigDecimal.valueOf(premium), 4, RoundingMode.HALF_UP);
            return new CommissionResolution(derivedRate, montantRetenu);
        }

        ProduitCommissionCommercial taux = produitCommissionCommercialRepository.findByProduitId(productId)
                .filter(ProduitCommissionCommercial::isActive)
                .orElse(null);
        if (taux == null) {
            return new CommissionResolution(null, 0L);
        }

        BigDecimal computedRate;
        long computedAmount;
        if ("%".equals(taux.getLibTauxCommission())) {
            computedRate = taux.getTauxCommission().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            computedAmount = computedRate.multiply(BigDecimal.valueOf(premium == null ? 0L : premium)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } else {
            computedAmount = taux.getTauxCommission().setScale(0, RoundingMode.HALF_UP).longValueExact();
            computedRate = (premium == null || premium == 0L)
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(computedAmount).divide(BigDecimal.valueOf(premium), 4, RoundingMode.HALF_UP);
        }

        return new CommissionResolution(computedRate, computedAmount);
    }

    /**
     * Resout le contrat precedent qu'un contrat renouvelle. currentContractId est null a la creation
     * (rien a exclure) et vaut l'id du contrat en cours de modification a la mise a jour, pour rejeter
     * un auto-lien. On rejette aussi le rattachement a un contrat precedent deja renouvele par un autre
     * contrat : la relation de renouvellement est 1-1 (une seule "suite" par contrat).
     */
    private Contract resolveContratPrecedent(Long contratPrecedentId, Long currentContractId) {
        if (contratPrecedentId == null) {
            return null;
        }
        if (contratPrecedentId.equals(currentContractId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un contrat ne peut pas se renouveler lui-meme");
        }
        Contract contratPrecedent = contractRepository.findById(contratPrecedentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat precedent introuvable: " + contratPrecedentId));

        contractRepository.findByContratPrecedentId(contratPrecedentId)
                .filter(existingRenewal -> !existingRenewal.getId().equals(currentContractId))
                .ifPresent(existingRenewal -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Le contrat " + contratPrecedent.getPolicyNumber() + " est deja renouvele par le contrat " + existingRenewal.getPolicyNumber());
                });

        return contratPrecedent;
    }

    /**
     * Si un exerciceComptableId est fourni explicitement, on l'utilise (et on verifie qu'il existe).
     * Sinon, on essaie de deduire automatiquement l'exercice comptable dont la periode contient la
     * date de reference du contrat (signedAt, ou a defaut dateEffet), afin que tout contrat garde une
     * tracabilite de l'exercice durant lequel il a ete conclu, meme sans saisie explicite.
     */
    private ExerciceComptable resolveExerciceComptable(Long exerciceComptableId, LocalDate referenceDate) {
        if (exerciceComptableId != null) {
            return exerciceComptableRepository.findById(exerciceComptableId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice comptable introuvable: " + exerciceComptableId));
        }
        if (referenceDate == null) {
            return null;
        }
        List<ExerciceComptable> candidats = exerciceComptableRepository
                .findByDateDebutLessThanEqualAndDateFinGreaterThanEqual(referenceDate, referenceDate);
        return candidats.stream()
                .filter(ExerciceComptable::isActive)
                .findFirst()
                .orElse(candidats.isEmpty() ? null : candidats.get(0));
    }

    /**
     * Un contrat signe a toujours une date de signature (2026-10-04) : celle fournie, sinon celle deja
     * enregistree, sinon aujourd'hui. Auparavant signedAt n'etait rempli que s'il etait envoye, et une
     * modification sans signedAt l'effacait : 360 contrats signes sur 449 n'en avaient pas sur la copie
     * de la prod, ignores par la performance/bonus des commerciaux (CommercialProductionService) qui
     * decoupe sur signedAt. Contrat non signe : comportement inchange (valeur fournie).
     */
    static LocalDate signedAtEffectif(ContractStatus statut, LocalDate demandee, LocalDate existante) {
        if (demandee != null || statut != ContractStatus.signed) {
            return demandee;
        }
        return existante != null ? existante : LocalDate.now();
    }

    private LocalDate referenceDateForExercice(LocalDate signedAt, LocalDate dateEffet) {
        return signedAt != null ? signedAt : dateEffet;
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commercial introuvable: " + userId));
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        String name = safe(() -> profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElse(user.getUsername()));
        return name == null ? "" : name;
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return safe(() -> insured.getPrenoms() + " " + insured.getNom());
    }

    private String insuredPhone(Insured insured) {
        return insured == null ? null : safe(insured::getClientPhone);
    }

    private String insuredPhone2(Insured insured) {
        return insured == null ? null : insured.getClientPhone2();
    }

    /**
     * En mode fractionne, le nombre d'echeances et la periodicite sont indispensables pour generer
     * l'echeancier : on refuse la souscription plutot que de laisser un contrat fractionne sans echeancier.
     */
    private void validateFractionnement(Contract contract) {
        if (contract.getModePaiement() == ModePaiement.FRACTIONNE) {
            if (contract.getNombreEcheances() == null || contract.getNombreEcheances() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nombre d'echeances doit etre >= 2 pour un paiement fractionne");
            }
            if (contract.getPeriodiciteEcheances() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La periodicite des echeances est obligatoire pour un paiement fractionne");
            }
        }
    }

    private String partenaireName(Partenaire partenaire) {
        if (partenaire == null) {
            return null;
        }
        return safe(() -> partenaire.getPrenoms() + " " + partenaire.getNom());
    }

    /**
     * Bug reel signale par l'utilisateur le 2026-09-08 : GET /contracts et GET /contracts/{id} en 500
     * sur tout contrat dont une FK (insured_id, partenaire_id, compagnie_id, product_id, vehicule_id,
     * sales_rep_id...) pointe vers une ligne depuis supprimee (reference orpheline - 24 contrats
     * concernes en dev, ex. insured_id=3 sur un Insured supprime). Le proxy Hibernate de l'association
     * lazy n'est JAMAIS null tant que la FK elle-meme est non-null (meme si la ligne ciblee n'existe
     * plus) - seule la lecture d'un champ AUTRE que l'id declenche l'initialisation et leve
     * EntityNotFoundException. Un contrat dont AUCUNE FK n'est orpheline n'est pas affecte - bug
     * pre-existant (donnees de test), sans rapport avec la commission commerciale du meme jour.
     * Deplace vers SafeAccess (2026-09-14) - meme garde-fou desormais partage par 10 services au lieu
     * d'etre duplique independamment.
     */

    private ContractResponse toResponse(Contract contract) {
        Contract renouvellement = contractRepository.findByContratPrecedentId(contract.getId()).orElse(null);
        Produit product = contract.getProduct();
        Insured insured = contract.getInsured();
        Vehicule vehicule = contract.getVehicule();
        Partenaire partenaire = contract.getPartenaire();
        Compagnie compagnie = contract.getCompagnie();
        ExerciceComptable exercice = contract.getExerciceComptable();
        Contract contratPrecedent = contract.getContratPrecedent();

        return new ContractResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getDateEffet(),
                contract.getDuree(),
                contract.getDateEcheance(),
                product == null ? null : safe(product::getId),
                product == null ? null : safe(product::getNomProduit),
                insured == null ? null : safe(insured::getId),
                insuredName(insured),
                vehicule == null ? null : safe(vehicule::getId),
                vehicule == null ? null : safe(vehicule::getImmatriculation),
                partenaire == null ? null : safe(partenaire::getId),
                partenaireName(partenaire),
                compagnie == null ? null : safe(compagnie::getId),
                compagnie == null ? null : safe(compagnie::getNom),
                contract.getPremium(),
                contract.getCommissionRate(),
                contract.getCommissionAmount(),
                contract.getPartnerShare(),
                contract.getStatus(),
                contract.getCommissionStatus(),
                contract.getSignedAt(),
                contract.getCreatedAt(),
                contract.getSalesRep() == null ? null : safe(contract.getSalesRep()::getId),
                displayName(contract.getSalesRep()),
                contract.getNotes(),
                exercice == null ? null : safe(exercice::getId),
                exercice == null ? null : safe(exercice::getLibelle),
                contract.getModePaiement(),
                contract.getNombreEcheances(),
                contract.getPeriodiciteEcheances(),
                contract.getMontantDejaVerse(),
                contratPrecedent == null ? null : safe(contratPrecedent::getId),
                contratPrecedent == null ? null : safe(contratPrecedent::getPolicyNumber),
                renouvellement == null ? null : renouvellement.getId(),
                renouvellement == null ? null : renouvellement.getPolicyNumber(),
                insured == null ? null : safe(insured::getPrenoms),
                insured == null ? null : safe(insured::getNom),
                insured == null ? null : safe(insured::getClientPhone),
                insured == null ? null : safe(insured::getClientCity),
                contract.getPrimeReversementStatus(),
                contract.getCommercialCommissionRate(),
                contract.getCommercialCommissionAmount(),
                contract.getCommercialCommissionStatus()
        );
    }

    /**
     * Marque la prime d'un contrat comme reversee a la compagnie par l'intermediaire qui l'a encaissee
     * (partenaire, ou commercial interne pour une vente directe depuis le 2026-09-25). Validation
     * manuelle admin/finance exclusivement (decision produit du 2026-08-22), pas de detection
     * automatique ni de delai de grace. Contrat "Espace Partenaire" - reversement de prime.
     */
    @Transactional
    public ContractResponse reverserPrime(Long id) {
        requireAdmin();
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + id));

        if (!aUnIntermediaire(contract)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " n'est rattache ni a un partenaire ni a un commercial");
        }
        if (contract.getStatus() != ContractStatus.signed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " n'est pas signe (statut: " + contract.getStatus() + ")");
        }
        if (contract.getPrimeReversementStatus() == StatutReversementPrime.reverse) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La prime du contrat " + contract.getPolicyNumber() + " a deja ete reversee");
        }

        contract.setPrimeReversementStatus(StatutReversementPrime.reverse);
        return toResponse(contractRepository.save(contract));
    }

    /**
     * Contrats signes d'un partenaire donne dont la prime encaissee n'a pas encore ete reversee a la
     * compagnie, pagine. 404 si le partenaire n'existe pas. Meme portee que findByPartenaire (admin,
     * le partenaire lui-meme, ou commercial scope a ses propres contrats).
     */
    public PageResponse<ContractResponse> findPrimeNonReverseeByPartenaire(Long partenaireId, Integer page, Integer size) {
        verifierAccesPartenaireCible(partenaireId);
        partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        return paginate(filterPrimeNonReverseeByPartenaire(partenaireId), page, size);
    }

    /**
     * Vue globale, tous intermediaires confondus (partenaires et commerciaux internes), des contrats
     * signes dont la prime n'a pas encore ete reversee - reserve aux administrateurs.
     */
    public PageResponse<ContractResponse> findPrimeNonReverseeGlobal(Integer page, Integer size) {
        requireAdmin();
        return paginate(filterPrimeNonReversee(), page, size);
    }

    private List<ContractResponse> filterPrimeNonReverseeByPartenaire(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        boolean estCePartenaire = !admin && estPartenaireConnecte(principal, partenaireId);

        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(contract -> admin || estCePartenaire
                        || (contract.getSalesRep() != null && principal.userId().equals(safe(contract.getSalesRep()::getId))))
                .filter(contract -> contract.getPartenaire() != null && partenaireId.equals(safe(contract.getPartenaire()::getId)))
                .filter(this::estPrimeNonReversee)
                .map(this::toResponse)
                .toList();
    }

    private List<ContractResponse> filterPrimeNonReversee() {
        return contractRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(ContractsService::aUnIntermediaire)
                .filter(this::estPrimeNonReversee)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Contrat dont la prime a ete encaissee par un intermediaire qui doit la reverser a la compagnie :
     * un partenaire, ou un commercial interne pour une vente directe (2026-09-25). "Commercial" = le
     * vendeur (salesRep) a le role COMMERCIAL : salesRep est renseigne sur TOUS les contrats (utilisateur
     * connecte a la saisie), une vente saisie par un administrateur n'est pas encaissee par un
     * intermediaire (428 contrats sur 449 en prod au 2026-09-25).
     */
    static boolean aUnIntermediaire(Contract contract) {
        if (contract.getPartenaire() != null) {
            return true;
        }
        User vendeur = contract.getSalesRep();
        String roles = vendeur == null ? null : safe(vendeur::getRoles);
        return roles != null && roles.toUpperCase(Locale.ROOT).contains("COMMERCIAL");
    }

    /**
     * Statut de reversement a la creation quand l'appelant ne le fournit pas (decision utilisateur du
     * 2026-09-25) : non_reverse si un intermediaire (partenaire ou commercial) a encaisse la prime et doit
     * la reverser, reverse sinon - une vente sans intermediaire n'a rien a reverser et ne doit pas
     * apparaitre dans les primes non reversees.
     */
    static StatutReversementPrime statutReversementParDefaut(Contract contract) {
        return aUnIntermediaire(contract) ? StatutReversementPrime.non_reverse : StatutReversementPrime.reverse;
    }

    private boolean estPrimeNonReversee(Contract contract) {
        return contract.getStatus() == ContractStatus.signed
                && contract.getPrimeReversementStatus() == StatutReversementPrime.non_reverse;
    }
}
