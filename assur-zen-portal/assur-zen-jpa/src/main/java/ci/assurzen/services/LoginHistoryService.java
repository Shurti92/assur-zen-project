package ci.assurzen.services;

import ci.assurzen.audit.AuditMdcKeys;
import ci.assurzen.dto.LoginHistoryResponse;
import ci.assurzen.dto.LoginHistoryStatsResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.LoginHistory;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.LoginHistoryRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Historique des connexions reussies (login classique ou 2FA/OTP complete), par utilisateur et,
 * quand applicable, par partenaire. Ecriture appelee depuis AuthService.login()/completeLoginWithOtp().
 * Depuis le 2026-10-03, les mots de passe incorrects sur un compte existant sont aussi historises
 * (echec = true) pour l'historique self-service de l'utilisateur ; les vues administrateur (search,
 * statistiques) restent limitees aux connexions reussies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");

    private final LoginHistoryRepository loginHistoryRepository;
    private final ProfileRepository profileRepository;
    private final PartenaireRepository partenaireRepository;
    private final UserAgentParserService userAgentParserService;
    private final GeoIpLookupService geoIpLookupService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final java.time.format.DateTimeFormatter DATE_HEURE =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZONE_ID);

    /**
     * Ne doit jamais faire echouer une connexion : toute exception est loguee en WARN et avalee
     * par l'appelant (cf. AuthService), meme principe que auditService.recordAsync (non-bloquant).
     */
    public void record(User user) {
        ClientInfo client = clientCourant();
        boolean nouvelAppareil = estNouvelAppareil(user.getId(), client);
        LoginHistory saved = loginHistoryRepository.save(construire(user, client));
        if (nouvelAppareil) {
            notifierNouvelleConnexion(saved);
        }
    }

    /**
     * Connexion depuis un appareil jamais vu sur ce compte (2026-10-04) : aucune des 100 dernieres connexions
     * reussies n'a le meme type d'appareil, la meme famille de navigateur et le meme systeme (versions
     * ignorees, sinon chaque mise a jour du navigateur declencherait une alerte). Premiere connexion du
     * compte : pas d'alerte (rien a comparer).
     */
    boolean estNouvelAppareil(Long userId, ClientInfo client) {
        List<LoginHistory> precedentes = loginHistoryRepository.findTop100ByUserIdAndEchecIsNullOrderByCreatedAtDesc(userId);
        if (precedentes.isEmpty()) {
            return false;
        }
        return precedentes.stream().noneMatch(h -> h.getDeviceType() == client.deviceType()
                && java.util.Objects.equals(famille(h.getBrowser()), famille(client.browser()))
                && java.util.Objects.equals(famille(h.getOs()), famille(client.os())));
    }

    /** "Chrome 141" -> "Chrome", "Windows 10" -> "Windows". */
    static String famille(String nomAvecVersion) {
        return nomAvecVersion == null ? null : nomAvecVersion.replaceFirst("\\s+\\d+$", "");
    }

    /** "Chrome 141 sur Windows 10", ou "un appareil non identifie" quand le navigateur n'est pas reconnu
     * (ua-parser renvoie alors "Other"). */
    static String decrireAppareil(String navigateur, String os) {
        if (!estIdentifie(navigateur)) {
            return "un appareil non identifié";
        }
        return navigateur + (estIdentifie(os) ? " sur " + os : "");
    }

    private static boolean estIdentifie(String valeur) {
        return valeur != null && !valeur.isBlank() && !"Other".equalsIgnoreCase(famille(valeur));
    }

    /** Notification SECURITE (in-app + push, jamais de SMS), creee apres commit du login. */
    private void notifierNouvelleConnexion(LoginHistory h) {
        String lieu = h.getCity() == null ? "" : " (" + h.getCity() + ")";
        String message = "Nouvelle connexion à votre compte depuis " + decrireAppareil(h.getBrowser(), h.getOs()) + lieu + " le "
                + DATE_HEURE.format(h.getCreatedAt() == null ? Instant.now() : h.getCreatedAt())
                + ". Si ce n'était pas vous, signalez-la et changez votre mot de passe.";
        eventPublisher.publishEvent(h.getPartenaireId() != null
                ? ci.assurzen.events.NotificationUtilisateurEvent.pourPartenaire(h.getPartenaireId(),
                        ci.assurzen.entities.PartenaireNotificationType.NOUVELLE_CONNEXION, "Nouvelle connexion", message,
                        null, "CONNEXION", h.getId())
                : ci.assurzen.events.NotificationUtilisateurEvent.pourUtilisateur(h.getUserId(),
                        ci.assurzen.entities.PartenaireNotificationType.NOUVELLE_CONNEXION, "Nouvelle connexion", message,
                        null, "CONNEXION", h.getId()));
    }

    /**
     * Mot de passe incorrect sur un compte existant (2026-10-03). REQUIRES_NEW : AuthService.login() leve
     * une exception juste apres, sa transaction est annulee - l'echec doit survivre a ce rollback (meme
     * lecon que LoginAttemptService.registerFailure). Ne doit jamais changer la reponse de login : toute
     * exception est avalee par l'appelant.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEchec(User user, String motif) {
        LoginHistory history = construire(user, clientCourant());
        history.setEchec(true);
        history.setMotifEchec(motif);
        loginHistoryRepository.save(history);
    }

    /** Appareil et localisation approximative de la requete courante (IP et User-Agent poses dans le MDC
     * par le filtre d'audit) - reutilise pour les sessions (appareils connectes). */
    public ClientInfo clientCourant() {
        String ip = MDC.get(AuditMdcKeys.CLIENT_IP);
        String userAgent = MDC.get(AuditMdcKeys.USER_AGENT);
        UserAgentParserService.ParsedUserAgent parsedUa = userAgentParserService.parse(userAgent);
        GeoIpLookupService.GeoIpResult geo = geoIpLookupService.lookup(ip);
        return new ClientInfo(ip, userAgent, parsedUa.deviceType(), parsedUa.browser(), parsedUa.os(),
                geo.country(), geo.city(), geo.isp());
    }

    public record ClientInfo(String ip, String userAgent, ci.assurzen.entities.DeviceType deviceType, String browser,
                             String os, String country, String city, String isp) {
    }

    private LoginHistory construire(User user, ClientInfo client) {
        return LoginHistory.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .partenaireId(resolvePartenaireId(user))
                .ipAddress(client.ip())
                .userAgent(client.userAgent())
                .deviceType(client.deviceType())
                .browser(client.browser())
                .os(client.os())
                .country(client.country())
                .city(client.city())
                .isp(client.isp())
                .build();
    }

    /** Dernieres connexions (reussies ET echouees) de l'utilisateur connecte, les plus recentes d'abord. */
    @Transactional(readOnly = true)
    public List<ci.assurzen.dto.MaConnexionResponse> mesConnexions(Long userId, Integer size) {
        int limite = size == null || size < 1 ? 20 : Math.min(size, 100);
        return loginHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limite)).stream()
                .map(h -> new ci.assurzen.dto.MaConnexionResponse(h.getId(), h.getCreatedAt(),
                        Boolean.TRUE.equals(h.getEchec()) ? "ECHEC" : "SUCCES", h.getMotifEchec(),
                        h.getDeviceType(), h.getBrowser(), h.getOs(), h.getCity(), h.getCountry(), h.getIpAddress(),
                        h.getSignaleAt() != null))
                .toList();
    }

    /** "Ce n'etait pas moi" : trace sur la ligne (idempotent). Renvoie la ligne, ou 404 si elle
     * n'appartient pas a l'utilisateur. Renvoie false si elle etait deja signalee. */
    @Transactional
    public boolean signaler(Long userId, Long historyId) {
        LoginHistory history = loginHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connexion introuvable: " + historyId));
        if (history.getSignaleAt() != null) {
            return false;
        }
        history.setSignaleAt(Instant.now());
        loginHistoryRepository.save(history);
        return true;
    }

    @Transactional(readOnly = true)
    public LoginHistory getMienne(Long userId, Long historyId) {
        return loginHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connexion introuvable: " + historyId));
    }

    /**
     * Meme resolution que AuthService.buildUserResponse() : non-null uniquement si le profil est
     * celui d'un partenaire, null (+ log) si plusieurs Partenaire pointent vers le meme Profile
     * (donnee anormale, on ne devine pas lequel retenir).
     */
    private Long resolvePartenaireId(User user) {
        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null) {
            return null;
        }
        List<Partenaire> partenaires = partenaireRepository.findByProfile_Id(profile.getId());
        if (partenaires.size() == 1) {
            return partenaires.get(0).getId();
        }
        if (partenaires.size() > 1) {
            log.warn("Profile {} est lie a {} partenaires, impossible de determiner un partenaireId unique pour l'historique de connexion",
                    profile.getId(), partenaires.size());
        }
        return null;
    }

    public PageResponse<LoginHistoryResponse> search(Long partenaireId, Long userId,
                                                       Instant dateDebut, Instant dateFin,
                                                       Integer page, Integer size) {
        List<LoginHistoryResponse> items = loginHistoryRepository.findAll().stream()
                .filter(h -> h.getEchec() == null)
                .filter(h -> partenaireId == null || partenaireId.equals(h.getPartenaireId()))
                .filter(h -> userId == null || userId.equals(h.getUserId()))
                .filter(h -> dateDebut == null || (h.getCreatedAt() != null && !h.getCreatedAt().isBefore(dateDebut)))
                .filter(h -> dateFin == null || (h.getCreatedAt() != null && !h.getCreatedAt().isAfter(dateFin)))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, size);
    }

    public LoginHistoryStatsResponse statistiques(Long partenaireId, Long userId) {
        ZonedDateTime now = ZonedDateTime.now(ZONE_ID);
        Instant debutJour = now.toLocalDate().atStartOfDay(ZONE_ID).toInstant();
        Instant debutSemaine = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(ZONE_ID).toInstant();
        Instant debutMois = LocalDate.of(now.getYear(), now.getMonth(), 1).atStartOfDay(ZONE_ID).toInstant();

        if (partenaireId != null) {
            return new LoginHistoryStatsResponse(
                    loginHistoryRepository.countByPartenaireIdAndCreatedAtGreaterThanEqualAndEchecIsNull(partenaireId, debutJour),
                    loginHistoryRepository.countByPartenaireIdAndCreatedAtGreaterThanEqualAndEchecIsNull(partenaireId, debutSemaine),
                    loginHistoryRepository.countByPartenaireIdAndCreatedAtGreaterThanEqualAndEchecIsNull(partenaireId, debutMois));
        }
        if (userId != null) {
            return new LoginHistoryStatsResponse(
                    loginHistoryRepository.countByUserIdAndCreatedAtGreaterThanEqualAndEchecIsNull(userId, debutJour),
                    loginHistoryRepository.countByUserIdAndCreatedAtGreaterThanEqualAndEchecIsNull(userId, debutSemaine),
                    loginHistoryRepository.countByUserIdAndCreatedAtGreaterThanEqualAndEchecIsNull(userId, debutMois));
        }
        return new LoginHistoryStatsResponse(
                loginHistoryRepository.countByCreatedAtGreaterThanEqualAndEchecIsNull(debutJour),
                loginHistoryRepository.countByCreatedAtGreaterThanEqualAndEchecIsNull(debutSemaine),
                loginHistoryRepository.countByCreatedAtGreaterThanEqualAndEchecIsNull(debutMois));
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 500);

        int totalElements = items.size();
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private LoginHistoryResponse toResponse(LoginHistory h) {
        return new LoginHistoryResponse(h.getId(), h.getUserId(), h.getUsername(), h.getPartenaireId(),
                h.getIpAddress(), h.getDeviceType(), h.getBrowser(), h.getOs(),
                h.getCountry(), h.getCity(), h.getIsp(), h.getCreatedAt());
    }
}
