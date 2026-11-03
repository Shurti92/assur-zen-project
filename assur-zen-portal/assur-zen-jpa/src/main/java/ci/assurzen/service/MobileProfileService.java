package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.MobileFeedback;
import ci.assurzen.entities.Profile;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MobileFeedbackRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Profil, liens legaux et avis du client mobile (2026-11-03). */
@Service
@RequiredArgsConstructor
public class MobileProfileService {

    private final ProfileRepository profileRepository;
    private final MobileFeedbackRepository feedbackRepository;
    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final ContractRepository contractRepository;
    private final DynamicConfigurationService configuration;

    /** Modifie le nom et l'e-mail du compte (le nom n'est PAS lie a l'assure du back-office). */
    @Transactional
    public MobileExtraDtos.ProfileView modifier(Long userId, MobileExtraDtos.ProfilePatchRequest r) {
        Profile profil = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new MobileApiException(404, "PROFILE_NOT_FOUND", "Profil introuvable."));
        if (r.fullName() != null) {
            String nom = r.fullName().trim().replaceAll("\\s+", " ");
            if (nom.length() < 2) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Le nom doit contenir au moins 2 caracteres.", "fullName");
            }
            int separateur = nom.indexOf(' ');
            profil.setFirstname(separateur < 0 ? nom : nom.substring(0, separateur));
            profil.setLastname(separateur < 0 ? nom : nom.substring(separateur + 1));
        }
        if (r.email() != null) {
            profil.setEmail(StringUtils.hasText(r.email()) ? r.email().trim() : null);
        }
        profileRepository.save(profil);
        return vue(userId, profil);
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.ProfileView lire(Long userId) {
        Profile profil = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new MobileApiException(404, "PROFILE_NOT_FOUND", "Profil introuvable."));
        return vue(userId, profil);
    }

    public MobileExtraDtos.LegalInfo mentionsLegales() {
        return new MobileExtraDtos.LegalInfo(valeur("LEGAL_TERMS_URL"), valeur("LEGAL_PRIVACY_URL"), valeur("LEGAL_UPDATED_AT"));
    }

    /** Avis du client (1 a 5) ; au plus 5 par jour et par compte. */
    @Transactional
    public MobileExtraDtos.FeedbackCreated avis(Long userId, MobileExtraDtos.FeedbackRequest r) {
        List<MobileFeedback> recents = feedbackRepository.findByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofDays(1)));
        if (recents.size() >= 5) {
            throw new MobileApiException(429, "RATE_LIMITED", "Trop d'avis envoyes aujourd'hui. Reessayez demain.");
        }
        Long contractId = null;
        if (StringUtils.hasText(r.contractId())) {
            try {
                contractId = Long.valueOf(r.contractId().trim());
            } catch (NumberFormatException e) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Contrat invalide.", "contractId");
            }
            List<Long> assures = clientAccountInsuredRepository.findByUserId(userId).stream().map(l -> l.getInsuredId()).toList();
            if (assures.isEmpty() || contractRepository.findByIdAndInsuredIdIn(contractId, assures).isEmpty()) {
                throw new ci.assurzen.exception.ContractNotFoundException();
            }
        }
        MobileFeedback f = new MobileFeedback();
        f.setUserId(userId);
        f.setContractId(contractId);
        f.setRating(r.rating());
        f.setComment(StringUtils.hasText(r.comment()) ? r.comment().trim() : null);
        f = feedbackRepository.save(f);
        return new MobileExtraDtos.FeedbackCreated(String.format("FB-%d-%06d", java.time.Year.now().getValue(), f.getId()));
    }

    private MobileExtraDtos.ProfileView vue(Long userId, Profile p) {
        String nom = (nonNull(p.getFirstname()) + " " + nonNull(p.getLastname())).trim();
        return new MobileExtraDtos.ProfileView(String.valueOf(userId), nom, p.getPhone(), p.getEmail());
    }

    private String valeur(String cle) {
        String v = configuration.getString(cle, null);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private String nonNull(String v) {
        return v == null ? "" : v;
    }
}
