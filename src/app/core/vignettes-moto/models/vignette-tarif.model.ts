import { VignetteMotoCategorieAge, VignetteMotoCategorieCylindree } from './vignette-moto.model';

/** Grille tarifaire (référentiel ADMIN) — rien n'est pré-chargé en base, les 6 lignes doivent
 * être créées via ces mêmes endpoints après déploiement (voir le contrat backend). */
export interface TarifVignetteMotoRequest {
  categorieAge: VignetteMotoCategorieAge;
  categorieCylindree: VignetteMotoCategorieCylindree;
  montantVignette: number;
  fraisOperation: number;
  dateDebutValidite: string;
  dateFinValidite?: string;
}

export interface TarifVignetteMotoResponse {
  id: number;
  categorieAge: VignetteMotoCategorieAge;
  categorieCylindree: VignetteMotoCategorieCylindree;
  montantVignette: number;
  fraisOperation: number;
  dateDebutValidite: string;
  dateFinValidite: string | null;
  actif: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}
