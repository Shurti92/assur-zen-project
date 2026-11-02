package ci.assurzen.entities;

/** Etat de l'envoi Web Push d'une notification (2026-09-26). NONE : aucun appareil abonne (ou push
 * inactif) au moment de la notification ; PENDING : envoi en attente (asynchrone, apres commit) ;
 * SENT : au moins un appareil atteint ; FAILED : aucun appareil atteint. */
public enum PushStatus {
    NONE,
    PENDING,
    SENT,
    FAILED
}
