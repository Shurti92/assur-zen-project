package ci.assurzen.services;

import ci.assurzen.dto.RecompenseParrainageResponse;
import ci.assurzen.entities.Parrainage;
import ci.assurzen.entities.RecompenseParrainage;
import ci.assurzen.entities.StatutParrainage;
import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.repositories.ParrainageRepository;
import ci.assurzen.repositories.RecompenseParrainageRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecompenseParrainageService {

    private static final List<StatutRecompenseParrainage> STATUTS_FINAUX = List.of(
            StatutRecompenseParrainage.UTILISEE, StatutRecompenseParrainage.EXPIREE,
            StatutRecompenseParrainage.ANNULEE, StatutRecompenseParrainage.REVERSEE);

    private final RecompenseParrainageRepository recompenseParrainageRepository;
    private final ParrainageRepository parrainageRepository;
    private final AuthService authService;

    public List<RecompenseParrainageResponse> search(StatutRecompenseParrainage statut, Long parrainageId, Long beneficiaireId) {
        return recompenseParrainageRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(r -> statut == null || r.getStatut() == statut)
                .filter(r -> parrainageId == null || (r.getParrainage() != null && parrainageId.equals(r.getParrainage().getId())))
                .filter(r -> beneficiaireId == null || (r.getBeneficiaire() != null && beneficiaireId.equals(r.getBeneficiaire().getId())))
                .map(this::toResponse)
                .toList();
    }

    public RecompenseParrainageResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public RecompenseParrainageResponse approuver(Long id) {
        requireCanManage();
        RecompenseParrainage recompense = getEntity(id);
        if (STATUTS_FINAUX.contains(recompense.getStatut()) || recompense.getStatut() == StatutRecompenseParrainage.APPROUVEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recompense non approuvable depuis son statut actuel: " + recompense.getStatut());
        }
        recompense.setStatut(StatutRecompenseParrainage.APPROUVEE);
        recompense.setDateApprobation(Instant.now());
        return toResponse(recompenseParrainageRepository.save(recompense));
    }

    @Transactional
    public RecompenseParrainageResponse attribuer(Long id) {
        requireCanManage();
        RecompenseParrainage recompense = getEntity(id);
        if (recompense.getStatut() != StatutRecompenseParrainage.APPROUVEE && recompense.getStatut() != StatutRecompenseParrainage.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recompense non attribuable depuis son statut actuel: " + recompense.getStatut());
        }
        recompense.setStatut(StatutRecompenseParrainage.ATTRIBUEE);
        recompense.setDateAttribution(Instant.now());
        recompense = recompenseParrainageRepository.save(recompense);

        rafraichirStatutParrainage(recompense.getParrainage());

        return toResponse(recompense);
    }

    @Transactional
    public RecompenseParrainageResponse annuler(Long id, String motif) {
        requireCanManage();
        RecompenseParrainage recompense = getEntity(id);
        if (STATUTS_FINAUX.contains(recompense.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recompense deja dans un statut final: " + recompense.getStatut());
        }
        recompense.setStatut(StatutRecompenseParrainage.ANNULEE);
        recompense.setDateAnnulation(Instant.now());
        recompense.setMotifAnnulation(StringUtils.hasText(motif) ? motif : "Annulee manuellement");
        return toResponse(recompenseParrainageRepository.save(recompense));
    }

    @Transactional
    public RecompenseParrainageResponse reverser(Long id, String motif) {
        requireCanManage();
        RecompenseParrainage recompense = getEntity(id);
        if (recompense.getStatut() != StatutRecompenseParrainage.ATTRIBUEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une recompense attribuee peut etre reversee (statut actuel: " + recompense.getStatut() + ")");
        }
        recompense.setStatut(StatutRecompenseParrainage.REVERSEE);
        recompense.setDateAnnulation(Instant.now());
        recompense.setMotifAnnulation(StringUtils.hasText(motif) ? motif : "Reversee manuellement");
        return toResponse(recompenseParrainageRepository.save(recompense));
    }

    private void rafraichirStatutParrainage(Parrainage parrainage) {
        if (parrainage == null) {
            return;
        }
        List<RecompenseParrainage> toutes = recompenseParrainageRepository.findByParrainageId(parrainage.getId());
        boolean auMoinsUneAttribuee = toutes.stream().anyMatch(r -> r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE);
        boolean toutesResolues = toutes.stream().allMatch(r -> STATUTS_FINAUX.contains(r.getStatut()) || r.getStatut() == StatutRecompenseParrainage.ATTRIBUEE);
        if (auMoinsUneAttribuee && toutesResolues && parrainage.getStatut() != StatutParrainage.RECOMPENSE_ATTRIBUEE) {
            parrainage.setStatut(StatutParrainage.RECOMPENSE_ATTRIBUEE);
            parrainage.setDateCompletion(Instant.now());
            parrainageRepository.save(parrainage);
        }
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private RecompenseParrainage getEntity(Long id) {
        return recompenseParrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recompense introuvable: " + id));
    }

    private RecompenseParrainageResponse toResponse(RecompenseParrainage r) {
        return new RecompenseParrainageResponse(
                r.getId(),
                r.getParrainage() == null ? null : r.getParrainage().getId(),
                r.getParrainage() == null ? null : r.getParrainage().getReference(),
                r.getRegle() == null ? null : r.getRegle().getId(),
                r.getRegle() == null ? null : r.getRegle().getNom(),
                r.getBeneficiaire() == null ? null : r.getBeneficiaire().getId(),
                insuredName(r.getBeneficiaire()),
                r.getTypeRecompense(),
                r.getStatut(),
                r.getMontant(),
                r.getDevise(),
                r.getPourcentage(),
                r.getPoints(),
                r.getDescription(),
                r.getDateEligibilite(),
                r.getDateApprobation(),
                r.getDateAttribution(),
                r.getDateAnnulation(),
                r.getDateExpiration(),
                r.getMotifAnnulation(),
                r.getCreatedAt()
        );
    }

    private String insuredName(ci.assurzen.entities.Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }
}
