package ci.assurzen.service;

import ci.assurzen.dto.QuoteDtos.*;
import ci.assurzen.entities.Quote;
import ci.assurzen.entities.QuoteEmail;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.QuoteEmailRepository;
import ci.assurzen.repositories.QuoteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** Envoi d'un devis par e-mail au prospect, au nom d'AssurZen (2026-11-03). Prix envoye = prix fige du devis. */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteEmailService {

    static final int LIMITE_PAR_JOUR = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BackOfficeQuoteService backOffice;
    private final MobileQuoteService mobileQuoteService;
    private final QuoteRepository quotes;
    private final QuoteEmailRepository emails;
    private final QuoteDocumentService documents;
    private final MailService mailService;
    private final AuthService authService;
    private final ProfileRepository profiles;

    @Transactional
    public QuoteView envoyer(String reference, SendEmailRequest r) {
        AuthenticatedUserPrincipal p = authService.currentPrincipal();
        Quote q = mobileQuoteService.actualiser(backOffice.trouver(reference));
        if ("EXPIRE".equals(q.getStatus())) {
            throw new MobileApiException(409, "QUOTE_EXPIRED", "Ce devis a expiré : il ne peut plus être envoyé.");
        }
        String destinataire = r != null && r.email() != null && !r.email().isBlank() ? r.email().trim() : q.getProspectEmail();
        if (destinataire == null || destinataire.isBlank()) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Renseignez l'adresse e-mail du prospect.", "email");
        }
        if (!EMAIL.matcher(destinataire).matches() || destinataire.length() > 150) {
            throw new MobileApiException(400, "VALIDATION_FAILED", "Adresse e-mail invalide.", "email");
        }
        if (emails.countByQuoteIdAndSentAtAfter(q.getId(), Instant.now().minus(Duration.ofHours(24))) >= LIMITE_PAR_JOUR) {
            throw new MobileApiException(429, "TOO_MANY_REQUESTS", "Limite atteinte : " + LIMITE_PAR_JOUR + " envois par devis et par jour.");
        }
        boolean avecPdf = r == null || r.attachPdf() == null || r.attachPdf();
        var profil = profiles.findByUserId(p.userId()).orElse(null);
        String copie = null;
        if (r != null && Boolean.TRUE.equals(r.copyToMe())) {
            copie = profil == null ? null : profil.getEmail();
            if (copie == null || copie.isBlank()) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Votre compte n'a pas d'adresse e-mail pour recevoir une copie.", "copyToMe");
            }
        }
        String expediteur = profil == null ? p.username() : ((profil.getFirstname() == null ? "" : profil.getFirstname()) + " "
                + (profil.getLastname() == null ? "" : profil.getLastname())).trim();
        if (expediteur.isBlank()) {
            expediteur = p.username();
        }
        QuoteView vue = mobileQuoteService.vue(q);
        String corps = documents.html(vue, r == null ? null : r.message(), expediteur, avecPdf);
        byte[] pdf = avecPdf ? documents.pdf(vue) : null;
        String erreur = mailService.envoyerAvecPiece(documents.adresseExpedition(), destinataire, copie, documents.objet(vue), corps, "Devis-" + q.getReference() + ".pdf", pdf);
        if ("DISABLED".equals(erreur)) {
            throw new MobileApiException(503, "MAIL_DISABLED", "L'envoi d'e-mails est désactivé (paramètre EMAIL_ENABLED).");
        }
        if (erreur != null) {
            log.warn("[DEVIS] echec d'envoi du devis {} : {}", q.getReference(), erreur);
            throw new MobileApiException(502, "MAIL_SEND_FAILED", "L'e-mail n'a pas pu être envoyé. Réessayez plus tard.");
        }
        QuoteEmail trace = new QuoteEmail();
        trace.setQuoteId(q.getId());
        trace.setToEmail(destinataire);
        trace.setSentBy(expediteur);
        trace.setWithPdf(avecPdf);
        trace.setCopyTo(copie);
        emails.save(trace);
        q.setProspectEmail(destinataire);
        q.setEmailSentCount((q.getEmailSentCount() == null ? 0 : q.getEmailSentCount()) + 1);
        q.setLastEmailSentAt(trace.getSentAt());
        q.setLastEmailTo(destinataire);
        q.setLastEmailSentBy(expediteur);
        log.info("[DEVIS] devis {} envoye par {} (pdf={})", q.getReference(), p.username(), avecPdf);
        return mobileQuoteService.vue(quotes.save(q));
    }

    /** Envoi demande a la creation (sendEmail=true) : un echec d'envoi ne fait pas echouer la creation (le devis reste enregistre). */
    public QuoteView envoyerSiDemande(QuoteView cree, Boolean sendEmail) {
        if (!Boolean.TRUE.equals(sendEmail) || cree.prospectEmail() == null || cree.prospectEmail().isBlank()) {
            return cree;
        }
        try {
            return envoyer(cree.reference(), new SendEmailRequest(null, null, true, false));
        } catch (MobileApiException e) {
            log.warn("[DEVIS] envoi a la creation du devis {} impossible : {}", cree.reference(), e.getCode());
            return cree;
        }
    }

    @Transactional(readOnly = true)
    public EmailHistory historique(String reference) {
        Quote q = backOffice.trouver(reference);
        List<EmailSent> items = emails.findByQuoteIdOrderBySentAtDesc(q.getId()).stream()
                .map(e -> new EmailSent(e.getSentAt(), e.getToEmail(), e.getSentBy(), e.isWithPdf(), e.getCopyTo())).toList();
        return new EmailHistory(items);
    }
}
