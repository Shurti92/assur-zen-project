package ci.assurzen.controller;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.repositories.MobileFeedbackRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.service.MobileSupportService;
import ci.assurzen.services.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration du support, de la FAQ et des avis de l'application mobile (back-office, 2026-11-03). Role ADMIN. */
@RestController
@RequestMapping("${env.ws}admin/mobile")
@Tag(name = "Administration mobile - Support", description = "Demandes d'aide, FAQ et avis des clients de l'application mobile (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminMobileSupportController {

    private final MobileSupportService supportService;
    private final MobileFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final CommercialService commercialService;

    @GetMapping("/support-requests")
    @Operation(operationId = "adminMobileListSupportRequests", summary = "Demandes d'aide des clients (statut : EN_ATTENTE, EN_COURS, REPONDU, CLOTURE), les 200 plus recentes")
    public ResponseEntity<List<MobileExtraDtos.SupportAdminItem>> demandes(@RequestParam(required = false) String statut) {
        return ResponseEntity.ok(supportService.demandes(statut));
    }

    @PostMapping("/support-requests/{id}/process")
    @Operation(operationId = "adminMobileProcessSupportRequest", summary = "Traiter une demande d'aide : statut EN_COURS, REPONDU (reponse obligatoire, notifie le client) ou CLOTURE")
    public ResponseEntity<MobileExtraDtos.SupportAdminItem> traiter(@PathVariable Long id, @Valid @RequestBody MobileExtraDtos.SupportProcessRequest request) {
        return ResponseEntity.ok(supportService.traiter(id, request));
    }

    @GetMapping("/faq")
    @Operation(operationId = "adminMobileListFaq", summary = "FAQ de l'application mobile (toutes les entrees, visibles ou non)")
    public ResponseEntity<MobileExtraDtos.FaqAdminList> faq() {
        return ResponseEntity.ok(supportService.faqAdmin());
    }

    @PutMapping("/faq")
    @Operation(operationId = "adminMobileReplaceFaq", summary = "Remplace TOUTE la FAQ de l'application mobile (l'ordre de la liste fixe l'ordre d'affichage)")
    public ResponseEntity<MobileExtraDtos.FaqAdminList> remplacerFaq(@Valid @RequestBody MobileExtraDtos.FaqReplaceRequest request) {
        return ResponseEntity.ok(supportService.remplacerFaq(request));
    }

    @GetMapping("/feedback")
    @Operation(operationId = "adminMobileListFeedback", summary = "Avis des clients de l'application mobile, les 200 plus recents")
    public ResponseEntity<List<MobileExtraDtos.FeedbackAdminItem>> avis() {
        commercialService.requireAdmin();
        var avis = feedbackRepository.findTop200ByOrderByCreatedAtDesc();
        var telephones = userRepository.findAllById(avis.stream().map(a -> a.getUserId()).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u.getUsername(), (x, y) -> x));
        return ResponseEntity.ok(avis.stream().map(a -> new MobileExtraDtos.FeedbackAdminItem(a.getId(), a.getUserId(), telephones.get(a.getUserId()),
                a.getContractId(), a.getRating(), a.getComment(), a.getCreatedAt())).toList());
    }
}
