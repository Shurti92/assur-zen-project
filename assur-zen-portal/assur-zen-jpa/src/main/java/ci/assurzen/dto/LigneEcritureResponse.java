package ci.assurzen.dto;

public record LigneEcritureResponse(
        Long id,
        Long compteId,
        String compteCode,
        String compteLibelle,
        Long montantDebit,
        Long montantCredit,
        String libelle
) {
}
