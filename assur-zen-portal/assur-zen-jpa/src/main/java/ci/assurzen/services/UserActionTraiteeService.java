package ci.assurzen.services;

import ci.assurzen.entities.UserActionTraitee;
import ci.assurzen.repositories.UserActionTraiteeRepository;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Actions "A traiter" marquees comme traitees par l'utilisateur connecte (2026-10-03) - toujours
 * limitees a son propre compte (userId du JWT), aucune donnee metier modifiee.
 */
@Service
@RequiredArgsConstructor
public class UserActionTraiteeService {

    /** Cle choisie par le front, ex. "ECHEANCE:123:2026-10-15" : lettres, chiffres et _ : - . uniquement. */
    private static final Pattern CLE = Pattern.compile("[A-Za-z0-9_:.\\-]{1,150}");

    /** Garde-fou contre un front qui ne nettoierait jamais : au-dela, les plus anciennes sont oubliees. */
    static final int MAX_PAR_UTILISATEUR = 2000;

    private final UserActionTraiteeRepository repository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public List<String> mesActionsTraitees() {
        return repository.findByUserIdOrderByCreatedAtDesc(userId()).stream().map(UserActionTraitee::getCle).toList();
    }

    /** Idempotent : marquer deux fois la meme cle ne cree qu'une ligne. */
    @Transactional
    public void marquerTraitee(String cle) {
        Long userId = userId();
        String valide = valider(cle);
        if (repository.existsByUserIdAndCle(userId, valide)) {
            return;
        }
        repository.save(UserActionTraitee.builder().userId(userId).cle(valide).build());
        List<UserActionTraitee> toutes = repository.findByUserIdOrderByCreatedAtDesc(userId);
        if (toutes.size() > MAX_PAR_UTILISATEUR) {
            repository.deleteAll(toutes.subList(MAX_PAR_UTILISATEUR, toutes.size()));
        }
    }

    /** Annulation, idempotente (204 meme si la cle n'etait pas marquee). */
    @Transactional
    public void annuler(String cle) {
        repository.deleteByUserIdAndCle(userId(), valider(cle));
    }

    private Long userId() {
        return authService.currentPrincipal().userId();
    }

    private static String valider(String cle) {
        if (cle == null || !CLE.matcher(cle).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cle d'action invalide (1 a 150 caracteres parmi lettres, chiffres, _ : - .)");
        }
        return cle;
    }
}
