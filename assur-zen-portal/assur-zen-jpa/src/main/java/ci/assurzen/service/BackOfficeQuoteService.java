package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.Quote;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CommercialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Devis moto crees au back-office (lot 3, 2026-11-03) par un commercial ou un administrateur, pour un prospect.
 * Un commercial ne voit que ses propres devis ; un administrateur voit tout. Pas de remise sur la moto.
 */
@Service
@RequiredArgsConstructor
public class BackOfficeQuoteService {

    private final MobileQuoteService mobileQuoteService;
    private final QuoteRepository quotes;
    private final AuthService authService;
    private final CommercialService commercialService;

    @Transactional
    public QuoteView creerMoto(BackOfficeQuoteRequest r) {
        AuthenticatedUserPrincipal p = authService.currentPrincipal();
        Quote q = mobileQuoteService.enregistrerMoto(r.vehicle(), devis -> {
            devis.setChannel("BACKOFFICE");
            devis.setCreatedByUserId(p.userId());
            devis.setProspectName(r.prospectName().trim());
            devis.setProspectPhone(r.prospectPhone());
        });
        return mobileQuoteService.vue(q);
    }

    /** Devis VTC pour un prospect ; une remise est possible, plafonnee par QUOTE_VTC_MAX_DISCOUNT_PERCENT. */
    @Transactional
    public QuoteView creerVtc(BackOfficeVtcQuoteRequest r) {
        AuthenticatedUserPrincipal p = authService.currentPrincipal();
        Quote q = mobileQuoteService.enregistrerVtc(r.vehicle(), true, devis -> {
            devis.setChannel("BACKOFFICE");
            devis.setCreatedByUserId(p.userId());
            devis.setProspectName(r.prospectName().trim());
            devis.setProspectPhone(r.prospectPhone());
        });
        return mobileQuoteService.vue(q);
    }

    /** statut et q (nom, telephone, reference, immatriculation) sont facultatifs. */
    @Transactional
    public QuoteList lister(String statut, String recherche) {
        AuthenticatedUserPrincipal p = authService.currentPrincipal();
        List<Quote> base = commercialService.isAdmin(p)
                ? quotes.findAllByOrderByCreatedAtDesc()
                : quotes.findByCreatedByUserIdOrderByCreatedAtDesc(p.userId());
        String s = statut == null || statut.isBlank() ? null : statut.trim().toUpperCase(Locale.ROOT);
        String needle = recherche == null || recherche.isBlank() ? null : recherche.trim().toLowerCase(Locale.ROOT);
        return new QuoteList(base.stream()
                .map(mobileQuoteService::actualiser)
                .filter(q -> s == null || s.equals(q.getStatus()))
                .filter(q -> needle == null || contient(q, needle))
                .limit(500)
                .map(mobileQuoteService::vue)
                .toList());
    }

    @Transactional
    public QuoteView detail(String reference) {
        return mobileQuoteService.vue(mobileQuoteService.actualiser(trouver(reference)));
    }

    /** Le prospect a donne son accord : meme regles que l'acceptation par le client. */
    @Transactional
    public QuoteView accepter(String reference) {
        Quote q = mobileQuoteService.actualiser(trouver(reference));
        if ("EXPIRE".equals(q.getStatus())) {
            throw new MobileApiException(409, "QUOTE_EXPIRED", "Ce devis a expiré. Créez-en un nouveau.");
        }
        if (!"ACCEPTE".equals(q.getStatus())) {
            q.setStatus("ACCEPTE");
            q.setAcceptedAt(Instant.now());
            quotes.save(q);
        }
        return mobileQuoteService.vue(q);
    }

    private Quote trouver(String reference) {
        AuthenticatedUserPrincipal p = authService.currentPrincipal();
        Quote q = quotes.findByReference(reference).orElseThrow(() -> new MobileApiException(404, "NOT_FOUND", "Devis introuvable."));
        // Un commercial n'accede qu'a ses propres devis (404 pour ne pas reveler l'existence des autres).
        if (!commercialService.isAdmin(p) && !p.userId().equals(q.getCreatedByUserId())) {
            throw new MobileApiException(404, "NOT_FOUND", "Devis introuvable.");
        }
        return q;
    }

    private static boolean contient(Quote q, String needle) {
        return java.util.stream.Stream.of(q.getReference(), q.getProspectName(), q.getProspectPhone(), q.getRegistrationNumber(), q.getChassisNumber())
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle));
    }
}
