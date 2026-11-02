package ci.assurzen.services;

import ci.assurzen.dto.PartenaireProduitCommissionRequest;
import ci.assurzen.dto.PartenaireProduitCommissionResponse;
import ci.assurzen.entities.BeneficiaireCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireProduitCommission;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.PartenaireProduitCommissionRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

import static ci.assurzen.utils.SafeAccess.safe;

/**
 * CRUD et resolution des commissions configurees par produit + beneficiaire (partenaire precis,
 * type de partenaire, ou commercial) - decision utilisateur du 2026-09-25. Resolution d'une
 * commission partenaire : le partenaire precis d'abord, puis son type. Deux configurations actives
 * d'une meme cle ne peuvent pas se chevaucher dans le temps (409).
 */
@Service
@RequiredArgsConstructor
public class PartenaireProduitCommissionService {

    private final PartenaireProduitCommissionRepository repository;
    private final ProduitRepository produitRepository;
    private final PartenaireRepository partenaireRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PartenaireProduitCommissionResponse> findAll(Long produitId, BeneficiaireCommission beneficiaire,
                                                             Long partenaireId, Long commercialId) {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation")).stream()
                .filter(c -> produitId == null || produitId.equals(idProduit(c)))
                .filter(c -> beneficiaire == null || beneficiaire == c.getBeneficiaire())
                .filter(c -> partenaireId == null || partenaireId.equals(idPartenaire(c)))
                .filter(c -> commercialId == null || commercialId.equals(idCommercial(c)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartenaireProduitCommissionResponse findById(Long id) {
        return toResponse(getConfiguration(id));
    }

    /** Commission partenaire applicable a cette date : partenaire precis, sinon type du partenaire. */
    @Transactional(readOnly = true)
    public Optional<PartenaireProduitCommission> resoudrePourPartenaire(Long produitId, Partenaire partenaire, LocalDate date) {
        if (produitId == null || partenaire == null) {
            return Optional.empty();
        }
        List<PartenaireProduitCommission> applicables = repository.findByProduitId(produitId).stream()
                .filter(c -> c.getBeneficiaire() == BeneficiaireCommission.PARTENAIRE && c.estApplicableLe(date))
                .toList();
        Optional<PartenaireProduitCommission> precise = applicables.stream()
                .filter(c -> partenaire.getId() != null && partenaire.getId().equals(idPartenaire(c)))
                .findFirst();
        if (precise.isPresent()) {
            return precise;
        }
        return applicables.stream()
                .filter(c -> c.getPartenaire() == null && c.getTypePartenaire() != null && c.getTypePartenaire() == partenaire.getType())
                .findFirst();
    }

    /** Commission configuree pour ce commercial precis a cette date (aucun repli ici : le repli sur la
     * commission commerciale par defaut du produit reste dans ContractsService). */
    @Transactional(readOnly = true)
    public Optional<PartenaireProduitCommission> resoudrePourCommercial(Long produitId, Long commercialId, LocalDate date) {
        if (produitId == null || commercialId == null) {
            return Optional.empty();
        }
        return repository.findByProduitId(produitId).stream()
                .filter(c -> c.getBeneficiaire() == BeneficiaireCommission.COMMERCIAL && c.estApplicableLe(date))
                .filter(c -> commercialId.equals(idCommercial(c)))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public PartenaireProduitCommissionResponse findForPartenaire(Long produitId, Long partenaireId, LocalDate date) {
        Partenaire partenaire = partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
        return resoudrePourPartenaire(produitId, partenaire, date == null ? LocalDate.now() : date)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune commission partenaire configuree pour le produit " + produitId + " et le partenaire " + partenaireId));
    }

    @Transactional(readOnly = true)
    public PartenaireProduitCommissionResponse findForCommercial(Long produitId, Long commercialId, LocalDate date) {
        return resoudrePourCommercial(produitId, commercialId, date == null ? LocalDate.now() : date)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune commission configuree pour le produit " + produitId + " et le commercial " + commercialId));
    }

    @Transactional
    public PartenaireProduitCommissionResponse create(PartenaireProduitCommissionRequest request) {
        PartenaireProduitCommission configuration = new PartenaireProduitCommission();
        appliquer(configuration, request);
        verifierAbsenceDeChevauchement(configuration);
        return toResponse(repository.save(configuration));
    }

    @Transactional
    public PartenaireProduitCommissionResponse update(Long id, PartenaireProduitCommissionRequest request) {
        PartenaireProduitCommission configuration = getConfiguration(id);
        appliquer(configuration, request);
        verifierAbsenceDeChevauchement(configuration);
        return toResponse(repository.save(configuration));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getConfiguration(id));
    }

    private void appliquer(PartenaireProduitCommission configuration, PartenaireProduitCommissionRequest request) {
        if (request.dateDebut() != null && request.dateFin() != null && request.dateFin().isBefore(request.dateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFin doit etre posterieure ou egale a dateDebut");
        }
        Produit produit = produitRepository.findById(request.produitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + request.produitId()));

        Partenaire partenaire = null;
        User commercial = null;
        if (request.beneficiaire() == BeneficiaireCommission.PARTENAIRE) {
            if (request.commercialId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commercialId doit etre absent pour une commission PARTENAIRE");
            }
            if ((request.partenaireId() == null) == (request.typePartenaire() == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une commission PARTENAIRE vise soit un partenaire precis (partenaireId), soit un type de partenaire (typePartenaire), pas les deux");
            }
            if (request.partenaireId() != null) {
                partenaire = partenaireRepository.findById(request.partenaireId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + request.partenaireId()));
            }
        } else {
            if (request.commercialId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commercialId est obligatoire pour une commission COMMERCIAL");
            }
            if (request.partenaireId() != null || request.typePartenaire() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "partenaireId et typePartenaire doivent etre absents pour une commission COMMERCIAL");
            }
            commercial = userRepository.findById(request.commercialId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commercial introuvable: " + request.commercialId()));
        }

        configuration.setProduit(produit);
        configuration.setBeneficiaire(request.beneficiaire());
        configuration.setPartenaire(partenaire);
        configuration.setTypePartenaire(partenaire == null ? request.typePartenaire() : null);
        configuration.setCommercial(commercial);
        configuration.setMontant(request.montant());
        configuration.setActive(request.active() == null || request.active());
        configuration.setDateDebut(request.dateDebut());
        configuration.setDateFin(request.dateFin());
    }

    private void verifierAbsenceDeChevauchement(PartenaireProduitCommission cible) {
        if (!cible.isActive()) {
            return;
        }
        repository.findByProduitId(cible.getProduit().getId()).stream()
                .filter(c -> c.isActive() && !Objects.equals(c.getId(), cible.getId()))
                .filter(c -> c.getBeneficiaire() == cible.getBeneficiaire()
                        && Objects.equals(idPartenaire(c), idPartenaire(cible))
                        && c.getTypePartenaire() == cible.getTypePartenaire()
                        && Objects.equals(idCommercial(c), idCommercial(cible)))
                .filter(c -> periodesSeChevauchent(c, cible))
                .findFirst()
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Une commission active existe deja pour ce produit et ce beneficiaire sur une periode qui chevauche (id " + c.getId() + ")");
                });
    }

    private static boolean periodesSeChevauchent(PartenaireProduitCommission a, PartenaireProduitCommission b) {
        boolean aFiniAvantB = a.getDateFin() != null && b.getDateDebut() != null && a.getDateFin().isBefore(b.getDateDebut());
        boolean bFiniAvantA = b.getDateFin() != null && a.getDateDebut() != null && b.getDateFin().isBefore(a.getDateDebut());
        return !aFiniAvantB && !bFiniAvantA;
    }

    private PartenaireProduitCommission getConfiguration(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission configuree introuvable: " + id));
    }

    private static Long idProduit(PartenaireProduitCommission c) {
        return c.getProduit() == null ? null : c.getProduit().getId();
    }

    private static Long idPartenaire(PartenaireProduitCommission c) {
        return c.getPartenaire() == null ? null : c.getPartenaire().getId();
    }

    private static Long idCommercial(PartenaireProduitCommission c) {
        return c.getCommercial() == null ? null : c.getCommercial().getId();
    }

    private PartenaireProduitCommissionResponse toResponse(PartenaireProduitCommission c) {
        return new PartenaireProduitCommissionResponse(
                c.getId(),
                idProduit(c),
                c.getProduit() == null ? null : safe(c.getProduit()::getNomProduit),
                c.getBeneficiaire(),
                idPartenaire(c),
                // "nom prenoms", comme les autres endpoints partenaires.
                c.getPartenaire() == null ? null : safe(() -> c.getPartenaire().getNom() + " " + c.getPartenaire().getPrenoms()),
                c.getTypePartenaire(),
                idCommercial(c),
                c.getCommercial() == null ? null : safe(c.getCommercial()::getUsername),
                c.getMontant(),
                c.isActive(),
                c.getDateDebut(),
                c.getDateFin(),
                c.getCreation(),
                c.getLastUpdate()
        );
    }
}
