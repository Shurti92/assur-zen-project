package ci.assurzen.entities;

public enum CanalEnvoi {
    SMS,
    EMAIL,
    /** Reserve : aucun expediteur reel branche pour l'instant, CampagneEnvoiService renvoie ECHEC explicite. */
    WHATSAPP,
    /** Reserve : aucun expediteur reel branche pour l'instant, CampagneEnvoiService renvoie ECHEC explicite. */
    PUSH,
    /** Reserve : aucun expediteur reel branche pour l'instant, CampagneEnvoiService renvoie ECHEC explicite. */
    PDF
}
