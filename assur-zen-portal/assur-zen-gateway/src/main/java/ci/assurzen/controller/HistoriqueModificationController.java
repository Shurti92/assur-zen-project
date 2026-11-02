package ci.assurzen.controller;

import ci.assurzen.dto.AuditChangesResponse;
import ci.assurzen.dto.HistoriqueModificationResponse;
import ci.assurzen.dto.HistoriqueModificationStatistiquesResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.StatutAudit;
import ci.assurzen.services.HistoriqueModificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Audit trail FONCTIONNEL (qui/quoi/avant-apres, lisible metier) - complementaire a
 * AuditController/${env.ws}audit (audit technique HTTP brut). Lecture seule : les entrees sont
 * immuables, aucun endpoint de modification/suppression n'est expose.
 *
 * Route "audit-logs" (pas "historique-modifications") pour matcher le contrat cross-equipe reel
 * (docs/api-contracts/audit-trail.md, repo frontend) : le frontend est deja branche sur ces chemins
 * exacts, notamment GET /audit-logs et GET /audit-logs/entity/{entite}/{id}.
 */
@RestController
@RequestMapping("${env.ws}audit-logs")
@Tag(name = "Audit Logs", description = "Audit trail fonctionnel centralise : qui a fait quoi, sur quelle entite, avant/apres quelle valeur")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class HistoriqueModificationController {

    private final HistoriqueModificationService historiqueModificationService;

    @GetMapping
    @Operation(operationId = "listAuditLogs", summary = "Rechercher dans le journal d'audit fonctionnel (pagine, filtrable) (reserve aux administrateurs)")
    public ResponseEntity<PageResponse<HistoriqueModificationResponse>> search(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) ActionAudit action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) StatutAudit statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFin,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(historiqueModificationService.search(module, entityName, entityId, action, userId, statut, dateDebut, dateFin, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAuditLog", summary = "Detail d'une entree d'audit (reserve aux administrateurs)")
    public ResponseEntity<HistoriqueModificationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(historiqueModificationService.get(id));
    }

    @GetMapping("/entity/{entityName}/{entityId}")
    @Operation(operationId = "getAuditLogsByEntity", summary = "Historique complet d'une entite donnee, ex. l'onglet Historique d'un ecran de detail (reserve aux administrateurs)")
    public ResponseEntity<List<HistoriqueModificationResponse>> getByEntity(@PathVariable String entityName, @PathVariable String entityId) {
        return ResponseEntity.ok(historiqueModificationService.getByEntity(entityName, entityId));
    }

    @GetMapping("/user/{userId}")
    @Operation(operationId = "getAuditLogsByUser", summary = "Historique complet des actions d'un utilisateur (reserve aux administrateurs)")
    public ResponseEntity<List<HistoriqueModificationResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(historiqueModificationService.getByUser(userId));
    }

    @GetMapping("/correlation/{correlationId}")
    @Operation(operationId = "getAuditLogsByCorrelation", summary = "Toutes les entrees fonctionnelles liees a une meme requete HTTP (correlationId partage avec AuditFlux) (reserve aux administrateurs)")
    public ResponseEntity<List<HistoriqueModificationResponse>> getByCorrelationId(@PathVariable String correlationId) {
        return ResponseEntity.ok(historiqueModificationService.getByCorrelationId(correlationId));
    }

    @GetMapping("/{id}/changes")
    @Operation(operationId = "getAuditLogChanges", summary = "Ancienne/nouvelle valeur et liste des champs modifies pour une entree UPDATE (reserve aux administrateurs)")
    public ResponseEntity<AuditChangesResponse> getChanges(@PathVariable Long id) {
        return ResponseEntity.ok(historiqueModificationService.getChanges(id));
    }

    @GetMapping("/statistics")
    @Operation(operationId = "getAuditLogStatistics", summary = "Statistiques globales du journal d'audit fonctionnel (reserve aux administrateurs)")
    public ResponseEntity<HistoriqueModificationStatistiquesResponse> statistics() {
        return ResponseEntity.ok(historiqueModificationService.statistiques());
    }

    @GetMapping("/export")
    @Operation(operationId = "exportAuditLogs", summary = "Exporter le journal d'audit filtre en CSV (reserve aux administrateurs ; l'export lui-meme est audite)")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) ActionAudit action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) StatutAudit statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFin) {
        byte[] csv = historiqueModificationService.exportCsv(module, entityName, entityId, action, userId, statut, dateDebut, dateFin);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
