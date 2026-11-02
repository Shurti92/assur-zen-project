package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Consultation du solde marchand SycaPay (checkcashoutsolde.php). Ne necessite ni token ni signature
 * cote SycaPay (juste le merchantId) - c'est pourquoi la protection de l'endpoint AssurZen expose
 * cote controller (ADMIN uniquement, doublee ici au niveau service - meme convention que
 * CommercialCommissionService.requireAdmin()) est notre SEULE ligne de defense sur cette information,
 * SycaPay ne nous en offre aucune de son cote.
 *
 * Cache courte duree (sycapay.balance-cache-ttl-seconds) : ce service alimente l'endpoint que le
 * frontend interroge regulierement pour rafraichir l'affichage du solde (GET /sycapay/balance) - sans
 * cache, chaque rafraichissement frontend declencherait un appel reseau reel vers SycaPay, avec un
 * risque de rate-limiting (code erreur -27/429 documente par SycaPay) si plusieurs administrateurs
 * consultent le tableau de bord en meme temps. Le flux de PAIEMENT (SycaPayPaymentService) n'utilise
 * JAMAIS ce cache : un virement doit toujours se baser sur le solde reel a l'instant T, jamais sur une
 * valeur vieille de plusieurs secondes - il appelle SycaPayClient.checkBalance() directement.
 */
@Service
@RequiredArgsConstructor
public class SycaPayBalanceService {

    private static final String MODULE = "SYCAPAY";

    private final SycaPayClient sycaPayClient;
    private final FunctionalAuditRecorder auditRecorder;
    private final AuthService authService;
    private final SycaPayProperties properties;

    private final AtomicReference<CachedBalance> cache = new AtomicReference<>();

    public SycaPayBalanceResponse checkBalance() {
        return checkBalance(false);
    }

    /** Oublie le solde en cache (changement des identifiants SycaPay : le solde affiche doit etre celui
     * du compte nouvellement configure). */
    public void viderCache() {
        cache.set(null);
    }

    /** forceRefresh=true contourne le cache (bouton "rafraichir" explicite cote frontend). */
    public SycaPayBalanceResponse checkBalance(boolean forceRefresh) {
        requireAdmin();

        if (!forceRefresh) {
            CachedBalance cached = cache.get();
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                return cached.response();
            }
        }

        try {
            SycaPayBalanceResponse response = sycaPayClient.checkBalance();
            cache.set(new CachedBalance(response, Instant.now().plusSeconds(properties.getBalanceCacheTtlSeconds())));
            auditRecorder.recordAction(MODULE, "SycaPayBalance", null, ActionAudit.BALANCE_CHECK,
                    "Consultation du solde SycaPay");
            return response;
        } catch (SycaPayBalanceException e) {
            auditRecorder.recordActionFailure(MODULE, "SycaPayBalance", null, ActionAudit.BALANCE_CHECK, e.getMessage());
            throw e;
        }
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private record CachedBalance(SycaPayBalanceResponse response, Instant expiresAt) {
    }
}
