import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';

import {
  adminGuard,
  quoteGuard,
  authGuard,
  changePasswordGuard,
  invalidUrlGuard,
  lockGuard,
  otpGuard,
  representantAccessGuard,
} from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: '',
    loadComponent: () => import('./layout').then((m) => m.DefaultLayoutComponent),
    canActivate: [authGuard, representantAccessGuard],
    data: {
      title: 'Home',
    },
    children: [
      {
        path: 'dashboard',
        loadChildren: () => import('./views/dashboard/routes').then((m) => m.routes),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./views/pages/profile/profile.component').then((m) => m.ProfileComponent),
        data: {
          title: 'Profil',
        },
      },
      {
        path: 'interceptors/logs',
        loadComponent: () => import('./views/logs/logs.component').then((m) => m.LogsComponent),
        data: {
          title: 'Logs Applicatif',
        },
      },
      {
        path: 'interceptors/audit',
        loadComponent: () => import('./views/audit/audit.component').then((m) => m.AuditComponent),
        data: {
          title: 'Audit',
        },
      },
      {
        path: 'interceptors/logs-serveur',
        loadComponent: () =>
          import('./views/logs-serveur/logs-serveur.component').then((m) => m.LogsServeurComponent),
        data: {
          title: 'Logs Serveur',
        },
      },
      {
        path: 'monitoring/prometheus',
        loadComponent: () =>
          import('./views/monitoring/monitoring.component').then((m) => m.MonitoringComponent),
        data: {
          title: 'Supervision Prometheus',
        },
      },
      {
        path: 'monitoring/etat-technique',
        loadComponent: () =>
          import('./views/monitoring/health-status/health-status.component').then(
            (m) => m.HealthStatusComponent,
          ),
        data: {
          title: 'État technique',
        },
      },
      {
        path: 'monitoring/indicateurs-metier',
        loadComponent: () =>
          import('./views/monitoring/business-metrics/business-metrics.component').then(
            (m) => m.BusinessMetricsComponent,
          ),
        data: {
          title: 'Indicateurs métier',
        },
      },
      {
        path: 'configuration/produit',
        loadComponent: () =>
          import('./views/configuration/produit/produit.component').then((m) => m.ProduitComponent),
        data: {
          title: 'Produits',
        },
      },
      {
        path: 'configuration/type-profile',
        loadComponent: () =>
          import('./views/configuration/type-profile/type-profile.component').then(
            (m) => m.TypeProfileComponent,
          ),
        data: {
          title: 'Types de profil',
        },
      },
      {
        path: 'configuration/type-produit',
        loadComponent: () =>
          import('./views/configuration/type-produit/type-produit.component').then((m) => m.TypeProduitComponent),
        data: {
          title: 'Types de produit',
        },
      },
      {
        path: 'configuration/ville-partenaire',
        loadComponent: () =>
          import('./views/configuration/ville-partenaire/ville-partenaire.component').then(
            (m) => m.VillePartenaireComponent,
          ),
        data: {
          title: 'Villes partenaires',
        },
      },
      {
        path: 'configuration/langue-application',
        loadComponent: () =>
          import('./views/configuration/langue-application/langue-application.component').then(
            (m) => m.LangueApplicationComponent,
          ),
        data: {
          title: 'Langue Application',
        },
      },
      {
        path: 'configuration/compagnie-assurances',
        loadComponent: () =>
          import('./views/configuration/compagnie-assurances/compagnie-assurances.component').then(
            (m) => m.CompagnieAssurancesComponent,
          ),
        data: {
          title: "Compagnies d'assurances",
        },
      },
      {
        path: 'configuration/taux-de-commission',
        loadComponent: () =>
          import('./views/configuration/taux-commission/taux-commission.component').then(
            (m) => m.TauxCommissionComponent,
          ),
        data: {
          title: 'Taux de commission',
        },
      },
      {
        path: 'configuration/commission-commerciale',
        loadComponent: () =>
          import('./views/configuration/commission-commerciale/commission-commerciale.component').then(
            (m) => m.CommissionCommercialeComponent,
          ),
        data: {
          title: 'Commission commerciale',
        },
      },
      {
        path: 'configuration/commissions-configurees',
        loadComponent: () =>
          import('./views/configuration/commissions-configurees/commissions-configurees.component').then(
            (m) => m.CommissionsConfigureesComponent,
          ),
        data: {
          title: 'Commissions configurées',
        },
      },
      {
        path: 'configuration/exercice-comptable',
        loadComponent: () =>
          import('./views/configuration/exercice-comptable/exercice-comptable.component').then(
            (m) => m.ExerciceComptableComponent,
          ),
        data: {
          title: 'Exercice comptable',
        },
      },
      // Configuration sans redéploiement — API backend confirmée via Swagger (endpoints /configurations/*).
      {
        path: 'administration/configuration/sans-redeploiement',
        loadComponent: () =>
          import('./views/configuration-dynamique/configuration-dynamique.component').then(
            (m) => m.ConfigurationDynamiqueComponent,
          ),
        data: { title: 'Tableau de bord', mode: 'dashboard' },
      },
      {
        path: 'administration/configuration/sans-redeploiement/configurations',
        loadComponent: () =>
          import('./views/configuration-dynamique/configuration-dynamique.component').then(
            (m) => m.ConfigurationDynamiqueComponent,
          ),
        data: { title: 'Configurations', mode: 'configurations' },
      },
      {
        path: 'administration/configuration/sans-redeploiement/categories',
        loadComponent: () =>
          import('./views/configuration-dynamique/configuration-dynamique.component').then(
            (m) => m.ConfigurationDynamiqueComponent,
          ),
        data: { title: 'Catégories', mode: 'categories' },
      },
      {
        path: 'administration/configuration/sans-redeploiement/modules',
        loadComponent: () =>
          import('./views/configuration-dynamique/configuration-dynamique.component').then(
            (m) => m.ConfigurationDynamiqueComponent,
          ),
        data: { title: 'Modules', mode: 'modules' },
      },
      {
        path: 'administration/configuration/sans-redeploiement/historique',
        loadComponent: () =>
          import('./views/configuration-dynamique/configuration-dynamique.component').then(
            (m) => m.ConfigurationDynamiqueComponent,
          ),
        data: { title: 'Historique', mode: 'historique' },
      },
      {
        path: 'configuration/historique-modifications',
        loadComponent: () =>
          import('./shared/coming-soon/coming-soon.component').then((m) => m.ComingSoonComponent),
        data: {
          title: 'Historique des modifications',
          section: 'Paramétrage',
        },
      },
      {
        path: 'comptabilite/dashboard',
        loadComponent: () =>
          import('./views/comptabilite/dashboard/ledger-dashboard.component').then(
            (m) => m.LedgerDashboardComponent,
          ),
        data: {
          title: 'Tableau de bord comptable',
        },
      },
      {
        // Hors du préfixe /comptabilite (module désormais promu en menu de premier niveau,
        // distinct de Comptabilité générale) — sinon le groupe "Comptabilité générale" se
        // déplie aussi au clic, la correspondance de route étant faite par préfixe d'URL.
        path: 'commissions',
        loadComponent: () =>
          import('./views/commissions/commissions.component').then((m) => m.CommissionsComponent),
        data: {
          title: 'Commissions',
        },
      },
      {
        path: 'primes-objectif',
        loadComponent: () =>
          import('./views/commissions/primes-objectif/primes-objectif.component').then(
            (m) => m.PrimesObjectifComponent
          ),
        data: {
          title: "Primes d'objectif",
        },
      },
      {
        path: 'comptabilite/plan-comptable',
        loadComponent: () =>
          import('./views/comptabilite/plan-comptable/plan-comptable.component').then(
            (m) => m.PlanComptableComponent,
          ),
        data: {
          title: 'Plan comptable',
        },
      },
      {
        path: 'comptabilite/balance-generale',
        loadComponent: () =>
          import('./views/comptabilite/balance-generale/balance-generale.component').then(
            (m) => m.BalanceGeneraleComponent,
          ),
        data: {
          title: 'Balance générale',
        },
      },
      {
        path: 'comptabilite/ecritures',
        loadComponent: () =>
          import('./views/comptabilite/ecritures/ecritures.component').then((m) => m.EcrituresComponent),
        data: {
          title: 'Écritures comptables',
        },
      },
      {
        path: 'comptabilite/grand-livre',
        loadComponent: () =>
          import('./views/comptabilite/grand-livre/grand-livre.component').then((m) => m.GrandLivreComponent),
        data: {
          title: 'Grand livre',
        },
      },
      {
        path: 'partenaire-affaire',
        loadComponent: () =>
          import('./views/partenaire-affaire/partenaire-affaire.component').then(
            (m) => m.PartenaireAffaireComponent,
          ),
        data: {
          title: "Partenaire d'affaire",
        },
      },
      // Gestion de la Fidélisation — no backend yet, every route below is a
      // placeholder (see ComingSoonComponent) until the corresponding API exists.
      {
        path: 'commercial',
        loadComponent: () =>
          import('./views/commercial/commercial.component').then((m) => m.CommercialComponent),
        data: {
          title: 'Force de vente',
        },
      },
      {
        path: 'fidelisation/campagnes/mes-campagnes',
        loadComponent: () =>
          import('./views/fidelisation/campagnes/campagnes.component').then((m) => m.CampagnesComponent),
        data: { title: 'Mes campagnes', mode: 'liste' },
      },
      {
        path: 'fidelisation/campagnes/modeles',
        loadComponent: () =>
          import('./views/fidelisation/modeles/modeles.component').then((m) => m.ModelesComponent),
        data: { title: 'Modèles', section: 'Campagnes Marketing' },
      },
      {
        path: 'fidelisation/campagnes/segments',
        loadComponent: () =>
          import('./views/fidelisation/segments/segments.component').then((m) => m.SegmentsComponent),
        data: { title: 'Segments', section: 'Campagnes Marketing' },
      },
      {
        path: 'fidelisation/campagnes/historique',
        loadComponent: () =>
          import('./views/fidelisation/campagnes/campagnes.component').then((m) => m.CampagnesComponent),
        data: { title: 'Historique des campagnes', mode: 'historique' },
      },
      {
        path: 'fidelisation/campagnes/statistiques',
        loadComponent: () =>
          import('./views/fidelisation/campagnes/campagnes.component').then((m) => m.CampagnesComponent),
        data: { title: 'Statistiques des campagnes', mode: 'statistiques' },
      },
      {
        path: 'fidelisation/programme/programmes',
        loadComponent: () =>
          import('./views/fidelisation/programmes/programmes.component').then((m) => m.ProgrammesComponent),
        data: { title: 'Programmes' },
      },
      {
        path: 'fidelisation/programme/niveaux',
        loadComponent: () =>
          import('./views/fidelisation/paliers/paliers.component').then((m) => m.PaliersComponent),
        data: { title: 'Niveaux de fidélité' },
      },
      {
        path: 'fidelisation/programme/recompenses',
        loadComponent: () =>
          import('./views/fidelisation/recompenses/recompenses.component').then((m) => m.RecompensesComponent),
        data: { title: 'Récompenses' },
      },
      {
        path: 'fidelisation/programme/parrainages',
        loadComponent: () =>
          import('./views/fidelisation/parrainages/parrainages.component').then(
            (m) => m.ParrainagesComponent,
          ),
        data: { title: 'Parrainages', section: 'Fidélisation' },
      },
      {
        path: 'fidelisation/programme/clients-vip',
        loadComponent: () =>
          import('./views/fidelisation/clients-vip/clients-vip.component').then((m) => m.ClientsVipComponent),
        data: { title: 'Clients VIP' },
      },
      {
        path: 'fidelisation/programme/vente-croisee',
        loadComponent: () =>
          import('./views/fidelisation/vente-croisee/vente-croisee.component').then((m) => m.VenteCroiseeComponent),
        data: { title: 'Vente croisée' },
      },
      {
        path: 'fidelisation/satisfaction/assistant-import',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/import-wizard/import-wizard.component').then((m) => m.ImportWizardComponent),
        data: { title: "Assistant d'import" },
      },
      {
        path: 'fidelisation/satisfaction/enquetes',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/satisfaction.component').then((m) => m.SatisfactionComponent),
        data: { title: 'Enquêtes de satisfaction', mode: 'enquetes' },
      },
      {
        path: 'fidelisation/satisfaction/reponses',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/satisfaction.component').then((m) => m.SatisfactionComponent),
        data: { title: 'Réponses aux enquêtes', mode: 'reponses' },
      },
      {
        path: 'fidelisation/satisfaction/nps',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/satisfaction.component').then((m) => m.SatisfactionComponent),
        data: { title: 'Score NPS', mode: 'nps' },
      },
      {
        path: 'fidelisation/satisfaction/reclamations',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/reclamations/reclamations.component').then((m) => m.ReclamationsComponent),
        data: { title: 'Réclamations' },
      },
      {
        path: 'fidelisation/satisfaction/tableau-de-bord',
        loadComponent: () =>
          import('./views/fidelisation/satisfaction/tableau-de-bord/satisfaction-dashboard.component').then(
            (m) => m.SatisfactionDashboardComponent,
          ),
        data: { title: 'Tableau de bord', section: 'Satisfaction Client' },
      },
      {
        path: 'assures',
        loadComponent: () =>
          import('./views/assures/assures.component').then((m) => m.AssuresComponent),
        data: {
          title: 'Assurés',
        },
      },
      {
        path: 'devis/grille-tarifaire',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/grille-tarifaire/grille-tarifaire.component').then((m) => m.GrilleTarifaireComponent),
        data: {
          title: 'Grille tarifaire',
          section: 'Devis',
        },
      },
      {
        path: 'devis/marques',
        canActivate: [adminGuard],
        loadComponent: () => import('./views/marques-moto/marques-moto.component').then((m) => m.MarquesMotoComponent),
        data: { title: 'Marques moto', section: 'Devis' },
      },
      {
        path: 'devis/vtc-parametrage',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/vtc-parametrage/vtc-parametrage.component').then((m) => m.VtcParametrageComponent),
        data: { title: 'Paramétrage VTC', section: 'Devis' },
      },
      {
        path: 'devis/nouveau-vtc',
        canActivate: [quoteGuard],
        loadComponent: () =>
          import('./views/devis/devis-nouveau-vtc.component').then((m) => m.DevisNouveauVtcComponent),
        data: { title: 'Nouveau devis VTC', section: 'Devis' },
      },
      {
        path: 'devis/liste',
        canActivate: [quoteGuard],
        loadComponent: () => import('./views/devis/devis-liste.component').then((m) => m.DevisListeComponent),
        data: { title: 'Devis', section: 'Devis' },
      },
      {
        path: 'devis/nouveau',
        canActivate: [quoteGuard],
        loadComponent: () => import('./views/devis/devis-nouveau.component').then((m) => m.DevisNouveauComponent),
        data: { title: 'Nouveau devis', section: 'Devis' },
      },
      {
        path: 'devis/liste/:reference',
        canActivate: [quoteGuard],
        loadComponent: () => import('./views/devis/devis-detail.component').then((m) => m.DevisDetailComponent),
        data: { title: 'Détail du devis', section: 'Devis' },
      },
      {
        path: 'mobile/comptes',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/comptes-mobile/comptes-mobile.component').then((m) => m.ComptesMobileComponent),
        data: {
          title: 'Comptes mobile',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'mobile/catalogue',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/catalogue-mobile/catalogue-mobile.component').then((m) => m.CatalogueMobileComponent),
        data: {
          title: 'Catalogue mobile',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'mobile/faq',
        canActivate: [adminGuard],
        loadComponent: () => import('./views/faq-mobile/faq-mobile.component').then((m) => m.FaqMobileComponent),
        data: {
          title: 'FAQ mobile',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'mobile/engagement',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/engagement-mobile/engagement-mobile.component').then((m) => m.EngagementMobileComponent),
        data: {
          title: 'Engagement mobile',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'mobile/avis',
        canActivate: [adminGuard],
        loadComponent: () => import('./views/avis-mobile/avis-mobile.component').then((m) => m.AvisMobileComponent),
        data: {
          title: 'Avis des clients',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'mobile/demandes',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/demandes-mobile/demandes-mobile.component').then((m) => m.DemandesMobileComponent),
        data: {
          title: 'Demandes mobile',
          section: 'Mobile AssurZen',
        },
      },
      {
        path: 'contrats/liste-des-contrats',
        loadComponent: () =>
          import('./views/contrats/contrats.component').then((m) => m.ContratsComponent),
        data: {
          title: 'Contrats valides',
          mode: 'valides',
        },
      },
      {
        path: 'contrats/fractionnes',
        loadComponent: () =>
          import('./views/contrats/fractionnes/contrats-fractionnes.component').then(
            (m) => m.ContratsFractionnesComponent,
          ),
        data: {
          title: 'Contrats fractionnés',
        },
      },
      {
        path: 'contrats/echus',
        loadComponent: () =>
          import('./views/contrats/contrats.component').then((m) => m.ContratsComponent),
        data: {
          title: 'Contrats échus',
          mode: 'echus',
        },
      },
      {
        path: 'contrats/non-renouveles',
        loadComponent: () =>
          import('./views/contrats/contrats.component').then((m) => m.ContratsComponent),
        data: {
          title: 'Contrats non renouvelés',
          mode: 'non-renouveles',
        },
      },
      {
        path: 'contrats/a-echeance-proche',
        loadComponent: () =>
          import('./views/contrats/contrats.component').then((m) => m.ContratsComponent),
        data: {
          title: 'Contrats à échéance proche',
          mode: 'echeance-proche',
        },
      },
      {
        path: 'contrats/cycle-de-vie-contrat',
        loadComponent: () =>
          import('./views/contrats/contrats.component').then((m) => m.ContratsComponent),
        data: {
          title: 'Cycle de vie du contrat',
          mode: 'cycle',
        },
      },
      {
        path: 'contrats/liste-des-messages-lie-contrat',
        loadComponent: () =>
          import('./views/contrats/messages/contrats-messages.component').then(
            (m) => m.ContratsMessagesComponent,
          ),
        data: {
          title: 'Liste des messages SMS',
        },
      },
      {
        path: 'contrats/prime-non-reversee',
        loadComponent: () =>
          import('./views/contrats/prime-non-reversee/prime-non-reversee.component').then(
            (m) => m.PrimeNonReverseeComponent,
          ),
        data: {
          title: 'Primes non reversées',
        },
      },
      {
        path: 'gestion-de-flotte',
        loadComponent: () =>
          import('./views/fleet/fleet.component').then((m) => m.FleetComponent),
        data: {
          title: 'Gestion de flotte',
        },
      },
      {
        path: 'ged',
        canActivate: [adminGuard],
        loadComponent: () => import('./views/ged/ged.component').then((m) => m.GedComponent),
        data: {
          title: 'GED',
        },
      },
      {
        path: 'sorties-de-fonds',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./views/fund-outflows/fund-outflows.component').then((m) => m.FundOutflowsComponent),
        data: {
          title: 'Sorties de fonds',
        },
      },
      {
        path: 'sycapay',
        canActivate: [adminGuard],
        loadComponent: () => import('./views/sycapay/sycapay.component').then((m) => m.SycaPayComponent),
        data: {
          title: 'Paiements SycaPay',
        },
      },
      {
        path: 'vignettes-moto',
        loadComponent: () =>
          import('./views/vignettes-moto/vignettes-moto.component').then((m) => m.VignettesMotoComponent),
        providers: [provideTranslocoScope('vignettesMoto')],
        data: {
          title: 'Vignettes moto',
        },
      },
      {
        path: 'sinistres/declare-sinistre',
        loadComponent: () =>
          import('./views/sinistres/declare-sinistre/declare-sinistre.component').then(
            (m) => m.DeclareSinistreComponent,
          ),
        providers: [provideTranslocoScope('sinistres')],
        data: {
          title: 'Déclarer un sinistre',
        },
      },
      {
        path: 'sinistres/liste-sinistre',
        loadComponent: () =>
          import('./views/sinistres/sinistres.component').then((m) => m.SinistresComponent),
        providers: [provideTranslocoScope('sinistres')],
        data: {
          title: 'Liste des sinistres',
          mode: 'liste',
        },
      },
      {
        path: 'sinistres/suivi-sinistre',
        loadComponent: () =>
          import('./views/sinistres/sinistres.component').then((m) => m.SinistresComponent),
        providers: [provideTranslocoScope('sinistres')],
        data: {
          title: 'Suivi des sinistres',
          mode: 'suivi',
        },
      },
      {
        path: 'souscription/souscription-produit-assurance',
        loadComponent: () =>
          import('./views/souscription/souscription.component').then(
            (m) => m.SouscriptionComponent,
          ),
        data: {
          title: 'Souscription produit',
        },
      },
      {
        path: 'souscription/televerser-fichier-souscription',
        loadComponent: () =>
          import('./views/souscription/televerser-fichier/televerser-fichier.component').then(
            (m) => m.TeleverserFichierComponent,
          ),
        data: {
          title: 'Souscription en masse',
        },
      },
      // Administration / Audit — Audit Trail Engine réel (endpoints /audit-logs/*, confirmés via Swagger).
      // Le contrat initialement proposé (docs/api-contracts/audit-trail.md) ne reflète plus le schéma réel.
      {
        path: 'administration/audit',
        loadComponent: () =>
          import('./views/audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
        data: { title: 'Tableau de bord', mode: 'dashboard' },
      },
      {
        path: 'administration/audit/historique',
        loadComponent: () =>
          import('./views/audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
        data: { title: 'Historique des modifications', mode: 'historique' },
      },
      {
        path: 'administration/audit/actions-sensibles',
        loadComponent: () =>
          import('./views/audit-trail/audit-trail.component').then((m) => m.AuditTrailComponent),
        data: { title: 'Actions sensibles', mode: 'sensibles' },
      },
      // Administration / Historique des connexions — module admin livré par le backend
      // (endpoints /historique-connexions et /historique-connexions/statistiques, confirmés
      // via Swagger le 2026-08-24).
      {
        path: 'administration/historique-connexions',
        loadComponent: () =>
          import('./views/historique-connexions/historique-connexions.component').then(
            (m) => m.HistoriqueConnexionsComponent,
          ),
        data: { title: 'Historique des connexions' },
      },
      // Déverrouillage manuel de compte — POST /auth/accounts/{username}/unlock, confirmé livré
      // via Swagger le 2026-08-10. Voir docs/api-contracts/account-lockout.md §4.2.
      {
        path: 'administration/deverrouillage-compte',
        loadComponent: () =>
          import('./views/deverrouillage-compte/deverrouillage-compte.component').then(
            (m) => m.DeverrouillageCompteComponent,
          ),
        data: { title: 'Déverrouillage de compte' },
      },
      // Comptes utilisateurs back-office — POST /users ("Créer un utilisateur et son profil en
      // une seule opération"), confirmé livré via Swagger le 2026-08-11.
      // Voir docs/api-contracts/creation-comptes-utilisateurs.md §4bis.
      {
        path: 'administration/utilisateurs',
        loadComponent: () =>
          import('./views/utilisateurs/utilisateurs.component').then((m) => m.UtilisateursComponent),
        data: { title: 'Utilisateurs' },
      },
    ],
  },
  {
    path: '404',
    loadComponent: () =>
      import('./views/pages/page404/page404.component').then((m) => m.Page404Component),
    data: {
      title: 'Page 404',
    },
  },
  {
    path: '500',
    loadComponent: () =>
      import('./views/pages/page500/page500.component').then((m) => m.Page500Component),
    data: {
      title: 'Page 500',
    },
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./views/pages/login/login.component').then((m) => m.LoginComponent),
    data: {
      title: 'Login Page',
    },
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./views/pages/forgot-password/forgot-password.component').then(
        (m) => m.ForgotPasswordComponent,
      ),
    data: {
      title: 'Mot de passe oublié',
    },
  },
  // La page publique de suivi de vignette (lien SMS, /suivi/vignette) n'est PAS une route de
  // cette SPA : le backend a confirmé le 2026-09-18 que app.vignette.suivi-lien-base-url pointe
  // vers un déploiement séparé (mini-app dédiée), pas un reverse-proxy vers assur-zen-core. Voir
  // le projet Angular `suivi-vignette` (projects/suivi-vignette/) dans ce même workspace.
  {
    path: 'otp',
    loadComponent: () => import('./views/pages/otp/otp.component').then((m) => m.OtpComponent),
    canActivate: [otpGuard],
    data: {
      title: 'Vérification en deux étapes',
    },
  },
  {
    path: 'lock',
    loadComponent: () => import('./views/pages/lock/lock.component').then((m) => m.LockComponent),
    canActivate: [lockGuard],
    data: {
      title: 'Session verrouillée',
    },
  },
  {
    path: 'change-password',
    loadComponent: () =>
      import('./views/pages/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
    canActivate: [changePasswordGuard],
    data: {
      title: 'Nouveau mot de passe',
    },
  },
  {
    path: '**',
    loadComponent: () =>
      import('./views/pages/page500/page500.component').then((m) => m.Page500Component),
    canActivate: [invalidUrlGuard],
    data: {
      title: 'Page 500',
    },
  },
];
