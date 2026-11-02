package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.dto.FraisLivraisonZoneRequest;
import ci.assurzen.dto.FraisLivraisonZoneResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.FraisLivraisonZone;
import ci.assurzen.repositories.FraisLivraisonZoneRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Zones de livraison paremetrables (§8 du contrat vignette moto). Reserve aux administrateurs -
 * memes gardes-fous que TarifVignetteMotoService (double couche : SecurityConfig + requireAdmin()).
 */
@Service
@RequiredArgsConstructor
public class FraisLivraisonZoneService {

    private final FraisLivraisonZoneRepository repository;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<FraisLivraisonZoneResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public FraisLivraisonZoneResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    /** Utilise par DemandeVignetteMotoService pour resoudre le montant fige a la creation d'une
     * demande / a une simulation - le code est saisi par le client (choix direct de la zone, pas de
     * resolution automatique depuis un texte libre de ville, decision produit du 2026-09-11). */
    public FraisLivraisonZone resoudre(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone de livraison obligatoire");
        }
        return repository.findByCodeAndActifTrue(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Zone de livraison inconnue ou desactivee: " + code));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "VIGNETTES_MOTO", entityName = "FraisLivraisonZone")
    public FraisLivraisonZoneResponse create(FraisLivraisonZoneRequest request) {
        requireAdmin();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une zone de livraison existe deja pour ce code: " + code);
        }
        FraisLivraisonZone zone = FraisLivraisonZone.builder()
                .code(code)
                .libelle(request.libelle())
                .montant(request.montant())
                .build();
        return toResponse(repository.save(zone));
    }

    @Transactional
    public FraisLivraisonZoneResponse update(Long id, FraisLivraisonZoneRequest request) {
        requireAdmin();
        FraisLivraisonZone zone = getEntity(id);
        FraisLivraisonZoneResponse avant = toResponse(zone);

        String code = request.code().trim().toUpperCase(Locale.ROOT);
        repository.findAll().stream()
                .filter(z -> !z.getId().equals(id) && z.getCode().equals(code))
                .findAny()
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Une zone de livraison existe deja pour ce code: " + code);
                });

        zone.setCode(code);
        zone.setLibelle(request.libelle());
        zone.setMontant(request.montant());
        zone = repository.save(zone);

        functionalAuditRecorder.recordUpdate("VIGNETTES_MOTO", "FraisLivraisonZone", String.valueOf(id), avant, toResponse(zone), null);
        return toResponse(zone);
    }

    @Transactional
    @Auditable(action = ActionAudit.DEACTIVATE, module = "VIGNETTES_MOTO", entityName = "FraisLivraisonZone")
    public FraisLivraisonZoneResponse desactiver(Long id) {
        requireAdmin();
        FraisLivraisonZone zone = getEntity(id);
        zone.setActif(false);
        return toResponse(repository.save(zone));
    }

    private FraisLivraisonZone getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone de livraison introuvable: " + id));
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private FraisLivraisonZoneResponse toResponse(FraisLivraisonZone z) {
        return new FraisLivraisonZoneResponse(
                z.getId(), z.getCode(), z.getLibelle(), z.getMontant(), z.getActif(),
                z.getCreatedAt(), z.getCreatedBy(), z.getUpdatedAt(), z.getUpdatedBy());
    }
}
