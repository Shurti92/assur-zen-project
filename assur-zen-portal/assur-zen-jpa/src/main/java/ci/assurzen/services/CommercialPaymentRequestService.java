package ci.assurzen.services;

import ci.assurzen.dto.CommercialPaymentRequestResponse;
import ci.assurzen.dto.CommercialPaymentRequestsResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.PaymentRequestType;
import ci.assurzen.exception.CommercialPaymentRequestException;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Demande de paiement de commission d'un commercial interne (contrat
 * docs/api-contracts/commercial-demande-paiement-commission.md, 2026-09-25) - self-service, le
 * commercial est deduit du JWT (aucun id en entree), comme /commercials/me/commissions.
 * - TOTAL : tout le solde disponible ; PARTIAL : montant libre entre 1 et le solde disponible, sans
 *   plafond en pourcentage ni limite de frequence (decisions produit du 2026-09-25).
 * - solde disponible = commission commerciale en attente MOINS les demandes deja accordees (pending
 *   ET paid) : la validation ne marque pas les contrats payes, un montant deja verse ne doit pas
 *   redevenir disponible (meme regle que le solde net partenaire, CommissionPartialPaymentService).
 * - versement sur le telephone du commercial (Profile.phone), fige au moment de la demande.
 * - validation par l'admin via le flux partenaire existant (CommissionPartialPaymentService.valider).
 */
@Service
@RequiredArgsConstructor
public class CommercialPaymentRequestService {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");
    private static final long FENETRE_DEDUPLICATION_SECONDES = 10L;
    /** Tout montant demande reste reserve : en attente, en cours de virement, en echec (relancable) ou paye. */
    private static final List<PartialPaymentRequestStatus> STATUTS_ACCORDES = List.of(PartialPaymentRequestStatus.values());

    private final CommissionPartialPaymentRequestRepository repository;
    private final CommercialRepository commercialRepository;
    private final CommercialCommissionService commercialCommissionService;
    private final AuthService authService;
    private final TelegramNotificationService telegramNotificationService;

    public CommercialPaymentRequestsResponse mesDemandes() {
        Commercial commercial = commercialConnecte();
        List<CommercialPaymentRequestResponse> historique = repository.findByCommercialIdOrderByRequestedAtDesc(commercial.getId())
                .stream()
                .map(CommercialPaymentRequestService::toResponse)
                .toList();
        return new CommercialPaymentRequestsResponse(Math.max(0, soldeDisponible(commercial)), historique);
    }

    /** idempotencyKey : header "Idempotency-Key" - un rejeu de la meme cle renvoie la demande deja creee. */
    @Transactional
    public CommercialPaymentRequestResponse demander(PaymentRequestType type, Long montant, String idempotencyKey) {
        Commercial commercial = commercialConnecte();
        // Verrou AVANT toute lecture du solde (meme raison que CommissionPartialPaymentService :
        // sous REPEATABLE READ la vue de la transaction se fige a la premiere lecture non verrouillee).
        repository.lockToutesPourCommercial(commercial.getId());

        if (type == null) {
            throw new CommercialPaymentRequestException(HttpStatus.BAD_REQUEST, "INVALID_TYPE",
                    "Le type de demande est obligatoire (TOTAL ou PARTIAL)");
        }
        if (type == PaymentRequestType.PARTIAL && (montant == null || montant <= 0)) {
            throw new CommercialPaymentRequestException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AMOUNT",
                    "Le montant demande doit etre un entier positif");
        }

        Optional<CommissionPartialPaymentRequest> dejaCreee = demandeDejaCreee(commercial, type, montant, idempotencyKey);
        if (dejaCreee.isPresent()) {
            return toResponse(dejaCreee.get());
        }

