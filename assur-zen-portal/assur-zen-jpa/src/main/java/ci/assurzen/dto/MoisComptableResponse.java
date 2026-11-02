package ci.assurzen.dto;

import java.time.LocalDate;

public record MoisComptableResponse(
        Long id,
        Integer numero,
        String libelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean cloture
) {
}
