package ci.assurzen.entities;

/**
 * Grande famille d'un avenant (TypeAvenant en precise le sous-type exact). Denormalisee sur Avenant
 * (en plus de type) pour permettre un filtrage/indexage direct sans devoir mapper chaque TypeAvenant
 * a sa categorie cote requete SQL.
 */
public enum CategorieAvenant {
    ADMINISTRATIF,
    TECHNIQUE,
    FINANCIER
}
