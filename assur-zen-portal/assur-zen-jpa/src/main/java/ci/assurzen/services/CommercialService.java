package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialOptionResponse;
import ci.assurzen.dto.CommercialRequest;
import ci.assurzen.dto.CommercialResponse;
import ci.assurzen.dto.CommercialStatusRequest;
import ci.assurzen.dto.CommercialUpdateRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.UserProfileRequest;
import ci.assurzen.dto.UserProfileResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Fiche commerciale (contrat "Module Commercial", 2026-09-07). Le compte de connexion est cree via
 * UserProfileService.create() (roles="COMMERCIAL") - meme mecanisme d'authentification et de SMS de
 * bienvenue que tout autre compte back-office, aucun doublon.
 *
 * Reservee aux administrateurs pour toute mutation (creation/modification/changement de statut) et
 * pour la consultation de la liste complete (contient la remuneration) - meme convention binaire
 * ADMIN/proprietaire que PartenaireService.verifierAccesPartenaireCible, pas de moteur de permissions
 * granulaires dans ce projet.
 */
@Service
@RequiredArgsConstructor
public class CommercialService {

    private final CommercialRepository repository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final VillePartenaireRepository villePartenaireRepository;
    private final UserProfileService userProfileService;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "COMMERCIAL", entityName = "Commercial")
    public CommercialResponse create(CommercialRequest request) {
        requireAdmin();
        validateBenefits(request.transportBenefitEnabled(), request.transportBenefitAmount(),
                request.phoneAllowanceEnabled(), request.phoneAllowanceAmount());

        String employeeCode = StringUtils.hasText(request.employeeCode())
                ? request.employeeCode()
                : codeGeneratorService.generate(CodePrefix.COMMERCIAL);
        if (repository.existsByEmployeeCode(employeeCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un commercial existe deja pour ce matricule: " + employeeCode);
        }

        UserProfileResponse compte = userProfileService.create(new UserProfileRequest(
                request.username(),           // username
                request.password(),           // password (genere si vide)
                "COMMERCIAL",                 // roles (User.roles - RBAC)
                null,                         // typeProfileId
                null,                         // code (Profile.code, auto-genere)
                request.firstname(),          // firstname
                request.lastname(),           // lastname
                request.phone(),              // phone
                "COMMERCIAL",                 // role (Profile.role, informatif)
                request.email(),              // email
                null,                         // sector
                request.villePartenaireId(),  // villePartenaireId
                true,                         // active
                null,                         // bio
                null,                         // facebookUrl
                null,                         // twitterUrl
                null,                         // linkedinUrl
                null,                         // instagramUrl
                null,                         // country
                null,                         // city
                null,                         // state
                null,                         // postalCode
                null,                         // taxId
                null                          // twoFactorEnabled (defaut true dans UserProfileService.create)
        ));
        Profile profile = profileRepository.findById(compte.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Profil introuvable apres creation: " + compte.profileId()));

        Commercial commercial = Commercial.builder()
                .employeeCode(employeeCode)
                .profile(profile)
                .baseSalary(request.baseSalary())
                .transportBenefitEnabled(request.transportBenefitEnabled())
                .transportBenefitAmount(request.transportBenefitEnabled() ? request.transportBenefitAmount() : BigDecimal.ZERO)
                .phoneAllowanceEnabled(request.phoneAllowanceEnabled())
                .phoneAllowanceAmount(request.phoneAllowanceEnabled() ? request.phoneAllowanceAmount() : BigDecimal.ZERO)
                .hireDate(request.hireDate())
                .statut(StatutCommercial.ACTIVE)
                .build();
        stampCreated(commercial);
        commercial = repository.save(commercial);

        return toResponse(commercial, compte.temporaryPassword(), compte.notificationEnvoyee());
    }

    @Transactional
    public CommercialResponse update(Long id, CommercialUpdateRequest request) {
        requireAdmin();
        Commercial commercial = getCommercial(id);
        CommercialResponse avant = toResponse(commercial, null, null);
        validateBenefits(request.transportBenefitEnabled(), request.transportBenefitAmount(),
                request.phoneAllowanceEnabled(), request.phoneAllowanceAmount());

        Profile profile = commercial.getProfile();
        profile.setFirstname(request.firstname());
        profile.setLastname(request.lastname());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setVillePartenaire(resolveVillePartenaire(request.villePartenaireId()));
        profileRepository.save(profile);

        commercial.setBaseSalary(request.baseSalary());
        commercial.setTransportBenefitEnabled(request.transportBenefitEnabled());
        commercial.setTransportBenefitAmount(request.transportBenefitEnabled() ? request.transportBenefitAmount() : BigDecimal.ZERO);
        commercial.setPhoneAllowanceEnabled(request.phoneAllowanceEnabled());
        commercial.setPhoneAllowanceAmount(request.phoneAllowanceEnabled() ? request.phoneAllowanceAmount() : BigDecimal.ZERO);
        commercial.setHireDate(request.hireDate());
        commercial.setEndDate(request.endDate());
        stampUpdated(commercial);
        commercial = repository.save(commercial);

        CommercialResponse apres = toResponse(commercial, null, null);
        functionalAuditRecorder.recordUpdate("COMMERCIAL", "Commercial", String.valueOf(id), avant, apres, null);
        return apres;
    }

    /**
     * ACTIVE -&gt; compte de connexion reactive ; INACTIVE/SUSPENDED/TERMINATED -&gt; compte desactive
     * (User.active=false), reutilisant le mecanisme existant de desactivation + revocation immediate
     * des jetons deja emis (UserActiveStatusCache) plutot qu'inventer un nouveau controle d'acces pour
     * "le commercial ne doit plus pouvoir effectuer d'operations commerciales" (§9). TERMINATED est
     * terminal (aucune transition ulterieure).
     */
    @Transactional
    public CommercialResponse changerStatut(Long id, CommercialStatusRequest request) {
        requireAdmin();
        Commercial commercial = getCommercial(id);
        if (commercial.getStatut() == StatutCommercial.TERMINATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce commercial est TERMINATED (statut terminal), aucune transition possible");
        }
        CommercialResponse avant = toResponse(commercial, null, null);

        User user = commercial.getProfile().getUser();
        user.setActive(request.statut() == StatutCommercial.ACTIVE);
        userRepository.save(user);

        commercial.setStatut(request.statut());
        if (request.statut() == StatutCommercial.TERMINATED && commercial.getEndDate() == null) {
            commercial.setEndDate(java.time.LocalDate.now());
        }
        stampUpdated(commercial);
        commercial = repository.save(commercial);

        CommercialResponse apres = toResponse(commercial, null, null);
        functionalAuditRecorder.recordUpdate("COMMERCIAL", "Commercial", String.valueOf(id), avant, apres, request.motif());
        return apres;
    }

    public CommercialResponse get(Long id) {
        verifierAccesCommercialCible(id);
        return toResponse(getCommercial(id), null, null);
    }

    public CommercialResponse me() {
        return toResponse(getCommercialByUser(currentUserId()), null, null);
    }

    /**
     * Force de vente interne proposee dans la liste "Commercial" de la souscription (2026-10-02) :
     * commerciaux ACTIFS disposant d'un compte de connexion (Contract.salesRepId = userId), tries par
     * nom puis prenoms. Pas de garde ADMIN (tout createur de contrat en a besoin) : la reponse ne
     * contient aucune donnee de remuneration, cf. CommercialOptionResponse.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CommercialOptionResponse> options() {
        return repository.findByStatut(StatutCommercial.ACTIVE).stream()
                .filter(c -> c.getProfile() != null && c.getProfile().getUser() != null)
                .map(c -> new CommercialOptionResponse(
                        c.getId(),
                        c.getProfile().getUser().getId(),
                        c.getProfile().getLastname(),
                        c.getProfile().getFirstname(),
                        c.getEmployeeCode(),
                        c.getProfile().getVillePartenaire() == null ? null : c.getProfile().getVillePartenaire().getNomVille()))
                .sorted(java.util.Comparator
                        .comparing((CommercialOptionResponse o) -> o.nom() == null ? "" : o.nom(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(o -> o.prenoms() == null ? "" : o.prenoms(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PageResponse<CommercialResponse> list(StatutCommercial statut, Long villePartenaireId, Integer page, Integer size) {
        requireAdmin();
        List<CommercialResponse> items = repository.findAll().stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> villePartenaireId == null || (c.getProfile().getVillePartenaire() != null
                        && villePartenaireId.equals(c.getProfile().getVillePartenaire().getId())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(c -> toResponse(c, null, null))
                .toList();
        return paginate(items, page, size);
    }

    // ------------------------------------------------------------------
    // Helpers reutilises par les autres services du module Commercial
    // ------------------------------------------------------------------

    public Commercial getCommercial(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commercial introuvable: " + id));
    }

    public Commercial getCommercialByUser(Long userId) {
        return repository.findByProfile_UserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche commerciale liee a cet utilisateur"));
    }

    public void verifierAccesCommercialCible(Long commercialId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        boolean estCeCommercial = repository.findByProfile_UserId(principal.userId())
                .map(Commercial::getId)
                .map(commercialId::equals)
                .orElse(false);
        if (!estCeCommercial) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces reserve a ce commercial ou a un administrateur");
        }
    }

    public boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    public void requireAdmin() {
        if (!isAdmin(authService.currentPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    public Long currentUserId() {
        return authService.currentPrincipal().userId();
    }

    // ------------------------------------------------------------------

    private void validateBenefits(boolean transportEnabled, BigDecimal transportAmount, boolean phoneEnabled, BigDecimal phoneAmount) {
        if (transportEnabled && (transportAmount == null || transportAmount.compareTo(BigDecimal.ZERO) < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant de l'avantage transport est obligatoire et doit etre positif quand l'avantage est active");
        }
        if (phoneEnabled && (phoneAmount == null || phoneAmount.compareTo(BigDecimal.ZERO) < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant de la dotation telephone est obligatoire et doit etre positif quand l'avantage est active");
        }
    }

    private VillePartenaire resolveVillePartenaire(Long villePartenaireId) {
        return villePartenaireRepository.findById(villePartenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Localite introuvable: " + villePartenaireId));
    }

    private void stampCreated(Commercial commercial) {
        String username = authService.currentPrincipal().username();
        commercial.setCreatedBy(username);
        commercial.setUpdatedBy(username);
    }

    private void stampUpdated(Commercial commercial) {
        commercial.setUpdatedBy(authService.currentPrincipal().username());
    }

    private CommercialResponse toResponse(Commercial commercial, String temporaryPassword, Boolean notificationEnvoyee) {
        Profile profile = commercial.getProfile();
        User user = profile.getUser();
        VillePartenaire ville = profile.getVillePartenaire();
        return new CommercialResponse(
                commercial.getId(),
                commercial.getEmployeeCode(),
                profile.getId(),
                user.getId(),
                user.getUsername(),
                profile.getFirstname(),
                profile.getLastname(),
                profile.getEmail(),
                profile.getPhone(),
                ville == null ? null : ville.getId(),
                ville == null ? null : ville.getNomVille(),
                commercial.getBaseSalary(),
                commercial.isTransportBenefitEnabled(),
                commercial.getTransportBenefitAmount(),
                commercial.isPhoneAllowanceEnabled(),
                commercial.getPhoneAllowanceAmount(),
                commercial.getHireDate(),
                commercial.getEndDate(),
                commercial.getStatut(),
                commercial.getCreatedAt(),
                commercial.getUpdatedAt(),
                temporaryPassword,
                notificationEnvoyee
        );
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
}
