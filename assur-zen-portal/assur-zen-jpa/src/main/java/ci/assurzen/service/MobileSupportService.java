package ci.assurzen.service;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.entities.MobileFaq;
import ci.assurzen.entities.SupportRequest;
import ci.assurzen.entities.User;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.exception.MobilePaymentException;
import ci.assurzen.repositories.MobileFaqRepository;
import ci.assurzen.repositories.SupportRequestRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.services.CommercialService;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Support client de l'application mobile (2026-11-03) : sujets (cle SUPPORT_TOPICS), demandes avec reponse du conseiller,
 * FAQ geree depuis le back-office. 5 demandes par heure et par compte au maximum (429 RATE_LIMITED). Le delai de reponse
 * annonce (expectedWithinMinutes) vient de SUPPORT_RESPONSE_MINUTES, null si vide. Une reponse du conseiller cree une
 * notification dans l'application.
 */
@Service
@RequiredArgsConstructor
public class MobileSupportService {

    private static final int MAX_PAR_HEURE = 5;

    private final SupportRequestRepository requestRepository;
    private final MobileFaqRepository faqRepository;
    private final UserRepository userRepository;
    private final DynamicConfigurationService configuration;
    private final MobileNotificationService notificationService;
    private final CommercialService commercialService;
    private final ci.assurzen.service.AuthService authService;

    // ------------------------------------------------------------------ public

