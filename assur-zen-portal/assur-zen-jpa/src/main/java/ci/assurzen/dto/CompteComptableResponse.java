package ci.assurzen.dto;

import ci.assurzen.entities.TypeCompte;

public record CompteComptableResponse(
        Long id,
        String code,
        String libelle,
        TypeCompte type,
        boolean active
) {
}
