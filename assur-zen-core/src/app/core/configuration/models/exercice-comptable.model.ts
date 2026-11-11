export interface ExerciceComptableRequest {
  libelle: string;
  dateDebut: string;
  dateFin: string;
  active: boolean;
  objectifGlobal: number;
}

export interface ExerciceComptableResponse extends ExerciceComptableRequest {
  id: number;
  cloture: boolean;
  /** Backend-computed sum of premiums on signed contracts for this exercice. */
  objectifRealiseGlobal: number;
  /** Backend-computed (objectifRealiseGlobal / objectifGlobal) * 100. */
  tauxRealisationGlobal: number;
  creation: string;
  lastUpdate: string;
  /** Généré automatiquement à la création de l'exercice (12 mois, Janvier..Décembre) — voir
   * docs/api-contracts (annonce backend du 2026-08-28). Toujours 12 entrées pour un exercice
   * calendaire, y compris les exercices déjà existants (backfillés). */
  mois: MoisComptableResponse[];
}

/** Clôture d'un mois : purement structurel/reporting à ce jour — ne bloque pas la saisie
 * d'écritures (seule la clôture de l'exercice annuel le fait). */
export interface MoisComptableResponse {
  id: number;
  numero: number;
  libelle: string;
  dateDebut: string;
  dateFin: string;
  cloture: boolean;
  /** Objectif de production du mois (FCFA) ; null = pas d'objectif. Absent sur une Gateway plus ancienne. */
  objectifMensuel?: number | null;
  /** Primes des contrats signés dans le mois (date de signature). Absent sur une Gateway plus ancienne. */
  realiseMensuel?: number;
}

/** Une ligne de l'enregistrement groupé des objectifs mensuels (montant null = effacer). */
export interface ObjectifMensuelItem {
  numero: number;
  montant: number | null;
}
