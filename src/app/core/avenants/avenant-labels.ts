import { AvenantCategorie, AvenantType } from './models/avenant.model';

/** Libellés lisibles pour les 36 valeurs de AvenantType — le backend ne fournit pas
 * de libellé, seule la valeur d'enum brute. */
export const AVENANT_TYPE_LABEL: Record<AvenantType, string> = {
  MODIFICATION_ASSURE: "Modification de l'assuré",
  MODIFICATION_SOUSCRIPTEUR: 'Modification du souscripteur',
  MODIFICATION_COORDONNEES: 'Modification des coordonnées',
  MODIFICATION_ADRESSE: "Modification d'adresse",
  MODIFICATION_BENEFICIAIRE: 'Modification du bénéficiaire',
  CORRECTION_INFORMATION: "Correction d'information",
  CHANGEMENT_INTERMEDIAIRE: "Changement d'intermédiaire",
  CHANGEMENT_MODE_PAIEMENT: 'Changement de mode de paiement',
  CHANGEMENT_FREQUENCE_PAIEMENT: 'Changement de fréquence de paiement',
  MODIFICATION_RISQUE: 'Modification du risque',
  MODIFICATION_BIEN_ASSURE: 'Modification du bien assuré',
  REMPLACEMENT_BIEN: 'Remplacement de bien',
  AJOUT_BIEN: 'Ajout de bien',
  RETRAIT_BIEN: 'Retrait de bien',
  AJOUT_GARANTIE: 'Ajout de garantie',
  SUPPRESSION_GARANTIE: 'Suppression de garantie',
  MODIFICATION_GARANTIE: 'Modification de garantie',
  MODIFICATION_FRANCHISE: 'Modification de franchise',
  MODIFICATION_PLAFOND: 'Modification de plafond',
  MODIFICATION_USAGE: "Modification d'usage",
  MODIFICATION_ZONE: 'Modification de zone',
  MODIFICATION_CONDUCTEUR: 'Modification de conducteur',
  MODIFICATION_CARACTERISTIQUE: 'Modification de caractéristique',
  EXTENSION_TERRITORIALE: 'Extension territoriale',
  REDUCTION_TERRITORIALE: 'Réduction territoriale',
  MODIFICATION_PRIME: 'Modification de prime',
  SURPRIME: 'Surprime',
  REMISE: 'Remise',
  ANNULATION_REMISE: 'Annulation de remise',
  PRIME_COMPLEMENTAIRE: 'Prime complémentaire',
  REDUCTION_PRIME: 'Réduction de prime',
  REMBOURSEMENT_PRIME: 'Remboursement de prime',
  REGULARISATION_PRIME: 'Régularisation de prime',
  MODIFICATION_TAXE: 'Modification de taxe',
  REGULARISATION_FINANCIERE: 'Régularisation financière',
  ANNULATION_OPERATION_FINANCIERE: "Annulation d'opération financière",
  TRANSFERT_CREDIT: 'Transfert de crédit',
};

/** Ordre d'affichage dans le <select> du formulaire de création — groupé par
 * catégorie métier plutôt que par ordre alphabétique brut, pour rester repérable
 * malgré les 36 valeurs. Purement indicatif : le backend recalcule `categorie`
 * lui-même à partir du type choisi. */
export const AVENANT_TYPE_GROUPS: { label: string; types: AvenantType[] }[] = [
  {
    label: 'Administratif',
    types: [
      'MODIFICATION_ASSURE',
      'MODIFICATION_SOUSCRIPTEUR',
      'MODIFICATION_COORDONNEES',
      'MODIFICATION_ADRESSE',
      'MODIFICATION_BENEFICIAIRE',
      'CORRECTION_INFORMATION',
      'CHANGEMENT_INTERMEDIAIRE',
      'CHANGEMENT_MODE_PAIEMENT',
      'CHANGEMENT_FREQUENCE_PAIEMENT',
    ],
  },
  {
    label: 'Technique',
    types: [
      'MODIFICATION_RISQUE',
      'MODIFICATION_BIEN_ASSURE',
      'REMPLACEMENT_BIEN',
      'AJOUT_BIEN',
      'RETRAIT_BIEN',
      'AJOUT_GARANTIE',
      'SUPPRESSION_GARANTIE',
      'MODIFICATION_GARANTIE',
      'MODIFICATION_FRANCHISE',
      'MODIFICATION_PLAFOND',
      'MODIFICATION_USAGE',
      'MODIFICATION_ZONE',
      'MODIFICATION_CONDUCTEUR',
      'MODIFICATION_CARACTERISTIQUE',
      'EXTENSION_TERRITORIALE',
      'REDUCTION_TERRITORIALE',
    ],
  },
  {
    label: 'Financier',
    types: [
      'MODIFICATION_PRIME',
      'SURPRIME',
      'REMISE',
      'ANNULATION_REMISE',
      'PRIME_COMPLEMENTAIRE',
      'REDUCTION_PRIME',
      'REMBOURSEMENT_PRIME',
      'REGULARISATION_PRIME',
      'MODIFICATION_TAXE',
      'REGULARISATION_FINANCIERE',
      'ANNULATION_OPERATION_FINANCIERE',
      'TRANSFERT_CREDIT',
    ],
  },
];

export const AVENANT_CATEGORIE_LABEL: Record<AvenantCategorie, string> = {
  ADMINISTRATIF: 'Administratif',
  TECHNIQUE: 'Technique',
  FINANCIER: 'Financier',
};

export const AVENANT_CATEGORIE_VARIANT: Record<AvenantCategorie, string> = {
  ADMINISTRATIF: 'info',
  TECHNIQUE: 'warning',
  FINANCIER: 'success',
};
