package ci.assurzen.services;

import ci.assurzen.dto.NotificationPreferenceDto;
import ci.assurzen.entities.NotificationCategorie;
import ci.assurzen.entities.NotificationPreference;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.NotificationPreferenceRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Preferences de notification par categorie (Parametres > Notifications, 2026-10-03). Ne renvoie que
 * les categories que le compte connecte peut reellement recevoir. Regles serveur : la notification
 * in-app est toujours creee ; le SMS de l'argent verse (COMMISSION_VERSEE) et du bonus reste toujours
 * envoye. Sans preference enregistree, tout est active.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final AuthService authService;
    private final ProfileRepository profileRepository;
    private final PartenaireRepository partenaireRepository;
    private final CommercialRepository commercialRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> mesPreferences() {
        Long userId = authService.currentPrincipal().userId();
        return lister(userId, categoriesDuCompte(userId));
    }

    /** Remplace les preferences des categories fournies (les autres restent inchangees). 400 pour une
     * categorie que le compte ne recoit pas ; un SMS non modifiable est force a true. */
    @Transactional
    public List<NotificationPreferenceDto> modifierMesPreferences(List<NotificationPreferenceDto> demandes) {
        Long userId = authService.currentPrincipal().userId();
        List<NotificationCategorie> autorisees = categoriesDuCompte(userId);
        for (NotificationPreferenceDto demande : demandes == null ? List.<NotificationPreferenceDto>of() : demandes) {
            if (demande == null || demande.categorie() == null || !autorisees.contains(demande.categorie())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Categorie non disponible pour ce compte : " + (demande == null ? null : demande.categorie()));
            }
            NotificationPreference pref = repository.findByUserIdAndCategorie(userId, demande.categorie())
                    .orElseGet(() -> NotificationPreference.builder().userId(userId).categorie(demande.categorie()).build());
            pref.setPush(!demande.categorie().pushModifiable() || demande.push());
            pref.setSms(!demande.categorie().sansSms() && (!demande.categorie().smsModifiable() || demande.sms()));
            repository.save(pref);
        }
        return lister(userId, autorisees);
    }

    /** Le push de ce type est-il autorise pour cet utilisateur ? (true sans utilisateur ni preference) */
    @Transactional(readOnly = true)
    public boolean pushAutorise(Long userId, PartenaireNotificationType type) {
        NotificationCategorie categorie = NotificationCategorie.of(type);
        if (userId == null || !categorie.pushModifiable()) {
            return true;
        }
        return repository.findByUserIdAndCategorie(userId, categorie)
                .map(NotificationPreference::isPush).orElse(true);
    }

    /** Le SMS de ce type est-il autorise ? Toujours true quand la categorie ne permet pas de le couper. */
    @Transactional(readOnly = true)
    public boolean smsAutorise(Long userId, PartenaireNotificationType type) {
        NotificationCategorie categorie = NotificationCategorie.of(type);
        if (userId == null || !categorie.smsModifiable()) {
            return true;
        }
        return repository.findByUserIdAndCategorie(userId, categorie).map(NotificationPreference::isSms).orElse(true);
    }

    private List<NotificationPreferenceDto> lister(Long userId, List<NotificationCategorie> categories) {
        Map<NotificationCategorie, NotificationPreference> enregistrees = repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategorie, Function.identity(), (a, b) -> a));
        return categories.stream().map(categorie -> {
            NotificationPreference pref = enregistrees.get(categorie);
            boolean push = !categorie.pushModifiable() || pref == null || pref.isPush();
            boolean sms = !categorie.sansSms() && (!categorie.smsModifiable() || pref == null || pref.isSms());
            return new NotificationPreferenceDto(categorie, push, sms, categorie.pushModifiable(), categorie.smsModifiable(),
                    categorie.pushModifiable() || categorie.smsModifiable());
        }).toList();
    }

    /** Partenaire : commissions gagnees et versees. Commercial interne : argent verse et bonus. Autre
     * compte : aucune categorie (liste vide). */
    private List<NotificationCategorie> categoriesDuCompte(Long userId) {
        List<NotificationCategorie> categories = new ArrayList<>();
        boolean partenaire = profileRepository.findByUserId(userId)
                .map(profile -> !partenaireRepository.findByProfile_Id(profile.getId()).isEmpty())
                .orElse(false);
        if (partenaire) {
            categories.add(NotificationCategorie.COMMISSION_GAGNEE);
            categories.add(NotificationCategorie.COMMISSION_VERSEE);
            categories.add(NotificationCategorie.ECHEANCE);
            categories.add(NotificationCategorie.PRIME_A_REVERSER);
            categories.add(NotificationCategorie.DOCUMENT);
            categories.add(NotificationCategorie.SECURITE);
        } else if (commercialRepository.findByProfile_UserId(userId).isPresent()) {
            categories.add(NotificationCategorie.COMMISSION_VERSEE);
            categories.add(NotificationCategorie.BONUS);
            categories.add(NotificationCategorie.SECURITE);
        }
        return categories;
    }
}
