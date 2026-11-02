package ci.assurzen.services;

import ci.assurzen.dto.CodeParrainageRequest;
import ci.assurzen.dto.CodeParrainageResponse;
import ci.assurzen.entities.CodeParrainage;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.ProgrammeParrainage;
import ci.assurzen.entities.StatutProgrammeParrainage;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CodeParrainageRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProgrammeParrainageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Genere et valide les codes/liens de parrainage reutilisables. Un meme (parrain, programme) reutilise
 * son code actif existant plutot que d'en creer un nouveau a chaque appel - le lien reste stable une
 * fois partage par le client.
 */
@Service
@RequiredArgsConstructor
public class CodeParrainageService {

    private final CodeParrainageRepository codeParrainageRepository;
    private final InsuredRepository insuredRepository;
    private final ProgrammeParrainageRepository programmeParrainageRepository;
    private final CodeGeneratorService codeGeneratorService;

    @Value("${app.parrainage.lien-base-url:https://assurzen.ci/ref/}")
    private String lienBaseUrl;

    @Transactional
    public CodeParrainageResponse obtenirOuCreer(CodeParrainageRequest request) {
        Insured parrain = resolveInsured(request.parrainInsuredId());
        ProgrammeParrainage programme = resolveProgramme(request.programmeId());

        CodeParrainage code = codeParrainageRepository.findByParrainIdAndProgrammeId(parrain.getId(), programme.getId()).stream()
                .filter(CodeParrainage::isActif)
                .filter(c -> c.getDateExpiration() == null || c.getDateExpiration().isAfter(Instant.now()))
                .findFirst()
                .orElseGet(() -> codeParrainageRepository.save(CodeParrainage.builder()
                        .code(codeGeneratorService.generate(CodePrefix.CODE_PARRAINAGE))
                        .parrain(parrain)
                        .programme(programme)
                        .nombreUtilisations(0)
                        .maxUtilisations(request.maxUtilisations())
                        .dateExpiration(request.dateExpiration())
                        .actif(true)
                        .build()));

        return toResponse(code);
    }

    public CodeParrainageResponse getByCode(String code) {
        return toResponse(getEntity(code));
    }

    /** Verifie qu'un code est utilisable (actif, non expire, quota non atteint, programme actif). Leve une exception sinon. */
    public CodeParrainageResponse valider(String code) {
        CodeParrainage codeParrainage = getEntity(code);
        verifierUtilisable(codeParrainage);
        return toResponse(codeParrainage);
    }

    void verifierUtilisable(CodeParrainage code) {
        if (!code.isActif()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce code de parrainage est desactive");
        }
        if (code.getDateExpiration() != null && code.getDateExpiration().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce code de parrainage a expire");
        }
        if (code.getProgramme().getStatut() != StatutProgrammeParrainage.ACTIF) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le programme de parrainage n'est plus actif");
        }
        if (code.getMaxUtilisations() != null && code.getNombreUtilisations() >= code.getMaxUtilisations()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce code de parrainage a atteint son nombre maximum d'utilisations");
        }
    }

    @Transactional
    public void enregistrerUtilisation(CodeParrainage code) {
        code.setNombreUtilisations(code.getNombreUtilisations() + 1);
        codeParrainageRepository.save(code);
    }

    @Transactional
    public CodeParrainageResponse disable(Long id) {
        CodeParrainage code = codeParrainageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code de parrainage introuvable: " + id));
        code.setActif(false);
        return toResponse(codeParrainageRepository.save(code));
    }

    public List<CodeParrainageResponse> listByParrain(Long parrainId) {
        return codeParrainageRepository.findByParrainId(parrainId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    CodeParrainage getEntity(String code) {
        return codeParrainageRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code de parrainage introuvable: " + code));
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId));
    }

    private ProgrammeParrainage resolveProgramme(Long programmeId) {
        return programmeParrainageRepository.findById(programmeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme de parrainage introuvable: " + programmeId));
    }

    private CodeParrainageResponse toResponse(CodeParrainage c) {
        return new CodeParrainageResponse(
                c.getId(),
                c.getCode(),
                lienBaseUrl + c.getCode(),
                c.getParrain() == null ? null : c.getParrain().getId(),
                insuredName(c.getParrain()),
                c.getProgramme() == null ? null : c.getProgramme().getId(),
                c.getProgramme() == null ? null : c.getProgramme().getNom(),
                c.getNombreUtilisations(),
                c.getMaxUtilisations(),
                c.getDateExpiration(),
                c.isActif(),
                c.getCreatedAt()
        );
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }
}
