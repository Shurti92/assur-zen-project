package ci.assurzen.services;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.VignetteCommissionPaymentRequest;
import ci.assurzen.dto.VignetteCommissionResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VignetteCommission;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VignetteCommissionRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registre des commissions forfaitaires dues a l'apporteur (partenaire ou commercial interne) d'une
 * demande de vignette moto - contrat "commission apporteur vignette", 2026-09-15. Les lignes sont
 * creees par DemandeVignetteMotoService.creer() (jamais ici) des qu'un apporteur est renseigne;
 * ce service ne fait que lister et payer. Reserve aux administrateurs (SecurityConfig :
 * /api-assur-zen/vignettes-moto/commissions/** en hasRole(ADMIN)), meme garde en double au niveau
 * service que les autres registres de commission de ce projet.
 */
@Service
@RequiredArgsConstructor
public class VignetteCommissionService {

    private static final String TEMPLATE_COMMISSION_VIGNETTE_PAYEE = "COMMISSION_VIGNETTE_PAYEE";

    private final VignetteCommissionRepository repository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final SmsNotificationService smsNotificationService;
    private final TelegramNotificationService telegramNotificationService;

    public PageResponse<VignetteCommissionResponse> list(CommissionStatus statut, Integer page, Integer size) {
        requireAdmin();
        List<VignetteCommissionResponse> items = (statut == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatutOrderByCreatedAtDesc(statut)).stream()
                .map(c -> toResponse(c, null))
                .toList();
        return paginate(items, page, size);
    }

    public PageResponse<VignetteCommissionResponse> listByPartenaire(Long partenaireId, Integer page, Integer size) {
        requireAdmin();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        Page<VignetteCommission> result = repository.findByPartenaireId(partenaireId,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPageResponse(result);
    }

    public PageResponse<VignetteCommissionResponse> listByCommercial(Long commercialId, Integer page, Integer size) {
        requireAdmin();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        Page<VignetteCommission> result = repository.findByCommercialId(commercialId,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPageResponse(result);
    }

    @Transactional
    public List<VignetteCommissionResponse> payer(VignetteCommissionPaymentRequest request) {
        requireAdmin();
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User paidBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        List<VignetteCommission> commissions = request.commissionIds().stream()
                .map(this::resolveEligible)
                .toList();

        commissions.forEach(c -> {
            c.setStatut(CommissionStatus.paid);
            c.setPaidBy(paidBy);
            c.setReference(request.reference());
            c.setPaidAt(java.time.Instant.now());
        });
        repository.saveAll(commissions);

        Map<Long, Boolean> notificationParCommission = notifierApporteurs(commissions, request.reference());

        return commissions.stream()
                .map(c -> toResponse(c, notificationParCommission.get(c.getId())))
                .toList();
    }

    private VignetteCommission resolveEligible(Long commissionId) {
        VignetteCommission commission = repository.findById(commissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission vignette introuvable: " + commissionId));
        if (commission.getStatut() == CommissionStatus.paid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Commission deja payee pour la demande " + commission.getDemandeVignetteMoto().getReference());
        }
        return commission;
    }

    /** Un SMS DISTINCT par vignette payee (2026-09-16, revu suite au retour utilisateur) - jamais
     * agrege, contrairement a CommissionService/CommercialCommissionService : le contenu inclut
     * desormais les details de LA moto concernee (marque/modele/chassis), qui n'ont de sens que pour
     * une vignette a la fois. Paie plusieurs commissions du meme apporteur dans le meme appel produit
     * donc plusieurs SMS, un par vignette - decision produit assumee. L'echec d'un envoi ne fait jamais
     * echouer le paiement (deja acquis en base a cette etape). */
    private Map<Long, Boolean> notifierApporteurs(List<VignetteCommission> commissions, String reference) {
        Map<Long, Boolean> resultat = new HashMap<>();
        for (VignetteCommission commission : commissions) {
            DemandeVignetteMoto demande = commission.getDemandeVignetteMoto();
            long montant = commission.getMontant() == null ? 0 : commission.getMontant();
            String telephone = telephoneApporteur(commission);
            String nomApporteur = nomApporteur(commission);

            boolean notifie = smsNotificationService.sendFromTemplate(
                    telephone,
                    TEMPLATE_COMMISSION_VIGNETTE_PAYEE,
                    Map.of(
                            "montant", formatMontant(montant),
                            "marque", demande.getMarqueMoto(),
                            "modele", demande.getModeleMoto(),
                            "numeroChassis", demande.getNumeroChassisMoto(),
                            "reference", StringUtils.hasText(reference) ? reference : "N/A"
                    ),
                    TypeMessageSms.commission_vignette_payee
            );
            resultat.put(commission.getId(), notifie);

            telegramNotificationService.envoyer(
                    "<b>Commission vignette payee</b>\n"
                            + TelegramNotificationService.echapperHtml(nomApporteur)
                            + " : " + formatMontant(montant) + " FCFA ("
                            + TelegramNotificationService.echapperHtml(demande.getMarqueMoto() + " " + demande.getModeleMoto())
                            + ", chassis " + TelegramNotificationService.echapperHtml(demande.getNumeroChassisMoto()) + ")");
        }
        return resultat;
    }

    private String telephoneApporteur(VignetteCommission c) {
        return c.getPartenaire() != null ? c.getPartenaire().getTelephone() : c.getCommercial().getProfile().getPhone();
    }

    private String nomApporteur(VignetteCommission c) {
        if (c.getPartenaire() != null) {
            Partenaire p = c.getPartenaire();
            return p.getNom() + " " + p.getPrenoms();
        }
        Commercial commercial = c.getCommercial();
        return commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname();
    }

    private String formatMontant(long montant) {
        String brut = String.valueOf(montant);
        StringBuilder resultat = new StringBuilder();
        int compteur = 0;
        for (int i = brut.length() - 1; i >= 0; i--) {
            resultat.append(brut.charAt(i));
            compteur++;
            if (compteur % 3 == 0 && i != 0) {
                resultat.append(' ');
            }
        }
        return resultat.reverse().toString();
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
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

    private PageResponse<VignetteCommissionResponse> toPageResponse(Page<VignetteCommission> page) {
        return new PageResponse<>(
                page.getContent().stream().map(c -> toResponse(c, null)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private VignetteCommissionResponse toResponse(VignetteCommission c, Boolean notificationEnvoyee) {
        DemandeVignetteMoto demande = c.getDemandeVignetteMoto();
        Partenaire partenaire = c.getPartenaire();
        Commercial commercial = c.getCommercial();
        User paidBy = c.getPaidBy();

        return new VignetteCommissionResponse(
                c.getId(),
                demande == null ? null : demande.getId(),
                demande == null ? null : demande.getReference(),
                partenaire == null ? null : partenaire.getId(),
                partenaire == null ? null : partenaire.getNom() + " " + partenaire.getPrenoms(),
                commercial == null ? null : commercial.getId(),
                commercial == null ? null : commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname(),
                c.getMontant(),
                c.getStatut(),
                paidBy == null ? null : paidBy.getId(),
                paidBy == null ? null : paidBy.getUsername(),
                c.getReference(),
                c.getPaidAt(),
                c.getCreatedAt(),
                notificationEnvoyee);
    }
}
