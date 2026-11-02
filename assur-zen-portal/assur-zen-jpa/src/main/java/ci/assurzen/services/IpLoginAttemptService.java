package ci.assurzen.services;

import ci.assurzen.audit.AuditMdcKeys;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.IpLoginAttemptResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.IpLoginAttempt;
import ci.assurzen.exception.IpBlockedException;
import ci.assurzen.repositories.IpLoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Verrouillage par adresse IP (brute-force / credential stuffing), en parallele du verrouillage
 * par compte deja existant (LoginAttemptService) - les deux coexistent independamment. Lit l'IP en
 * interne via MDC (meme mecanisme que LoginHistoryService/FunctionalAuditRecorder), pas besoin de
 * faire remonter HttpServletRequest jusqu'a AuthService.
 */
@Service
@RequiredArgsConstructor
public class IpLoginAttemptService {

    /** ~100 ans : pas de vrai "illimite" en base (colonne datetime), donc une echeance tres lointaine. */
    private static final long INDEFINITE_BLOCK_MINUTES = 100L * 365 * 24 * 60;

    private final IpLoginAttemptRepository ipLoginAttemptRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public void assertNotBlocked() {
        String ip = MDC.get(AuditMdcKeys.CLIENT_IP);
        if (!StringUtils.hasText(ip)) {
            return;
        }
        ipLoginAttemptRepository.findByIpAddress(ip)
                .filter(attempt -> attempt.getBlockedUntil() != null && Instant.now().isBefore(attempt.getBlockedUntil()))
                .ifPresent(attempt -> {
                    throw new IpBlockedException(attempt.getBlockedUntil());
                });
    }

    /**
     * Meme isolation REQUIRES_NEW que LoginAttemptService.registerFailure, pour la meme raison :
     * AuthService.login() est @Transactional et leve ensuite une exception - sans isolation, le
     * ROLLBACK annulerait silencieusement cet increment.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RuntimeException registerFailure() {
        String ip = MDC.get(AuditMdcKeys.CLIENT_IP);
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        IpLoginAttempt attempt = ipLoginAttemptRepository.findByIpAddress(ip)
                .orElseGet(() -> IpLoginAttempt.builder().ipAddress(ip).failedAttempts(0).build());

        int maxAttempts = dynamicConfigurationService.getInteger("MAX_IP_FAILED_ATTEMPTS", 20);
        attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);
        attempt.setLastAttemptAt(Instant.now());

        if (attempt.getFailedAttempts() >= maxAttempts) {
            long lockoutMinutes = dynamicConfigurationService.getLong("IP_LOCKOUT_DURATION_MINUTES", 30L);
            Instant blockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60);
            attempt.setBlockedUntil(blockedUntil);
            ipLoginAttemptRepository.save(attempt);
            functionalAuditRecorder.recordActionFailure("SECURITY", "AdresseIP", ip, ActionAudit.SUSPEND,
                    "Adresse IP bloquee jusqu'a " + blockedUntil + " (" + attempt.getFailedAttempts() + " tentatives de connexion echouees)");
            return new IpBlockedException(blockedUntil);
        }

        ipLoginAttemptRepository.save(attempt);
        return null;
    }

    public void registerSuccess() {
        String ip = MDC.get(AuditMdcKeys.CLIENT_IP);
        if (!StringUtils.hasText(ip)) {
            return;
        }
        ipLoginAttemptRepository.findByIpAddress(ip).ifPresent(attempt -> {
            attempt.setFailedAttempts(0);
            attempt.setBlockedUntil(null);
            ipLoginAttemptRepository.save(attempt);
        });
    }

    /** Deverrouillage manuel par un administrateur - garde ADMIN verifiee par l'appelant (AuthService.unblockIp). */
    public void unblock(String ipAddress) {
        ipLoginAttemptRepository.findByIpAddress(ipAddress).ifPresent(attempt -> {
            attempt.setFailedAttempts(0);
            attempt.setBlockedUntil(null);
            ipLoginAttemptRepository.save(attempt);
        });
    }

    /**
     * Blocage manuel par un administrateur (backoffice) - garde ADMIN verifiee par l'appelant
     * (AuthService.blockIp). durationMinutes null = blocage indefini (jusqu'a deblocage manuel
     * explicite via unblock) - modelise comme une echeance tres lointaine plutot qu'un booleen
     * separe, pour reutiliser exactement la meme logique de verification (assertNotBlocked) que le
     * blocage automatique.
     */
    public void block(String ipAddress, Integer durationMinutes) {
        long minutes = durationMinutes != null ? durationMinutes : INDEFINITE_BLOCK_MINUTES;
        IpLoginAttempt attempt = ipLoginAttemptRepository.findByIpAddress(ipAddress)
                .orElseGet(() -> IpLoginAttempt.builder().ipAddress(ipAddress).failedAttempts(0).build());
        attempt.setBlockedUntil(Instant.now().plusSeconds(minutes * 60));
        attempt.setLastAttemptAt(Instant.now());
        ipLoginAttemptRepository.save(attempt);
    }

    public List<IpLoginAttemptResponse> listBlocked() {
        return ipLoginAttemptRepository.findByBlockedUntilAfterOrderByBlockedUntilDesc(Instant.now()).stream()
                .map(a -> new IpLoginAttemptResponse(a.getIpAddress(), a.getFailedAttempts(), a.getBlockedUntil(), a.getLastAttemptAt()))
                .toList();
    }
}
