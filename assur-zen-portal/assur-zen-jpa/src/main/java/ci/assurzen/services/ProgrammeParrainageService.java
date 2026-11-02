package ci.assurzen.services;

import ci.assurzen.dto.ProgrammeParrainageRequest;
import ci.assurzen.dto.ProgrammeParrainageResponse;
import ci.assurzen.entities.ProgrammeParrainage;
import ci.assurzen.entities.StatutProgrammeParrainage;
import ci.assurzen.repositories.ProgrammeParrainageRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgrammeParrainageService {

    private final ProgrammeParrainageRepository programmeParrainageRepository;
    private final AuthService authService;

    public List<ProgrammeParrainageResponse> list() {
        return programmeParrainageRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProgrammeParrainageResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public ProgrammeParrainageResponse create(ProgrammeParrainageRequest request) {
        requireCanManage();
        programmeParrainageRepository.findByCode(request.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un programme existe deja pour ce code: " + request.code());
        });

        ProgrammeParrainage programme = ProgrammeParrainage.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .statut(StatutProgrammeParrainage.BROUILLON)
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .maxParrainagesParClient(request.maxParrainagesParClient())
                .maxRecompensesParClient(request.maxRecompensesParClient())
                .delaiCarenceJours(request.delaiCarenceJours())
                .dureeValiditeJours(request.dureeValiditeJours())
                .primeMinimum(request.primeMinimum())
                .produitsEligibles(request.produitsEligibles() != null ? new HashSet<>(request.produitsEligibles()) : new HashSet<>())
                .build();

        return toResponse(programmeParrainageRepository.save(programme));
    }

    @Transactional
    public ProgrammeParrainageResponse update(Long id, ProgrammeParrainageRequest request) {
        requireCanManage();
        ProgrammeParrainage programme = getEntity(id);

        programmeParrainageRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un programme existe deja pour ce code: " + request.code());
                });

        programme.setCode(request.code());
        programme.setNom(request.nom());
        programme.setDescription(request.description());
        programme.setDateDebut(request.dateDebut());
        programme.setDateFin(request.dateFin());
        programme.setMaxParrainagesParClient(request.maxParrainagesParClient());
        programme.setMaxRecompensesParClient(request.maxRecompensesParClient());
        programme.setDelaiCarenceJours(request.delaiCarenceJours());
        programme.setDureeValiditeJours(request.dureeValiditeJours());
        programme.setPrimeMinimum(request.primeMinimum());
        programme.setProduitsEligibles(request.produitsEligibles() != null ? new HashSet<>(request.produitsEligibles()) : new HashSet<>());

        return toResponse(programmeParrainageRepository.save(programme));
    }

    @Transactional
    public ProgrammeParrainageResponse activer(Long id) {
        requireCanManage();
        ProgrammeParrainage programme = getEntity(id);
        programme.setStatut(StatutProgrammeParrainage.ACTIF);
        return toResponse(programmeParrainageRepository.save(programme));
    }

    @Transactional
    public ProgrammeParrainageResponse suspendre(Long id) {
        requireCanManage();
        ProgrammeParrainage programme = getEntity(id);
        programme.setStatut(StatutProgrammeParrainage.SUSPENDU);
        return toResponse(programmeParrainageRepository.save(programme));
    }

    @Transactional
    public ProgrammeParrainageResponse cloturer(Long id) {
        requireCanManage();
        ProgrammeParrainage programme = getEntity(id);
        programme.setStatut(StatutProgrammeParrainage.CLOTURE);
        return toResponse(programmeParrainageRepository.save(programme));
    }

    ProgrammeParrainage getEntity(Long id) {
        return programmeParrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme de parrainage introuvable: " + id));
    }

    private void requireCanManage() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (!FidelisationRoles.isAdmin(principal) && !FidelisationRoles.isManager(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs et managers");
        }
    }

    private ProgrammeParrainageResponse toResponse(ProgrammeParrainage p) {
        return new ProgrammeParrainageResponse(
                p.getId(),
                p.getCode(),
                p.getNom(),
                p.getDescription(),
                p.getStatut(),
                p.getDateDebut(),
                p.getDateFin(),
                p.getMaxParrainagesParClient(),
                p.getMaxRecompensesParClient(),
                p.getDelaiCarenceJours(),
                p.getDureeValiditeJours(),
                p.getPrimeMinimum(),
                p.getProduitsEligibles(),
                p.getCreatedAt(),
                p.getLastUpdate()
        );
    }
}
