package ci.assurzen.services;

import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.events.NotificationUtilisateurEvent;
import org.springframework.context.ApplicationEventPublisher;
import ci.assurzen.dto.PartialPaymentRequestResponse;
import ci.assurzen.dto.PartialPaymentStatusResponse;
import ci.assurzen.dto.PaymentRequestValidationItemResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.exception.DailyPartialPaymentLimitReachedException;
import ci.assurzen.exception.PartialPaymentAmountExceedsCapException;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.utils.SafeAccess;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Demande de versement anticipe d'une partie du solde de commissions en attente d'un partenaire
 * (contrat "Espace Partenaire" §2, decisions tranchees le 2026-08-21) :
 * - base de calcul : solde net = CommissionService.totalPendingFor(partenaireId) (somme des
 *   Contract.partnerShare des contrats signed/pending) MOINS ce qui a deja ete accorde via des
 *   demandes anterieures (pending ou paid) - permet au solde de decroitre a chaque demande sans
 *   toucher a Contract.commissionStatus (qui reste gere exclusivement par CommissionService).
 * - chaque demande verse 40% du solde net, sauf si ce solde est <= au seuil plancher configurable
 *   (cle dynamique COMMISSION_PARTIAL_PAYMENT_FLOOR, defaut 5000 FCFA - AUCUN seuil metier n'existait
 *   avant cette feature, valeur proposee a confirmer avec le produit/finance) : dans ce cas le solde
 *   entier est verse pour permettre l'epuisement complet.
 * - maximum 2 demandes par jour par partenaire (jour calendaire Africa/Abidjan).
 * - une demande reste "pending" jusqu'a validation manuelle (admin/finance, cf. valider()) - pas de
 *   versement automatique, coherent avec POST /commissions/pay qui est deja reserve aux admins.
 * - CommissionService.pay() (paiement admin par contrat) est bloque tant qu'une demande est EN ATTENTE ;
 *   les demandes payees sont ensuite des avances imputees sur les contrats payes par l'admin
 *   (montantImpute, 2026-09-26) - jamais de double versement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionPartialPaymentService {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");
    private static final int MAX_REQUESTS_PER_DAY = 2;
    private static final long DEFAULT_FLOOR_THRESHOLD = 5000L;
    private static final long FENETRE_DEDUPLICATION_SECONDES = 10L;
    private static final String MODE_VIREMENT = CommissionService.MODE_VIREMENT;
    private static final String MODE_HORS_APPLICATION = CommissionService.MODE_HORS_APPLICATION;

    private final CommissionPartialPaymentRequestRepository repository;
    private final PartenaireRepository partenaireRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CommissionService commissionService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final AuthService authService;
    private final TelegramNotificationService telegramNotificationService;
    private final SycaPayProperties sycaPayProperties;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final SmsNotificationService smsNotificationService;
    private final PlatformTransactionManager transactionManager;
    private final LedgerService ledgerService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * montantDemande est desormais saisi par le partenaire (contrat "Espace Partenaire" §2,
     * complement du 2026-08-21) - le serveur ne l'accepte JAMAIS tel quel : il revalide
     * systematiquement qu'il ne depasse pas le plafond de 40% du solde net (ou le solde entier si
     * celui-ci est sous le seuil plancher, meme logique qu'avant pour permettre l'epuisement complet
     * d'un petit reliquat). Rejet avec un code dedie (AMOUNT_EXCEEDS_CAP) si depassement, sans jamais
     * faire confiance a une validation cote app mobile.
     */
    @Transactional
    public PartialPaymentRequestResponse demanderPaiementPartiel(Long partenaireId, Long montantDemande) {
        return demanderPaiementPartiel(partenaireId, montantDemande, null);
    }

    /**
     * idempotencyKey : header "Idempotency-Key" (UUID v4) fourni par le client, recommande mais pas
     * encore obligatoire (accord equipe frontend/securite du 2026-08-23). Remplace la deduplication par
     * fenetre de 10s ci-dessous quand fourni - un rejeu de la meme cle pour ce partenaire renvoie la
     * demande deja creee (meme montant) ou 409 (montant different, cle reutilisee de facon ambigue).
     */
    @Transactional
    public PartialPaymentRequestResponse demanderPaiementPartiel(Long partenaireId, Long montantDemande, String idempotencyKey) {
        // Verrou pessimiste EN TOUT PREMIER, avant TOUTE autre lecture en base - y compris
        // verifierAcces()/la deduplication ci-dessous. Sous REPEATABLE READ (isolation par defaut
        // MariaDB/InnoDB), la "vue coherente" d'une transaction se fige des sa PREMIERE lecture NON
        // verrouillee (un SELECT ... FOR UPDATE n'y participe pas, il lit toujours la derniere version
        // committee). Verifie en live le 2026-08-23 : avec verifierAcces()/resolvePartenaire() (deux
        // SELECT simples) executes AVANT ce verrou, 5 requetes concurrentes reussissaient TOUTES avec
        // exactement le meme solde de depart - chaque transaction avait deja fige sa vue au moment de
        // ce premier SELECT anodin, AVANT meme de tenter le verrou, rendant celui-ci inutile pour les
        // lectures posterieures (comptage du jour, calcul du solde net) meme si le verrou lui-meme
        // fonctionnait correctement cote base (confirme via information_schema.innodb_trx : la 2e
        // transaction attendait bien en LOCK WAIT). Deplacer ce verrou en toute premiere position
        // regle le probleme : la vue de la transaction ne se fige qu'apres avoir attendu son tour.
        repository.lockToutesPourPartenaire(partenaireId);

        verifierAcces(partenaireId);

        if (montantDemande == null || montantDemande <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant demande doit etre positif");
        }

        if (StringUtils.hasText(idempotencyKey)) {
            // Idempotence par cle client (accord equipe frontend/securite du 2026-08-23) : prioritaire
            // sur la fenetre de 10s ci-dessous quand le header est fourni.
            Optional<CommissionPartialPaymentRequest> existante = repository
                    .findByPartenaireIdAndIdempotencyKey(partenaireId, idempotencyKey);
            if (existante.isPresent()) {
                if (!existante.get().getRequestedAmount().equals(montantDemande)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cle d'idempotence deja utilisee pour un montant different");
                }
                return toResponse(existante.get());
            }
        } else {
            // Deduplication (double-clic / retry reseau immediat) : si une demande identique (meme
            // partenaire, meme montant) vient d'etre creee il y a moins de FENETRE_DEDUPLICATION_SECONDES,
            // on renvoie celle-ci au lieu d'en creer une seconde - faille "idempotence" remontee par
            // l'audit de securite pre-production du 2026-08-23. Filet historique pour les clients qui
            // n'envoient pas encore le header Idempotency-Key.
            Instant depuis = Instant.now().minusSeconds(FENETRE_DEDUPLICATION_SECONDES);
            Optional<CommissionPartialPaymentRequest> doublon = repository
                    .findFirstByPartenaireIdAndRequestedAmountAndRequestedAtAfterOrderByRequestedAtDesc(partenaireId, montantDemande, depuis);
            if (doublon.isPresent()) {
                return toResponse(doublon.get());
            }
        }

        Partenaire partenaire = resolvePartenaire(partenaireId);

        Instant debutJournee = debutJournee();
        Instant finJournee = debutJournee.plus(java.time.Duration.ofDays(1));
        long demandesAujourdhui = repository.countByPartenaireIdAndRequestedAtBetween(partenaireId, debutJournee, finJournee);
        if (demandesAujourdhui >= MAX_REQUESTS_PER_DAY) {
            throw new DailyPartialPaymentLimitReachedException(MAX_REQUESTS_PER_DAY, finJournee);
        }

        long soldeNetAvant = soldeNet(partenaireId);
        if (soldeNetAvant <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun solde de commission en attente pour ce partenaire");
        }

        long seuilPlancher = dynamicConfigurationService.getLong("COMMISSION_PARTIAL_PAYMENT_FLOOR", DEFAULT_FLOOR_THRESHOLD);
        long plafond = soldeNetAvant <= seuilPlancher ? soldeNetAvant : quarantePourcent(soldeNetAvant);
        if (montantDemande > plafond) {
            throw new PartialPaymentAmountExceedsCapException(plafond);
        }

        long soldeNetApres = soldeNetAvant - montantDemande;

        // numeroWaveCommission fige AU MOMENT DE LA DEMANDE (pas relu a la validation) - cible de
        // fraude la plus sensible du module, cf. CommissionPartialPaymentRequest.numeroWaveCommissionSnapshot.
        CommissionPartialPaymentRequest saved = repository.save(CommissionPartialPaymentRequest.builder()
                .partenaire(partenaire)
                .requestedAmount(montantDemande)
                .pendingBalanceBefore(soldeNetAvant)
                .pendingBalanceAfter(soldeNetApres)
                .status(PartialPaymentRequestStatus.pending)
                .numeroWaveCommissionSnapshot(partenaire.getNumeroWaveCommission())
                .idempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null)
                .build());

        telegramNotificationService.envoyer(construireMessageNotification(partenaire, saved));

        return toResponse(saved);
    }

    /** Best-effort (cf. TelegramNotificationService) - ne fait jamais echouer la creation de la demande.
     * Nom + telephone plutot que l'id technique du partenaire, format multi-lignes (retour utilisateur,
     * 2026-08-30) - plus lisible et exploitable directement par qui recoit la notification. */
    private String construireMessageNotification(Partenaire partenaire, CommissionPartialPaymentRequest saved) {
        String nomPartenaire = TelegramNotificationService.echapperHtml(
                (partenaire.getPrenoms() == null ? "" : partenaire.getPrenoms() + " ") + partenaire.getNom());
        String telephone = TelegramNotificationService.echapperHtml(
                StringUtils.hasText(partenaire.getTelephone()) ? partenaire.getTelephone() : "non renseigne");
        String date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZONE_ID)
                .format(saved.getRequestedAt() != null ? saved.getRequestedAt() : Instant.now());
        return "Nouvelle demande de paiement partiel : " + saved.getRequestedAmount() + " FCFA \n"
                + "- Partenaire : <b>" + nomPartenaire + " (" + telephone + ")</b> \n"
                + "- date et heure: " + date + " \n"
                + "- Solde restant apres : " + saved.getPendingBalanceAfter() + " FCFA";
    }

    public PartialPaymentStatusResponse getStatus(Long partenaireId) {
        resolvePartenaire(partenaireId);
        verifierAcces(partenaireId);

        long soldeNet = Math.max(0, soldeNet(partenaireId));

        Instant debutJournee = debutJournee();
        Instant finJournee = debutJournee.plus(java.time.Duration.ofDays(1));
        long demandesAujourdhui = repository.countByPartenaireIdAndRequestedAtBetween(partenaireId, debutJournee, finJournee);
        Instant prochaineDemandePossible = demandesAujourdhui >= MAX_REQUESTS_PER_DAY ? finJournee : null;

        List<PartialPaymentRequestResponse> historique = repository.findByPartenaireIdOrderByRequestedAtDesc(partenaireId).stream()
                .map(this::toResponse)
                .toList();

        return new PartialPaymentStatusResponse(soldeNet, demandesAujourdhui, MAX_REQUESTS_PER_DAY, prochaineDemandePossible, historique);
    }

    /**
     * Validation (admin/finance) d'une demande de paiement - avance partenaire OU demande de commercial
     * (meme table). HYBRIDE (decision utilisateur du 2026-09-26, meme regle que POST /commissions/pay) :
     * - SycaPay desactive : versement fait hors application, la demande passe "paid" (comportement historique) ;
     * - SycaPay active : VIREMENT Wave de requestedAmount (relu en base) vers le numero fige a la demande.
     *   "paid" uniquement si SycaPay confirme (SUCCESS, ou reconciliation SUCCESS) ; FAILED -> "failed"
     *   (montant toujours reserve, relancable par cette meme route) ; PROCESSING/UNKNOWN -> "processing"
     *   (a reconcilier) ; REJECTED -> aucune tentative, la demande garde son statut.
     * Pas @Transactional : verrou et changements d'etat dans des transactions courtes, jamais d'appel reseau
     * sous transaction. Ecriture au grand livre a l'avance payee (decision utilisateur du 2026-09-26),
     * deduite ensuite de l'ecriture des contrats sur lesquels l'avance est imputee.
     */
    public PartialPaymentRequestResponse valider(Long requestId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User validateur = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        Preparation preparation = new TransactionTemplate(transactionManager).execute(statut -> preparer(requestId, validateur));
        if (preparation.reponse() != null) {
            return preparation.reponse();
        }

        CommissionPartialPaymentRequest demande = preparation.demande();
        if (!StringUtils.hasText(demande.getNumeroWaveCommissionSnapshot())) {
            return rejeter(requestId, preparation.statutPrecedent(),
                    "Numero de versement non renseigne sur la demande - aucun virement n'est parti");
        }
        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(SycaPayPaymentCommand.forDemande(demande.getId(),
                    demande.getCommercial() != null ? SycaPayBeneficiaryType.COMMERCIAL : SycaPayBeneficiaryType.PARTENAIRE,
                    demande.getCommercial() == null ? null : demande.getCommercial().getId(),
                    demande.getPartenaire() == null ? null : demande.getPartenaire().getId(),
                    demande.getRequestedAmount(), preparation.nomBeneficiaire(), demande.getNumeroWaveCommissionSnapshot()));
        } catch (SycaPayException e) {
            log.warn("[COMMISSION-PARTIEL] demande {} : virement impossible ({})", requestId, e.getMessage());
            return rejeter(requestId, preparation.statutPrecedent(), "Service de paiement SycaPay indisponible - aucun virement n'est parti");
        }

        if (outcome.rejected()) {
            return rejeter(requestId, preparation.statutPrecedent(), outcome.message());
        }
        if (outcome.isSuccess()) {
            return new TransactionTemplate(transactionManager).execute(statut -> {
                CommissionPartialPaymentRequest aJour = repository.findByIdForUpdate(requestId).orElseThrow();
                return marquerPayee(aJour, validateur);
            });
        }
        if (outcome.status() == SycaPayTransactionStatus.FAILED) {
            return new TransactionTemplate(transactionManager).execute(statut -> {
                CommissionPartialPaymentRequest aJour = repository.findByIdForUpdate(requestId).orElseThrow();
                aJour.setStatus(PartialPaymentRequestStatus.failed);
                notifierEchecVirement(aJour);
                return toResponse(repository.save(aJour));
            });
        }
        // PENDING/PROCESSING/UNKNOWN : reste "processing", a reconcilier via /sycapay/transactions/{id}/reconcile.
        return toResponse(demande);
    }

    /** Etat verifie sous verrou pessimiste (evite qu'une meme demande soit versee deux fois). */
    private Preparation preparer(Long requestId, User validateur) {
        CommissionPartialPaymentRequest demande = repository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable: " + requestId));

        switch (demande.getStatus()) {
            case paid -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette demande a deja ete traitee");
            case processing -> {
                SycaPayTransaction transaction = sycaPayTransactionRepository.findByPartialPaymentRequestId(requestId).orElse(null);
                if (transaction != null && transaction.getStatus() == SycaPayTransactionStatus.SUCCESS) {
                    return Preparation.terminee(marquerPayee(demande, validateur)); // confirmation deja acquise
                }
                if (transaction == null || transaction.getStatus() != SycaPayTransactionStatus.FAILED) {
                    throw new SycaPayDuplicatePaymentException("Un virement SycaPay est deja en cours pour cette demande"
                            + (transaction == null ? "" : " (reference " + transaction.getTransactionReference() + ")")
                            + " - a reconcilier avant toute nouvelle tentative.");
                }
                // Virement en echec : relance autorisee.
            }
            default -> {
                // pending ou failed (relance)
            }
        }

        if (!sycaPayProperties.isEnabled()) {
            return Preparation.terminee(marquerPayee(demande, validateur));
        }
        PartialPaymentRequestStatus statutPrecedent = demande.getStatus() == PartialPaymentRequestStatus.processing
                ? PartialPaymentRequestStatus.failed : demande.getStatus();
        demande.setStatus(PartialPaymentRequestStatus.processing);
        CommissionPartialPaymentRequest enregistree = repository.save(demande);
        return new Preparation(null, enregistree, statutPrecedent, nomBeneficiaire(enregistree));
    }

    private record Preparation(PartialPaymentRequestResponse reponse, CommissionPartialPaymentRequest demande,
                               PartialPaymentRequestStatus statutPrecedent, String nomBeneficiaire) {
        static Preparation terminee(PartialPaymentRequestResponse reponse) {
            return new Preparation(reponse, null, null, null);
        }
    }

    /** Aucune tentative de virement : la demande retrouve son statut d'avant la validation. */
    private PartialPaymentRequestResponse rejeter(Long requestId, PartialPaymentRequestStatus statutPrecedent, String message) {
        return new TransactionTemplate(transactionManager).execute(statut -> {
            CommissionPartialPaymentRequest demande = repository.findByIdForUpdate(requestId).orElseThrow();
            demande.setStatus(statutPrecedent);
            PartialPaymentRequestResponse base = toResponse(repository.save(demande));
            return avecResultat(base, "REJECTED", null, null, message, MODE_VIREMENT);
        });
    }

    /** Passage a "paid" (+ SMS de confirmation si le versement a ete fait par virement ou a la main). */
    private PartialPaymentRequestResponse marquerPayee(CommissionPartialPaymentRequest demande, User validateur) {
        demande.setStatus(PartialPaymentRequestStatus.paid);
        demande.setValidatedBy(validateur);
        demande.setValidatedAt(Instant.now());
        // Grand livre des l'avance payee (decision utilisateur du 2026-09-26) ; marquee pour ne pas etre
        // comptee une seconde fois quand elle sera imputee sur des contrats (CommissionService.pay()).
        ledgerService.enregistrerAvanceCommission(nomBeneficiaire(demande), demande.getRequestedAmount(), demande.getId(),
                demande.getCommercial() != null);
        demande.setComptabilisee(true);
        CommissionPartialPaymentRequest enregistree = repository.save(demande);
        notifierBeneficiaire(enregistree, PartenaireNotificationType.DEMANDE_PAIEMENT_PAYEE, "Demande de paiement payée",
                "Votre demande de paiement de " + montant(enregistree.getRequestedAmount()) + " FCFA a été payée.");
        SycaPayTransaction transaction = sycaPayTransactionRepository.findByPartialPaymentRequestId(enregistree.getId()).orElse(null);
        if (transaction != null) {
            smsNotificationService.sendDemandePaiementVerseeSms(enregistree.getNumeroWaveCommissionSnapshot(),
                    enregistree.getRequestedAmount(), transaction.getTransactionReference(), enregistree.getCommercial() != null);
        }
        return toResponse(enregistree);
    }

    /** Reconciliation SUCCESS d'un virement de demande (PartenaireCommissionEventListener) - idempotent. */
    public void confirmerVirement(Long sycapayTransactionId, String confirmePar) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null || transaction.getPartialPaymentRequestId() == null
                || transaction.getStatus() != SycaPayTransactionStatus.SUCCESS) {
            return;
        }
        User validateur = StringUtils.hasText(confirmePar) ? userRepository.findByUsername(confirmePar).orElse(null) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(statut ->
                repository.findByIdForUpdate(transaction.getPartialPaymentRequestId())
                        .filter(demande -> demande.getStatus() != PartialPaymentRequestStatus.paid)
                        .ifPresent(demande -> marquerPayee(demande, validateur)));
    }

    /** Reconciliation FAILED d'un virement de demande : la demande passe en echec, relancable. */
    public void marquerEchecVirement(Long sycapayTransactionId) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null || transaction.getPartialPaymentRequestId() == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(statut ->
                repository.findByIdForUpdate(transaction.getPartialPaymentRequestId())
                        .filter(demande -> demande.getStatus() == PartialPaymentRequestStatus.processing)
                        .ifPresent(demande -> {
                            demande.setStatus(PartialPaymentRequestStatus.failed);
                            notifierEchecVirement(demande);
                            repository.save(demande);
                        }));
    }

    private void notifierEchecVirement(CommissionPartialPaymentRequest demande) {
        notifierBeneficiaire(demande, PartenaireNotificationType.DEMANDE_PAIEMENT_ECHEC, "Virement en échec",
                "Le virement de votre demande de paiement de " + montant(demande.getRequestedAmount())
                        + " FCFA n'a pas abouti. Il sera relancé.");
    }

    /** Notification in-app du beneficiaire (partenaire, ou compte utilisateur du commercial), creee apres
     * commit (NotificationUtilisateurListener) - 2026-10-03. */
    private void notifierBeneficiaire(CommissionPartialPaymentRequest demande, PartenaireNotificationType type, String titre, String message) {
        NotificationUtilisateurEvent event;
        if (demande.getPartenaire() != null) {
            event = NotificationUtilisateurEvent.pourPartenaire(demande.getPartenaire().getId(), type, titre, message,
                    demande.getRequestedAmount(), "DEMANDE_PAIEMENT", demande.getId());
        } else if (demande.getCommercial() != null && demande.getCommercial().getProfile() != null
                && demande.getCommercial().getProfile().getUser() != null) {
            event = NotificationUtilisateurEvent.pourUtilisateur(demande.getCommercial().getProfile().getUser().getId(), type,
                    titre, message, demande.getRequestedAmount(), "DEMANDE_PAIEMENT", demande.getId());
        } else {
            return;
        }
        eventPublisher.publishEvent(event);
    }

    private static String montant(Long valeur) {
        return String.format("%,d", valeur == null ? 0L : valeur).replace(',', ' ');
    }

    private static String nomBeneficiaire(CommissionPartialPaymentRequest demande) {
        if (demande.getPartenaire() != null) {
            Partenaire p = demande.getPartenaire();
            return (p.getPrenoms() == null ? "" : p.getPrenoms() + " ") + p.getNom();
        }
        Commercial c = demande.getCommercial();
        return c == null ? null : c.getProfile().getFirstname() + " " + c.getProfile().getLastname();
    }

    /** Solde net = solde brut (partnerShare des contrats pending) moins ce qui a deja ete accorde via
     * des demandes anterieures non rejetees (pending ou paid) - peut etre negatif transitoirement si
     * appele en dehors du flux normal, d'ou le Math.max(0, ...) applique par les appelants publics. */
    private long soldeNet(Long partenaireId) {
        long soldeBrut = commissionService.totalPendingFor(partenaireId);
        // Depuis l'imputation des avances (2026-09-26), la part d'une demande payee deja imputee sur des
        // contrats payes n'est plus dans soldeBrut (contrats passes "paid") : seule l'avance RESTANTE
        // (requestedAmount - montantImpute) est deduite, sinon elle le serait deux fois.
        // Une demande en attente, en cours de virement ou en echec reserve tout son montant (decision du
        // 2026-09-26 : un echec reste reserve jusqu'a sa relance).
        long dejaAccorde = repository.findByPartenaireIdAndStatusIn(partenaireId, List.of(PartialPaymentRequestStatus.values())).stream()
                .mapToLong(demande -> demande.getStatus() == PartialPaymentRequestStatus.paid
                        ? demande.avanceRestante() : demande.getRequestedAmount())
                .sum();
        return soldeBrut - dejaAccorde;
    }

    /** Admin, ou le partenaire connecte lui-meme (meme mecanisme que ContractsService.estPartenaireConnecte
     * / AuthService.buildUserResponse : relation reelle Partenaire.profile, pas de comparaison de role). */
    private void verifierAcces(Long partenaireId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        boolean estCePartenaire = profileRepository.findByUserId(principal.userId())
                .map(Profile::getId)
                .map(profileId -> partenaireRepository.findByProfile_Id(profileId))
                .map(partenaires -> partenaires.stream().anyMatch(p -> p.getId().equals(partenaireId)))
                .orElse(false);
        if (!estCePartenaire) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce partenaire ou a un administrateur");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private Instant debutJournee() {
        return LocalDate.now(ZONE_ID).atStartOfDay(ZONE_ID).toInstant();
    }

    private Partenaire resolvePartenaire(Long partenaireId) {
        return partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
    }

    /**
     * Ecran de validation admin (2026-09-25) : demandes des partenaires ET des commerciaux internes,
     * plus recentes d'abord - status null = toutes. Route sous /commissions/** (reservee ADMIN).
     */
    public List<PaymentRequestValidationItemResponse> listerPourValidation(PartialPaymentRequestStatus status) {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
        List<CommissionPartialPaymentRequest> demandes = repository.findForValidation(status);
        java.util.Map<Long, SycaPayTransaction> virements = sycaPayTransactionRepository
                .findByPartialPaymentRequestIdIn(demandes.stream().map(CommissionPartialPaymentRequest::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(SycaPayTransaction::getPartialPaymentRequestId, t -> t));
        return demandes.stream()
                .map(demande -> toValidationItem(demande, virements.get(demande.getId())))
                .toList();
    }

    private static PaymentRequestValidationItemResponse toValidationItem(CommissionPartialPaymentRequest request,
                                                                         SycaPayTransaction transaction) {
        Partenaire partenaire = request.getPartenaire();
        Commercial commercial = request.getCommercial();
        String nom = partenaire != null
                ? SafeAccess.safe(() -> partenaire.getNom() + " " + partenaire.getPrenoms())
                : commercial == null ? null
                : SafeAccess.safe(() -> commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname());
        return new PaymentRequestValidationItemResponse(
                request.getId(),
                partenaire != null ? "PARTENAIRE" : "COMMERCIAL",
                partenaire == null ? null : SafeAccess.idOf(partenaire, partenaire::getId),
                commercial == null ? null : SafeAccess.idOf(commercial, commercial::getId),
                nom,
                request.getNumeroWaveCommissionSnapshot(),
                request.getType(),
                request.getRequestedAmount(),
                request.getRequestedAt(),
                request.getStatus(),
                request.getValidatedAt(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getSycapayResponseMessage(),
                transaction != null ? MODE_VIREMENT
                        : request.getStatus() == PartialPaymentRequestStatus.paid ? MODE_HORS_APPLICATION : null);
    }

    private PartialPaymentRequestResponse toResponse(CommissionPartialPaymentRequest request) {
        SycaPayTransaction transaction = request.getId() == null ? null
                : sycaPayTransactionRepository.findByPartialPaymentRequestId(request.getId()).orElse(null);
        String mode = transaction != null ? MODE_VIREMENT
                : request.getStatus() == PartialPaymentRequestStatus.paid ? MODE_HORS_APPLICATION : null;
        return new PartialPaymentRequestResponse(
                request.getId(),
                request.getPartenaire() == null ? null : request.getPartenaire().getId(),
                request.getRequestedAmount(),
                request.getPendingBalanceBefore(),
                request.getPendingBalanceAfter(),
                request.getRequestedAt(),
                request.getStatus(),
                request.getNumeroWaveCommissionSnapshot(),
                request.getCommercial() == null ? null : request.getCommercial().getId(),
                request.getType(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getTransactionReference(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getSycapayResponseMessage(),
                mode);
    }

    private static PartialPaymentRequestResponse avecResultat(PartialPaymentRequestResponse base, String sycapayStatus,
                                                              String reference, Long pk, String message, String mode) {
        return new PartialPaymentRequestResponse(base.id(), base.partenaireId(), base.requestedAmount(), base.pendingBalanceBefore(),
                base.pendingBalanceAfter(), base.requestedAt(), base.status(), base.numeroWaveCommission(), base.commercialId(),
                base.type(), sycapayStatus, reference, pk, message, mode);
    }

    /** 40% arrondi a l'entier le plus proche (montants en FCFA, pas de decimales). */
    private long quarantePourcent(long amount) {
        return Math.round(amount * 0.40);
    }
}
