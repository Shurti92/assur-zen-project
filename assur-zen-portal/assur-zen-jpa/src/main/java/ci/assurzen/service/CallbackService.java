package ci.assurzen.service;

import ci.assurzen.dto.PublicMobileDtos;
import ci.assurzen.entities.CallbackRequest;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.CallbackRequestRepository;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;

/**
 * Demande de rappel de l'application mobile, avec ou sans compte (demande n°5, 2026-11-03). Limites : 3 demandes par
 * heure et par numero, 10 par heure et par adresse IP (429 RATE_LIMITED). Une demande deja en attente pour le meme
 * numero renvoie la meme reference. L'equipe est alertee par Telegram. Le delai annonce (expectedWithinMinutes) n'est
 * renvoye que s'il est configure (CALLBACK_EXPECTED_MINUTES) : aucun engagement n'est invente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackService {

    private static final int MAX_PAR_NUMERO = 3;
    private static final int MAX_PAR_IP = 10;

    private final CallbackRequestRepository repository;
    private final TelegramNotificationService telegramNotificationService;
    private final DynamicConfigurationService dynamicConfigurationService;

    @Transactional
    public PublicMobileDtos.CallbackCreated demander(PublicMobileDtos.CallbackRequest requete, Long userId, String ip) {
        String phone = requete.phone();
        Instant depuis = Instant.now().minus(Duration.ofHours(1));

        CallbackRequest enAttente = repository.findFirstByPhoneAndStatutOrderByCreatedAtDesc(phone, "EN_ATTENTE").orElse(null);
        if (enAttente != null && enAttente.getCreatedAt().isAfter(depuis)) {
            return new PublicMobileDtos.CallbackCreated(enAttente.getReference(), delaiAnnonce());
        }
        List<CallbackRequest> recentes = repository.findByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(phone, depuis);
        if (recentes.size() >= MAX_PAR_NUMERO
                || (StringUtils.hasText(ip) && repository.countByIpAddressAndCreatedAtAfter(ip, depuis) >= MAX_PAR_IP)) {
            long attente = recentes.isEmpty() ? 3600 : Math.max(1, Duration.between(Instant.now(),
                    recentes.get(0).getCreatedAt().plus(Duration.ofHours(1))).getSeconds() + 1);
            throw new MobileApiException(429, "RATE_LIMITED",
                    "Trop de demandes de rappel. Réessayez dans " + Math.max(1, (attente + 59) / 60) + " minute(s).", null);
        }

        CallbackRequest demande = new CallbackRequest();
        demande.setPhone(phone);
        demande.setSlot(requete.slot());
        demande.setProductId(StringUtils.hasText(requete.productId()) ? requete.productId().trim() : null);
        demande.setUserId(userId);
        demande.setIpAddress(StringUtils.hasText(ip) ? ip : null);
        demande.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        demande = repository.saveAndFlush(demande);
        demande.setReference(String.format("CB-%d-%06d", Year.now().getValue(), demande.getId()));
        repository.save(demande);
        try {
            telegramNotificationService.envoyer("<b>Demande de rappel (mobile)</b>\nNuméro : "
                    + TelegramNotificationService.echapperHtml(phone) + "\nCréneau : " + demande.getSlot()
                    + (demande.getProductId() == null ? "" : "\nProduit : " + TelegramNotificationService.echapperHtml(demande.getProductId()))
                    + "\nRéférence : " + demande.getReference());
        } catch (RuntimeException e) {
            // alerte non bloquante
        }
        log.info("[MOBILE-RAPPEL] demande {} enregistree (creneau {})", demande.getReference(), demande.getSlot());
        return new PublicMobileDtos.CallbackCreated(demande.getReference(), delaiAnnonce());
    }

    private Integer delaiAnnonce() {
        long minutes = dynamicConfigurationService.getLong("CALLBACK_EXPECTED_MINUTES", 0L);
        return minutes > 0 ? (int) minutes : null;
    }
}