        long soldeAvant = soldeDisponible(commercial);
        if (soldeAvant <= 0) {
            throw new CommercialPaymentRequestException(HttpStatus.CONFLICT, "NOTHING_TO_PAY",
                    "Aucune commission disponible a demander");
        }
        long montantDemande = type == PaymentRequestType.TOTAL ? soldeAvant : montant;
        if (montantDemande > soldeAvant) {
            throw new CommercialPaymentRequestException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_EXCEEDS_BALANCE",
                    "Le montant demande (" + CommissionMontants.formater(montantDemande)
                            + " FCFA) depasse votre solde disponible (" + CommissionMontants.formater(soldeAvant) + " FCFA)");
        }

        CommissionPartialPaymentRequest saved = repository.save(CommissionPartialPaymentRequest.builder()
                .commercial(commercial)
                .type(type)
                .requestedAmount(montantDemande)
                .pendingBalanceBefore(soldeAvant)
                .pendingBalanceAfter(soldeAvant - montantDemande)
                .status(PartialPaymentRequestStatus.pending)
                .numeroWaveCommissionSnapshot(commercial.getProfile().getPhone())
                .idempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null)
                .build());

        telegramNotificationService.envoyer(messageNotification(commercial, saved));
        return toResponse(saved);
    }

    /** Idempotence par cle client, sinon deduplication d'un double-clic (meme montant sous 10 s). Une
     * cle rejouee pour une demande differente est ambigue : 409. */
    private Optional<CommissionPartialPaymentRequest> demandeDejaCreee(Commercial commercial, PaymentRequestType type,
                                                                      Long montant, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<CommissionPartialPaymentRequest> existante =
                    repository.findByCommercialIdAndIdempotencyKey(commercial.getId(), idempotencyKey);
            existante.ifPresent(demande -> {
                boolean memeDemande = demande.getType() == type
                        && (type == PaymentRequestType.TOTAL || demande.getRequestedAmount().equals(montant));
                if (!memeDemande) {
                    throw new CommercialPaymentRequestException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                            "Cle d'idempotence deja utilisee pour une demande differente");
                }
            });
            return existante;
        }
        if (type == PaymentRequestType.PARTIAL) {
            return repository.findFirstByCommercialIdAndRequestedAmountAndRequestedAtAfterOrderByRequestedAtDesc(
                    commercial.getId(), montant, Instant.now().minusSeconds(FENETRE_DEDUPLICATION_SECONDES));
        }
        return Optional.empty();
    }

    long soldeDisponible(Commercial commercial) {
        long dejaAccorde = repository.findByCommercialIdAndStatusIn(commercial.getId(), STATUTS_ACCORDES).stream()
                .mapToLong(CommissionPartialPaymentRequest::getRequestedAmount)
                .sum();
        return commercialCommissionService.totalPendingFor(commercial) - dejaAccorde;
    }

    /** Reserve au role COMMERCIAL (contrat : tout autre role -> 403), puis sa fiche commerciale. */
    private Commercial commercialConnecte() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("COMMERCIAL")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux commerciaux");
        }
        return commercialRepository.findByProfile_UserId(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche commerciale liee a cet utilisateur"));
    }

    /** Best-effort (TelegramNotificationService ne fait jamais echouer la demande), meme format que la
     * notification des demandes partenaire. */
    private String messageNotification(Commercial commercial, CommissionPartialPaymentRequest saved) {
        String nom = TelegramNotificationService.echapperHtml(
                commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname());
        String telephone = TelegramNotificationService.echapperHtml(
                StringUtils.hasText(commercial.getProfile().getPhone()) ? commercial.getProfile().getPhone() : "non renseigne");
        String date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE_ID)
                .format(saved.getRequestedAt() != null ? saved.getRequestedAt() : Instant.now());
        return "Nouvelle demande de paiement de commission (" + (saved.getType() == PaymentRequestType.TOTAL ? "totale" : "partielle")
                + ") : " + saved.getRequestedAmount() + " FCFA \n"
                + "- Commercial : <b>" + nom + " (" + telephone + ")</b> \n"
                + "- date et heure: " + date + " \n"
                + "- Solde restant apres : " + saved.getPendingBalanceAfter() + " FCFA";
    }

    static CommercialPaymentRequestResponse toResponse(CommissionPartialPaymentRequest request) {
        return new CommercialPaymentRequestResponse(request.getId(), request.getType(), request.getRequestedAmount(),
                request.getRequestedAt(), request.getStatus());
    }
}
