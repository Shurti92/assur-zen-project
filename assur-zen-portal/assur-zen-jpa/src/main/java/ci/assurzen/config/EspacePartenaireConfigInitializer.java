package ci.assurzen.config;

import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.ConfigurationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Reglages de l'Espace Partenaire (2026-10-04), crees au demarrage s'ils n'existent pas pour etre
 * REGLABLES DEPUIS LE BACK-OFFICE (module Configuration) dans chaque environnement, sans script SQL.
 * Une valeur deja presente n'est jamais ecrasee (meme principe que SycaPayBalanceReportConfigInitializer).
 * - Contact support affiche par l'Espace Partenaire (GET /me/support-contact) : VIDE par defaut, aucune
 *   coordonnee n'est inventee ;
 * - rappels automatiques (echeances, primes a reverser, documents) : DESACTIVES par defaut, ce sont de
 *   nouveaux envois aux partenaires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EspacePartenaireConfigInitializer {

    private static final String SYSTEME = "system";

    private final DynamicConfigurationRepository repository;
    private final ConfigurationCacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialiser() {
        creerSiAbsente("SUPPORT_NOM", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : nom affiche (ex. Service partenaires AssurZen). Vide = carte masquee.", ConfigCategory.GENERAL, 10);
        creerSiAbsente("SUPPORT_FONCTION", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : fonction ou service (facultatif).", ConfigCategory.GENERAL, 11);
        creerSiAbsente("SUPPORT_TELEPHONE", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : telephone.", ConfigCategory.GENERAL, 12);
        creerSiAbsente("SUPPORT_EMAIL", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : e-mail.", ConfigCategory.GENERAL, 13);
        creerSiAbsente("SUPPORT_WHATSAPP", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : numero WhatsApp (facultatif).", ConfigCategory.GENERAL, 14);
        creerSiAbsente("SUPPORT_TELEPHONE_2", "", ConfigValueType.STRING, "Application mobile : second telephone d'assistance (facultatif). Vide = non communique.", ConfigCategory.GENERAL, 16);
        creerSiAbsente("SUPPORT_ADRESSE", "", ConfigValueType.STRING, "Application mobile : adresse de l'agence (facultatif). Vide = rubrique masquee.", ConfigCategory.GENERAL, 17);
        creerSiAbsente("SUPPORT_DISPONIBILITE", "", ConfigValueType.STRING, "Application mobile : phrase de disponibilite du support affichee au client (ex. Un conseiller vous repond du lundi au samedi). Vide = non communique.", ConfigCategory.GENERAL, 18);
        creerSiAbsente("SUPPORT_URGENCES", "", ConfigValueType.STRING, "Application mobile : numeros d'urgence, au format Libelle:numero;Libelle:numero (ex. SAMU:185;Pompiers:180). Vide = rubrique masquee.", ConfigCategory.GENERAL, 19);
        creerSiAbsente("CALLBACK_EXPECTED_MINUTES", "", ConfigValueType.STRING, "Application mobile : delai de rappel annonce au client, en minutes (ex. 30). Vide = aucun engagement affiche.", ConfigCategory.GENERAL, 20);
        creerSiAbsente("SUPPORT_RESPONSE_MINUTES", "", ConfigValueType.STRING, "Application mobile : delai de reponse du support annonce au client, en minutes (ex. 120). Vide = aucun engagement affiche.", ConfigCategory.GENERAL, 21);
        creerSiAbsente("CLAIM_HANDLING_HOURS", "", ConfigValueType.STRING, "Application mobile : delai de prise en charge d'un sinistre annonce au client, en heures (ex. 24). Vide = aucun engagement affiche.", ConfigCategory.GENERAL, 22);
        creerSiAbsente("CONTRACT_REQUEST_RESPONSE_HOURS", "", ConfigValueType.STRING, "Application mobile : delai de reponse a une demande de modification ou de resiliation de contrat, en heures (ex. 24). Vide = aucun engagement affiche.", ConfigCategory.GENERAL, 23);
        creerSiAbsente("QUOTE_MOTO_BRANDS", "Yamaha, Honda, Suzuki, TVS, Bajaj, Haojue, Sanya, Apsonic, Piaggio, Kymco, Jincheng, Dayun, Lifan, Kawasaki, Hero, Loncin, Zongshen, Qingqi, Jialing, Haojin, Vespa, SYM, Peugeot, Benelli, KTM, BMW, Ducati, Triumph, Royal Enfield, Harley-Davidson, Aprilia, Mahindra, Daelim, Hyosung, Keeway, CFMoto, Sunl, Foton, Haojing, Dayang, Senke, Sinski, Wuyang, Master, Qianjiang, Skygo, Bashan, Fekon, Kenbo, Ranger, Sunrise, Kiden, Zontes, Voge, QJ Motor, SWM, Moto Morini, Kove, Benda, Husqvarna, Indian, Yadea, NIU, Super Soco, Tailg, Ather, Ola Electric, Hero Electric, Autre", ConfigValueType.STRING, "Devis en ligne moto : marques proposees dans la liste deroulante, separees par des virgules. Vide = saisie libre de la marque.", ConfigCategory.GENERAL, 28);
        creerSiAbsente("QUOTE_VTC_BRANDS", "Toyota, Hyundai, Kia, Suzuki, Nissan, Renault, Peugeot, Dacia, Volkswagen, Honda, Mazda, Mitsubishi, Ford, Chevrolet, Citroen, Fiat, Opel, Skoda, Mercedes-Benz, BMW, Audi, Lexus, Isuzu, Datsun, Chery, Geely, BYD, Changan, Haval, MG, JAC, Tata, Mahindra, Maruti Suzuki, Acura, Alfa Romeo, Aston Martin, Bentley, Bugatti, Buick, Cadillac, Chrysler, Cupra, Daewoo, Daihatsu, Dodge, DS Automobiles, Ferrari, GMC, Great Wall, Hummer, Infiniti, Jaguar, Jeep, Lada, Lamborghini, Lancia, Land Rover, Lincoln, Lotus, Maserati, Maybach, McLaren, Mini, Mercury, Pontiac, Porsche, Ram, Rolls-Royce, Rover, Saab, Seat, Smart, SsangYong, Subaru, Tesla, Volvo, Zotye, Dongfeng, FAW, Foton, Gonow, GAC, Hongqi, Lynk & Co, Nio, Ora, Wuling, Xpeng, Zeekr, Baic, Brilliance, Haima, Jmc, King Long, Leapmotor, Lifan, SWM, Tank, Yutong, Ashok Leyland, Force Motors, Hindustan, Premier, Proton, Perodua, Iveco, Man, Scania, Opel Vauxhall, Vauxhall, Holden, Genesis, Polestar, Dfsk, Kaiyi, Jetour, Omoda, Jaecoo, Exeed, Soueast, Maxus, Ldv, Autre", ConfigValueType.STRING, "Devis en ligne VTC : marques de vehicules proposees dans la liste deroulante, separees par des virgules. Vide = saisie libre.", ConfigCategory.GENERAL, 29);
        creerSiAbsente("QUOTE_VTC_PLATFORMS", "Yango, Heetch, Uber, Bolt, inDrive, Autre", ConfigValueType.STRING, "Devis en ligne VTC : plateformes de transport proposees, separees par des virgules. Vide = saisie libre.", ConfigCategory.GENERAL, 30);
        creerSiAbsente("QUOTE_VTC_MAX_DISCOUNT_PERCENT", "10", ConfigValueType.STRING, "Devis en ligne VTC : remise maximale accordee par le back-office, en pourcentage de la prime nette (0 = aucune remise).", ConfigCategory.GENERAL, 31);
        creerSiAbsente("QUOTE_EMAIL_FROM", "customer@assurzen.ci", ConfigValueType.STRING, "Devis en ligne : adresse d'expedition des e-mails de devis (le compte SMTP configure doit etre autorise a envoyer pour cette adresse).", ConfigCategory.GENERAL, 34);
        creerSiAbsente("QUOTE_EMAIL_LEGAL_TEXT", "Ce document est une proposition d'assurance établie à partir des informations que vous avez déclarées. Il ne constitue pas un contrat d'assurance : la couverture ne prend effet qu'après acceptation du devis, paiement de la prime et émission de l'attestation. Les garanties sont soumises aux conditions générales et aux exclusions du contrat.", ConfigValueType.STRING, "Devis en ligne : mention legale affichee dans l'e-mail et le PDF d'un devis.", ConfigCategory.GENERAL, 32);
        creerSiAbsente("QUOTE_EMAIL_FOOTER", "Une offre AssurZen Côte d'Ivoire\nBesoin d'aide ? Répondez à cet e-mail ou appelez le service client.", ConfigValueType.STRING, "Devis en ligne : pied de page de l'e-mail et du PDF d'un devis.", ConfigCategory.GENERAL, 33);
        creerSiAbsente("QUOTE_VALIDITY_DAYS", "30", ConfigValueType.STRING, "Devis en ligne : duree de validite d'un devis, en jours (defaut 30).", ConfigCategory.GENERAL, 27);
        creerSiAbsente("PUSH_ENABLED", "false", ConfigValueType.STRING, "Application mobile : envoi reel des notifications push (true ou false). Necessite le compte de service Firebase (variable FIREBASE_CREDENTIALS_PATH). Modifiable sans redemarrage.", ConfigCategory.GENERAL, 35);
        creerSiAbsente("LEGAL_TERMS_URL", "", ConfigValueType.STRING, "Application mobile : lien des conditions generales d'utilisation. Vide = lien masque.", ConfigCategory.GENERAL, 30);
        creerSiAbsente("LEGAL_PRIVACY_URL", "", ConfigValueType.STRING, "Application mobile : lien de la politique de confidentialite. Vide = lien masque.", ConfigCategory.GENERAL, 31);
        creerSiAbsente("LEGAL_UPDATED_AT", "", ConfigValueType.STRING, "Application mobile : date de derniere mise a jour des documents legaux (texte libre, ex. 2026-11-03).", ConfigCategory.GENERAL, 32);
        creerSiAbsente("SUPPORT_TOPICS", "CONTRACT:Contrat;PAYMENT:Paiement;CLAIM:Sinistre;OTHER:Autre", ConfigValueType.STRING, "Application mobile : sujets des demandes d'aide, au format CODE:Libelle;CODE:Libelle. Vide = aucune demande possible.", ConfigCategory.GENERAL, 33);
        creerSiAbsente("NOTIFICATION_RETENTION_MONTHS", "12", ConfigValueType.STRING, "Application mobile : duree de conservation des notifications, en mois (defaut 12).", ConfigCategory.GENERAL, 34);
        creerSiAbsente("SUPPORT_HORAIRES", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : horaires (ex. Lun-Ven 8h-17h).", ConfigCategory.GENERAL, 15);
        creerSiAbsente("NOTIFICATIONS_RAPPELS_AUTO_ENABLED", "false", ConfigValueType.BOOLEAN,
                "Rappels automatiques de l'Espace Partenaire, chaque jour a 8h (in-app + push selon preferences, jamais de SMS) : echeance de contrat a J-30 et J-7, prime non reversee, document expirant ou expire.",
                ConfigCategory.NOTIFICATION, 20);
        creerSiAbsente("PRIME_NON_REVERSEE_RAPPEL_JOURS", "7", ConfigValueType.INTEGER, "MIN:1;MAX:365",
                "Rappel au partenaire d'une prime encaissee non reversee, ce nombre de jours apres la signature du contrat (une seule fois).",
                ConfigCategory.NOTIFICATION, 21);
    }

    private void creerSiAbsente(String cle, String valeur, ConfigValueType type, String description, ConfigCategory categorie, int ordre) {
        creerSiAbsente(cle, valeur, type, null, description, categorie, ordre);
    }

    private void creerSiAbsente(String cle, String valeur, ConfigValueType type, String regle, String description,
                                ConfigCategory categorie, int ordre) {
        if (repository.findByConfigKey(cle).isPresent()) {
            return;
        }
        DynamicConfiguration configuration = DynamicConfiguration.builder()
                .configKey(cle)
                .configValue(valeur)
                .defaultValue(valeur)
                .valueType(type)
                .validationRule(regle)
                .description(description)
                .module("ESPACE_PARTENAIRE")
                .category(categorie)
                .editable(true)
                .displayOrder(ordre)
                .build();
        configuration.setCreatedBy(SYSTEME);
        configuration.setUpdatedBy(SYSTEME);
        try {
            cacheService.put(cle, repository.saveAndFlush(configuration));
            log.info("[ESPACE-PARTENAIRE] reglage {} cree, modifiable depuis le back-office", cle);
        } catch (DataIntegrityViolationException e) {
            log.info("[ESPACE-PARTENAIRE] reglage {} deja cree par une autre instance", cle);
        }
    }
}
