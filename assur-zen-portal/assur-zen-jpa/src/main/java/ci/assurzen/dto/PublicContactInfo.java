package ci.assurzen.dto;

import java.util.List;

/**
 * Coordonnees affichees par l'application mobile (2026-11-03), lues dans la configuration du back-office (cles SUPPORT_*).
 * Un champ null ou une liste vide = non communique : l'application masque la rubrique.
 */
public record PublicContactInfo(
        List<Entry> phones,
        String whatsapp,
        String email,
        String address,
        /** Texte libre, tel que saisi dans le back-office (ex. Lun-Sam 8h-17h). */
        String hours,
        List<Entry> emergencyNumbers,
        String supportAvailability
) {
    public record Entry(String label, String number) {
    }
}
