package ci.assurzen.services;

import ci.assurzen.entities.PasswordHistory;
import ci.assurzen.entities.User;
import ci.assurzen.exception.PasswordReusedException;
import ci.assurzen.repositories.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Empeche la reutilisation d'un ancien mot de passe. Consultee AVANT tout changement/reset de mot
 * de passe existant (pas a la creation initiale d'un compte, ou rien n'existe encore a comparer -
 * cf. les points d'appel dans AuthService/UserProfileService/PartenaireService).
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final PasswordEncoder passwordEncoder;

    public void assertNotReused(User user, String rawNewPassword) {
        if (passwordEncoder.matches(rawNewPassword, user.getPassword())) {
            throw new PasswordReusedException();
        }
        int depth = dynamicConfigurationService.getInteger("PASSWORD_HISTORY_SIZE", 5);
        List<PasswordHistory> recent = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (int i = 0; i < Math.min(depth, recent.size()); i++) {
            if (passwordEncoder.matches(rawNewPassword, recent.get(i).getPasswordHash())) {
                throw new PasswordReusedException();
            }
        }
    }

    /** hashBeingReplaced : le hash bcrypt sur le point d'etre ecrase (ancien pour un changement, initial pour une creation). */
    public void record(Long userId, String hashBeingReplaced) {
        passwordHistoryRepository.save(PasswordHistory.builder()
                .userId(userId)
                .passwordHash(hashBeingReplaced)
                .build());

        int depth = dynamicConfigurationService.getInteger("PASSWORD_HISTORY_SIZE", 5);
        List<PasswordHistory> all = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.size() > depth) {
            passwordHistoryRepository.deleteAll(all.subList(depth, all.size()));
        }
    }
}
