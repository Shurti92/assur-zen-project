package ci.assurzen.events;

import ci.assurzen.entities.TypeMessageSms;

import java.util.Map;

/**
 * Publie par PartenaireNotificationService.notifier() quand le partenaire a au moins un appareil abonne
 * au push (2026-09-26). Traite APRES commit, en asynchrone (PartenaireNotificationPushListener) : un echec
 * d'envoi push ne peut jamais annuler la notification ni l'operation metier qui l'a produite.
 * smsDeSecours : le SMS n'a pas ete envoye car le push devait le remplacer - il est envoye si AUCUN
 * appareil n'est atteint (telephone/modele/variables pour cet envoi de secours).
 */
public record PartenaireNotificationCreeeEvent(
        Long notificationId,
        Long userId,
        boolean smsDeSecours,
        String telephone,
        String smsTemplateCode,
        Map<String, String> smsVariables,
        TypeMessageSms typeSms
) {
}
