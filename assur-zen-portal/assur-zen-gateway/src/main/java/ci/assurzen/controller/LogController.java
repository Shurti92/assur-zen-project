package ci.assurzen.controller;

import ci.assurzen.dto.LogTailResponse;
import ci.assurzen.services.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}logs")
@Tag(name = "Logs", description = "Consultation du fichier de log applicatif")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    @Operation(operationId = "getLogTail", summary = "Consulter les dernieres lignes du fichier de log applicatif")
    public ResponseEntity<LogTailResponse> tail(
            @Parameter(description = "Nombre de dernieres lignes a retourner (max 5000)")
            @RequestParam(required = false) Integer lines) {
        return ResponseEntity.ok(logService.tail(lines));
    }
}
