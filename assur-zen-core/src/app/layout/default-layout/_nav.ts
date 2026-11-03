import { INavData } from '@coreui/angular';

const baseNavItems: INavData[] = [
  {
    name: 'nav.dashboard',
    url: '/dashboard',
    iconComponent: { name: 'grid' },
  },
  {
    name: 'nav.section.components',
    title: true,
  },
  {
    name: 'nav.souscription.title',
    url: '/souscription',
    iconComponent: { name: 'book' },
    children: [
      {
        name: 'nav.souscription.enregistrerContrat',
        url: '/souscription/souscription-produit-assurance',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.souscription.televerserFichier',
        url: '/souscription/televerser-fichier-souscription',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    name: 'nav.contrats.title',
    url: '/contrats',
    iconComponent: { name: 'targetCheck' },
    children: [
      {
        name: 'nav.contrats.liste',
        url: '/contrats/liste-des-contrats',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.echeanceProche',
        url: '/contrats/a-echeance-proche',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.fractionnes',
        url: '/contrats/fractionnes',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.echus',
        url: '/contrats/echus',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.nonRenouveles',
        url: '/contrats/non-renouveles',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.cycleDeVie',
        url: '/contrats/cycle-de-vie-contrat',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.messagesSms',
        url: '/contrats/liste-des-messages-lie-contrat',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.primeNonReversee',
        url: '/contrats/prime-non-reversee',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.contrats.commissions',
        url: '/commissions',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    name: 'nav.sinistres.title',
    url: '/sinistres',
    iconComponent: { name: 'triangleAlert' },
    children: [
      {
        name: 'nav.sinistres.declarer',
        url: '/sinistres/declare-sinistre',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.sinistres.liste',
        url: '/sinistres/liste-sinistre',
        icon: 'nav-icon-bullet',
      },
      /*{
        name: 'Suivi sinistre',
        url: '/sinistres/suivi-sinistre',
        icon: 'nav-icon-bullet',
      },*/
    ],
  },
  {
    name: 'nav.assures',
    iconComponent: { name: 'person' },
    url: '/assures',
  },
  {
    // Devis en ligne (produit Moto). Les devis sont ouverts à ADMIN et COMMERCIAL ; la grille
    // tarifaire est ADMIN seulement (voir getNavItems).
    name: 'nav.devis.title',
    url: '/devis',
    iconComponent: { name: 'cilCalculator' },
    children: [
      {
        name: 'nav.devis.liste',
        url: '/devis/liste',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.devis.grille',
        url: '/devis/grille-tarifaire',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.devis.marques',
        url: '/devis/marques',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.devis.vtc',
        url: '/devis/vtc-parametrage',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    // Administration de l'application mobile (ADMIN). Les écrans Catalogue et Demandes mobile
    // s'ajouteront ici au fur et à mesure.
    name: 'nav.mobile.title',
    url: '/mobile',
    iconComponent: { name: 'cilMobile' },
    children: [
      {
        name: 'nav.mobile.comptes',
        url: '/mobile/comptes',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.mobile.catalogue',
        url: '/mobile/catalogue',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.mobile.demandes',
        url: '/mobile/demandes',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.mobile.faq',
        url: '/mobile/faq',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.mobile.avis',
        url: '/mobile/avis',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    name: 'nav.gestionFlotte',
    iconComponent: { name: 'cilCarAlt' },
    url: '/gestion-de-flotte',
  },
  {
    name: 'nav.ged',
    iconComponent: { name: 'cilFile' },
    url: '/ged',
  },
  {
    name: 'nav.sortiesDeFonds',
    iconComponent: { name: 'cilCreditCard' },
    url: '/sorties-de-fonds',
  },
  {
    name: 'nav.sycapay.title',
    iconComponent: { name: 'cilDollar' },
    url: '/sycapay',
  },
  {
    name: 'nav.vignettesMoto',
    iconComponent: { name: 'cilTags' },
    url: '/vignettes-moto',
  },
  {
    name: 'nav.commissions',
    iconComponent: { name: 'coin' },
    url: '/commissions',
  },
  {
    name: 'nav.primesObjectif',
    iconComponent: { name: 'cilChartPie' },
    url: '/primes-objectif',
  },
  {
    name: 'nav.comptabilite.title',
    url: '/comptabilite',
    iconComponent: { name: 'dollar' },
    children: [
      {
        name: 'nav.comptabilite.dashboard',
        url: '/comptabilite/dashboard',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.comptabilite.planComptable',
        url: '/comptabilite/plan-comptable',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.comptabilite.balanceGenerale',
        url: '/comptabilite/balance-generale',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.comptabilite.ecritures',
        url: '/comptabilite/ecritures',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.comptabilite.grandLivre',
        url: '/comptabilite/grand-livre',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    name: 'nav.partenaireAffaire',
    url: '/partenaire-affaire',
    iconComponent: { name: 'briefcase' },
  },
  {
    name: 'nav.commercial',
    url: '/commercial',
    iconComponent: { name: 'cilPeople' },
  },
  {
    name: 'nav.fidelisation.title',
    url: '/fidelisation',
    iconComponent: { name: 'heart' },
    children: [
      {
        name: 'nav.fidelisation.campagnes.title',
        url: '/fidelisation/campagnes',
        children: [
          {
            name: 'nav.fidelisation.campagnes.mesCampagnes',
            url: '/fidelisation/campagnes/mes-campagnes',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.campagnes.modeles',
            url: '/fidelisation/campagnes/modeles',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.campagnes.segments',
            url: '/fidelisation/campagnes/segments',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.campagnes.historique',
            url: '/fidelisation/campagnes/historique',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.campagnes.statistiques',
            url: '/fidelisation/campagnes/statistiques',
            icon: 'nav-icon-bullet',
          },
        ],
      },
      {
        name: 'nav.fidelisation.programme.title',
        url: '/fidelisation/programme',
        children: [
          {
            name: 'nav.fidelisation.programme.programmes',
            url: '/fidelisation/programme/programmes',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.programme.niveaux',
            url: '/fidelisation/programme/niveaux',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.programme.recompenses',
            url: '/fidelisation/programme/recompenses',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.programme.parrainages',
            url: '/fidelisation/programme/parrainages',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.programme.clientsVip',
            url: '/fidelisation/programme/clients-vip',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.programme.venteCroisee',
            url: '/fidelisation/programme/vente-croisee',
            icon: 'nav-icon-bullet',
          },
        ],
      },
      {
        name: 'nav.fidelisation.satisfaction.title',
        url: '/fidelisation/satisfaction',
        children: [
          {
            name: 'nav.fidelisation.satisfaction.assistantImport',
            url: '/fidelisation/satisfaction/assistant-import',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.satisfaction.enquetes',
            url: '/fidelisation/satisfaction/enquetes',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.satisfaction.reponses',
            url: '/fidelisation/satisfaction/reponses',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.satisfaction.nps',
            url: '/fidelisation/satisfaction/nps',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.satisfaction.reclamations',
            url: '/fidelisation/satisfaction/reclamations',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.fidelisation.satisfaction.tableauDeBord',
            url: '/fidelisation/satisfaction/tableau-de-bord',
            icon: 'nav-icon-bullet',
          },
        ],
      },
    ],
  },
];

/** Masqués pour un compte REPRESENTANT (voir NavPermissionsService) — configuration
 * applicative et administration système, réservées aux profils ADMIN. */
const adminNavItems: INavData[] = [
  {
    title: true,
    name: 'nav.section.extra',
  },
  {
    name: 'nav.configuration.title',
    url: '/configuration',
    iconComponent: { name: 'cil-star' },
    children: [
      {
        name: 'nav.configuration.produit',
        url: '/configuration/produit',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.typeProfile',
        url: '/configuration/type-profile',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.typeProduit',
        url: '/configuration/type-produit',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.villePartenaire',
        url: '/configuration/ville-partenaire',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.langueApplication',
        url: '/configuration/langue-application',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.compagnieAssurances',
        url: '/configuration/compagnie-assurances',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.tauxCommission',
        url: '/configuration/taux-de-commission',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.commissionCommerciale',
        url: '/configuration/commission-commerciale',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.commissionsConfigurees',
        url: '/configuration/commissions-configurees',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.exerciceComptable',
        url: '/configuration/exercice-comptable',
        icon: 'nav-icon-bullet',
      },
    ],
  },
  {
    name: 'nav.administration.title',
    url: '/administration',
    iconComponent: { name: 'cilLockLocked' },
    children: [
      {
        name: 'nav.administration.audit.title',
        url: '/administration/audit',
        children: [
          {
            name: 'nav.administration.audit.tableauDeBord',
            url: '/administration/audit',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.administration.audit.historique',
            url: '/administration/audit/historique',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.administration.audit.actionsSensibles',
            url: '/administration/audit/actions-sensibles',
            icon: 'nav-icon-bullet',
          },
        ],
      },
      {
        name: 'nav.administration.deverrouillageCompte',
        url: '/administration/deverrouillage-compte',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.administration.utilisateurs',
        url: '/administration/utilisateurs',
        icon: 'nav-icon-bullet',
      },
      {
        name: 'nav.configuration.sansRedeploiement.title',
        url: '/administration/configuration/sans-redeploiement',
        children: [
          {
            name: 'nav.configuration.sansRedeploiement.tableauDeBord',
            url: '/administration/configuration/sans-redeploiement',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.configuration.sansRedeploiement.configurations',
            url: '/administration/configuration/sans-redeploiement/configurations',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.configuration.sansRedeploiement.categories',
            url: '/administration/configuration/sans-redeploiement/categories',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.configuration.sansRedeploiement.modules',
            url: '/administration/configuration/sans-redeploiement/modules',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.configuration.sansRedeploiement.historique',
            url: '/administration/configuration/sans-redeploiement/historique',
            icon: 'nav-icon-bullet',
          },
        ],
      },
      {
        name: 'nav.monitoring.title',
        url: '/monitoring',
        children: [
          {
            name: 'nav.monitoring.etatTechnique',
            url: '/monitoring/etat-technique',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.monitoring.indicateursMetier',
            url: '/monitoring/indicateurs-metier',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.monitoring.metriquesBrutes',
            url: '/monitoring/prometheus',
            icon: 'nav-icon-bullet',
          },
        ],
      },
      {
        name: 'nav.interceptors.title',
        url: '/interceptors',
        children: [
          {
            name: 'nav.interceptors.audit',
            url: '/interceptors/audit',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.interceptors.logsApplicatif',
            url: '/interceptors/logs',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.interceptors.logsServeur',
            url: '/interceptors/logs-serveur',
            icon: 'nav-icon-bullet',
          },
          {
            name: 'nav.interceptors.historiqueConnexions',
            url: '/administration/historique-connexions',
            icon: 'nav-icon-bullet',
          },
        ],
      },
    ],
  },
];

/** GED et Sorties de fonds/Prêts sont réservés ADMIN côté backend depuis le durcissement IDOR du
 * 2026-09-12 (voir docs/api-contracts/securisation-idor-2026-09-12.md §2 et §4) — lecture en 403
 * pour tout le reste. Vignette Moto n'en fait plus partie : le blocage ADMIN-only envisagé un
 * temps sur ce module a été annulé le jour même par le backend, remplacé par un owner-scoping
 * (voir la section "Correction du 2026-09-12" du même document) — le module reste ouvert à tout
 * compte authentifié, seuls Tarifs/Livraisons restent ADMIN-only (géré dans VignettesMotoComponent
 * lui-même). Masquage de menu uniquement, le vrai rempart reste le 403 backend. Distinct de
 * canAccessAdminMenus (masquage Paramétrage pour un type de profil partenaire, question
 * différente) — même choix que role === 'ADMIN' dans VignettesMotoComponent. */
const ADMIN_ONLY_NAV_NAMES = new Set(['nav.ged', 'nav.sortiesDeFonds', 'nav.sycapay.title', 'nav.mobile.title']);

/** Devis : la grille tarifaire est réservée ADMIN, le reste du menu est ouvert aux commerciaux. */
const DEVIS_ADMIN_ONLY_CHILDREN = new Set(['nav.devis.grille', 'nav.devis.marques', 'nav.devis.vtc']);

export function getNavItems(canAccessAdminMenus: boolean, isAdmin: boolean, isCommercial = false): INavData[] {
  const base = baseNavItems
    // Menu Devis : ADMIN et COMMERCIAL ; les autres profils ne le voient pas.
    .filter((item) => item.name !== 'nav.devis.title' || isAdmin || isCommercial)
    .map((item) =>
      item.name === 'nav.devis.title' && !isAdmin
        ? { ...item, children: item.children?.filter((c) => !DEVIS_ADMIN_ONLY_CHILDREN.has(c.name ?? '')) }
        : item,
    );
  const items = isAdmin ? base : base.filter((item) => !ADMIN_ONLY_NAV_NAMES.has(item.name ?? ''));
  return canAccessAdminMenus ? [...items, ...adminNavItems] : items;
}
