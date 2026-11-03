package ci.assurzen.services;

import ci.assurzen.dto.NotificateurSmsResponse;
import ci.assurzen.entities.CommunicationTemplate;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Echeance;
import ci.assurzen.entities.NotificateurSms;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.entities.StatutEcheance;
import ci.assurzen.entities.StatutEnvoiSms;
import ci.assurzen.entities.TemplateStatut;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.CommunicationTemplateRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.NotificateurSmsRepository;
import ci.assurzen.sycapay.config.SycaPayModeTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {

    private static final Pattern MONTHS_PATTERN = Pattern.compile("\\d+");

    /**
     * SMS lies aux commissions et primes versees (2026-09-24) : rediriges vers le numero de test quand le
     * MODE TEST SycaPay est actif (hors production, SycaPayModeTest), pour qu'aucun vrai partenaire ou
     * commercial ne recoive "Commission versee : X FCFA" alors que le virement est parti vers le numero de
     * test. Jamais les SMS d'authentification (OTP, acces) ni les SMS clients (echeances, sinistres...).
     */
    static final Set<TypeMessageSms> SMS_REDIRIGES_EN_MODE_TEST = EnumSet.of(
            TypeMessageSms.commission_payee,
            TypeMessageSms.commission_demande_recue,
            TypeMessageSms.commission_commerciale_payee,
            TypeMessageSms.commission_disponible,
            TypeMessageSms.commission_gagnee,
            TypeMessageSms.commission_vignette_payee,
            TypeMessageSms.bonus_commercial_paye,
            TypeMessageSms.prime_objectif_payee);

    /** Mode test (hors production) : optionnel, injecte par champ - absent (tests), rien n'est redirige. */
    @Autowired(required = false)
    private SycaPayModeTest modeTest;

    private final ProviderService providerService;
    private final NotificateurSmsRepository notificateurSmsRepository;
    private final ContractRepository contractRepository;
    private final CommunicationTemplateRepository communicationTemplateRepository;
    private final TemplateVariableResolver templateVariableResolver;

    /**
     * Envoie immediatement un SMS de bienvenue et journalise le resultat. produits est le nom du
     * produit souscrit (ou plusieurs noms distincts separes par une virgule si la souscription porte
     * sur plusieurs contrats/produits en une seule operation).
     */
    public boolean sendWelcomeSms(String phone, String fullName, String produits) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de bienvenue non envoye pour {}", fullName);
            return false;
        }

        String message = "Bienvenue " + fullName + " chez AssurZen Assurance CI ! Votre souscription à notre "
                + produits + " a été effectué avec succès. Nous vous remercions pour votre confiance. AssurZen Assurance Votre sérénité, Notre engagement";
        return sendImmediate(phone, message, TypeMessageSms.bienvenue);
    }


    /**
     * Planifie les SMS de suivi du cycle de vie d'un contrat, en fonction de sa duree:
     * - 1 mois: 2 SMS (J-5 avis d'echeance, J0 fin de contrat)
     * - toute autre duree (3/6/12 mois...): 4 SMS (J-45, J-15, J-5 avis d'echeance, J0 fin de contrat)
     * Un assure peut avoir un 2e numero (phone2, optionnel) : chaque SMS est alors planifie une fois par
     * numero renseigne (une ligne NotificateurSms distincte chacun - aucune contrainte d'unicite ne s'y
     * oppose). Retourne le nombre total de SMS planifies (tous numeros confondus).
     */
    public int scheduleContractReminders(Long contractId, String phone, String phone2, String policyNumber, String duree, LocalDate dateEcheance) {
        List<String> destinataires = distinctPhones(phone, phone2);
        if (destinataires.isEmpty() || dateEcheance == null) {
            log.warn("Impossible de planifier les rappels SMS pour le contrat {} (telephone ou date d'échéance manquant)", policyNumber);
            return 0;
        }

        Contract contract = contractId == null ? null : contractRepository.getReferenceById(contractId);

        int months = parseMonths(duree);
        List<Integer> avisOffsets = months == 1 ? List.of(5) : List.of(45, 15, 5);
        LocalDate today = LocalDate.now();

        int count = 0;
        for (String destinataire : destinataires) {
            for (int offsetDays : avisOffsets) {
                LocalDate sendDate = dateEcheance.minusDays(offsetDays);
                if (sendDate.isBefore(today)) {
                    // La date de rappel est deja passee (contrat cree tardivement) : on ne planifie pas un SMS qui ne serait jamais envoye.
                    continue;
                }
                String message = String.format(
                        "Cher(e) assure, votre contrat n %s arrivera a echeance le %s. Contactez-nous au 0700000011 ou 0700000012 (whatsapp) pour le renouveler. AssurZen Assurance Votre sérénité, Notre engagement",
                        sansPrefixeContrat(policyNumber), dateEcheance);
                scheduleRow(contract, destinataire, message, TypeMessageSms.avis_echeance, sendDate);
                count++;
            }

            String finMessage = String.format(
                    "Cher(e) assure, votre contrat n %s a pris fin ce jour, %s. AssurZen Assurance Votre sérénité, Notre engagement",
                    policyNumber, dateEcheance);
            scheduleRow(contract, destinataire, finMessage, TypeMessageSms.fin_contrat, dateEcheance);
            count++;
        }

        return count;
    }


    /**
     * Planifie un SMS de relance (J-3) pour chaque echeance en attente d'un echeancier de contrat
     * fractionne. Meme logique multi-numero que scheduleContractReminders (phone2 optionnel).
     * Retourne le nombre total de SMS planifies (tous numeros confondus).
     */
    public int scheduleEcheanceReminders(Contract contract, String phone, String phone2, String policyNumber, List<Echeance> echeances) {
        List<String> destinataires = distinctPhones(phone, phone2);
        if (destinataires.isEmpty() || echeances == null || echeances.isEmpty()) {
            log.warn("Impossible de planifier les relances d'echeance pour le contrat {} (telephone ou echeancier manquant)", policyNumber);
            return 0;
        }

        LocalDate today = LocalDate.now();
        int count = 0;
        for (Echeance echeance : echeances) {
            if (echeance.getStatut() != StatutEcheance.EN_ATTENTE) {
                continue;
            }
            LocalDate sendDate = echeance.getDateEcheance().minusDays(3);
            if (sendDate.isBefore(today)) {
                continue;
            }
            String message = String.format(
                    "Cher(e) assure, l'échéance n %d de votre contrat n %s (%d FCFA) arrive le %s. Merci de procéder au paiement. AssurZen Assurance Votre sérénité, Notre engagement",
                    echeance.getNumeroEcheance(), policyNumber, echeance.getMontant(), echeance.getDateEcheance());
            for (String destinataire : destinataires) {
                scheduleRow(contract, destinataire, message, TypeMessageSms.relance_echeance_paiement, sendDate);
                count++;
            }
        }
        return count;
    }

    /** Numero de contrat affiche dans le SMS d'avis d'echeance sans le prefixe technique "CTR"
     * (CTR2026000028 -> 2026000028), a la demande metier du 2026-09-24. */
    private static String sansPrefixeContrat(String policyNumber) {
        String prefixe = CodePrefix.CONTRAT.getValue();
        return policyNumber != null && policyNumber.startsWith(prefixe)
                ? policyNumber.substring(prefixe.length())
                : policyNumber;
    }

    /** Filtre les numeros vides/blancs et deduplique (evite d'envoyer 2x le meme SMS si phone==phone2). */
    private List<String> distinctPhones(String... phones) {
        return Arrays.stream(phones)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * Liste les messages SMS (programmes et deja envoyes) lies a un contrat, tries par date d'envoi.
     */
    public List<NotificateurSmsResponse> getMessagesForContract(Long contractId) {
        return notificateurSmsRepository.findByContractIdOrderByDateEnvoiAsc(contractId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Meme mapping que getMessagesForContract(), en lot - reutilise par FleetService pour l'API
     * "avis d'echeance par flotte" (GET /fleets/{id}/echeances), qui regroupe ensuite par contrat. */
    public List<NotificateurSmsResponse> getMessagesForContracts(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return List.of();
        }
        return notificateurSmsRepository.findByContractIdInOrderByDateEnvoiAsc(contractIds).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * A appeler avant de supprimer un contrat (ContractsService.delete()) : ces rappels
     * (programmes ou deja envoyes) referencent le contrat via une FK sans cascade en base
     * (fk_notificateur_sms_contract) - sans ce nettoyage prealable, la suppression du contrat
     * echoue en DataIntegrityViolationException (500 brut, bug reel constate le 2026-09-14 lors
     * d'un nettoyage de donnees de test). Simple historique d'envoi de SMS, pas une donnee
     * metier/comptable a preserver independamment du contrat - suppression definitive assumee.
     */
    public void deleteRemindersForContract(Long contractId) {
        notificateurSmsRepository.deleteByContractId(contractId);
    }

    /**
     * Confirmation du versement d'une demande de paiement (avance partenaire ou demande de commercial),
     * 2026-09-26 - texte propre : le modele "commission payee" parle de contrats, une avance n'en a pas.
     * Redirige vers le numero de test en mode test (type commission_*, cf. SMS_REDIRIGES_EN_MODE_TEST).
     */
    public boolean sendDemandePaiementVerseeSms(String phone, long montant, String reference, boolean commercial) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de versement de la demande {} non envoye", reference);
            return false;
        }
        String message = String.format("AssurZen : votre demande de paiement de %s FCFA a ete versee sur votre compte Wave. Ref : %s.",
                CommissionMontants.formater(montant), reference);
        return sendImmediate(phone, message, commercial ? TypeMessageSms.commission_commerciale_payee : TypeMessageSms.commission_payee);
    }

    /**
     * Envoie immediatement un SMS confirmant l'enregistrement d'une declaration de sinistre.
     */
    public boolean sendClaimDeclaredSms(String phone, String numeroSinistre) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de declaration de sinistre non envoye pour {}", numeroSinistre);
            return false;
        }

        String message = String.format(
                "Cher(e) assure, votre déclaration de sinistre n %s a bien ete enregistrée. Nous reviendrons vers vous rapidement. AssurZen Assurance Votre sérénité, Notre engagement",
                numeroSinistre);
        return sendImmediate(phone, message, TypeMessageSms.sinistre_declare);
    }

    /**
     * Envoie immediatement un SMS notifiant l'assure d'une nouvelle etape du suivi de son sinistre.
     */
    public boolean sendClaimStatusUpdateSms(String phone, String numeroSinistre, SinistreStatus status, String commentaire) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de suivi de sinistre non envoye pour {}", numeroSinistre);
            return false;
        }

        String message = String.format(
                "Sinistre n %s : nouveau statut = %s.%s",
                numeroSinistre, status, StringUtils.hasText(commentaire) ? " " + commentaire : "");
        return sendImmediate(phone, message, TypeMessageSms.sinistre_suivi);
    }

    /**
     * Envoie immediatement un SMS contenant un code de verification OTP.
     */
    public boolean sendOtpSms(String phone, String code, long validityMinutes) {
        return sendOtpSms(phone, code, validityMinutes, false);
    }

    /**
     * Variante mobile (2026-11-02) : texte propre a l'application AssurZen Mobile. Le portail back-office garde son
     * texte historique. Le delai affiche suit OTP_CODE_VALIDITY_MINUTES.
     */
    public boolean sendOtpSms(String phone, String code, long validityMinutes, boolean mobile) {
        if (mobile) {
            if (!StringUtils.hasText(phone)) {
                log.warn("Aucun numero de telephone fourni, SMS OTP mobile non envoye");
                return false;
            }
            String texte = String.format(
                    "Votre code de vérification AssurZen Mobile est %s. Il expire dans %d minutes. Ne le partagez avec personne.",
                    code, validityMinutes);
            return sendImmediate(phone, texte, TypeMessageSms.otp_verification);
        }
        return envoyerOtpPortail(phone, code, validityMinutes);
    }

    private boolean envoyerOtpPortail(String phone, String code, long validityMinutes) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS OTP non envoye");
            return false;
        }

        String message = String.format(
                "Votre code de verification CIP AssurZen est %s. Il expire dans %d minutes. Ne le partagez avec personne.",
                code, validityMinutes);
        return sendImmediate(phone, message, TypeMessageSms.otp_verification);
    }

    /**
     * Envoie immediatement un SMS informant l'assure qu'une enquete de satisfaction lui a ete envoyee.
     */
    public boolean sendSatisfactionSurveySms(String phone, String codeEnquete) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS d'enquete de satisfaction non envoye pour {}", codeEnquete);
            return false;
        }

        String message = String.format(
                "Cher(e) assure, une enquete de satisfaction (n %s) vous a ete envoyée concernant votre experience avec nous. Votre avis compte pour nous. AssurZen Assurance Votre sérénité, Notre engagement",
                codeEnquete);
        return sendImmediate(phone, message, TypeMessageSms.enquete_satisfaction);
    }

    /**
     * Envoie immediatement un SMS confirmant l'enregistrement d'une reclamation.
     */
    public boolean sendReclamationCreatedSms(String phone, String numeroReclamation) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de creation de reclamation non envoye pour {}", numeroReclamation);
            return false;
        }

        String message = String.format(
                "Cher(e) assure, votre reclamation n %s a bien été enregistrée. Nous reviendrons vers vous rapidement. AssurZen Assurance Votre sérénité, Notre engagement",
                numeroReclamation);
        return sendImmediate(phone, message, TypeMessageSms.reclamation_creee);
    }

    /**
     * Envoie immediatement un SMS notifiant l'assure d'une etape de suivi (affectation, changement de
     * statut, cloture) d'une reclamation. Le detail est libre, integre tel quel dans le message.
     */
    public boolean sendReclamationSuiviSms(String phone, String numeroReclamation, String detail) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de suivi de reclamation non envoye pour {}", numeroReclamation);
            return false;
        }

        String message = String.format("Reclamation n %s : %s", numeroReclamation, detail);
        return sendImmediate(phone, message, TypeMessageSms.reclamation_suivi);
    }

    /**
     * Envoie immediatement un SMS ponctuel resolu depuis un modele de communication (canal SMS,
     * statut ACTIF) identifie par son code - reutilise TemplateVariableResolver.resoudreAvecValeurs
     * (deja utilise pour l'apercu admin, jamais jusqu'ici pour un envoi reel) plutot que de coder un
     * message en dur en Java, contrairement aux autres methodes de cette classe. Introduit pour le
     * contrat "Creation de comptes utilisateurs" (SMS de bienvenue partenaire/back-office).
     */
    public boolean sendFromTemplate(String phone, String templateCode, Map<String, String> variables, TypeMessageSms type) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS depuis le modele {} non envoye", templateCode);
            return false;
        }

        CommunicationTemplate template = communicationTemplateRepository.findByCode(templateCode).orElse(null);
        if (template == null || template.getStatut() != TemplateStatut.ACTIF || !template.isActive()) {
            log.warn("Modele de communication {} introuvable ou inactif, SMS non envoye a {}", templateCode, phone);
            return false;
        }

        String message = templateVariableResolver.resoudreAvecValeurs(template.getContenu(), variables);
        return sendImmediate(phone, message, type);
    }

    private int parseMonths(String duree) {
        if (!StringUtils.hasText(duree)) {
            return -1;
        }
        Matcher matcher = MONTHS_PATTERN.matcher(duree);
        return matcher.find() ? Integer.parseInt(matcher.group()) : -1;
    }

    /**
     * Envoie immediatement un SMS confirmant a un partenaire qu'un pret vient de lui etre accorde
     * (cree cote sortie de fonds - avant meme decaissement).
     */
    public boolean sendLoanCreatedSms(String phone, String partnerName, String reference, Long amount, String currency) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, SMS de pret accorde non envoye pour {}", reference);
            return false;
        }

        String message = String.format(
                "Cher(e) %s, un pret de %d %s (reference %s) a ete enregistre pour vous. AssurZen Assurance Votre sérénité, Notre engagement",
                partnerName, amount, currency, reference);
        return sendImmediate(phone, message, TypeMessageSms.pret_accorde);
    }

    /**
     * Envoie immediatement un rappel d'echeance de pret a venir (J-10/J-5/J-1, declenche par
     * LoanReminderService a 10h).
     */
    public boolean sendLoanInstallmentUpcomingSms(String phone, String loanReference, Integer installmentNumber, Long amount, LocalDate dueDate, long joursAvant) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, rappel d'echeance de pret non envoye pour {}", loanReference);
            return false;
        }

        String message = String.format(
                "Rappel : l'echeance n %d de votre pret n %s (%d FCFA) arrive dans %d jour(s), le %s. AssurZen Assurance Votre sérénité, Notre engagement",
                installmentNumber, loanReference, amount, joursAvant, dueDate);
        return sendImmediate(phone, message, TypeMessageSms.echeance_pret_rappel);
    }

    /**
     * Envoie immediatement un rappel d'echeance de pret arrivant a echeance ce jour (declenche par
     * LoanReminderService a 9h).
     */
    public boolean sendLoanInstallmentDueTodaySms(String phone, String loanReference, Integer installmentNumber, Long amount) {
        if (!StringUtils.hasText(phone)) {
            log.warn("Aucun numero de telephone fourni, rappel d'echeance du jour non envoye pour {}", loanReference);
            return false;
        }

        String message = String.format(
                "L'echeance n %d de votre pret n %s (%d FCFA) arrive a echeance aujourd'hui. AssurZen Assurance Votre sérénité, Notre engagement",
                installmentNumber, loanReference, amount);
        return sendImmediate(phone, message, TypeMessageSms.echeance_pret_jour_j);
    }

    private boolean sendImmediate(String phone, String message, TypeMessageSms type) {
        if (modeTest != null && modeTest.isActif() && SMS_REDIRIGES_EN_MODE_TEST.contains(type)) {
            // Texte reel conserve (montant reel), prefixe du vrai destinataire ; envoye au numero de test.
            log.warn("[SYCAPAY] MODE TEST - SMS {} destine a {} redirige vers {}", type, phone, modeTest.getTelephone());
            message = "[TEST -> " + phone + "] " + message;
            phone = modeTest.getTelephone();
        }
        NotificateurSms sms = NotificateurSms.builder()
                .telephoneAssure(phone)
                .message(message)
                .typeMessage(type)
                .dateEnvoi(LocalDate.now())
                .statutEnvoi(StatutEnvoiSms.en_attente)
                .build();

        try {
            String response = providerService.send(sms);
            sms.setStatutEnvoi(StatutEnvoiSms.envoye);
            sms.setResponseProvider(response);
            notificateurSmsRepository.save(sms);
            return true;
        } catch (RuntimeException e) {
            log.error("Echec de l'envoi du SMS immediat a {}", phone, e);
            sms.setStatutEnvoi(StatutEnvoiSms.non_envoye);
            sms.setResponseProvider(e.getMessage());
            notificateurSmsRepository.save(sms);
            return false;
        }
    }

    private void scheduleRow(Contract contract, String phone, String message, TypeMessageSms type, LocalDate sendDate) {
        NotificateurSms sms = NotificateurSms.builder()
                .contract(contract)
                .telephoneAssure(phone)
                .message(message)
                .typeMessage(type)
                .dateEnvoi(sendDate)
                .statutEnvoi(StatutEnvoiSms.en_attente)
                .build();

        notificateurSmsRepository.save(sms);
    }

    private NotificateurSmsResponse toResponse(NotificateurSms sms) {
        return new NotificateurSmsResponse(
                sms.getId(),
                sms.getContract() == null ? null : sms.getContract().getId(),
                sms.getTelephoneAssure(),
                sms.getMessage(),
                sms.getTypeMessage(),
                sms.getDateEnvoi(),
                sms.getStatutEnvoi(),
                sms.getResponseProvider());
    }
}
