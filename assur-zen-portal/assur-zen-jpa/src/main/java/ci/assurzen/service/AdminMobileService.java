package ci.assurzen.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AdminMobileDtos;
import ci.assurzen.entities.AccountDeletionRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CallbackRequest;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractCustomerRequest;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.UserSession;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.AccountDeletionRequestRepository;
import ci.assurzen.repositories.CallbackRequestRepository;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractCustomerRequestRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.UserSessionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.security.UserActiveStatusCache;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.CommercialService;
import ci.assurzen.services.PasswordHistoryService;
import ci.assurzen.services.SmsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administration des comptes et des demandes de l'application mobile (back-office, 2026-11-03) : creation d'un compte
 * pour un assure existant, activation / desactivation, rattachement des assures, et traitement des demandes de
 * suppression de compte, de modification / resiliation de contrat et de rappel. Reserve aux administrateurs.
 *
 * Un compte cree par l'administrateur n'a AUCUN PIN connu : le client le definit lui-meme via "PIN oublie" (code SMS).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMobileService {

    private static final String MODULE = "MOBILE";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserSessionRepository userSessionRepository;
    private final InsuredRepository insuredRepository;
    private final ContractRepository contractRepository;
    private final ClientAccountInsuredRepository linkRepository;
    private final AccountDeletionRequestRepository deletionRepository;
    private final ContractCustomerRequestRepository contractRequestRepository;
    private final CallbackRequestRepository callbackRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;
    private final CodeGeneratorService codeGeneratorService;
    private final SmsNotificationService smsNotificationService;
    private final UserActiveStatusCache userActiveStatusCache;
    private final FunctionalAuditRecorder auditRecorder;
    private final CommercialService commercialService;
    private final ci.assurzen.service.AuthService authService;

    // ------------------------------------------------------------------ comptes

    @Transactional(readOnly = true)
    public AdminMobileDtos.AccountPage comptes(String q, Boolean actif, int page, int size) {
        garde();
        int taille = Math.min(Math.max(size, 1), 100);
        Page<User> resultat = userRepository.rechercherComptesMobile(StringUtils.hasText(q) ? q.trim() : null, actif,
                PageRequest.of(Math.max(page, 0), taille, Sort.by(Sort.Direction.DESC, "id")));
        List<AdminMobileDtos.Account> items = resultat.getContent().stream().map(this::vueCompte).toList();
        return new AdminMobileDtos.AccountPage(items, resultat.getNumber(), taille, resultat.getTotalElements(), resultat.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminMobileDtos.AccountDetail compte(Long userId) {
        garde();
        return detail(compteMobile(userId));
    }

    @Transactional
    public AdminMobileDtos.AccountDetail creerCompte(AdminMobileDtos.CreateAccountRequest requete) {
        garde();
        Insured assure = insuredRepository.findById(requete.insuredId())
                .orElseThrow(() -> new MobileApiException(404, "INSURED_NOT_FOUND", "Assuré introuvable.", "insuredId"));
        String brut = StringUtils.hasText(requete.phone()) ? requete.phone() : assure.getClientPhone();
        String telephone = normaliser(brut);
        if (telephone == null) {
            throw new MobileApiException(400, "PHONE_INVALID",
                    "Le numéro doit contenir 10 chiffres et commencer par 01, 05 ou 07. Renseignez le champ phone.", "phone");
        }
        if (userRepository.findByUsername(telephone).isPresent()) {
            throw new MobileApiException(409, "PHONE_ALREADY_REGISTERED", "Un compte existe déjà pour ce numéro.", "phone");
        }

        User user = new User();
        user.setUsername(telephone);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // inconnu de tous
        user.setRoles(MobileAuthService.ROLE_CLIENT);
        user.setActive(true);
        user.setFirstConnexion(0);
        user.setDateChangPwd(new Date());
        user.setCreation(Instant.now());
        user.setLastUpdate(Instant.now());
        user = userRepository.saveAndFlush(user);
        passwordHistoryService.record(user.getId(), user.getPassword());

        profileRepository.save(Profile.builder()
                .code(codeGeneratorService.generate(CodePrefix.PROFILE))
                .firstname(tronquer(assure.getPrenoms(), 150, "Client"))
                .lastname(tronquer(assure.getNom(), 150, "AssurZen"))
                .phone(telephone)
                .role(MobileAuthService.ROLE_CLIENT)
                .email(StringUtils.hasText(assure.getClientEmail()) ? assure.getClientEmail() : null)
                .twoFactorEnabled(false)
                .user(user)
                .active(true)
                .expiration(false)
                .build());

        // l'assure choisi + tous ceux qui partagent ce numero (meme regle que l'inscription par le client)
        lier(user.getId(), assure.getId());
        for (Insured autre : insuredRepository.findByPhoneNormalise(telephone)) {
            lier(user.getId(), autre.getId());
        }
        boolean invitation = requete.sendInvitation() == null || requete.sendInvitation();
        if (invitation) {
            smsNotificationService.sendMobileAccountCreatedSms(telephone);
        }
        auditRecorder.recordAction(MODULE, "CompteMobile", String.valueOf(user.getId()), ActionAudit.CREATE,
                "Compte mobile cree par un administrateur pour l'assure " + assure.getId());
        return detail(user);
    }

    @Transactional
    public AdminMobileDtos.AccountDetail changerActivation(Long userId, boolean actif, String commentaire) {
        garde();
        User user = compteMobile(userId);
        user.setActive(actif);
        userRepository.save(user);
        userActiveStatusCache.invalidate(userId);
        if (!actif) {
            revoquerSessions(userId);
        }
        auditRecorder.recordAction(MODULE, "CompteMobile", String.valueOf(userId), actif ? ActionAudit.ACTIVATE : ActionAudit.DEACTIVATE,
                (actif ? "Compte mobile reactive" : "Compte mobile desactive") + (StringUtils.hasText(commentaire) ? " : " + commentaire : ""));
        return detail(user);
    }

    @Transactional
    public AdminMobileDtos.AccountDetail rattacher(Long userId, Long insuredId) {
        garde();
        User user = compteMobile(userId);
        insuredRepository.findById(insuredId)
                .orElseThrow(() -> new MobileApiException(404, "INSURED_NOT_FOUND", "Assuré introuvable.", "insuredId"));
        lier(userId, insuredId);
        auditRecorder.recordAction(MODULE, "CompteMobile", String.valueOf(userId), ActionAudit.ADD, "Assure " + insuredId + " rattache au compte mobile");
        return detail(user);
    }

    @Transactional
    public AdminMobileDtos.AccountDetail detacher(Long userId, Long insuredId) {
        garde();
        User user = compteMobile(userId);
        linkRepository.deleteByUserIdAndInsuredId(userId, insuredId);
        auditRecorder.recordAction(MODULE, "CompteMobile", String.valueOf(userId), ActionAudit.REMOVE, "Assure " + insuredId + " detache du compte mobile");
        return detail(user);
    }

    // ------------------------------------------------------------------ suppressions de compte

    @Transactional(readOnly = true)
    public List<AdminMobileDtos.DeletionRequestItem> suppressions(String statut) {
        garde();
        List<AccountDeletionRequest> demandes = StringUtils.hasText(statut)
                ? deletionRepository.findTop200ByStatutOrderByRequestedAtDesc(statut.trim().toUpperCase(Locale.ROOT))
                : deletionRepository.findTop200ByOrderByRequestedAtDesc();
        return demandes.stream().map(this::vueSuppression).toList();
    }

    /**
     * APPROVE : le compte est anonymise (identifiant libere, nom et telephone effaces, rattachements retires) et reste
     * desactive ; les contrats et assures du back-office ne sont PAS touches. Politique par defaut, a confirmer avec
     * AssurZen (conservation legale). Exige confirm=true. REJECT : demande refusee, compte reactive.
     */
    @Transactional
    public AdminMobileDtos.DeletionRequestItem traiterSuppression(Long id, AdminMobileDtos.ProcessDeletionRequest requete) {
        garde();
        AccountDeletionRequest demande = deletionRepository.findById(id)
                .orElseThrow(() -> new MobileApiException(404, "REQUEST_NOT_FOUND", "Demande introuvable."));
        if (!"EN_ATTENTE".equals(demande.getStatut())) {
            throw new MobileApiException(409, "REQUEST_ALREADY_PROCESSED", "Cette demande a déjà été traitée.");
        }
        User user = userRepository.findById(demande.getUserId()).orElse(null);
        if ("APPROVE".equals(requete.decision())) {
            if (!Boolean.TRUE.equals(requete.confirm())) {
                throw new MobileApiException(400, "CONFIRMATION_REQUIRED",
                        "La suppression est irréversible : renvoyez confirm=true pour confirmer.", "confirm");
            }
            if (user != null) {
                profileRepository.findByUserId(user.getId()).ifPresent(p -> {
                    p.setFirstname("Compte");
                    p.setLastname("supprimé");
                    p.setPhone(null);
                    p.setEmail(null);
                    p.setActive(false);
                    profileRepository.save(p);
                });
                user.setUsername("supprime-" + user.getId());
                user.setActive(false);
                userRepository.save(user);
                userActiveStatusCache.invalidate(user.getId());
                linkRepository.deleteByUserId(user.getId());
                revoquerSessions(user.getId());
            }
            demande.setStatut("TRAITEE");
        } else {
            if (user != null) {
                user.setActive(true);
                userRepository.save(user);
                userActiveStatusCache.invalidate(user.getId());
            }
            demande.setStatut("REFUSEE");
        }
        marquer(demande::setProcessedAt, demande::setProcessedBy, demande::setProcessComment, requete.comment());
        deletionRepository.save(demande);
        auditRecorder.recordAction(MODULE, "DemandeSuppressionCompte", demande.getReference(),
                "APPROVE".equals(requete.decision()) ? ActionAudit.APPROVE : ActionAudit.REJECT, "Demande de suppression " + demande.getStatut());
        return vueSuppression(demande);
    }

    // ------------------------------------------------------------------ demandes de contrat

    @Transactional(readOnly = true)
    public List<AdminMobileDtos.ContractRequestItem> demandesContrat(String statut) {
        garde();
        List<ContractCustomerRequest> demandes = StringUtils.hasText(statut)
                ? contractRequestRepository.findTop200ByStatutOrderByCreatedAtDesc(statut.trim().toUpperCase(Locale.ROOT))
                : contractRequestRepository.findTop200ByOrderByCreatedAtDesc();
        Map<Long, Contract> contrats = contractRepository.findAllById(demandes.stream().map(ContractCustomerRequest::getContractId).distinct().toList())
                .stream().collect(Collectors.toMap(Contract::getId, Function.identity()));
        Map<Long, String> telephones = telephonesDe(demandes.stream().map(ContractCustomerRequest::getUserId).toList());
        return demandes.stream().map(d -> vueDemandeContrat(d, contrats.get(d.getContractId()), telephones.get(d.getUserId()))).toList();
    }

    @Transactional
    public AdminMobileDtos.ContractRequestItem traiterDemandeContrat(Long id, AdminMobileDtos.ProcessRequest requete) {
        garde();
        if (!requete.statut().equals("TRAITEE") && !requete.statut().equals("REFUSEE")) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "statut doit valoir TRAITEE ou REFUSEE.", "statut");
        }
        ContractCustomerRequest demande = contractRequestRepository.findById(id)
                .orElseThrow(() -> new MobileApiException(404, "REQUEST_NOT_FOUND", "Demande introuvable."));
        if (!"EN_ATTENTE".equals(demande.getStatut())) {
            throw new MobileApiException(409, "REQUEST_ALREADY_PROCESSED", "Cette demande a déjà été traitée.");
        }
        demande.setStatut(requete.statut());
        marquer(demande::setProcessedAt, demande::setProcessedBy, demande::setProcessComment, requete.comment());
        contractRequestRepository.save(demande);
        auditRecorder.recordAction(MODULE, "DemandeContratMobile", demande.getReference(), ActionAudit.UPDATE, "Demande " + demande.getType() + " " + demande.getStatut());
        Contract contrat = contractRepository.findById(demande.getContractId()).orElse(null);
        return vueDemandeContrat(demande, contrat, telephonesDe(List.of(demande.getUserId())).get(demande.getUserId()));
    }

    // ------------------------------------------------------------------ rappels

    @Transactional(readOnly = true)
    public List<AdminMobileDtos.CallbackItem> rappels(String statut) {
        garde();
        List<CallbackRequest> demandes = StringUtils.hasText(statut)
                ? callbackRepository.findTop200ByStatutOrderByCreatedAtDesc(statut.trim().toUpperCase(Locale.ROOT))
                : callbackRepository.findTop200ByOrderByCreatedAtDesc();
        return demandes.stream().map(this::vueRappel).toList();
    }

    @Transactional
    public AdminMobileDtos.CallbackItem traiterRappel(Long id, AdminMobileDtos.ProcessRequest requete) {
        garde();
        if (!requete.statut().equals("RAPPELE") && !requete.statut().equals("ANNULE")) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "statut doit valoir RAPPELE ou ANNULE.", "statut");
        }
        CallbackRequest demande = callbackRepository.findById(id)
                .orElseThrow(() -> new MobileApiException(404, "REQUEST_NOT_FOUND", "Demande introuvable."));
        if (!"EN_ATTENTE".equals(demande.getStatut())) {
            throw new MobileApiException(409, "REQUEST_ALREADY_PROCESSED", "Cette demande a déjà été traitée.");
        }
        demande.setStatut(requete.statut());
        marquer(demande::setProcessedAt, demande::setProcessedBy, demande::setProcessComment, requete.comment());
        callbackRepository.save(demande);
        auditRecorder.recordAction(MODULE, "DemandeRappel", demande.getReference(), ActionAudit.UPDATE, "Rappel " + demande.getStatut());
        return vueRappel(demande);
    }

    // ------------------------------------------------------------------

    private void garde() {
        commercialService.requireAdmin();
    }

    private void marquer(java.util.function.Consumer<Instant> date, java.util.function.Consumer<String> par,
                         java.util.function.Consumer<String> commentaire, String texte) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        date.accept(Instant.now());
        par.accept(principal.username());
        commentaire.accept(StringUtils.hasText(texte) ? texte.trim() : null);
    }

    private User compteMobile(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> MobileAuthService.ROLE_CLIENT.equalsIgnoreCase(u.getRoles() == null ? "" : u.getRoles().trim()))
                .orElseThrow(() -> new MobileApiException(404, "ACCOUNT_NOT_FOUND", "Compte mobile introuvable."));
    }

    private void lier(Long userId, Long insuredId) {
        if (!linkRepository.existsByUserIdAndInsuredId(userId, insuredId)) {
            linkRepository.save(new ClientAccountInsured(userId, insuredId));
        }
    }

    private void revoquerSessions(Long userId) {
        List<UserSession> sessions = userSessionRepository.findAllByUserIdAndRevokedFalse(userId);
        sessions.forEach(s -> s.setRevoked(true));
        userSessionRepository.saveAll(sessions);
    }

    /** "0700000000", "+225 07 00 00 00 00"... -> 10 chiffres commencant par 01, 05 ou 07, sinon null. */
    static String normaliser(String brut) {
        if (brut == null) {
            return null;
        }
        String chiffres = brut.replaceAll("\\D", "");
        if (chiffres.length() > 10) {
            chiffres = chiffres.substring(chiffres.length() - 10);
        }
        return chiffres.matches("^(01|05|07)\\d{8}$") ? chiffres : null;
    }

    private String tronquer(String valeur, int max, String parDefaut) {
        if (!StringUtils.hasText(valeur)) {
            return parDefaut;
        }
        String v = valeur.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private AdminMobileDtos.Account vueCompte(User u) {
        Profile profil = profileRepository.findByUserId(u.getId()).orElse(null);
        String nom = profil == null ? null : (nonNull(profil.getFirstname()) + " " + nonNull(profil.getLastname())).trim();
        AccountDeletionRequest suppression = deletionRepository
                .findFirstByUserIdAndStatutOrderByRequestedAtDesc(u.getId(), "EN_ATTENTE").orElse(null);
        return new AdminMobileDtos.Account(u.getId(), u.getUsername(), nom, u.isActive(), u.getCreation(),
                (int) linkRepository.countByUserId(u.getId()), suppression == null ? null : suppression.getReference());
    }

    private AdminMobileDtos.AccountDetail detail(User u) {
        List<Long> ids = linkRepository.findByUserId(u.getId()).stream().map(ClientAccountInsured::getInsuredId).toList();
        List<AdminMobileDtos.LinkedInsured> assures = insuredRepository.findAllById(ids).stream()
                .map(i -> new AdminMobileDtos.LinkedInsured(i.getId(), (nonNull(i.getPrenoms()) + " " + nonNull(i.getNom())).trim(), i.getClientPhone()))
                .toList();
        return new AdminMobileDtos.AccountDetail(vueCompte(u), assures);
    }

    private AdminMobileDtos.DeletionRequestItem vueSuppression(AccountDeletionRequest d) {
        User user = userRepository.findById(d.getUserId()).orElse(null);
        Profile profil = user == null ? null : profileRepository.findByUserId(user.getId()).orElse(null);
        String nom = profil == null ? null : (nonNull(profil.getFirstname()) + " " + nonNull(profil.getLastname())).trim();
        return new AdminMobileDtos.DeletionRequestItem(d.getId(), d.getReference(), d.getUserId(), user == null ? null : user.getUsername(),
                nom, d.getStatut(), d.getRequestedAt(), d.getProcessedAt(), d.getProcessedBy(), d.getProcessComment());
    }

    private AdminMobileDtos.ContractRequestItem vueDemandeContrat(ContractCustomerRequest d, Contract c, String telephone) {
        Insured assure = c == null ? null : c.getInsured();
        return new AdminMobileDtos.ContractRequestItem(d.getId(), d.getReference(), d.getContractId(),
                c == null ? null : c.getPolicyNumber(),
                assure == null ? null : (nonNull(assure.getPrenoms()) + " " + nonNull(assure.getNom())).trim(),
                d.getUserId(), telephone, d.getType(), d.getMessage(), d.getStatut(), d.getCreatedAt(), d.getProcessedAt(),
                d.getProcessedBy(), d.getProcessComment());
    }

    private AdminMobileDtos.CallbackItem vueRappel(CallbackRequest d) {
        return new AdminMobileDtos.CallbackItem(d.getId(), d.getReference(), d.getPhone(), d.getSlot(), d.getProductId(), d.getUserId(),
                d.getStatut(), d.getCreatedAt(), d.getProcessedAt(), d.getProcessedBy(), d.getProcessComment());
    }

    private Map<Long, String> telephonesDe(List<Long> userIds) {
        return userRepository.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private String nonNull(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
