package ci.assurzen.services;

import ci.assurzen.dto.RegleRecompenseParrainageRequest;
import ci.assurzen.dto.RegleRecompenseParrainageResponse;
import ci.assurzen.entities.ProgrammeParrainage;
import ci.assurzen.entities.RegleRecompenseParrainage;
import ci.assurzen.repositories.ProgrammeParrainageRepository;
import ci.assurzen.repositories.RegleRecompenseParrainageRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegleRecompenseParrainageService {

    private final RegleRecompenseParrainageRepository regleRecompenseParrainageRepository;
    private final ProgrammeParrainageRepository programmeParrainageRepository;
    private final AuthService authService;

    public List<RegleRecompenseParrainageResponse> list(Long programmeId) {
        List<RegleRecompenseParrainage> regles = programmeId != null
                ? regleRecompenseParrainageRepository.findByProgrammeIdOrderByPrioriteAsc(programmeId)
                : regleRecompenseParrainageRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return regles.stream().map(this::toResponse).toList();
    }

    public RegleRecompenseParrainageResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public RegleRecompenseParrainageResponse create(RegleRecompenseParrainageRequest request) {
        requireCanManage();
        ProgrammeParrainage programme = resolveProgramme(request.programmeId());

        RegleRecompenseParrainage regle = RegleRecompenseParrainage.builder()
                .programme(programme)
                .nom(request.nom())
                .description(request.description())
                .typeRecompense(request.typeRecompense())
                .beneficiaire(request.beneficiaire())
                .montantFixe(request.montantFixe())
                .pourcentage(request.pourcentage())
                .points(request.points())
                .primeMinimum(request.primeMinimum())
                .plafondRecompense(request.plafondRecompense())
                .priorite(request.priorite() != null ? request.priorite() : 0)
                .actif(request.actif() == null || request.actif())
                .build();

        return toResponse(regleRecompenseParrainageRepository.save(regle));
    }

    @Transactional
    public RegleRecompenseParrainageResponse update(Long id, RegleRecompenseParrainageRequest request) {
        requireCanManage();
        RegleRecompenseParrainage regle = getEntity(id);
        ProgrammeParrainage programme = resolveProgramme(request.programmeId());

        regle.setProgramme(programme);
        regle.setNom(request.nom());
        regle.setDescription(request.description());
        regle.setTypeRecompense(request.typeRecompense());
        regle.setBeneficiaire(request.beneficiaire());
        regle.setMontantFixe(request.montantFixe());
        regle.setPourcentage(request.pourcentage());
        regle.setPoints(request.points());
        regle.setPrimeMinimum(request.primeMinimum());
        regle.setPlafondRecompense(request.plafondRecompense());
        if (request.priorite() != null) {
            regle.setPriorite(request.priorite());
        }
        if (request.actif() != null) {
            regle.setActif(request.actif());
        }

        return toResponse(regleRecompenseParrainageRepository.save(regle));
    }

    @Transactional
    public void delete(Long id) {
        requireCanManage();
        RegleRecompenseParrainage regle = getEntity(id);
        regle.setActif(false);
        regleRecompenseParrainageRepository.save(regle);
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private ProgrammeParrainage resolveProgramme(Long programmeId) {
        return programmeParrainageRepository.findById(programmeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme de parrainage introuvable: " + programmeId));
    }

    private RegleRecompenseParrainage getEntity(Long id) {
        return regleRecompenseParrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regle de recompense introuvable: " + id));
    }

    private RegleRecompenseParrainageResponse toResponse(RegleRecompenseParrainage r) {
        return new RegleRecompenseParrainageResponse(
                r.getId(),
                r.getProgramme() == null ? null : r.getProgramme().getId(),
                r.getProgramme() == null ? null : r.getProgramme().getNom(),
                r.getNom(),
                r.getDescription(),
                r.getTypeRecompense(),
                r.getBeneficiaire(),
                r.getMontantFixe(),
                r.getPourcentage(),
                r.getPoints(),
                r.getPrimeMinimum(),
                r.getPlafondRecompense(),
                r.getPriorite(),
                r.isActif()
        );
    }
}
