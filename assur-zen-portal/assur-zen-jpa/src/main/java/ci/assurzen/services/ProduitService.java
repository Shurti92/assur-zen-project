package ci.assurzen.services;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ImportRowError;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ProduitRequest;
import ci.assurzen.dto.ProduitResponse;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProductType;
import ci.assurzen.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProduitService {

    private final ProduitRepository repository;
    private final ProductTypeService productTypeService;

    public PageResponse<ProduitResponse> search(String q, Boolean active, String productTypeCode, Integer page, Integer size) {
        return paginate(filter(q, active, productTypeCode), page, size);
    }

    public byte[] exportCsv(String q, Boolean active) {
        List<ProduitResponse> rows = filter(q, active, null);

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Id;NomProduit;Actif\n");
        for (ProduitResponse row : rows) {
            csv.append(CsvUtils.field(row.id()))
                    .append(';').append(CsvUtils.field(row.nomProduit()))
                    .append(';').append(CsvUtils.field(row.active()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV manquant ou vide");
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier CSV");
        }

        List<ImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        for (int i = 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (!StringUtils.hasText(rawLine)) {
                continue;
            }
            totalRows++;
            int lineNumber = i + 1;

            List<String> fields = CsvUtils.parseLine(rawLine);
            String nomProduit = CsvUtils.value(fields, 0);
            Boolean active = parseBoolean(CsvUtils.value(fields, 1));

            if (!StringUtils.hasText(nomProduit)) {
                errors.add(new ImportRowError(lineNumber, nomProduit, "Champ obligatoire manquant (nomProduit)"));
                continue;
            }

            try {
                create(new ProduitRequest(nomProduit, active, null));
                successCount++;
            } catch (ResponseStatusException e) {
                errors.add(new ImportRowError(lineNumber, nomProduit, e.getReason()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(lineNumber, nomProduit, "Erreur inattendue: " + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, successCount, errors.size(), errors);
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "oui".equalsIgnoreCase(value);
    }

    private List<ProduitResponse> filter(String q, Boolean active, String productTypeCode) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;
        String normalizedTypeCode = StringUtils.hasText(productTypeCode) ? productTypeCode.trim().toUpperCase() : null;

        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .filter(produit -> active == null || produit.isActive() == active)
                .filter(produit -> normalizedQuery == null || contains(produit.getNomProduit(), normalizedQuery))
                .filter(produit -> normalizedTypeCode == null
                        || (produit.getProductType() != null && normalizedTypeCode.equals(produit.getProductType().getCode())))
                .map(this::toResponse)
                .toList();
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
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

    public ProduitResponse findById(Long id) {
        return toResponse(getProduit(id));
    }

    public ProduitResponse create(ProduitRequest request) {
        repository.findByNomProduitIgnoreCase(request.nomProduit())
                .ifPresent(produit -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Produit deja existant: " + request.nomProduit());
                });

        Produit produit = Produit.builder()
                .nomProduit(request.nomProduit())
                .active(request.active() == null || request.active())
                .productType(resolveProductType(request.productTypeId()))
                .build();

        return toResponse(repository.save(produit));
    }

    public ProduitResponse update(Long id, ProduitRequest request) {
        Produit produit = getProduit(id);

        repository.findByNomProduitIgnoreCase(request.nomProduit())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Produit deja existant: " + request.nomProduit());
                });

        produit.setNomProduit(request.nomProduit());
        produit.setActive(request.active() == null || request.active());
        if (request.productTypeId() != null) {
            produit.setProductType(productTypeService.getEntity(request.productTypeId()));
        }

        return toResponse(repository.save(produit));
    }

    /** Type explicitement fourni (valide qu'il existe), sinon repli sur le type systeme "AUTRES". */
    private ProductType resolveProductType(Long productTypeId) {
        return productTypeId != null ? productTypeService.getEntity(productTypeId) : productTypeService.resolveOuCreerTypeParDefaut();
    }

    public void delete(Long id) {
        repository.delete(getProduit(id));
    }

    private Produit getProduit(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + id));
    }

    private ProduitResponse toResponse(Produit produit) {
        return new ProduitResponse(
                produit.getId(),
                produit.getNomProduit(),
                produit.isActive(),
                produit.getProductType() == null ? null : produit.getProductType().getId(),
                produit.getProductType() == null ? null : produit.getProductType().getCode(),
                produit.getProductType() == null ? null : produit.getProductType().getNom(),
                produit.getCreation(),
                produit.getLastUpdate()
        );
    }
}
