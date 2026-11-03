package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.MobileDevice;
import ci.assurzen.entities.MobileNotificationPreference;
import ci.assurzen.repositories.MobileDeviceRepository;
import ci.assurzen.repositories.MobileNotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Appareils (jetons FCM) et preferences de notification push d'un compte mobile (2026-11-03). */
@Service
@RequiredArgsConstructor
public class MobileDeviceService {

    public static final List<String> CATEGORIES = List.of("EXPIRY", "SUBSCRIPTION", "PAYMENT", "CLAIM", "DOCUMENT", "SERVICE");

    private final MobileDeviceRepository deviceRepository;
    private final MobileNotificationPreferenceRepository preferenceRepository;

    /** Enregistre ou met a jour l'appareil. Un jeton deja connu est rattache au compte courant (changement d'utilisateur sur le meme telephone). */
    @Transactional
    public MobileExtraDtos.DeviceView enregistrer(Long userId, MobileExtraDtos.DeviceRequest r) {
        String jeton = r.token().trim();
        MobileDevice appareil = deviceRepository.findByToken(jeton).orElseGet(MobileDevice::new);
        appareil.setToken(jeton);
        appareil.setUserId(userId);
        appareil.setPlatform(r.platform());
        appareil.setAppVersion(StringUtils.hasText(r.appVersion()) ? r.appVersion().trim() : null);
        appareil.setActive(true);
        appareil.setLastSeenAt(Instant.now());
        appareil = deviceRepository.save(appareil);
        return new MobileExtraDtos.DeviceView(String.valueOf(appareil.getId()), appareil.getPlatform(), appareil.getAppVersion());
    }

    /** Supprime l'appareil du compte (deconnexion). Idempotent : un appareil inconnu ou d'un autre compte ne renvoie pas d'erreur. */
    @Transactional
    public void supprimer(Long userId, Long id) {
        deviceRepository.findByIdAndUserId(id, userId).ifPresent(deviceRepository::delete);
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.PreferenceList preferences(Long userId) {
        Map<String, Boolean> actuelles = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(MobileNotificationPreference::getCategory, MobileNotificationPreference::isEnabled, (a, b) -> a));
        return new MobileExtraDtos.PreferenceList(CATEGORIES.stream()
                .map(c -> new MobileExtraDtos.PreferenceItem(c, actuelles.getOrDefault(c, true))).toList());
    }

    @Transactional
    public MobileExtraDtos.PreferenceList modifierPreferences(Long userId, MobileExtraDtos.PreferenceUpdate r) {
        for (MobileExtraDtos.PreferenceItem item : r.items()) {
            MobileNotificationPreference p = preferenceRepository.findByUserIdAndCategory(userId, item.category()).orElseGet(() -> {
                MobileNotificationPreference nouvelle = new MobileNotificationPreference();
                nouvelle.setUserId(userId);
                nouvelle.setCategory(item.category());
                return nouvelle;
            });
            p.setEnabled(item.enabled());
            preferenceRepository.save(p);
        }
        return preferences(userId);
    }
}
