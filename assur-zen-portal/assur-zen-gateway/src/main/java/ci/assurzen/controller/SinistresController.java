package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SinistrePhotoContent;
import ci.assurzen.dto.SinistrePhotoResponse;
import ci.assurzen.dto.SinistreRequest;
import ci.assurzen.dto.SinistreResponse;
import ci.assurzen.dto.SinistreSuiviRequest;
import ci.assurzen.dto.SinistreSuiviResponse;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.services.SinistreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("${env.ws}sinistres")
@Tag(name = "Sinistres", description = "Déclaration et suivi des sinistres")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SinistresController {

    private final SinistreService sinistreService;

    @GetMapping
    @Operation(operationId = "listSinistres", summary = "Lister/rechercher les sinistres (paginé, filtrable par statut)")
    public ResponseEntity<PageResponse<SinistreResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SinistreStatus status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(sinistreService.search(q, status, contractId, insuredId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSinistre", summary = "Détail d'un sinistre")
    public ResponseEntity<SinistreResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(sinistreService.getSinistre(id));
    }

    @PostMapping
    @Operation(operationId = "declareSinistre", summary = "Déclarer un sinistre")
    public ResponseEntity<SinistreResponse> declare(@Valid @RequestBody SinistreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sinistreService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateSinistre", summary = "Mettre à jour les informations descriptives d'un sinistre (le statut se change via /suivis)")
    public ResponseEntity<SinistreResponse> update(@PathVariable Long id, @Valid @RequestBody SinistreRequest request) {
        return ResponseEntity.ok(sinistreService.update(id, request));
    }

    @GetMapping("/{id}/suivis")
    @Operation(operationId = "listSinistreSuivis", summary = "Historique de suivi d'un sinistre")
    public ResponseEntity<List<SinistreSuiviResponse>> suivis(@PathVariable Long id) {
        return ResponseEntity.ok(sinistreService.listSuivis(id));
    }

    @PostMapping("/{id}/suivis")
    @Operation(operationId = "advanceSinistre", summary = "Faire avancer le dossier (nouveau statut + notification SMS de l'assuré)")
    public ResponseEntity<SinistreSuiviResponse> advance(@PathVariable Long id, @Valid @RequestBody SinistreSuiviRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sinistreService.addSuivi(id, request));
    }

    @GetMapping("/{id}/photos")
    @Operation(operationId = "listSinistrePhotos", summary = "Lister les photos jointes à un sinistre")
    public ResponseEntity<List<SinistrePhotoResponse>> listPhotos(@PathVariable Long id) {
        return ResponseEntity.ok(sinistreService.listPhotos(id));
    }

    @PostMapping("/{id}/photos")
    @Operation(operationId = "uploadSinistrePhoto", summary = "Joindre une photo à un sinistre")
    public ResponseEntity<SinistrePhotoResponse> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sinistreService.uploadPhoto(id, file));
    }

    @GetMapping("/{id}/photos/{photoId}")
    @Operation(operationId = "downloadSinistrePhoto", summary = "Télécharger/afficher une photo jointe à un sinistre")
    public ResponseEntity<byte[]> downloadPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        SinistrePhotoContent photo = sinistreService.getPhotoContent(id, photoId);
        MediaType mediaType = photo.contentType() != null
                ? MediaType.parseMediaType(photo.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(photo.fileName()).build().toString())
                .body(photo.content());
    }
}
