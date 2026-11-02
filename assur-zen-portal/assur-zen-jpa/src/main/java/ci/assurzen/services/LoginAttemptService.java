package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.User;
import ci.assurzen.exception.AccountLockedException;
import ci.assurzen.exception.InvalidCredentialsException;
import ci.assurzen.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persistance du compteur d'echecs de connexion / verrouillage, isolee dans sa PROPRE transaction
 * (REQUIRES_NEW). Indispensable : AuthService.login() est @Transactional et leve ensuite une
 * exception (InvalidCredentialsException/AccountLockedException) - sans isolation, le ROLLBACK
 * automatique de Spring sur cette exception annulerait silencieusement l'increment du compteur
 * (verifie en base : failed_login_attempts restait a 0 apres 5 tentatives echouees). Meme piege que
 * celui deja documente sur CsvImportRowProcessor. Bean separe car REQUIRES_NEW n'a d'effet qu'a
 * travers un appel inter-bean (proxy Spring), jamais en auto-invocation.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    /**
     * Recharge l'utilisateur par id (plutot que de reutiliser l'instance detachee passee par
     * l'appelant) pour rester correct sous tentatives concurrentes sur le meme compte.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RuntimeException registerFailure(Long userId, String username) {
        User user = userRepository.findById(userId).orElseThrow();
        int maxAttempts = dynamicConfigurationService.getInteger("MAX_LOGIN_ATTEMPTS", 5);
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxAttempts) {
            long lockoutMinutes = dynamicConfigurationService.getLong("ACCOUNT_LOCKOUT_DURATION_MINUTES", 15L);
            Instant lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60);
            user.setLockedUntil(lockedUntil);
            userRepository.save(user);
            functionalAuditRecorder.recordActionFailure("SECURITY", "Utilisateur", username, ActionAudit.SUSPEND,
                    "Compte verrouille jusqu'a " + lockedUntil + " (" + attempts + " tentatives de connexion echouees)");
            return new AccountLockedException(lockedUntil);
        }

        userRepository.save(user);
        return new InvalidCredentialsException(maxAttempts - attempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(Long userId, String username) {
        User user = userRepository.findById(userId).orElseThrow();
        boolean wasLocked = user.getLockedUntil() != null;
        if (user.getFailedLoginAttempts() == 0 && !wasLocked) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        if (wasLocked) {
            functionalAuditRecorder.recordAction("SECURITY", "Utilisateur", username, ActionAudit.RESTORE,
                    "Deverrouillage automatique (connexion reussie)");
        }
    }
}
