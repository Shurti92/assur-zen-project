package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.MaConnexionResponse;
import ci.assurzen.dto.MaSessionResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.LoginHistory;
import ci.assurzen.entities.UserSession;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.JwtTokenService;
import ci.assurzen.security.TokenRevocationService;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Parametres > Securite de l'Espace Partenaire (2026-10-03), toujours limite au compte connecte :
 * historique de connexion (reussites et echecs), appareils connectes (sessions de refresh token) et
 * signalement "Ce n'etait pas moi".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecuriteCompteService {

    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Africa/Abidjan"));

    private final AuthService authService;
    private final LoginHistoryService loginHistoryService;
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final TelegramNotificationService telegramNotificationService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<MaConnexionResponse> mesConnexions(Integer size) {
        return loginHistoryService.mesConnexions(authService.currentPrincipal().userId(), size);
    }

    /**
     * Trace le signalement sur la connexion, journal d'audit fonctionnel et alerte Telegram aux
     * administrateurs (une seule fois par connexion). Ne deconnecte rien : le front propose ensuite de
     * deconnecter les autres appareils et de changer le mot de passe.
     */
    public void signalerConnexion(Long historyId) {
        var principal = authService.currentPrincipal();
        boolean nouveau = loginHistoryService.signaler(principal.userId(), historyId);
        if (!nouveau) {
            return;
        }
        LoginHistory connexion = loginHistoryService.getMienne(principal.userId(), historyId);
        functionalAuditRecorder.recordAction("SECURITY", "Connexion", String.valueOf(historyId), ActionAudit.REJECT,
                "Connexion signalee \"Ce n'etait pas moi\" par " + principal.username());
        telegramNotificationService.envoyer("<b>Connexion signalee par l'utilisateur</b>\n"
                + TelegramNotificationService.echapperHtml(principal.username()) + " ne reconnait pas la connexion du "
                + DATE_HEURE.format(connexion.getCreatedAt())
                + (Boolean.TRUE.equals(connexion.getEchec()) ? " (tentative echouee)" : "")
                + "\nIP " + TelegramNotificationService.echapperHtml(Objects.toString(connexion.getIpAddress(), "?"))
                + " - " + TelegramNotificationService.echapperHtml(Objects.toString(connexion.getCity(), "ville inconnue"))
                + " - " + TelegramNotificationService.echapperHtml(Objects.toString(connexion.getBrowser(), "navigateur inconnu")));
    }

    /** Sessions non revoquees et dont le refresh token n'a pas expire, la plus recente d'abord. */
    @Transactional(readOnly = true)
    public List<MaSessionResponse> mesSessions(String rawAccessToken) {
        Long userId = authService.currentPrincipal().userId();
        String courante = sessionCourante(rawAccessToken);
        // Une ligne par session : des renouvellements concurrents du meme refresh token (rechargement de
        // page, plusieurs onglets) peuvent laisser plusieurs jetons actifs pour une meme session -
        // seule la plus recente est affichee (la revocation, elle, les couvre toutes).
        java.util.Map<String, UserSession> parSession = new java.util.LinkedHashMap<>();
        actives(userId).forEach(s -> parSession.merge(idPublic(s), s,
                (a, b) -> derniereActivite(b).isAfter(derniereActivite(a)) ? b : a));
        return parSession.values().stream()
                .map(s -> new MaSessionResponse(idPublic(s), s.getDeviceType(), s.getBrowser(), s.getOs(), s.getCity(),
                        s.getCountry(), s.getIpAddress(),
                        s.getConnectedAt() != null ? s.getConnectedAt() : s.getCreatedAt(),
                        s.getLastActivityAt() != null ? s.getLastActivityAt() : s.getCreatedAt(),
                        courante != null && courante.equals(s.getSessionId())))
                .sorted(Comparator.comparing(MaSessionResponse::derniereActivite, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Deconnecte un appareil : son refresh token ne peut plus etre renouvele et son dernier access
     * token est revoque immediatement. 404 si la session n'est pas (ou plus) active pour ce compte. */
    @Transactional
    public void revoquerSession(String id) {
        Long userId = authService.currentPrincipal().userId();
        List<UserSession> cibles = actives(userId).stream().filter(s -> idPublic(s).equals(id)).toList();
        if (cibles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable: " + id);
        }
        revoquer(cibles);
    }

    /** Deconnecte tous les autres appareils (la session courante est conservee). Renvoie leur nombre. */
    @Transactional
    public int revoquerAutresSessions(String rawAccessToken) {
        Long userId = authService.currentPrincipal().userId();
        String courante = sessionCourante(rawAccessToken);
        if (courante == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session courante inconnue (jeton emis avant la gestion des appareils) - reconnectez-vous puis reessayez");
        }
        List<UserSession> autres = actives(userId).stream().filter(s -> !courante.equals(s.getSessionId())).toList();
        revoquer(autres);
        return (int) autres.stream().map(SecuriteCompteService::idPublic).distinct().count();
    }

    private void revoquer(List<UserSession> sessions) {
        Instant maintenant = Instant.now();
        sessions.forEach(s -> {
            s.setRevoked(true);
            if (StringUtils.hasText(s.getAccessJti()) && s.getAccessExpiresAt() != null && s.getAccessExpiresAt().isAfter(maintenant)) {
                tokenRevocationService.revoquer(s.getAccessJti(), s.getAccessExpiresAt());
            }
        });
        userSessionRepository.saveAll(sessions);
    }

    private List<UserSession> actives(Long userId) {
        Instant limite = Instant.now().minusSeconds(jwtTokenService.getRefreshTtlSeconds());
        return userSessionRepository.findAllByUserIdAndRevokedFalse(userId).stream()
                .filter(s -> s.getCreatedAt() == null || s.getCreatedAt().isAfter(limite))
                .toList();
    }

    private static Instant derniereActivite(UserSession s) {
        Instant valeur = s.getLastActivityAt() != null ? s.getLastActivityAt() : s.getCreatedAt();
        return valeur != null ? valeur : Instant.EPOCH;
    }

    /** sessionId stable ; "L" + id de ligne pour une session ouverte avant la gestion des appareils. */
    private static String idPublic(UserSession s) {
        return StringUtils.hasText(s.getSessionId()) ? s.getSessionId() : "L" + s.getId();
    }

    private String sessionCourante(String rawAccessToken) {
        if (!StringUtils.hasText(rawAccessToken)) {
            return null;
        }
        try {
            return jwtTokenService.parseAndValidate(rawAccessToken).sessionId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
