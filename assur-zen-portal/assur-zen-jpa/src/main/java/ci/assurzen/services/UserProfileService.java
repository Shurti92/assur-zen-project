package ci.assurzen.services;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ImportRowError;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PurgeSessionsResult;
import ci.assurzen.dto.ResetPasswordRequest;
import ci.assurzen.dto.UserProfileRequest;
import ci.assurzen.dto.UserProfileResponse;
import ci.assurzen.dto.UserProfileUpdateRequest;
import ci.assurzen.dto.UserSessionResponse;
import ci.assurzen.dto.UserStatusRequest;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.TypeProfile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.UserSession;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.TypeProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
import ci.assurzen.security.UserActiveStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    /** Sans caracteres ambigus (0/O, 1/l/I) : un mot de passe temporaire doit rester lisible s'il est communique par SMS/telephone. */
    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private static final String TEMPLATE_BIENVENUE_BACKOFFICE = "BIENVENUE_BACKOFFICE";
    private static final String TEMPLATE_BIENVENUE_COMMERCIAL = "BIENVENUE_COMMERCIAL";
    private static final String TEMPLATE_RENVOI_ACCES_BACKOFFICE = "RENVOI_ACCES_BACKOFFICE";
    private static final String TEMPLATE_RENVOI_ACCES_PARTENAIRE = "RENVOI_ACCES_PARTENAIRE";

    /** Lien inclus dans le SMS de renvoi d'acces PARTENAIRE (contrat "Lien application dans le SMS de
     * bienvenue partenaire" §2, 2026-08-30) et, depuis le 2026-09-15, dans le SMS de bienvenue et de
     * renvoi d'acces COMMERCIAL (meme Espace Partenaire, meme lien) - meme propriete que
     * PartenaireService, differe entre prod/recette-sandbox/dev. */
    @Value("${app.partenaire.lien-connexion}")
    private String lienConnexionPartenaire;

    private final PartenaireRepository partenaireRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TypeProfileRepository typeProfileRepository;
    private final VillePartenaireRepository villePartenaireRepository;
    private final UserSessionRepository userSessionRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final SmsNotificationService smsNotificationService;
    private final PasswordHistoryService passwordHistoryService;
    private final UserActiveStatusCache userActiveStatusCache;
    private final SecureRandom secureRandom = new SecureRandom();

    public PageResponse<UserProfileResponse> search(String q, Boolean active, String roles, Long typeProfileId, Long villePartenaireId, Integer page, Integer size) {
        return paginate(filter(q, active, roles, typeProfileId, villePartenaireId), page, size);
    }

    public PageResponse<UserSessionResponse> getLoginHistory(Long userId, Integer page, Integer size) {
        getUser(userId);

        List<UserSessionResponse> history = userSessionRepository.findAllByUserId(userId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(session -> new UserSessionResponse(session.getId(), session.getCreatedAt(), session.isRevoked()))
                .toList();

        return paginate(history, page, size);
    }

    @Transactional
    public PurgeSessionsResult purgeRevokedSessions() {
        long deletedCount = userSessionRepository.deleteByRevokedTrue();
        return new PurgeSessionsResult(deletedCount);
    }

    public List<String> getAvailableRoles() {
        Set<String> roles = new TreeSet<>(List.of("ADMIN", "USER"));

        userRepository.findAll().stream()
                .map(User::getRoles)
                .filter(StringUtils::hasText)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .forEach(roles::add);

        return List.copyOf(roles);
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

    public byte[] exportCsv(String q, Boolean active, String roles, Long typeProfileId, Long villePartenaireId) {
        List<UserProfileResponse> rows = filter(q, active, roles, typeProfileId, villePartenaireId);

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Id;Login;Roles;Actif;Code profil;Prenom;Nom;Telephone;Fonction;Email;Secteur;Profil actif\n");
        for (UserProfileResponse row : rows) {
            csv.append(csvField(row.userId()))
                    .append(';').append(csvField(row.username()))
                    .append(';').append(csvField(row.roles()))
                    .append(';').append(csvField(row.userActive()))
                    .append(';').append(csvField(row.code()))
                    .append(';').append(csvField(row.firstname()))
                    .append(';').append(csvField(row.lastname()))
                    .append(';').append(csvField(row.phone()))
                    .append(';').append(csvField(row.role()))
                    .append(';').append(csvField(row.email()))
                    .append(';').append(csvField(row.sector()))
                    .append(';').append(csvField(row.profileActive()))
                    .append('\n');
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public ImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV manquant ou vide");
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier CSV");
        }

        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        for (int i = 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (!StringUtils.hasText(rawLine)) {
                continue;
            }
            totalRows++;
            int lineNumber = i + 1;

            List<String> fields = parseCsvLine(rawLine);
            String username = csvValue(fields, 0);
            String password = csvValue(fields, 1);
            String roles = csvValue(fields, 2);
            String firstname = csvValue(fields, 3);
            String lastname = csvValue(fields, 4);
            String phone = csvValue(fields, 5);
            String role = csvValue(fields, 6);
            String email = csvValue(fields, 7);
            String sector = csvValue(fields, 8);
            Boolean active = parseActive(csvValue(fields, 9));

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)
                    || !StringUtils.hasText(firstname) || !StringUtils.hasText(lastname)) {
                errors.add(new ImportRowError(lineNumber, username, "Champs obligatoires manquants (login, mot de passe, prénom, nom)"));
                continue;
            }
            if (password.length() < 6) {
                errors.add(new ImportRowError(lineNumber, username, "Mot de passe trop court (minimum 6 caractères)"));
                continue;
            }

            try {
                create(new UserProfileRequest(username, password, roles, null, null, firstname, lastname, phone, role, email, sector, null, active,
                        null, null, null, null, null, null, null, null, null, null, null));
                successCount++;
            } catch (ResponseStatusException e) {
                errors.add(new ImportRowError(lineNumber, username, e.getReason()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(lineNumber, username, "Erreur inattendue: " + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, successCount, errors.size(), errors);
    }

    private Boolean parseActive(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "oui".equalsIgnoreCase(value);
    }

    private String csvValue(List<String> fields, int index) {
        if (index >= fields.size()) {
            return null;
        }
        String value = fields.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ';') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(";") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private List<UserProfileResponse> filter(String q, Boolean active, String roles, Long typeProfileId, Long villePartenaireId) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;
        String normalizedRoles = StringUtils.hasText(roles) ? roles.trim().toLowerCase() : null;

        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .filter(user -> active == null || user.isActive() == active)
                .filter(user -> normalizedRoles == null
                        || (user.getRoles() != null && user.getRoles().toLowerCase().contains(normalizedRoles)))
                .filter(user -> typeProfileId == null
                        || (user.getTypeProfile() != null && typeProfileId.equals(user.getTypeProfile().getId())))
                .map(user -> new UserWithProfile(user, profileRepository.findByUserId(user.getId()).orElse(null)))
                .filter(entry -> normalizedQuery == null || matchesQuery(entry.user(), entry.profile(), normalizedQuery))
                .filter(entry -> villePartenaireId == null
                        || (entry.profile() != null && entry.profile().getVillePartenaire() != null
                        && villePartenaireId.equals(entry.profile().getVillePartenaire().getId())))
                .map(entry -> toResponse(entry.user(), entry.profile()))
                .toList();
    }

    private record UserWithProfile(User user, Profile profile) {
    }

    private boolean matchesQuery(User user, Profile profile, String normalizedQuery) {
        if (containsIgnoreCase(user.getUsername(), normalizedQuery)) {
            return true;
        }
        if (profile == null) {
            return false;
        }
        return containsIgnoreCase(profile.getFirstname(), normalizedQuery)
                || containsIgnoreCase(profile.getLastname(), normalizedQuery)
                || containsIgnoreCase(profile.getEmail(), normalizedQuery)
                || containsIgnoreCase(profile.getPhone(), normalizedQuery)
                || containsIgnoreCase(profile.getCode(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    @Transactional
    public UserProfileResponse create(UserProfileRequest request) {
        userRepository.findByUsername(request.username())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un utilisateur existe déjà pour ce login: " + request.username());
                });

        boolean motDePasseGenere = !StringUtils.hasText(request.password());
        String motDePasseClair = motDePasseGenere ? generateTemporaryPassword() : request.password();

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(motDePasseClair));
        user.setRoles(StringUtils.hasText(request.roles()) ? request.roles() : "USER");
        user.setActive(request.active() == null || request.active());
        user.setFirstConnexion(1);
        user.setCreation(Instant.now());
        user.setLastUpdate(Instant.now());
        user.setTypeProfile(resolveTypeProfile(request.typeProfileId()));
        user = userRepository.save(user);
        // Historise le mot de passe INITIAL (pas un remplacement) pour qu'on ne puisse pas y revenir
        // juste apres l'avoir change - meme mecanisme que pour un changement, cf. PasswordHistoryService.
        passwordHistoryService.record(user.getId(), user.getPassword());

        Profile profile = Profile.builder()
                .code(StringUtils.hasText(request.code()) ? request.code() : codeGeneratorService.generate(CodePrefix.PROFILE))
                .firstname(request.firstname())
                .lastname(request.lastname())
                .phone(request.phone())
                .role(request.role())
                .email(request.email())
                .sector(request.sector())
                .bio(request.bio())
                .facebookUrl(request.facebookUrl())
                .twitterUrl(request.twitterUrl())
                .linkedinUrl(request.linkedinUrl())
                .instagramUrl(request.instagramUrl())
                .country(request.country())
                .city(request.city())
                .state(request.state())
                .postalCode(request.postalCode())
                .taxId(request.taxId())
                .twoFactorEnabled(request.twoFactorEnabled() == null || request.twoFactorEnabled())
                .user(user)
                .villePartenaire(resolveVillePartenaire(request.villePartenaireId()))
                .active(true)
                .expiration(false)
                .build();
        profile = profileRepository.save(profile);

        // Gap remonte par l'utilisateur le 2026-09-15 : un Commercial (CommercialService.create() ->
        // ici, roles="COMMERCIAL") doit lui aussi se connecter a l'Espace Partenaire, mais ce SMS
        // generique n'incluait jamais de lien - contrairement au partenaire (meme propriete
        // app.partenaire.lien-connexion, prod/recette/dev identiques pour les deux profils).
        boolean estCommercial = user.getRoles() != null && user.getRoles().toUpperCase(Locale.ROOT).contains("COMMERCIAL");
        Boolean notifie = smsNotificationService.sendFromTemplate(
                profile.getPhone(),
                estCommercial ? TEMPLATE_BIENVENUE_COMMERCIAL : TEMPLATE_BIENVENUE_BACKOFFICE,
                estCommercial
                        ? Map.of("username", user.getUsername(), "motDePasse", motDePasseClair, "lienApplication", lienConnexionPartenaire)
                        : Map.of("username", user.getUsername(), "motDePasse", motDePasseClair),
                estCommercial ? TypeMessageSms.bienvenue_commercial : TypeMessageSms.bienvenue_backoffice
        );

        return toResponse(user, profile, motDePasseGenere ? motDePasseClair : null, notifie);
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_ALPHABET.charAt(secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    @Transactional
    public UserProfileResponse update(Long userId, UserProfileUpdateRequest request) {
        User user = getUser(userId);

        userRepository.findByUsername(request.username())
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un utilisateur existe déjà pour ce login: " + request.username());
                });

        user.setUsername(request.username());
        if (StringUtils.hasText(request.password())) {
            passwordHistoryService.assertNotReused(user, request.password());
            passwordHistoryService.record(user.getId(), user.getPassword());
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        String nouveauxRoles = StringUtils.hasText(request.roles()) ? request.roles() : user.getRoles();
        rejeterSiRoleCommercialSurCompteRattacheAPartenaire(user, nouveauxRoles);
        user.setRoles(nouveauxRoles);
        if (request.active() != null) {
            user.setActive(request.active());
            // Ferme la fenetre d'exposition d'un access token deja emis pour ce compte, cf.
            // UserActiveStatusCache - sans ca, un access token en poche reste utilisable jusqu'a
            // 1h apres cette desactivation malgre le blocage correct du login lui-meme.
            userActiveStatusCache.invalidate(userId);
        }
        user.setTypeProfile(resolveTypeProfile(request.typeProfileId()));
        user.setLastUpdate(Instant.now());
        User savedUser = userRepository.save(user);

        Profile profile = profileRepository.findByUserId(savedUser.getId())
                .orElseGet(() -> Profile.builder().user(savedUser).build());

        if (profile.getCode() == null) {
            profile.setCode(StringUtils.hasText(request.code()) ? request.code() : codeGeneratorService.generate(CodePrefix.PROFILE));
        } else if (StringUtils.hasText(request.code())) {
            profile.setCode(request.code());
        }
        profile.setFirstname(request.firstname());
        profile.setLastname(request.lastname());
        profile.setPhone(request.phone());
        profile.setRole(request.role());
        profile.setEmail(request.email());
        profile.setSector(request.sector());
        profile.setBio(request.bio());
        profile.setFacebookUrl(request.facebookUrl());
        profile.setTwitterUrl(request.twitterUrl());
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setInstagramUrl(request.instagramUrl());
        profile.setCountry(request.country());
        profile.setCity(request.city());
        profile.setState(request.state());
        profile.setPostalCode(request.postalCode());
        profile.setTaxId(request.taxId());
        if (request.twoFactorEnabled() != null) {
            profile.setTwoFactorEnabled(request.twoFactorEnabled());
        }
        profile.setVillePartenaire(resolveVillePartenaire(request.villePartenaireId()));
        profile = profileRepository.save(profile);

        return toResponse(savedUser, profile);
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        User user = getUser(userId);

        passwordHistoryService.assertNotReused(user, request.newPassword());
        passwordHistoryService.record(user.getId(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFirstConnexion(1);
        user.setDateChangPwd(new Date());
        user.setLastUpdate(Instant.now());
        userRepository.save(user);

        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedFalse(userId);
        activeSessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(activeSessions);
    }

    /**
     * Renvoie les acces d'un compte DEJA existant : regenere un mot de passe temporaire et l'envoie
     * par SMS (meme mecanisme sendFromTemplate que create()), sans jamais l'exposer dans la reponse
     * HTTP - contrairement a resetPassword() ci-dessus (mot de passe CHOISI par l'admin, jamais
     * envoye par SMS), ici c'est l'inverse : mot de passe genere par le backend, jamais visible de
     * l'admin, transmis uniquement par SMS au titulaire (contrat "Renvoi automatique des acces (SMS)
     * a un partenaire deja cree", 2026-08-25). Pas de verification d'historique de mots de passe
     * (PasswordHistoryService.assertNotReused) : le mot de passe est genere par le backend, pas
     * choisi par un humain, meme raison que le mot de passe initial de create()/PartenaireService
     * (record-only, cf. PasswordHistoryService).
     */
    @Transactional
    public UserProfileResponse resendAccess(Long userId) {
        User user = getUser(userId);
        Profile profile = profileRepository.findByUserId(userId).orElse(null);

        String motDePasseTemporaire = generateTemporaryPassword();
        passwordHistoryService.record(user.getId(), user.getPassword());
        user.setPassword(passwordEncoder.encode(motDePasseTemporaire));
        user.setFirstConnexion(1);
        user.setDateChangPwd(new Date());
        user.setLastUpdate(Instant.now());
        User savedUser = userRepository.save(user);

        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedFalse(userId);
        activeSessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(activeSessions);

        boolean estPartenaire = estCompteDePartenaire(profile);
        Boolean notifie = smsNotificationService.sendFromTemplate(
                profile == null ? null : profile.getPhone(),
                estPartenaire ? TEMPLATE_RENVOI_ACCES_PARTENAIRE : TEMPLATE_RENVOI_ACCES_BACKOFFICE,
                estPartenaire
                        ? Map.of("username", savedUser.getUsername(), "motDePasse", motDePasseTemporaire, "lienApplication", lienConnexionPartenaire)
                        : Map.of("username", savedUser.getUsername(), "motDePasse", motDePasseTemporaire),
                estPartenaire ? TypeMessageSms.renvoi_acces_partenaire : TypeMessageSms.renvoi_acces
        );

        return toResponse(savedUser, profile, null, notifie);
    }

    /** Meme resolution "prudente" que AuthService.buildUserResponse() (partenaireId du DTO de
     * connexion) : plusieurs Partenaire lies au meme Profile est une donnee anormale (rien ne l'empeche
     * en base) - dans ce cas on ne devine pas, on journalise et on retombe sur le template back-office
     * (sans lien partenaire) plutot que de risquer d'envoyer le lien partenaire a un compte ambigu. */
    private boolean estCompteDePartenaire(Profile profile) {
        if (profile == null) {
            return false;
        }
        List<Partenaire> partenaires = partenaireRepository.findByProfile_Id(profile.getId());
        if (partenaires.size() > 1) {
            log.warn("Profile {} est lie a {} partenaires, resendAccess() retombe sur le template back-office par prudence",
                    profile.getId(), partenaires.size());
            return false;
        }
        return partenaires.size() == 1;
    }

    @Transactional
    public UserProfileResponse setActive(Long userId, UserStatusRequest request) {
        User user = getUser(userId);
        user.setActive(request.active());
        user.setLastUpdate(Instant.now());
        User savedUser = userRepository.save(user);
        // Cf. UserActiveStatusCache - meme raison que dans update() ci-dessus. C'est ce point d'entree
        // qu'utilise PartenaireService.synchroniserActivationCompte (desactivation en cascade via
        // Partenaire.accesActiviteCommerciale).
        userActiveStatusCache.invalidate(userId);

        if (!request.active()) {
            List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedFalse(userId);
            activeSessions.forEach(session -> session.setRevoked(true));
            userSessionRepository.saveAll(activeSessions);
        }

        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        return toResponse(savedUser, profile);
    }

    @Transactional
    public void delete(Long userId) {
        User user = getUser(userId);
        profileRepository.findByUserId(userId).ifPresent(profileRepository::delete);
        userSessionRepository.deleteAll(userSessionRepository.findAllByUserId(userId));
        userRepository.delete(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + userId));
    }

    private TypeProfile resolveTypeProfile(Long typeProfileId) {
        if (typeProfileId == null) {
            return null;
        }
        return typeProfileRepository.findById(typeProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TypeProfile introuvable: " + typeProfileId));
    }

    private VillePartenaire resolveVillePartenaire(Long villePartenaireId) {
        if (villePartenaireId == null) {
            return null;
        }
        return villePartenaireRepository.findById(villePartenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VillePartenaire introuvable: " + villePartenaireId));
    }

    /**
     * Le frontend du module Commercial bascule son interface sur le seul signal "roles contient
     * COMMERCIAL" pour distinguer un vrai commercial interne d'un compte partenaire (cf.
     * docs/api-contracts/module-commercial-retour-frontend.md §2). Rien ne l'empechait cote back :
     * roles est un champ libre sur User, independant de la resolution partenaireId (basee sur la
     * relation reelle Partenaire.profile, cf. AuthService.buildUserResponse()). Garde-fou symetrique
     * a celui de PartenaireService.resolveProfileUnique().
     */
    private void rejeterSiRoleCommercialSurCompteRattacheAPartenaire(User user, String roles) {
        if (roles == null || !roles.toUpperCase(java.util.Locale.ROOT).contains("COMMERCIAL")) {
            return;
        }
        profileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            if (!partenaireRepository.findByProfile_Id(profile.getId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ce compte est rattache a un partenaire, le role COMMERCIAL ne peut pas lui etre attribue");
            }
        });
    }

    private UserProfileResponse toResponse(User user, Profile profile) {
        return toResponse(user, profile, null, null);
    }

    private UserProfileResponse toResponse(User user, Profile profile, String temporaryPassword, Boolean notificationEnvoyee) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles(),
                user.isActive(),
                profile == null ? null : profile.getId(),
                profile == null ? null : profile.getCode(),
                profile == null ? null : profile.getFirstname(),
                profile == null ? null : profile.getLastname(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getRole(),
                profile == null ? null : profile.getEmail(),
                profile == null ? null : profile.getSector(),
                profile != null && profile.isActive(),
                profile == null || profile.getVillePartenaire() == null ? null : profile.getVillePartenaire().getId(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getFacebookUrl(),
                profile == null ? null : profile.getTwitterUrl(),
                profile == null ? null : profile.getLinkedinUrl(),
                profile == null ? null : profile.getInstagramUrl(),
                profile == null ? null : profile.getCountry(),
                profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getState(),
                profile == null ? null : profile.getPostalCode(),
                profile == null ? null : profile.getTaxId(),
                profile == null || profile.isTwoFactorEnabled(),
                temporaryPassword,
                notificationEnvoyee,
                user.getTypeProfile() == null ? null : user.getTypeProfile().getId()
        );
    }
}
