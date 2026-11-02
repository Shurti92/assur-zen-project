export interface TypeProfileRequest {
  libelle: string;
  active: boolean;
  /** Quand actif, tout Partenaire d'affaire ayant ce Type Profil déclenche automatiquement
   * la création d'un compte de connexion mobile (username = téléphone) à l'enregistrement —
   * voir docs/api-contracts/creation-comptes-utilisateurs.md. Coché uniquement sur "REPRESENTANT". */
  declencheCreationCompte?: boolean;
}

export interface TypeProfileResponse extends TypeProfileRequest {
  id: number;
  creation: string;
  lastUpdate: string;
}
