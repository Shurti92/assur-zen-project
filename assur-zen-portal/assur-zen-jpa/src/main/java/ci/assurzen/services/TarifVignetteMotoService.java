package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.TarifVignetteMotoRequest;
import ci.assurzen.dto.TarifVignetteMotoResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.TarifVignetteMoto;
import ci.assurzen.repositories.TarifVignetteMotoRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Grille tarifaire des vignettes moto (§8/§9/§20 du contrat). Reserve aux administrateurs. La
 * resolution d'un tarif applicable (resoudre()) est utilisee par DemandeVignetteMotoService pour la
 * simulation ET la creation de demande - modifier un tarif ici n'affecte jamais une demande deja
 * creee (les montants y sont figes, cf. DemandeVignetteMoto).
 */
@Service
@RequiredArgsConstructor
public class TarifVignetteMotoService {

    private final TarifVignetteMotoRepository repository;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public List<TarifVignetteMotoResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public TarifVignetteMotoResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    /** Resolution du tarif actif et valide a une date de reference donnee - utilisee par
     * DemandeVignetteMotoService, jamais relue pour recalculer une demande existante. Plus d'un
     * resultat = configuration incoherente (chevauchement qui n'aurait pas du passer le garde-fou de
     * create()/update()) : on refuse plutot que de deviner lequel appliquer. */
    public TarifVignetteMoto resoudre(CategorieAgeMoto categorieAge, CategorieCylindree categorieCylindree, LocalDate dateReference) {
        List<TarifVignetteMoto> applicables = repository.findTarifsApplicables(categorieAge, categorieCylindree, dateReference);
        if (applicables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucun tarif vignette moto actif et valide pour cette categorie (" + categorieAge + " / " + categorieCylindree + ")");
        }
        if (applicables.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Plusieurs tarifs actifs se chevauchent pour cette categorie (" + categorieAge + " / " + categorieCylindree + "), configuration a corriger");
        }
        return applicables.get(0);
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "VIGNETTES_MOTO", entityName = "TarifVignetteMoto")
    public TarifVignetteMotoResponse create(TarifVignetteMotoRequest request) {
        requireAdmin();
        validerPeriode(request.dateDebutValidite(), request.dateFinValidite());
        validerAbsenceChevauchement(request.categorieAge(), request.categorieCylindree(),
                request.dateDebutValidite(), request.dateFinValidite(), null);

        TarifVignetteMoto tarif = TarifVignetteMoto.builder()
                .categorieAge(request.categorieAge())
                .categorieCylindree(request.categorieCylindree())
                .montantVignette(request.montantVignette())
                .fraisOperation(request.fraisOperation())
                .dateDebutValidite(request.dateDebutValidite())
                .dateFinValidite(request.dateFinValidite())
                .build();
        return toResponse(repository.save(tarif));
    }

    @Transactional
    public TarifVignetteMotoResponse update(Long id, TarifVignetteMotoRequest request) {
        requireAdmin();
        TarifVignetteMoto tarif = getEntity(id);
        TarifVignetteMotoResponse avant = toResponse(tarif);

        validerPeriode(request.dateDebutValidite(), request.dateFinValidite());
        validerAbsenceChevauchement(request.categorieAge(), request.categorieCylindree(),
                request.dateDebutValidite(), request.dateFinValidite(), id);

        tarif.setCategorieAge(request.categorieAge());
        tarif.setCategorieCylindree(request.categorieCylindree());
        tarif.setMontantVignette(request.montantVignette());
        tarif.setFraisOperation(request.fraisOperation());
        tarif.setDateDebutValidite(request.dateDebutValidite());
        tarif.setDateFinValidite(request.dateFinValidite());
        tarif = repository.save(tarif);

        functionalAuditRecorder.recordUpdate("VIGNETTES_MOTO", "TarifVignetteMoto", String.valueOf(id), avant, toResponse(tarif), null);
        return toResponse(tarif);
    }

    @Transactional
    @Auditable(action = ActionAudit.DEACTIVATE, module = "VIGNETTES_MOTO", entityName = "TarifVignetteMoto")
    public TarifVignetteMotoResponse desactiver(Long id) {
        requireAdmin();
        TarifVignetteMoto tarif = getEntity(id);
        tarif.setActif(false);
        return toResponse(repository.save(tarif));
    }

    private void validerPeriode(LocalDate debut, LocalDate fin) {
        if (fin != null && fin.isBefore(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin de validite ne peut pas preceder la date de debut");
        }
    }

    /** Refuse la creation/modification d'un tarif actif dont la periode de validite chevaucherait un
     * autre tarif deja actif pour le meme couple (age, cylindree) - evite l'ambiguite silencieuse
     * detectee dans resoudre(). Comparaison faite en Java (pas en JPQL) pour rester independante du
     * dialecte SQL sur l'arithmetique de dates. */
    private void validerAbsenceChevauchement(CategorieAgeMoto categorieAge, CategorieCylindree categorieCylindree,
                                              LocalDate debut, LocalDate fin, Long excludingId) {
        boolean chevauche = repository.findByCategorieAgeAndCategorieCylindreeAndActifTrue(categorieAge, categorieCylindree).stream()
                .filter(t -> excludingId == null || !t.getId().equals(excludingId))
                .anyMatch(t -> seChevauchent(debut, fin, t.getDateDebutValidite(), t.getDateFinValidite()));
        if (chevauche) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette periode de validite chevauche un tarif deja actif pour cette categorie (" + categorieAge + " / " + categorieCylindree + ")");
        }
    }

    private boolean seChevauchent(LocalDate debut1, LocalDate fin1, LocalDate debut2, LocalDate fin2) {
        boolean debut1AvantOuEgalFin2 = fin2 == null || !debut1.isAfter(fin2);
        boolean debut2AvantOuEgalFin1 = fin1 == null || !debut2.isAfter(fin1);
        return debut1AvantOuEgalFin2 && debut2AvantOuEgalFin1;
    }

    private TarifVignetteMoto getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarif vignette moto introuvable: " + id));
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private TarifVignetteMotoResponse toResponse(TarifVignetteMoto t) {
        return new TarifVignetteMotoResponse(
                t.getId(), t.getCategorieAge(), t.getCategorieCylindree(), t.getMontantVignette(), t.getFraisOperation(),
                t.getDateDebutValidite(), t.getDateFinValidite(), t.getActif(),
                t.getCreatedAt(), t.getCreatedBy(), t.getUpdatedAt(), t.getUpdatedBy());
    }
}
