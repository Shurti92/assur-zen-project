package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.events.SycaPayPaymentConfirmedEvent;
import ci.assurzen.events.SycaPayPaymentFailedEvent;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Reconciliation manuelle des transactions PROCESSING/UNKNOWN via getStatusByOrderID (section 11 de
 * la specification - point critique pour eviter un double paiement). Volontairement declenchee a la
 * demande (endpoint ADMIN) plutot que par un job planifie automatique dans ce lot : le brief n'exige
 * pas de planification, et une reconciliation manuelle premiere version reste plus sure a auditer
 * qu'un scheduler qui pourrait, en cas de bug, relancer une reconciliation en boucle sur des
 * transactions financieres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SycaPayStatusService {

    private static final String MODULE = "SYCAPAY";
    private static final EnumSet<SycaPayTransactionStatus> RECONCILIABLE = EnumSet.of(
            SycaPayTransactionStatus.PROCESSING, SycaPayTransactionStatus.UNKNOWN);

    private final SycaPayTransactionRepository transactionRepository;
    private final SycaPayTransactionWriter writer;
    private final SycaPayClient sycaPayClient;
    private final FunctionalAuditRecorder auditRecorder;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public SycaPayTransaction reconcile(Long transactionId) {
        String confirmePar = requireAdmin().username();
        SycaPayTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction SycaPay introuvable: " + transactionId));

        if (!RECONCILIABLE.contains(transaction.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transaction " + transaction.getTransactionReference() + " en statut " + transaction.getStatus()
                            + " : rien a reconcilier (reserve a PROCESSING/UNKNOWN)");
        }

        SycaPayStatusResponse response = sycaPayClient.getStatus(transaction.getTransactionReference());
        if (response == null) {
            log.warn("[SYCAPAY] reconciliation sans reponse exploitable pour {} - statut inchange ({})",
                    transaction.getTransactionReference(), transaction.getStatus());
            return transaction;
        }

        if (response.isSuccess()) {
            SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.SUCCESS,
                    response.referenceid(), response.transactionid(), response.code(), response.details(), null, null);
            auditRecorder.recordAction(MODULE, "SycaPayTransaction", updated.getId().toString(), ActionAudit.PAYMENT_SUCCESS,
                    "Reconciliation : virement Wave confirme reussi (reference SycaPay " + response.referenceid() + ")");
            // Commission partenaire ou commerciale : paiement enregistre apres commit (PartenaireCommissionEventListener).
            eventPublisher.publishEvent(new SycaPayPaymentConfirmedEvent(updated.getId(), confirmePar));
            return updated;
        }

        if (response.isFailed()) {
            SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.FAILED,
                    response.referenceid(), response.transactionid(), response.code(), response.details(),
                    response.code() == null ? null : String.valueOf(response.code()), response.details());
            auditRecorder.recordActionFailure(MODULE, "SycaPayTransaction", updated.getId().toString(), ActionAudit.PAYMENT_FAILED,
                    response.details());
            // Demande de paiement liee (2026-09-26) : passe en echec, relancable (PartenaireCommissionEventListener).
            eventPublisher.publishEvent(new SycaPayPaymentFailedEvent(updated.getId()));
            return updated;
        }

        // -32/-33 (introuvable) ou tout autre cas non tranche : peut simplement signifier que la
        // transaction n'est pas encore indexee cote SycaPay - on ne decide JAMAIS FAILED sur une
        // absence de reponse, la transaction reste UNKNOWN/PROCESSING en attente d'une reconciliation
        // ulterieure (cf. section 11 : jamais interpreter une incertitude comme un echec).
        log.info("[SYCAPAY] reconciliation de {} toujours non concluante (code={}, status={}) - statut inchange",
                transaction.getTransactionReference(), response.code(), response.status());
        return transaction;
    }

    private AuthenticatedUserPrincipal requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
        return principal;
    }
}
