package ci.assurzen.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String roles,
        boolean userActive,
        Long profileId,
        String code,
        String firstname,
        String lastname,
        String phone,
        String role,
        String email,
        String sector,
        boolean profileActive,
        Long villePartenaireId,
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
        /** Present UNE SEULE FOIS, uniquement sur la reponse de create() quand genere par le backend - jamais sur les lectures ulterieures (GET). */
        String temporaryPassword,
        /** Present uniquement sur create() : confirme si le SMS de bienvenue est parti. */
        Boolean notificationEnvoyee,
        /** Type Profil du compte (User.typeProfile), ecrit via UserProfileRequest/UserProfileUpdateRequest
         * mais jamais relu jusqu'ici (meme gap que sur User/login, contrat "Exposer le Type Profil de
         * l'utilisateur connecte", 2026-08-18). Null si aucun Type Profil n'est assigne. */
        Long typeProfileId
) {
}
