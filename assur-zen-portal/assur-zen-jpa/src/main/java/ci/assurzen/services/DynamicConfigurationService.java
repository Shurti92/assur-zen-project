package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.DynamicConfigurationRequest;
import ci.assurzen.dto.DynamicConfigurationResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigEnvironment;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.entities.HistoriqueModification;
import ci.assurzen.events.ConfigurationActivatedEvent;
import ci.assurzen.events.ConfigurationChangedEvent;
import ci.assurzen.events.ConfigurationDeactivatedEvent;
import ci.assurzen.events.ConfigurationRefreshedEvent;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.repositories.HistoriqueModificationRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Dynamic Configuration Engine : lecture typee (avec cache Caffeine, invalidation immediate a
 * chaque ecriture) + CRUD admin. Reutilise l'Audit Trail Engine existant (FunctionalAuditRecorder /
 * HistoriqueModification) pour l'historisation ET la restauration - pas de table d'historique
 * dediee. La cle metier (configKey, un String) est utilisee comme entityId dans l'audit ; les
 * methodes de ce service appellent FunctionalAuditRecorder explicitement plutot que via @Auditable,
 * car AuditAspect ne resout automatiquement l'entityId que depuis un argument Long (voir sa
 * javadoc) - une String pourrait etre n'importe quel parametre (ex. un motif), donc pas de
 * resolution automatique fiable possible ici.
 */
@Service
@RequiredArgsConstructor
public class DynamicConfigurationService {

    /** Valeur affichee a la place d'une valeur sensible (toResponse). Si un client la renvoie telle
     * quelle dans une modification (formulaire non retouche), la valeur existante est CONSERVEE. */
    static final String MASQUE = "********";

    /** Cles toujours sensibles, quel que soit le flag envoye (durcissement du 2026-09-24, identifiants
     * SycaPay en base) : un secret ne doit jamais pouvoir etre enregistre ou affiche en clair par erreur. */
    static final java.util.regex.Pattern CLES_TOUJOURS_SENSIBLES =
            java.util.regex.Pattern.compile("(?i).*(SECRET|PASSWORD|PASSWD|API_?KEY|PRIVATE_KEY|MERCHANT_ID).*");

    private final DynamicConfigurationRepository dynamicConfigurationRepository;
    private final HistoriqueModificationRepository historiqueModificationRepository;
    private final ConfigurationCacheService cacheService;
    private final ConfigurationEncryptionService encryptionService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final ApplicationEventPublisher eventPublisher;
    // Injection par champ : ce service est lu (getBoolean/getLong/...) depuis des services qui se
    // trouvent eux-memes dans l'arbre de dependances d'AuthService (OtpService, etc.) - une
    // injection constructeur creerait un cycle de beans, et @Lazy sur un champ Lombok n'est pas
    // recopie sur le parametre du constructeur genere (confirme au demarrage). authService n'est
    // utilise ici que par requireManage() (garde d'ecriture admin, hors chemin chaud de lecture),
    // donc differer sa resolution via proxy n'a aucun impact fonctionnel.
    @Autowired
    @Lazy
    private AuthService authService;
    // Construit localement, JavaTimeModule inclus : pas de bean ObjectMapper com.fasterxml
    // injectable dans ce projet (Jackson 2 + Jackson 3 cohabitent, cf. meme correction et
    // justification detaillee sur FunctionalAuditRecorder).
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ------------------------------------------------------------------
    // Lecture typee (chemin chaud : cache-first)
    // ------------------------------------------------------------------

    public String getString(String key) {
        return resolveRawValue(key).orElseThrow(() -> configurationRequise(key));
    }

    public String getString(String key, String fallback) {
        return resolveRawValue(key).orElse(fallback);
    }

    public Integer getInteger(String key) {
        return Integer.parseInt(getString(key).trim());
    }

    public Integer getInteger(String key, Integer fallback) {
        return resolveRawValue(key).map(v -> Integer.parseInt(v.trim())).orElse(fallback);
    }

    public Long getLong(String key) {
        return Long.parseLong(getString(key).trim());
    }

