package ci.assurzen.dto;

/**
 * Commercial de la force de vente interne, tel que propose dans la liste "Commercial" de la
 * souscription (2026-10-02). Volontairement minimal - aucune donnee de remuneration - car expose a
 * tout utilisateur authentifie qui cree un contrat (contrairement a GET /commercials, reserve aux
 * administrateurs). userId = valeur attendue par Contract.salesRepId.
 */
public record CommercialOptionResponse(
        Long commercialId,
        Long userId,
        String nom,
        String prenoms,
        String employeeCode,
        String villeNom
) {
}
