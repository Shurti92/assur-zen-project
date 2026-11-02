/** Zones de livraison (référentiel ADMIN) — le "code" est la valeur choisie par l'utilisateur
 * dans zoneLivraison (demande) et zoneLivraison (simulation), ex. "ABIDJAN"/"HORS_ABIDJAN". */
export interface FraisLivraisonZoneRequest {
  code: string;
  libelle: string;
  montant: number;
}

export interface FraisLivraisonZoneResponse {
  id: number;
  code: string;
  libelle: string;
  montant: number;
  actif: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}
