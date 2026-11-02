package ci.assurzen.services;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ImportRowError;
import ci.assurzen.dto.InsuredRequest;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.TypeClient;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Garde-fou IDOR ajoute le 2026-09-12 (audit de securite) : findById()/update()/delete()/search()
 * n'avaient auparavant AUCUNE verification d'acces - n'importe quel compte authentifie pouvait
 * consulter/modifier/supprimer n'importe quel assure par id. Meme principe de perimetre que
 * ContractsService.getContractScoped() (un non-admin n'a droit qu'aux assures rattaches a au moins un
 * de ses propres contrats, via Contract.salesRep) - pas de nouvelle notion de proprietaire inventee,
 * reutilise la seule relation d'appartenance qui existe deja dans le modele.
 */
@Service
@RequiredArgsConstructor
public class InsuredService {

    private final InsuredRepository repository;
    private final ContractRepository contractRepository;
    private final AuthService authService;

    public PageResponse<InsuredResponse> search(String q, TypeClient typeClient, Integer page, Integer size) {
        return paginate(filter(q, typeClient), page, size);
    }

    public byte[] exportCsv(String q, TypeClient typeClient) {
        List<InsuredResponse> rows = filter(q, typeClient);

        StringBuilder csv = new StringBuilder("﻿");
        // ClientTelephone2 ajoute EN FIN de ligne (pas apres ClientTelephone) pour rester compatible
        // avec les fichiers CSV deja exportes/geres par les utilisateurs avant l'ajout de ce champ -
        // importCsv() lit les colonnes existantes aux memes index qu'avant, la nouvelle est optionnelle
        // en fin de ligne.
        csv.append("Id;Nom;Prenoms;ClientTelephone;ClientVille;ClientEmail;TypeClient;ClientTelephone2\n");
        for (InsuredResponse row : rows) {
            csv.append(CsvUtils.field(row.id()))
                    .append(';').append(CsvUtils.field(row.nom()))
                    .append(';').append(CsvUtils.field(row.prenoms()))
                    .append(';').append(CsvUtils.field(row.clientPhone()))
                    .append(';').append(CsvUtils.field(row.clientCity()))
                    .append(';').append(CsvUtils.field(row.clientEmail()))
                    .append(';').append(CsvUtils.field(row.typeClient()))
                    .append(';').append(CsvUtils.field(row.clientPhone2()))
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
            String nom = CsvUtils.value(fields, 0);
            String prenoms = CsvUtils.value(fields, 1);
            String clientPhone = CsvUtils.value(fields, 2);
            String clientCity = CsvUtils.value(fields, 3);
            String clientEmail = CsvUtils.value(fields, 4);
            TypeClient typeClient = parseTypeClient(CsvUtils.value(fields, 5));
            // Colonne optionnelle en fin de ligne (absente des CSV exportes/geres avant son ajout) ;
            // CsvUtils.value renvoie null si l'index depasse le nombre de colonnes de la ligne.
            String clientPhone2 = CsvUtils.value(fields, 6);

            if (!StringUtils.hasText(nom) || !StringUtils.hasText(prenoms)) {
                errors.add(new ImportRowError(lineNumber, nom, "Champs obligatoires manquants (nom, prenoms)"));
                continue;
            }

            try {
                create(new InsuredRequest(nom, prenoms, clientPhone, clientCity, clientEmail, typeClient, null, clientPhone2));
                successCount++;
            } catch (ResponseStatusException e) {
                errors.add(new ImportRowError(lineNumber, nom, e.getReason()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(lineNumber, nom, "Erreur inattendue: " + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, successCount, errors.size(), errors);
    }

    private List<InsuredResponse> filter(String q, TypeClient typeClient) {
        String normalizedQuery = StringUtils.hasText(q) ? q.trim().toLowerCase() : null;
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = isAdmin(principal);
        // Un seul aller-retour pour obtenir le perimetre du non-admin, plutot qu'une requete
        // d'existence par ligne (evite un N+1 sur la liste/l'export).
        Set<Long> insuredIdsAutorises = admin ? null : contractRepository.findBySalesRepIdOrderByCreatedAtDesc(principal.userId())
                .stream()
                .map(Contract::getInsured)
                .filter(java.util.Objects::nonNull)
                .map(Insured::getId)
                .collect(Collectors.toSet());

        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .filter(insured -> admin || insuredIdsAutorises.contains(insured.getId()))
                .map(this::toResponse)
                .filter(response -> normalizedQuery == null || matchesQuery(response, normalizedQuery))
                .filter(response -> typeClient == null || typeClient == response.typeClient())
                .toList();
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private void verifierAccesInsuredCible(Long insuredId) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        if (!contractRepository.existsByInsuredIdAndSalesRepId(insuredId, principal.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + insuredId);
        }
    }

    private TypeClient parseTypeClient(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return TypeClient.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean matchesQuery(InsuredResponse response, String normalizedQuery) {
        return contains(response.nom(), normalizedQuery)
                || contains(response.prenoms(), normalizedQuery)
                || contains(response.clientPhone(), normalizedQuery)
                || contains(response.clientCity(), normalizedQuery);
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

    public InsuredResponse findById(Long id) {
        verifierAccesInsuredCible(id);
        return toResponse(getInsured(id));
    }

    public InsuredResponse create(InsuredRequest request) {
        Insured insured = Insured.builder()
                .nom(request.nom())
                .prenoms(request.prenoms())
                .clientPhone(request.clientPhone())
                .clientPhone2(request.clientPhone2())
                .clientCity(request.clientCity())
                .clientEmail(request.clientEmail())
                .typeClient(request.typeClient())
                .build();

        return toResponse(repository.save(insured));
    }

    public InsuredResponse update(Long id, InsuredRequest request) {
        verifierAccesInsuredCible(id);
        Insured insured = getInsured(id);

        insured.setNom(request.nom());
        insured.setPrenoms(request.prenoms());
        insured.setClientPhone(request.clientPhone());
        insured.setClientPhone2(request.clientPhone2());
        insured.setClientCity(request.clientCity());
        insured.setClientEmail(request.clientEmail());
        insured.setTypeClient(request.typeClient());

        return toResponse(repository.save(insured));
    }

    public void delete(Long id) {
        verifierAccesInsuredCible(id);
        repository.delete(getInsured(id));
    }

    Insured getInsured(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assure introuvable: " + id));
    }

    private InsuredResponse toResponse(Insured insured) {
        return new InsuredResponse(
                insured.getId(),
                insured.getNom(),
                insured.getPrenoms(),
                insured.getClientPhone(),
                insured.getClientCity(),
                insured.getClientEmail(),
                insured.getTypeClient(),
                insured.getClientPhone2()
        );
    }
}
