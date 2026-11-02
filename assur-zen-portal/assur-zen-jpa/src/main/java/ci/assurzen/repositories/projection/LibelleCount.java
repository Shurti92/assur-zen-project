package ci.assurzen.repositories.projection;

/** Projection Spring Data generique pour un GROUP BY "libelle -> comptage" (JPQL ou SQL natif,
 * fonctionne dans les deux cas tant que l'alias de colonne correspond au nom de la propriete). Reutilisee
 * par les repartitions des 3 dashboards KPI (type de demande, categorie, statut, ville, type client...). */
public interface LibelleCount {
    String getLibelle();
    Long getTotal();
}
