package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AuditChangesResponse;
import ci.assurzen.dto.AuditCompteItem;
import ci.assurzen.dto.AuditJourItem;
import ci.assurzen.dto.HistoriqueModificationResponse;
import ci.assurzen.dto.HistoriqueModificationStatistiquesResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.HistoriqueModification;
import ci.assurzen.entities.StatutAudit;
import ci.assurzen.repositories.HistoriqueModificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consultation seule (lecture) de HistoriqueModification - l'ecriture passe exclusivement par
 * FunctionalAuditRecorder (AOP @Auditable ou appel explicite depuis un service). Aucune methode
 * update()/delete() n'est exposee : les entrees d'audit sont immuables par conception.
 * Acces reserve ADMIN au niveau SecurityConfig (${env.ws}audit-logs), meme convention que /audit et
 * /logs. Route renommee de "historique-modifications" vers "audit-logs" pour matcher le contrat
 * cross-equipe reel (docs/api-contracts/audit-trail.md, repo frontend) : le frontend est deja
 * branche sur GET /audit-logs et GET /audit-logs/entity/{entite}/{id}.
 */
@Service
@RequiredArgsConstructor
public class HistoriqueModificationService {

    private final HistoriqueModificationRepository historiqueModificationRepository;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    public PageResponse<HistoriqueModificationResponse> search(String module, String entityName, String entityId,
                                                                 ActionAudit action, Long userId, StatutAudit statut,
                                                                 Instant dateDebut, Instant dateFin, Integer page, Integer size) {
        List<HistoriqueModificationResponse> items = filter(module, entityName, entityId, action, userId, statut, dateDebut, dateFin);
        return paginate(items, page, size);
    }

    private List<HistoriqueModificationResponse> filter(String module, String entityName, String entityId,
                                                          ActionAudit action, Long userId, StatutAudit statut,
                                                          Instant dateDebut, Instant dateFin) {
        return historiqueModificationRepository.findAll().stream()
                .filter(h -> module == null || module.equalsIgnoreCase(h.getModule()))
                .filter(h -> entityName == null || entityName.equalsIgnoreCase(h.getEntityName()))
                .filter(h -> entityId == null || entityId.equals(h.getEntityId()))
                .filter(h -> action == null || action == h.getAction())
                .filter(h -> userId == null || userId.equals(h.getUserId()))
                .filter(h -> statut == null || statut == h.getStatut())
                .filter(h -> dateDebut == null || (h.getCreatedAt() != null && !h.getCreatedAt().isBefore(dateDebut)))
                .filter(h -> dateFin == null || (h.getCreatedAt() != null && !h.getCreatedAt().isAfter(dateFin)))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    public HistoriqueModificationResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public List<HistoriqueModificationResponse> getByEntity(String entityName, String entityId) {
        return historiqueModificationRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<HistoriqueModificationResponse> getByCorrelationId(String correlationId) {
        return historiqueModificationRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<HistoriqueModificationResponse> getByUser(Long userId) {
        return historiqueModificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AuditChangesResponse getChanges(Long id) {
        HistoriqueModification h = getEntity(id);
        return new AuditChangesResponse(h.getId(), h.getEntityName(), h.getEntityId(),
                h.getAncienneValeur(), h.getNouvelleValeur(), h.getChampsModifies());
    }

    public HistoriqueModificationStatistiquesResponse statistiques() {
        List<HistoriqueModification> tous = historiqueModificationRepository.findAll();

        long total = tous.size();
        long echecs = tous.stream().filter(h -> h.getStatut() == StatutAudit.ECHEC).count();
        long exports = tous.stream().filter(h -> h.getAction() == ActionAudit.EXPORT || h.getAction() == ActionAudit.DOWNLOAD).count();
        long connexions = tous.stream().filter(h -> h.getAction() == ActionAudit.LOGIN).count();

        List<AuditCompteItem> parAction = tous.stream()
                .collect(Collectors.groupingBy(h -> h.getAction().name(), Collectors.counting())).entrySet().stream()
                .map(e -> new AuditCompteItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(AuditCompteItem::nombre).reversed())
                .toList();

        List<AuditCompteItem> parModule = tous.stream()
                .collect(Collectors.groupingBy(HistoriqueModification::getModule, Collectors.counting())).entrySet().stream()
                .map(e -> new AuditCompteItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(AuditCompteItem::nombre).reversed())
                .toList();

        List<AuditCompteItem> parUtilisateur = tous.stream()
                .filter(h -> h.getUsername() != null)
                .collect(Collectors.groupingBy(HistoriqueModification::getUsername, Collectors.counting())).entrySet().stream()
                .map(e -> new AuditCompteItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(AuditCompteItem::nombre).reversed())
                .limit(20)
                .toList();

        DateTimeFormatter formatteurJour = DateTimeFormatter.ISO_LOCAL_DATE;
        Map<String, Long> parJourMap = tous.stream()
                .filter(h -> h.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getCreatedAt().atZone(ZoneOffset.UTC).format(formatteurJour),
                        Collectors.counting()));
        List<AuditJourItem> parJour = parJourMap.entrySet().stream()
                .map(e -> new AuditJourItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(AuditJourItem::jour))
                .toList();

        return new HistoriqueModificationStatistiquesResponse(total, echecs, exports, connexions, parAction, parModule, parJour, parUtilisateur);
    }

    public byte[] exportCsv(String module, String entityName, String entityId, ActionAudit action, Long userId,
                             StatutAudit statut, Instant dateDebut, Instant dateFin) {
        List<HistoriqueModificationResponse> rows = filter(module, entityName, entityId, action, userId, statut, dateDebut, dateFin);

        functionalAuditRecorder.recordAction("AUDIT", "HistoriqueModification", null, ActionAudit.EXPORT,
                "Export CSV du journal d'audit (" + rows.size() + " lignes)");

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Reference;Date;Utilisateur;Module;Entite;EntiteId;Action;Description;Motif;Statut;IP;Canal\n");
        for (HistoriqueModificationResponse row : rows) {
            csv.append(CsvUtils.field(row.reference()))
                    .append(';').append(CsvUtils.field(row.createdAt()))
                    .append(';').append(CsvUtils.field(row.username()))
                    .append(';').append(CsvUtils.field(row.module()))
                    .append(';').append(CsvUtils.field(row.entityName()))
                    .append(';').append(CsvUtils.field(row.entityId()))
                    .append(';').append(CsvUtils.field(row.action()))
                    .append(';').append(CsvUtils.field(row.description()))
                    .append(';').append(CsvUtils.field(row.motif()))
                    .append(';').append(CsvUtils.field(row.statut()))
                    .append(';').append(CsvUtils.field(row.ipAddress()))
                    .append(';').append(CsvUtils.field(row.canal()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private HistoriqueModification getEntity(Long id) {
        return historiqueModificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entree d'historique introuvable: " + id));
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 500);

        int totalElements = items.size();
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private HistoriqueModificationResponse toResponse(HistoriqueModification h) {
        return new HistoriqueModificationResponse(
                h.getId(),
                h.getReference(),
                h.getCorrelationId(),
                h.getModule(),
                h.getEntityName(),
                h.getEntityId(),
                h.getAction(),
                h.getDescription(),
                h.getMotif(),
                h.getAncienneValeur(),
                h.getNouvelleValeur(),
                h.getChampsModifies(),
                h.getUserId(),
                h.getUsername(),
                h.getUserRole(),
                h.getIpAddress(),
                h.getCanal(),
                h.getStatut(),
                h.getErrorMessage(),
                h.getCreatedAt()
        );
    }
}
