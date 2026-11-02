package ci.assurzen.services;

import ci.assurzen.dto.MoEnvoiMessage;
import ci.assurzen.dto.SmsSendResult;
import ci.assurzen.entities.CampagneDestinataire;
import ci.assurzen.entities.CampagneMarketing;
import ci.assurzen.entities.CampagneStatut;
import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.CommunicationTemplate;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.StatutEnvoiDestinataire;
import ci.assurzen.repositories.CampagneDestinataireRepository;
import ci.assurzen.repositories.CampagneMarketingRepository;
import ci.assurzen.repositories.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Separee de CampagneMarketingService car @Async ne fonctionne que sur un appel inter-bean
 * (l'auto-invocation depuis la meme classe contournerait le proxy Spring et enverrait en
 * synchrone, bloquant la requete HTTP de lancement).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampagneEnvoiService {

    private final CampagneMarketingRepository campagneMarketingRepository;
    private final CampagneDestinataireRepository campagneDestinataireRepository;
    private final ContractRepository contractRepository;
    private final SmsSenderService smsSenderService;
    private final MailService mailService;
    private final TemplateVariableResolver templateVariableResolver;
    private final DynamicConfigurationService dynamicConfigurationService;

    @Async("campagneTaskExecutor")
    @Transactional
    public void envoyerAsync(Long campagneId) {
        CampagneMarketing campagne = campagneMarketingRepository.findById(campagneId).orElse(null);
        if (campagne == null) {
            log.warn("Campagne {} introuvable au moment de l'envoi asynchrone", campagneId);
            return;
        }

        List<CampagneDestinataire> destinataires = campagneDestinataireRepository
                .findByCampagneIdOrderByCreatedAtAsc(campagneId).stream()
                .filter(d -> d.getStatutEnvoi() == StatutEnvoiDestinataire.EN_ATTENTE)
                .toList();

        for (CampagneDestinataire destinataire : destinataires) {
            envoyerUn(campagne, destinataire);
        }

        campagne.setStatut(CampagneStatut.TERMINEE);
        campagneMarketingRepository.save(campagne);
    }

    private void envoyerUn(CampagneMarketing campagne, CampagneDestinataire destinataire) {
        Insured insured = destinataire.getInsured();
        Contract contract = contractRepository.findByInsuredIdOrderByCreatedAtDesc(insured.getId()).stream()
                .findFirst().orElse(null);

        String message = resoudreContenu(campagne, insured, contract);
        String sujet = resoudreSujet(campagne, insured, contract);

        boolean succes;
        String erreur = null;

        switch (destinataire.getCanal()) {
            case SMS -> {
                if (!dynamicConfigurationService.getBoolean("SMS_ENABLED", true)) {
                    succes = false;
                    erreur = "SMS desactive (configuration SMS_ENABLED)";
                } else if (!StringUtils.hasText(insured.getClientPhone())) {
                    succes = false;
                    erreur = "Aucun numero de telephone connu pour cet assure";
                } else {
                    try {
                        SmsSendResult result = smsSenderService.envoiMessageAuNumero(new MoEnvoiMessage(insured.getClientPhone(), message));
                        succes = result.success();
                        erreur = succes ? null : "Echec fournisseur SMS (HTTP " + result.httpStatus() + ")";
                    } catch (RuntimeException e) {
                        succes = false;
                        erreur = "Erreur technique SMS: " + e.getMessage();
                    }
                }
            }
            case EMAIL -> {
                if (!dynamicConfigurationService.getBoolean("EMAIL_ENABLED", true)) {
                    succes = false;
                    erreur = "Email desactive (configuration EMAIL_ENABLED)";
                } else if (!StringUtils.hasText(insured.getClientEmail())) {
                    succes = false;
                    erreur = "Aucune adresse email connue pour cet assure";
                } else {
                    succes = mailService.envoyer(insured.getClientEmail(), sujet, message);
                    erreur = succes ? null : "Echec de l'envoi email";
                }
            }
            case WHATSAPP, PUSH, PDF -> {
                succes = false;
                erreur = "Canal " + destinataire.getCanal() + " non encore implemente";
            }
            default -> {
                succes = false;
                erreur = "Canal inconnu";
            }
        }

        destinataire.setStatutEnvoi(succes ? StatutEnvoiDestinataire.ENVOYE : StatutEnvoiDestinataire.ECHEC);
        destinataire.setDateEnvoi(Instant.now());
        destinataire.setErreurMessage(erreur);
        campagneDestinataireRepository.save(destinataire);
    }

    private String resoudreContenu(CampagneMarketing campagne, Insured insured, Contract contract) {
        CommunicationTemplate template = campagne.getTemplate();
        if (template != null) {
            return templateVariableResolver.resoudre(template.getContenu(), insured, contract);
        }
        return personnaliser(campagne.getMessageContenu(), insured);
    }

    private String resoudreSujet(CampagneMarketing campagne, Insured insured, Contract contract) {
        CommunicationTemplate template = campagne.getTemplate();
        if (template != null && StringUtils.hasText(template.getObjet())) {
            return templateVariableResolver.resoudre(template.getObjet(), insured, contract);
        }
        return campagne.getSujetEmail();
    }

    private String personnaliser(String gabarit, Insured insured) {
        String prenom = insured.getPrenoms() == null ? "" : insured.getPrenoms();
        String nom = insured.getNom() == null ? "" : insured.getNom();
        return gabarit.replace("{prenom}", prenom).replace("{nom}", nom);
    }
}