    public MobileExtraDtos.TopicList sujets() {
        return new MobileExtraDtos.TopicList(analyserSujets(configuration.getString("SUPPORT_TOPICS", null)));
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.FaqList faq() {
        return new MobileExtraDtos.FaqList(faqRepository.findByVisibleTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(f -> new MobileExtraDtos.FaqItem(String.valueOf(f.getId()), f.getQuestion(), f.getAnswer(), f.getDisplayOrder())).toList());
    }

    static List<MobileExtraDtos.Topic> analyserSujets(String brut) {
        List<MobileExtraDtos.Topic> resultat = new ArrayList<>();
        if (!StringUtils.hasText(brut)) {
            return resultat;
        }
        for (String element : brut.split(";")) {
            int separateur = element.indexOf(':');
            if (separateur <= 0) {
                continue;
            }
            String code = element.substring(0, separateur).trim().toUpperCase(Locale.ROOT);
            String libelle = element.substring(separateur + 1).trim();
            if (!code.isEmpty() && !libelle.isEmpty() && code.matches("[A-Z0-9_]{1,40}")) {
                resultat.add(new MobileExtraDtos.Topic(code, libelle));
            }
        }
        return resultat;
    }

    // ------------------------------------------------------------------ client

    @Transactional
    public MobileExtraDtos.SupportCreated creer(Long userId, MobileExtraDtos.SupportCreateRequest r) {
        String code = r.topic().trim().toUpperCase(Locale.ROOT);
        if (analyserSujets(configuration.getString("SUPPORT_TOPICS", null)).stream().noneMatch(t -> t.id().equals(code))) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Ce sujet n'existe pas.", "topic");
        }
        List<SupportRequest> recentes = requestRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, Instant.now().minus(Duration.ofHours(1)));
        if (recentes.size() >= MAX_PAR_HEURE) {
            long attente = Math.max(1, Duration.between(Instant.now(), recentes.get(0).getCreatedAt().plus(Duration.ofHours(1))).getSeconds() + 1);
            throw new MobilePaymentException(429, "RATE_LIMITED", "Trop de demandes. Réessayez dans " + Math.max(1, (attente + 59) / 60) + " minute(s).", null, attente);
        }
        SupportRequest demande = new SupportRequest();
        demande.setUserId(userId);
        demande.setTopic(code);
        demande.setMessage(r.message().trim());
        demande.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        demande = requestRepository.saveAndFlush(demande);
        demande.setReference(String.format("SP-%d-%06d", Year.now().getValue(), demande.getId()));
        requestRepository.save(demande);
        long minutes = configuration.getLong("SUPPORT_RESPONSE_MINUTES", 0L);
        return new MobileExtraDtos.SupportCreated(demande.getReference(), minutes > 0 ? (int) minutes : null);
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.SupportList lister(Long userId) {
        Map<String, String> libelles = libelles();
        return new MobileExtraDtos.SupportList(requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(d -> vue(d, libelles)).toList());
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.SupportItem detail(Long userId, Long id) {
        SupportRequest demande = requestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MobileApiException(404, "REQUEST_NOT_FOUND", "Demande introuvable."));
        return vue(demande, libelles());
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public List<MobileExtraDtos.SupportAdminItem> demandes(String statut) {
        commercialService.requireAdmin();
        List<SupportRequest> demandes = StringUtils.hasText(statut)
                ? requestRepository.findTop200ByStatutOrderByCreatedAtDesc(statut.trim().toUpperCase(Locale.ROOT))
                : requestRepository.findTop200ByOrderByCreatedAtDesc();
        Map<Long, String> telephones = userRepository.findAllById(demandes.stream().map(SupportRequest::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        return demandes.stream().map(d -> new MobileExtraDtos.SupportAdminItem(d.getId(), d.getReference(), d.getUserId(), telephones.get(d.getUserId()),
                d.getTopic(), d.getMessage(), d.getStatut(), d.getResponse(), d.getRespondedAt(), d.getRespondedBy(), d.getCreatedAt())).toList();
    }

    @Transactional
    public MobileExtraDtos.SupportAdminItem traiter(Long id, MobileExtraDtos.SupportProcessRequest r) {
        commercialService.requireAdmin();
        SupportRequest demande = requestRepository.findById(id)
                .orElseThrow(() -> new MobileApiException(404, "REQUEST_NOT_FOUND", "Demande introuvable."));
        if ("CLOTURE".equals(demande.getStatut())) {
            throw new MobileApiException(409, "REQUEST_ALREADY_PROCESSED", "Cette demande est déjà clôturée.");
        }
        if ("REPONDU".equals(r.statut()) && !StringUtils.hasText(r.response())) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Une réponse est obligatoire pour répondre au client.", "response");
        }
        demande.setStatut(r.statut());
        if (StringUtils.hasText(r.response())) {
            demande.setResponse(r.response().trim());
            demande.setRespondedAt(Instant.now());
            demande.setRespondedBy(authService.currentPrincipal().username());
        }
        requestRepository.save(demande);
        if ("REPONDU".equals(r.statut())) {
            notificationService.notifier(demande.getUserId(), "SERVICE", "Réponse du support",
                    demande.getResponse(), null, null, "SUP-" + demande.getId() + "-" + demande.getRespondedAt().toEpochMilli());
        }
        String telephone = userRepository.findById(demande.getUserId()).map(User::getUsername).orElse(null);
        return new MobileExtraDtos.SupportAdminItem(demande.getId(), demande.getReference(), demande.getUserId(), telephone, demande.getTopic(),
                demande.getMessage(), demande.getStatut(), demande.getResponse(), demande.getRespondedAt(), demande.getRespondedBy(), demande.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public MobileExtraDtos.FaqAdminList faqAdmin() {
        commercialService.requireAdmin();
        return new MobileExtraDtos.FaqAdminList(faqRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::vueFaq).toList());
    }

    /** Remplace toute la FAQ : l'ordre de la liste fixe l'ordre d'affichage (10, 20, ...) sauf si order est fourni. */
    @Transactional
    public MobileExtraDtos.FaqAdminList remplacerFaq(MobileExtraDtos.FaqReplaceRequest r) {
        commercialService.requireAdmin();
        faqRepository.deleteAll();
        faqRepository.flush();
        int ordre = 10;
        for (MobileExtraDtos.FaqInput entree : r.items()) {
            MobileFaq f = new MobileFaq();
            f.setQuestion(entree.question().trim());
            f.setAnswer(entree.answer().trim());
            f.setDisplayOrder(entree.order() == null ? ordre : entree.order());
            f.setVisible(entree.visible() == null || entree.visible());
            f.setUpdatedAt(Instant.now());
            faqRepository.save(f);
            ordre += 10;
        }
        return faqAdmin();
    }

    // ------------------------------------------------------------------

    private Map<String, String> libelles() {
        return analyserSujets(configuration.getString("SUPPORT_TOPICS", null)).stream()
                .collect(Collectors.toMap(MobileExtraDtos.Topic::id, MobileExtraDtos.Topic::label, (a, b) -> a));
    }

    private MobileExtraDtos.SupportItem vue(SupportRequest d, Map<String, String> libelles) {
        return new MobileExtraDtos.SupportItem(String.valueOf(d.getId()), d.getReference(), d.getTopic(), libelles.get(d.getTopic()), d.getMessage(),
                d.getStatut(), d.getResponse(), d.getRespondedAt(), d.getCreatedAt());
    }

    private MobileExtraDtos.FaqAdminItem vueFaq(MobileFaq f) {
        return new MobileExtraDtos.FaqAdminItem(f.getId(), f.getQuestion(), f.getAnswer(), f.getDisplayOrder(), f.isVisible());
    }
}
