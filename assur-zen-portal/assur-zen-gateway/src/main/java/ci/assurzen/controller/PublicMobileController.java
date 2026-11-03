package ci.assurzen.controller;

import ci.assurzen.dto.PublicMobileDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.CallbackService;
import ci.assurzen.service.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue et demande de rappel de l'application mobile, SANS compte requis (demande n°5, 2026-11-03) : l'inscription
 * n'est pas necessaire pour decouvrir les produits ou demander a etre rappele. Routes /public/** (permitAll).
 */
@RestController
@RequestMapping("${env.ws}public")
@Tag(name = "Mobile - Catalogue public", description = "Produits et demande de rappel, sans compte (application mobile)")
@RequiredArgsConstructor
public class PublicMobileController {

    private final PublicCatalogService catalogService;
    private final CallbackService callbackService;
    private final ci.assurzen.service.PublicContactService contactService;
    private final ci.assurzen.service.MobileProfileService profileService;
    private final ci.assurzen.service.MobileSupportService supportService;

    @GetMapping("/products")
    @Operation(operationId = "publicListProducts", summary = "Produits actifs du catalogue (titre tel quel ; onlineSubscription=false tant que la souscription en ligne n'existe pas)")
    @SecurityRequirements
    public ResponseEntity<PublicMobileDtos.ProductList> produits() {
        return ResponseEntity.ok(catalogService.produits());
    }

    @GetMapping("/products/{id}")
    @Operation(operationId = "publicGetProduct", summary = "Un produit du catalogue (404 PRODUCT_NOT_FOUND)")
    @SecurityRequirements
    public ResponseEntity<PublicMobileDtos.Product> produit(@PathVariable String id) {
        return ResponseEntity.ok(catalogService.produit(id));
    }

    @GetMapping("/contact-info")
    @Operation(operationId = "publicContactInfo", summary = "Coordonnees de l'entreprise (telephones, WhatsApp, e-mail, adresse, horaires, numeros d'urgence, disponibilite), lues dans la configuration SUPPORT_* du back-office ; un champ null = non communique")
    @SecurityRequirements
    public ResponseEntity<ci.assurzen.dto.PublicContactInfo> contact() {
        return ResponseEntity.ok(contactService.contact());
    }

    @GetMapping("/legal")
    @Operation(operationId = "publicLegal", summary = "Liens des conditions generales et de la politique de confidentialite (cles LEGAL_* du back-office) ; null = lien masque")
    @SecurityRequirements
    public ResponseEntity<ci.assurzen.dto.MobileExtraDtos.LegalInfo> mentionsLegales() {
        return ResponseEntity.ok(profileService.mentionsLegales());
    }

    @GetMapping("/support/topics")
    @Operation(operationId = "publicSupportTopics", summary = "Sujets des demandes d'aide (cle SUPPORT_TOPICS du back-office)")
    @SecurityRequirements
    public ResponseEntity<ci.assurzen.dto.MobileExtraDtos.TopicList> sujets() {
        return ResponseEntity.ok(supportService.sujets());
    }

    @GetMapping("/support/faq")
    @Operation(operationId = "publicSupportFaq", summary = "Questions frequentes visibles, triees (geree depuis le back-office)")
    @SecurityRequirements
    public ResponseEntity<ci.assurzen.dto.MobileExtraDtos.FaqList> faq() {
        return ResponseEntity.ok(supportService.faq());
    }

    @PostMapping("/callbacks")
    @Operation(operationId = "publicRequestCallback", summary = "Demander a etre rappele (creneau ASAP, MORNING, AFTERNOON ou EVENING) ; 3 demandes par heure et par numero (429 RATE_LIMITED) ; avec ou sans compte")
    @SecurityRequirements
    public ResponseEntity<PublicMobileDtos.CallbackCreated> rappel(@Valid @RequestBody PublicMobileDtos.CallbackRequest request,
                                                                  Authentication authentication, HttpServletRequest http) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                ? principal.userId() : null;
        return ResponseEntity.status(201).body(callbackService.demander(request, userId, adresseIp(http)));
    }

    private String adresseIp(HttpServletRequest http) {
        String transmis = http.getHeader("X-Forwarded-For");
        if (transmis != null && !transmis.isBlank()) {
            return transmis.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
