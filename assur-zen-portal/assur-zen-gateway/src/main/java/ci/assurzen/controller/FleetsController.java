package ci.assurzen.controller;

import ci.assurzen.dto.AddVehicleRequest;
import ci.assurzen.dto.AttachContractRequest;
import ci.assurzen.dto.FleetContractResponse;
import ci.assurzen.dto.FleetEcheanceResponse;
import ci.assurzen.dto.FleetImportErrorResponse;
import ci.assurzen.dto.FleetImportResponse;
import ci.assurzen.dto.FleetMovementResponse;
import ci.assurzen.dto.FleetRequest;
import ci.assurzen.dto.FleetResponse;
import ci.assurzen.dto.FleetVehicleResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RemoveVehicleRequest;
import ci.assurzen.dto.ReplaceVehicleRequest;
import ci.assurzen.dto.TransferVehicleRequest;
import ci.assurzen.entities.FleetImportFormat;
import ci.assurzen.entities.FleetImportStatus;
import ci.assurzen.entities.FleetMovementType;
import ci.assurzen.entities.FleetStatus;
import ci.assurzen.entities.FleetType;
import ci.assurzen.entities.FleetVehicleStatus;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.services.FleetService;
import ci.assurzen.services.VehiculeImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${env.ws}fleets")
@Tag(name = "Flottes", description = "Gestion de flotte auto/moto : flottes, vehicules membres, mouvements")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FleetsController {

    private final FleetService fleetService;
    private final VehiculeImportService vehiculeImportService;

    @GetMapping
    @Operation(operationId = "listFleets", summary = "Lister/rechercher les flottes (pagine, filtrable)")
    public ResponseEntity<PageResponse<FleetResponse>> list(
            @RequestParam(required = false) String fleetNumber,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) FleetType fleetType,
            @RequestParam(required = false) FleetStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(fleetService.search(fleetNumber, clientId, name, fleetType, status, startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getFleet", summary = "Detail d'une flotte")
    public ResponseEntity<FleetResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(fleetService.getFleet(id));
    }

    @PostMapping
    @Operation(operationId = "createFleet", summary = "Creer une flotte (statut initial DRAFT)")
    public ResponseEntity<FleetResponse> create(@Valid @RequestBody FleetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fleetService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateFleet", summary = "Mettre a jour les informations d'une flotte (impossible si cloturee)")
    public ResponseEntity<FleetResponse> update(@PathVariable Long id, @Valid @RequestBody FleetRequest request) {
        return ResponseEntity.ok(fleetService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @Operation(operationId = "activateFleet", summary = "Activer une flotte (depuis DRAFT ou SUSPENDED)")
    public ResponseEntity<FleetResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(fleetService.activate(id));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(operationId = "suspendFleet", summary = "Suspendre une flotte active")
    public ResponseEntity<FleetResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(fleetService.suspend(id));
    }

    @PatchMapping("/{id}/close")
    @Operation(operationId = "closeFleet", summary = "Cloturer une flotte (transition terminale)")
    public ResponseEntity<FleetResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(fleetService.close(id));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteFleet", summary = "Supprimer une flotte jamais activee (statut DRAFT uniquement, reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fleetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{fleetId}/vehicles")
    @Operation(operationId = "listFleetVehicles", summary = "Lister les vehicules d'une flotte (historique inclus, filtrable par statut)")
    public ResponseEntity<PageResponse<FleetVehicleResponse>> listVehicles(
            @PathVariable Long fleetId,
            @RequestParam(required = false) FleetVehicleStatus status,
            @RequestParam(required = false) VehiculeType vehiculeType,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(fleetService.listVehicles(fleetId, status, vehiculeType, page, size));
    }

    @PostMapping("/{fleetId}/vehicles")
    @Operation(operationId = "addFleetVehicle", summary = "Ajouter un vehicule a une flotte active")
    public ResponseEntity<FleetVehicleResponse> addVehicle(@PathVariable Long fleetId, @Valid @RequestBody AddVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fleetService.addVehicle(fleetId, request));
    }

    @PostMapping("/{fleetId}/vehicles/remove")
    @Operation(operationId = "removeFleetVehicle", summary = "Retirer un vehicule d'une flotte (motif + historique conserves)")
    public ResponseEntity<FleetVehicleResponse> removeVehicle(@PathVariable Long fleetId, @Valid @RequestBody RemoveVehicleRequest request) {
        return ResponseEntity.ok(fleetService.removeVehicle(fleetId, request));
    }

    @PostMapping("/{fleetId}/vehicles/replace")
    @Operation(operationId = "replaceFleetVehicle", summary = "Remplacer un vehicule d'une flotte par un autre")
    public ResponseEntity<FleetVehicleResponse> replaceVehicle(@PathVariable Long fleetId, @Valid @RequestBody ReplaceVehicleRequest request) {
        return ResponseEntity.ok(fleetService.replaceVehicle(fleetId, request));
    }

    @PostMapping("/{sourceFleetId}/vehicles/{vehicleId}/transfer")
    @Operation(operationId = "transferFleetVehicle", summary = "Transferer un vehicule vers une autre flotte du meme client")
    public ResponseEntity<FleetVehicleResponse> transferVehicle(@PathVariable Long sourceFleetId, @PathVariable Long vehicleId,
                                                                 @Valid @RequestBody TransferVehicleRequest request) {
        return ResponseEntity.ok(fleetService.transferVehicle(sourceFleetId, vehicleId, request));
    }

    @GetMapping("/{fleetId}/movements")
    @Operation(operationId = "listFleetMovements", summary = "Historique des mouvements de vehicules d'une flotte (pagine, filtrable)")
    public ResponseEntity<PageResponse<FleetMovementResponse>> listMovements(
            @PathVariable Long fleetId,
            @RequestParam(required = false) FleetMovementType movementType,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(fleetService.listMovements(fleetId, movementType, vehicleId, dateFrom, dateTo, userId, page, size));
    }

    @GetMapping("/{fleetId}/contracts")
    @Operation(operationId = "listFleetContracts", summary = "Lister les contrats des vehicules (actuels et passes) d'une flotte")
    public ResponseEntity<List<FleetContractResponse>> listContracts(@PathVariable Long fleetId) {
        return ResponseEntity.ok(fleetService.listContracts(fleetId));
    }

    @PostMapping("/{fleetId}/contracts/attach")
    @Operation(operationId = "attachFleetContract",
            summary = "Rattacher un contrat mono existant (sans vehicule) a une flotte, en le liant a un vehicule "
                    + "(ajoute le vehicule a la flotte au passage s'il n'en est pas deja membre)")
    public ResponseEntity<FleetContractResponse> attachContract(@PathVariable Long fleetId, @Valid @RequestBody AttachContractRequest request) {
        return ResponseEntity.ok(fleetService.attachContract(fleetId, request));
    }

    @GetMapping("/{fleetId}/echeances")
    @Operation(operationId = "listFleetEcheances",
            summary = "Lister les avis d'echeance (SMS programmes et envoyes) des vehicules/motos ACTUELLEMENT incorpores dans la flotte")
    public ResponseEntity<List<FleetEcheanceResponse>> listEcheances(@PathVariable Long fleetId) {
        return ResponseEntity.ok(fleetService.listEcheances(fleetId));
    }

    @GetMapping("/{fleetId}/vehicles/import/template")
    @Operation(operationId = "getFleetVehicleImportTemplate", summary = "Telecharger un modele de fichier d'import (colonnes fixes, CSV ou XLSX)")
    public ResponseEntity<byte[]> importTemplate(@PathVariable Long fleetId, @RequestParam(required = false, defaultValue = "csv") String format) {
        FleetImportFormat fleetImportFormat = "xlsx".equalsIgnoreCase(format) ? FleetImportFormat.XLSX : FleetImportFormat.CSV;
        byte[] content = vehiculeImportService.template(fleetImportFormat);
        String fileName = fleetImportFormat == FleetImportFormat.CSV ? "modele_vehicules.csv" : "modele_vehicules.xlsx";
        MediaType mediaType = fleetImportFormat == FleetImportFormat.CSV
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(content);
    }

    @PostMapping("/{fleetId}/vehicles/import/preview")
    @Operation(operationId = "previewFleetVehicleImport", summary = "Valider un fichier d'import de vehicules sans rien ecrire (rapport de previsualisation)")
    public ResponseEntity<FleetImportResponse> importPreview(@PathVariable Long fleetId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehiculeImportService.preview(fleetId, file));
    }

    @PostMapping("/{fleetId}/vehicles/import/{importId}/confirm")
    @Operation(operationId = "confirmFleetVehicleImport", summary = "Confirmer un import valide (READY) : ecrit reellement les vehicules et les rattache a la flotte")
    public ResponseEntity<FleetImportResponse> importConfirm(@PathVariable Long fleetId, @PathVariable Long importId) {
        return ResponseEntity.ok(vehiculeImportService.confirm(fleetId, importId));
    }

    @GetMapping("/{fleetId}/vehicles/import")
    @Operation(operationId = "listFleetVehicleImports", summary = "Historique des imports de vehicules d'une flotte (pagine, filtrable par statut)")
    public ResponseEntity<PageResponse<FleetImportResponse>> importList(
            @PathVariable Long fleetId,
            @RequestParam(required = false) FleetImportStatus status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(vehiculeImportService.list(fleetId, status, page, size));
    }

    @GetMapping("/{fleetId}/vehicles/import/{importId}")
    @Operation(operationId = "getFleetVehicleImport", summary = "Detail/rapport d'un import de vehicules")
    public ResponseEntity<FleetImportResponse> importGet(@PathVariable Long fleetId, @PathVariable Long importId) {
        return ResponseEntity.ok(vehiculeImportService.get(fleetId, importId));
    }

    @GetMapping("/{fleetId}/vehicles/import/{importId}/errors")
    @Operation(operationId = "listFleetVehicleImportErrors", summary = "Lister les erreurs ligne par ligne d'un import de vehicules")
    public ResponseEntity<PageResponse<FleetImportErrorResponse>> importErrors(
            @PathVariable Long fleetId,
            @PathVariable Long importId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(vehiculeImportService.listErrors(fleetId, importId, page, size));
    }
}
