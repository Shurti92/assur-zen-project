package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ProductTypeRequest;
import ci.assurzen.dto.ProductTypeResponse;
import ci.assurzen.dto.ProductTypeSummaryResponse;
import ci.assurzen.dto.ProduitResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProductType;
import ci.assurzen.repositories.ProductTypeRepository;
import ci.assurzen.repositories.ProduitRepository;
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

@Service
@RequiredArgsConstructor
public class ProductTypeService {

    private final ProductTypeRepository productTypeRepository;
    private final ProduitRepository produitRepository;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<ProductTypeResponse> search(String q, Boolean actif, Integer page, Integer size) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        List<ProductTypeResponse> items = productTypeRepository.findAll().stream()
                .filter(t -> actif == null || t.isActif() == actif)
                .filter(t -> normalizedQuery == null || matchesQuery(t, normalizedQuery))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(a.getOrdre(), b.getOrdre());
                    return cmp != 0 ? cmp : a.getNom().compareToIgnoreCase(b.getNom());
                })
                .map(this::toResponse)
                .toList();

        return paginate(items, page, size);
    }

    public List<ProductTypeSummaryResponse> listActive() {
        return productTypeRepository.findByActifTrueOrderByOrdreAsc().stream()
                .map(t -> new ProductTypeSummaryResponse(t.getId(), t.getCode(), t.getNom(), t.getIcone(), t.getCouleur()))
                .toList();
    }

    public ProductTypeResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public List<ProduitResponse> produits(Long id) {
        getEntity(id);
        return produitRepository.findByProductTypeId(id).stream()
                .sorted((a, b) -> b.getCreation().compareTo(a.getCreation()))
                .map(this::toProduitResponse)
                .toList();
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "PRODUIT", entityName = "ProductType")
    public ProductTypeResponse create(ProductTypeRequest request) {
        requireWriteAccess();
        checkCodeUnique(request.code(), null);
        checkNomUnique(request.nom(), null);

        ProductType type = ProductType.builder()
                .code(request.code())
                .nom(request.nom())
                .description(request.description())
                .icone(request.icone())
                .couleur(request.couleur())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .actif(request.actif() == null || request.actif())
                .systeme(false)
                .build();

        return toResponse(productTypeRepository.save(type));
    }

    @Transactional
    public ProductTypeResponse update(Long id, ProductTypeRequest request) {
        requireWriteAccess();
        ProductType type = getEntity(id);
        ProductTypeResponse avant = toResponse(type);
        checkCodeUnique(request.code(), id);
        checkNomUnique(request.nom(), id);

        type.setCode(request.code());
        type.setNom(request.nom());
        type.setDescription(request.description());
        type.setIcone(request.icone());
        type.setCouleur(request.couleur());
        if (request.ordre() != null) {
            type.setOrdre(request.ordre());
        }
        if (request.actif() != null) {
            type.setActif(request.actif());
        }

        ProductTypeResponse apres = toResponse(productTypeRepository.save(type));
        functionalAuditRecorder.recordUpdate("PRODUIT", "ProductType", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "PRODUIT", entityName = "ProductType")
    public void delete(Long id) {
        requireWriteAccess();
        ProductType type = getEntity(id);
        if (type.isSysteme()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce type de produit systeme ne peut pas etre supprime: " + type.getCode());
        }
        long nombreProduits = produitRepository.countByProductTypeId(id);
        if (nombreProduits > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer : " + nombreProduits + " produit(s) rattache(s) a ce type");
        }
        type.setDeleted(true);
        productTypeRepository.save(type);
    }

    @Transactional
    @Auditable(action = ActionAudit.ACTIVATE, module = "PRODUIT", entityName = "ProductType")
    public ProductTypeResponse enable(Long id) {
        requireWriteAccess();
        ProductType type = getEntity(id);
        type.setActif(true);
        return toResponse(productTypeRepository.save(type));
    }

    @Transactional
    @Auditable(action = ActionAudit.DEACTIVATE, module = "PRODUIT", entityName = "ProductType")
    public ProductTypeResponse disable(Long id) {
        requireWriteAccess();
        ProductType type = getEntity(id);
        long produitsActifs = produitRepository.countByProductTypeIdAndActiveTrue(id);
        if (produitsActifs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de desactiver : " + produitsActifs + " produit(s) actif(s) rattache(s) a ce type");
        }
        type.setActif(false);
        return toResponse(productTypeRepository.save(type));
    }

    ProductType getEntity(Long id) {
        return productTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de produit introuvable: " + id));
    }

    /** Type de repli utilise par ProduitService quand aucun productTypeId n'est fourni (creation ou migration). */
    ProductType resolveOuCreerTypeParDefaut() {
        return productTypeRepository.findByCodeIgnoreCase("AUTRES")
                .orElseGet(() -> {
                    ProductType autres = ProductType.builder()
                            .code("AUTRES")
                            .nom("Autres")
                            .ordre(999)
                            .actif(true)
                            .systeme(true)
                            .build();
                    return productTypeRepository.save(autres);
                });
    }

    private void checkCodeUnique(String code, Long excludeId) {
        productTypeRepository.findByCodeIgnoreCase(code)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un type de produit existe deja pour ce code: " + code);
                });
    }

    private void checkNomUnique(String nom, Long excludeId) {
        productTypeRepository.findByNomIgnoreCase(nom)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un type de produit existe deja pour ce nom: " + nom);
                });
    }

    private boolean matchesQuery(ProductType type, String normalizedQuery) {
        return contains(type.getCode(), normalizedQuery) || contains(type.getNom(), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void requireWriteAccess() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        String roles = principal.roles() == null ? "" : principal.roles().toUpperCase(Locale.ROOT);
        if (!roles.contains("ADMIN") && !roles.contains("PRODUCT_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs / gestionnaires de produits");
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

    private ProductTypeResponse toResponse(ProductType t) {
        return new ProductTypeResponse(
                t.getId(),
                t.getUuid(),
                t.getCode(),
                t.getNom(),
                t.getDescription(),
                t.getIcone(),
                t.getCouleur(),
                t.getOrdre(),
                t.isActif(),
                t.isSysteme(),
                produitRepository.countByProductTypeId(t.getId()),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private ProduitResponse toProduitResponse(Produit p) {
        return new ProduitResponse(
                p.getId(),
                p.getNomProduit(),
                p.isActive(),
                p.getProductType() == null ? null : p.getProductType().getId(),
                p.getProductType() == null ? null : p.getProductType().getCode(),
                p.getProductType() == null ? null : p.getProductType().getNom(),
                p.getCreation(),
                p.getLastUpdate()
        );
    }
}
