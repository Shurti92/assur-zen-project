package ci.assurzen.dto;

import ci.assurzen.entities.TypeClient;

public record InsuredResponse(
        Long id,
        String nom,
        String prenoms,
        String clientPhone,
        String clientCity,
        String clientEmail,
        TypeClient typeClient,
        String clientPhone2
) {
}