    public Long getLong(String key, Long fallback) {
        return resolveRawValue(key).map(v -> Long.parseLong(v.trim())).orElse(fallback);
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(getString(key).trim());
    }

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        return resolveRawValue(key).map(v -> new BigDecimal(v.trim())).orElse(fallback);
    }

    public Boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key).trim());
    }

    public Boolean getBoolean(String key, Boolean fallback) {
        return resolveRawValue(key).map(v -> Boolean.parseBoolean(v.trim())).orElse(fallback);
    }

    public LocalDate getDate(String key) {
        return LocalDate.parse(getString(key).trim());
    }

    public Instant getDateTime(String key) {
        return Instant.parse(getString(key).trim());
    }

    public <T> T getJson(String key, Class<T> type) {
        try {
            return objectMapper.readValue(getString(key), type);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Configuration JSON invalide pour la cle: " + key, e);
        }
    }

    public List<String> getList(String key) {
        return Arrays.stream(getString(key).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Optional<String> resolveRawValue(String key) {
        DynamicConfiguration entity = cacheService.get(key).orElseGet(() -> loadAndCache(key));
        if (entity == null) {
            return Optional.empty();
        }
        String raw = !entity.isActive() ? null
                : entity.isSensitive() ? encryptionService.decrypt(entity.getConfigValue())
                : entity.getConfigValue();
        if (!StringUtils.hasText(raw)) {
            raw = entity.getDefaultValue();
        }
        return Optional.ofNullable(raw).filter(StringUtils::hasText);
    }

    private DynamicConfiguration loadAndCache(String key) {
        DynamicConfiguration entity = dynamicConfigurationRepository.findByConfigKey(key).orElse(null);
        if (entity != null) {
            cacheService.put(key, entity);
        }
        return entity;
    }

    private ResponseStatusException configurationRequise(String key) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration obligatoire manquante ou sans valeur exploitable: " + key);
    }

    // ------------------------------------------------------------------
    // CRUD / administration
    // ------------------------------------------------------------------

    public PageResponse<DynamicConfigurationResponse> search(String q, String module, ConfigCategory category, ConfigValueType valueType,
                                                               ConfigEnvironment environment, Boolean active, Boolean editable, Boolean sensitive,
                                                               Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<DynamicConfigurationResponse> items = dynamicConfigurationRepository.findAll().stream()
                .filter(c -> normalizedQuery == null || matchesQuery(c, normalizedQuery))
                .filter(c -> module == null || module.equalsIgnoreCase(c.getModule()))
                .filter(c -> category == null || category == c.getCategory())
                .filter(c -> valueType == null || valueType == c.getValueType())
                .filter(c -> environment == null || environment == c.getEnvironment())
                .filter(c -> active == null || active == c.isActive())
                .filter(c -> editable == null || editable == c.isEditable())
                .filter(c -> sensitive == null || sensitive == c.isSensitive())
                .sorted(Comparator.comparing(DynamicConfiguration::getDisplayOrder).thenComparing(DynamicConfiguration::getConfigKey))
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public DynamicConfigurationResponse get(String key) {
        return toResponse(getEntityOrThrow(key));
    }

    public List<ConfigCategory> categories() {
        return List.of(ConfigCategory.values());
    }

    public List<String> modules() {
        return dynamicConfigurationRepository.findAll().stream()
                .map(DynamicConfiguration::getModule)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public DynamicConfigurationResponse create(DynamicConfigurationRequest request) {
        requireManage();
        dynamicConfigurationRepository.findByConfigKey(request.configKey()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une configuration existe deja pour la cle: " + request.configKey());
        });

        boolean sensible = Boolean.TRUE.equals(request.sensitive()) || estCleToujoursSensible(request.configKey());
        if (sensible) {
            exigerProtectionDesValeursSensibles(request.defaultValue());
        }
        validate(request.configValue(), request.valueType(), request.validationRule());
        String storedValue = sensible ? encryptionService.encrypt(request.configValue()) : request.configValue();

        DynamicConfiguration entity = DynamicConfiguration.builder()
                .configKey(request.configKey())
                .configValue(storedValue)
                .valueType(request.valueType())
                .description(request.description())
                .module(request.module())
                .category(request.category() != null ? request.category() : ConfigCategory.GENERAL)
                .environment(request.environment())
                .defaultValue(sensible ? null : request.defaultValue())
                .validationRule(request.validationRule())
                .allowedValues(request.allowedValues())
                .active(request.active() == null || request.active())
                .editable(request.editable() == null || request.editable())
                .sensitive(sensible)
                .restartRequired(request.restartRequired() != null && request.restartRequired())
                .cacheEnabled(request.cacheEnabled() == null || request.cacheEnabled())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();
        stampCreated(entity);
        entity = dynamicConfigurationRepository.save(entity);
        cacheService.put(entity.getConfigKey(), entity);

        functionalAuditRecorder.recordAction("CONFIGURATION", "DynamicConfiguration", entity.getConfigKey(), ActionAudit.CREATE,
                "Configuration creee: " + entity.getConfigKey());
        publishAfterCommit(new ConfigurationChangedEvent(entity.getConfigKey(), null, sensible ? "***" : storedValue));

        return toResponse(entity);
    }

    @Transactional
    public DynamicConfigurationResponse update(String key, DynamicConfigurationRequest request) {
        requireManage();
        DynamicConfiguration entity = getEntityOrThrow(key);
        if (!entity.isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette configuration n'est pas modifiable via l'API: " + key);
        }
        DynamicConfigurationResponse avant = toResponse(entity);
        String ancienneValeurEvent = entity.isSensitive() ? "***" : entity.getConfigValue();

        // Une valeur sensible ne redevient jamais lisible : pas de retour a non-sensible via l'API.
        if (Boolean.FALSE.equals(request.sensitive()) && (entity.isSensitive() || estCleToujoursSensible(key))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une configuration sensible ne peut pas redevenir non sensible: " + key);
        }
        boolean sensible = entity.isSensitive() || Boolean.TRUE.equals(request.sensitive()) || estCleToujoursSensible(key);
        boolean valeurConservee = sensible && (request.configValue() == null || MASQUE.equals(request.configValue()));
        // Cle securisee exigee des qu'un secret est (re)chiffre : nouvelle valeur, ou valeur en clair
        // existante qui devient sensible.
        if (sensible && (!valeurConservee || !entity.isSensitive())) {
            exigerProtectionDesValeursSensibles(request.defaultValue());
        } else if (sensible) {
            refuserValeurParDefautEnClair(request.defaultValue());
        }
        String storedValue;
        if (valeurConservee) {
            // Valeur non ressaisie (masque renvoye tel quel par le formulaire) : on conserve le secret existant.
            storedValue = entity.isSensitive() || entity.getConfigValue() == null
                    ? entity.getConfigValue() : encryptionService.encrypt(entity.getConfigValue());
        } else {
            validate(request.configValue(), request.valueType(), request.validationRule());
            String nouvelleValeur = sensible && request.configValue().isEmpty() ? null : request.configValue();
            storedValue = sensible ? encryptionService.encrypt(nouvelleValeur) : nouvelleValeur;
        }

        entity.setConfigValue(storedValue);
        entity.setValueType(request.valueType());
        entity.setDescription(request.description());
        entity.setModule(request.module());
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        entity.setEnvironment(request.environment());
        entity.setDefaultValue(sensible ? null : request.defaultValue());
        entity.setValidationRule(request.validationRule());
        entity.setAllowedValues(request.allowedValues());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.editable() != null) {
            entity.setEditable(request.editable());
        }
        entity.setSensitive(sensible);
        if (request.restartRequired() != null) {
            entity.setRestartRequired(request.restartRequired());
        }
        if (request.cacheEnabled() != null) {
            entity.setCacheEnabled(request.cacheEnabled());
        }
        if (request.displayOrder() != null) {
            entity.setDisplayOrder(request.displayOrder());
        }
        stampUpdated(entity);

        DynamicConfiguration saved = dynamicConfigurationRepository.save(entity);
        cacheService.refresh(key, () -> dynamicConfigurationRepository.findByConfigKey(key).orElse(null));

        DynamicConfigurationResponse apres = toResponse(saved);
        functionalAuditRecorder.recordUpdate("CONFIGURATION", "DynamicConfiguration", key, avant, apres, request.motif());
        publishAfterCommit(new ConfigurationChangedEvent(key, ancienneValeurEvent, sensible ? "***" : storedValue));

        return apres;
    }

    @Transactional
    public DynamicConfigurationResponse activate(String key) {
        requireManage();
        DynamicConfiguration entity = getEntityOrThrow(key);
        DynamicConfigurationResponse avant = toResponse(entity);
        entity.setActive(true);
        stampUpdated(entity);
        DynamicConfiguration saved = dynamicConfigurationRepository.save(entity);
        cacheService.refresh(key, () -> dynamicConfigurationRepository.findByConfigKey(key).orElse(null));

        DynamicConfigurationResponse apres = toResponse(saved);
        functionalAuditRecorder.recordUpdate("CONFIGURATION", "DynamicConfiguration", key, avant, apres, null);
        publishAfterCommit(new ConfigurationActivatedEvent(key));
        return apres;
    }

    @Transactional
    public DynamicConfigurationResponse deactivate(String key) {
        requireManage();
        DynamicConfiguration entity = getEntityOrThrow(key);
        DynamicConfigurationResponse avant = toResponse(entity);
        entity.setActive(false);
        stampUpdated(entity);
        DynamicConfiguration saved = dynamicConfigurationRepository.save(entity);
        cacheService.refresh(key, () -> dynamicConfigurationRepository.findByConfigKey(key).orElse(null));

        DynamicConfigurationResponse apres = toResponse(saved);
        functionalAuditRecorder.recordUpdate("CONFIGURATION", "DynamicConfiguration", key, avant, apres, null);
        publishAfterCommit(new ConfigurationDeactivatedEvent(key));
        return apres;
    }

    @Transactional
    public void delete(String key) {
        requireManage();
        DynamicConfiguration entity = getEntityOrThrow(key);
        String ancienneValeurEvent = entity.isSensitive() ? "***" : entity.getConfigValue();
        entity.setDeleted(true);
        stampUpdated(entity);
        dynamicConfigurationRepository.save(entity);
        cacheService.evict(key);

        functionalAuditRecorder.recordAction("CONFIGURATION", "DynamicConfiguration", key, ActionAudit.DELETE, null);
        publishAfterCommit(new ConfigurationChangedEvent(key, ancienneValeurEvent, null));
    }

    public DynamicConfigurationResponse refresh(String key) {
        DynamicConfiguration entity = cacheService.refresh(key, () -> dynamicConfigurationRepository.findByConfigKey(key).orElse(null));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration introuvable: " + key);
        }
        eventPublisher.publishEvent(new ConfigurationRefreshedEvent(key));
        return toResponse(entity);
    }

    public void refreshAll() {
        cacheService.evictAll();
        eventPublisher.publishEvent(new ConfigurationRefreshedEvent(null));
    }

    /**
     * Restaure la valeur d'une configuration a partir d'une entree passee de HistoriqueModification
     * (le "nouvelleValeur" JSON de cette entree, champ configValue). Impossible pour une configuration
     * sensible : l'audit ne conserve jamais sa valeur en clair (masquee "***"), donc rien d'exploitable
     * a restaurer depuis l'historique pour ce cas.
     */
    @Transactional
    public DynamicConfigurationResponse restore(String key, Long historiqueId) {
        requireManage();
        DynamicConfiguration entity = getEntityOrThrow(key);
        if (entity.isSensitive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de restaurer une configuration sensible depuis l'historique (valeur jamais conservee en clair)");
        }

        HistoriqueModification historique = historiqueModificationRepository.findById(historiqueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entree d'historique introuvable: " + historiqueId));
        if (!"DynamicConfiguration".equals(historique.getEntityName()) || !key.equals(historique.getEntityId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette entree d'historique ne concerne pas la configuration: " + key);
        }

        String restoredValue = extractFieldFromJson(historique.getNouvelleValeur(), "configValue");
        if (restoredValue == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette entree d'historique ne contient pas de changement de valeur restaurable");
        }

        DynamicConfigurationRequest request = new DynamicConfigurationRequest(
                key, restoredValue, entity.getValueType(), entity.getDescription(), entity.getModule(), entity.getCategory(),
                entity.getEnvironment(), entity.getDefaultValue(), entity.getValidationRule(), entity.getAllowedValues(),
                entity.isActive(), entity.isEditable(), entity.isSensitive(), entity.isRestartRequired(), entity.isCacheEnabled(),
                entity.getDisplayOrder(), "Restauration depuis l'historique #" + historiqueId);
        return update(key, request);
    }

    private String extractFieldFromJson(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(String rawValue, ConfigValueType type, String validationRule) {
        if (rawValue == null) {
            return;
        }
        validateFormat(rawValue, type);
        if (!StringUtils.hasText(validationRule)) {
            return;
        }
        for (String rule : validationRule.split(";")) {
            applyValidationRule(rule.trim(), rawValue);
        }
    }

    private void validateFormat(String rawValue, ConfigValueType type) {
        try {
            switch (type) {
                case INTEGER -> Integer.parseInt(rawValue.trim());
                case LONG -> Long.parseLong(rawValue.trim());
                case DECIMAL -> new BigDecimal(rawValue.trim());
                case BOOLEAN -> {
                    if (!rawValue.trim().equalsIgnoreCase("true") && !rawValue.trim().equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                }
                case DATE -> LocalDate.parse(rawValue.trim());
                case DATETIME -> Instant.parse(rawValue.trim());
                case JSON -> objectMapper.readTree(rawValue);
                default -> {
                    // STRING, LIST, ENUM, URL, DURATION : pas de contrainte de format stricte au-dela des regles ci-dessous
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur '" + rawValue + "' invalide pour le type " + type);
        }
    }

    private void applyValidationRule(String rule, String rawValue) {
        if (rule.startsWith("MIN:")) {
            double min = Double.parseDouble(rule.substring(4));
            if (Double.parseDouble(rawValue.trim()) < min) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur inferieure au minimum autorise (" + min + ")");
            }
        } else if (rule.startsWith("MAX:")) {
            double max = Double.parseDouble(rule.substring(4));
            if (Double.parseDouble(rawValue.trim()) > max) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur superieure au maximum autorise (" + max + ")");
            }
        } else if (rule.startsWith("REGEX:")) {
            String pattern = rule.substring(6);
            if (!Pattern.matches(pattern, rawValue)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur non conforme au format attendu");
            }
        } else if (rule.startsWith("ENUM:")) {
            List<String> allowed = List.of(rule.substring(5).split(","));
            if (!allowed.contains(rawValue.trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur non autorisee, attendu l'une de: " + allowed);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static boolean estCleToujoursSensible(String key) {
        return key != null && CLES_TOUJOURS_SENSIBLES.matcher(key).matches();
    }

    /** Refuse d'enregistrer un secret si la cle de chiffrement n'est pas securisee (valeur par defaut
     * publique) ou si une valeur par defaut - stockee en clair - est fournie. */
    private void exigerProtectionDesValeursSensibles(String defaultValue) {
        if (!encryptionService.isCleSecurisee()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cle de chiffrement non configuree (APP_CONFIG_ENCRYPTION_SECRET / APP_CONFIG_ENCRYPTION_SALT) : "
                            + "enregistrement d'une valeur sensible refuse");
        }
        refuserValeurParDefautEnClair(defaultValue);
    }

    private static void refuserValeurParDefautEnClair(String defaultValue) {
        if (StringUtils.hasText(defaultValue) && !MASQUE.equals(defaultValue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une configuration sensible ne peut pas avoir de valeur par defaut (elle serait stockee en clair)");
        }
    }

    private void requireManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        String roles = principal.roles() == null ? "" : principal.roles().toUpperCase(Locale.ROOT);
        if (!roles.contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private DynamicConfiguration getEntityOrThrow(String key) {
        return dynamicConfigurationRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration introuvable: " + key));
    }

    private boolean matchesQuery(DynamicConfiguration c, String normalizedQuery) {
        return contains(c.getConfigKey(), normalizedQuery) || contains(c.getDescription(), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void stampCreated(DynamicConfiguration entity) {
        String username = authService.currentPrincipal().username();
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);
    }

    private void stampUpdated(DynamicConfiguration entity) {
        entity.setUpdatedBy(authService.currentPrincipal().username());
    }

    private void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private DynamicConfigurationResponse toResponse(DynamicConfiguration c) {
        String displayValue = c.isSensitive() ? MASQUE : c.getConfigValue();
        return new DynamicConfigurationResponse(
                c.getId(),
                c.getUuid(),
                c.getConfigKey(),
                displayValue,
                c.getValueType(),
                c.getDescription(),
                c.getModule(),
                c.getCategory(),
                c.getEnvironment(),
                c.isSensitive() && c.getDefaultValue() != null ? MASQUE : c.getDefaultValue(),
                c.getValidationRule(),
                c.getAllowedValues(),
                c.isActive(),
                c.isEditable(),
                c.isSensitive(),
                c.isRestartRequired(),
                c.isCacheEnabled(),
                c.getDisplayOrder(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCreatedBy(),
                c.getUpdatedBy(),
                c.getVersion()
        );
    }
}
