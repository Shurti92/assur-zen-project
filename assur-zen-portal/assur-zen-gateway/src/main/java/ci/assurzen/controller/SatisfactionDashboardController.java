package ci.assurzen.controller;

import ci.assurzen.dto.NpsBreakdownResponse;
import ci.assurzen.dto.ReclamationStatistiquesResponse;
import ci.assurzen.dto.RepartitionNotesResponse;
import ci.assurzen.dto.SatisfactionMensuelleResponse;
import ci.assurzen.dto.SatisfactionOverviewResponse;
import ci.assurzen.dto.SatisfactionParDimensionItem;
import ci.assurzen.services.SatisfactionDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}satisfaction-dashboard")
@Tag(name = "Dashboard Satisfaction Client", description = "Analytique lecture-seule agregeant enquetes, NPS et reclamations (ADMIN/MARKETING/MANAGER)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SatisfactionDashboardController {

    private final SatisfactionDashboardService satisfactionDashboardService;

    @GetMapping("/overview")
    @Operation(operationId = "getSatisfactionDashboardOverview", summary = "KPIs generaux (satisfaction moyenne, NPS, taux de reponse, reclamations)")
    public ResponseEntity<SatisfactionOverviewResponse> overview() {
        return ResponseEntity.ok(satisfactionDashboardService.overview());
    }

    @GetMapping("/nps")
    @Operation(operationId = "getSatisfactionDashboardNps", summary = "Repartition promoteurs/passifs/detracteurs et score NPS")
    public ResponseEntity<NpsBreakdownResponse> nps() {
        return ResponseEntity.ok(satisfactionDashboardService.nps());
    }

    @GetMapping("/mensuel")
    @Operation(operationId = "getSatisfactionDashboardMensuel", summary = "Tendance de satisfaction sur les 12 derniers mois")
    public ResponseEntity<List<SatisfactionMensuelleResponse>> mensuel() {
        return ResponseEntity.ok(satisfactionDashboardService.mensuel());
    }

    @GetMapping("/repartition-notes")
    @Operation(operationId = "getSatisfactionDashboardRepartitionNotes", summary = "Histogramme des notes NPS (0 a 10)")
    public ResponseEntity<RepartitionNotesResponse> repartitionNotes() {
        return ResponseEntity.ok(satisfactionDashboardService.repartitionNotes());
    }

    @GetMapping("/par-produit")
    @Operation(operationId = "getSatisfactionDashboardParProduit", summary = "Satisfaction moyenne et NPS par produit")
    public ResponseEntity<List<SatisfactionParDimensionItem>> parProduit() {
        return ResponseEntity.ok(satisfactionDashboardService.parProduit());
    }

    @GetMapping("/par-gestionnaire")
    @Operation(operationId = "getSatisfactionDashboardParGestionnaire", summary = "Satisfaction moyenne et NPS par gestionnaire/commercial")
    public ResponseEntity<List<SatisfactionParDimensionItem>> parGestionnaire() {
        return ResponseEntity.ok(satisfactionDashboardService.parGestionnaire());
    }

    @GetMapping("/par-courtier")
    @Operation(operationId = "getSatisfactionDashboardParCourtier", summary = "Satisfaction moyenne et NPS par courtier/partenaire")
    public ResponseEntity<List<SatisfactionParDimensionItem>> parCourtier() {
        return ResponseEntity.ok(satisfactionDashboardService.parCourtier());
    }

    @GetMapping("/reclamations")
    @Operation(operationId = "getSatisfactionDashboardReclamations", summary = "Statistiques des reclamations (ouvertes/en cours/resolues/fermees)")
    public ResponseEntity<ReclamationStatistiquesResponse> reclamations() {
        return ResponseEntity.ok(satisfactionDashboardService.reclamations());
    }

    @GetMapping("/export/csv")
    @Operation(operationId = "exportSatisfactionDashboardCsv", summary = "Exporter le dashboard complet en CSV")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = satisfactionDashboardService.exportCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dashboard-satisfaction-client.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/export/pdf")
    @Operation(operationId = "exportSatisfactionDashboardPdf", summary = "Exporter le dashboard complet en PDF")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = satisfactionDashboardService.exportPdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dashboard-satisfaction-client.pdf").build().toString())
                .body(pdf);
    }
}
