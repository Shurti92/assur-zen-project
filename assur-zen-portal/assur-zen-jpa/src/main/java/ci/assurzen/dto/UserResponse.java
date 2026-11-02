package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "User")
public record UserResponse(
        String id,
        String fullName,
        String email,
        String role,
        String sector,
        String avatarUrl,
        boolean mustChangePassword,
        String bio,
        String facebookUrl,
        String twitterUrl,
        String linkedinUrl,
        String instagramUrl,
        String country,
        String city,
        String state,
        String postalCode,
        String taxId,
        boolean twoFactorEnabled,
        String langueCode,
        /** Non-null uniquement si le profil connecte est celui d'un partenaire (compte cree via
         * Partenaire.typeProfile.declencheCreationCompte, cf. PartenaireService) - permet au frontend
         * partenaire d'appeler GET /partenaires/{id} sans devoir le chercher autrement. */
        Long partenaireId,
        /** Type Profil du compte connecte (User.typeProfile), ecrit via UserProfileRequest/UserProfileUpdateRequest
         * mais jamais relu jusqu'ici - permet au frontend de restreindre les menus Parametrage/Administration
         * selon le Type Profil (ex. CO-FOUNDER), sans appel reseau supplementaire (contrat "Exposer le Type
         * Profil de l'utilisateur connecte", 2026-08-18). Null si aucun Type Profil n'est assigne. */
        Long typeProfileId
) {
}
