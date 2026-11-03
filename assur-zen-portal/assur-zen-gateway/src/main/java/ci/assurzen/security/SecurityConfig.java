package ci.assurzen.security;

import ci.assurzen.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(JwtTokenService jwtTokenService) {
        return new BearerTokenAuthenticationFilter(jwtTokenService);
    }

    @Bean
    public AuditLoggingFilter auditLoggingFilter(AuditService auditService) {
        return new AuditLoggingFilter(auditService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // setAllowedOriginPatterns (pas setAllowedOrigins) : accepte aussi les motifs avec "*", pas
        // seulement des origines exactes. Necessaire pour Vercel, qui genere une URL de PREVIEW unique
        // a chaque deploiement (ex. vente-rkjwhky1y-dore772-6340s-projects.vercel.app) - impossible a
        // lister une par une. Les entrees sans "*" dans allowed-origins continuent de matcher en exact,
        // ce bean reste donc retro-compatible avec la liste existante.
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
                                                     AuditLoggingFilter auditLoggingFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight CORS : doit rester ouvert AVANT toute autre regle (Spring Security
                        // retient le PREMIER matcher qui correspond). Sans ce permitAll, le CorsFilter
                        // pose bien les en-tetes Access-Control-* mais la requete OPTIONS retombe quand
                        // meme sur .anyRequest().authenticated() plus bas et se fait rejeter (401/403) -
                        // le navigateur interprete alors le prevol comme un echec CORS complet, meme si
                        // l'origine appelante est correctement listee dans allowed-origins. Bug reel
                        // constate sur POST /auth/login depuis medicis-apps.com.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/error",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/swagger-config",
                                "/webjars/**",
                                // Racine nue seulement (statut UP/DOWN, sans detail - cf. management.endpoint.health.show-details
                                // = never desormais) : necessaire a un load balancer non authentifie. Le detail par composant
                                // (/actuator/health/**) et les metriques Prometheus sont protégés juste en dessous - avant le 25/08/2026
                                // (audit de securite pre-production), toute cette famille de endpoints etait permitAll et exposait
                                // publiquement chemin disque serveur, uptime JVM, memoire heap et espace disque.
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").hasRole("ADMIN")
                        // Deverrouillage manuel de compte (admin) : vit sous /auth/** comme le reste des
                        // endpoints d'authentification, mais doit rester protege. Meme regle d'ordre que
                        // pour /i18n plus bas : ce matcher DOIT precede le permitAll de /auth/** (Spring
                        // Security retient le PREMIER matcher qui correspond).
                        .requestMatchers("/api-assur-zen/auth/accounts/**").hasRole("ADMIN")
                        // Blocage/deblocage IP (admin) : meme regle d'ordre que /auth/accounts/** juste au-dessus.
                        .requestMatchers("/api-assur-zen/auth/ips/**").hasRole("ADMIN")
                        // Mobile lot 1 (2026-11-02) : verification du PIN et demande de suppression reservees au role CLIENT ;
                        // DOIVENT preceder le permitAll de /auth/** (premier matcher retenu).
                        .requestMatchers("/api-assur-zen/auth/mobile/pin/verify", "/api-assur-zen/auth/mobile/me/**").hasRole("CLIENT")
                        // Catalogue et demande de rappel du mobile, sans compte (2026-11-03).
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/public/products", "/api-assur-zen/public/products/*", "/api-assur-zen/public/contact-info", "/api-assur-zen/public/legal", "/api-assur-zen/public/support/topics", "/api-assur-zen/public/support/faq").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/public/callbacks").permitAll()
                        // Devis en ligne moto : formulaire et simulation sans compte ; grille reservee aux administrateurs (2026-11-03).
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/public/quotes/moto/form").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/public/quotes/moto/simulate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/public/quotes/vtc/form").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/public/quotes/vtc/simulate").permitAll()
                        .requestMatchers("/api-assur-zen/admin/quotes/tariffs/**", "/api-assur-zen/admin/quotes/tariffs").hasRole("ADMIN")
                        .requestMatchers("/api-assur-zen/admin/quotes/moto/brands", "/api-assur-zen/admin/quotes/vtc/brands",
                                "/api-assur-zen/admin/quotes/vtc/platforms", "/api-assur-zen/admin/quotes/vtc/settings").hasRole("ADMIN")
                        .requestMatchers("/api-assur-zen/admin/quotes/**").hasAnyRole("ADMIN", "COMMERCIAL")
                        // Administration des comptes et demandes mobile (2026-11-03) : reserve aux administrateurs.
                        .requestMatchers("/api-assur-zen/admin/mobile/**").hasRole("ADMIN")
                        // Espace mobile (2026-11-02) : reserve au role CLIENT.
                        .requestMatchers("/api-assur-zen/mobile/**").hasRole("CLIENT")
                        .requestMatchers("/api-assur-zen/auth/**").permitAll()
                        .requestMatchers("/api-assur-zen/otp/**").permitAll()
                        // i18n : le dictionnaire de traductions doit etre accessible AVANT connexion (ecran de login
                        // inclus), mais /i18n/entries (administration des traductions) doit rester protege. Comme
                        // /i18n/entries et /i18n/{langueCode} vivent a la meme profondeur de chemin, l'exclusion
                        // DOIT etre declaree avant le permitAll (Spring Security retient le PREMIER matcher qui
                        // correspond) : ne pas reordonner ces deux lignes, et ne pas elargir le permitAll en "/**".
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/i18n/entries").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/i18n/version", "/api-assur-zen/i18n/*").permitAll()
                        // Parrainage : un lien de parrainage est partage avec un prospect qui n'a pas encore de
                        // compte, donc la resolution/validation d'un code doit rester publique. Seuls ces deux
                        // couples methode+chemin precis sont ouverts ; la creation (POST /parrainage/codes sans
                        // segment) et la liste (GET /parrainage/codes sans segment) restent authentifiees.
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/parrainage/codes/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/parrainage/codes/*/valider").permitAll()
                        // Suivi public d'une demande de vignette (contrat "Suivi public securise par token",
                        // 2026-09-17) : l'assure consulte l'evolution de sa demande via un lien recu par SMS,
                        // sans compte. Le token (verifie cote service via son hash SHA-256, jamais l'id
                        // technique ni la reference) tient lieu de preuve d'acces - meme principe que
                        // /parrainage/codes/* ci-dessus, matcher precis sur le chemin exact uniquement.
                        .requestMatchers(HttpMethod.GET, "/api-assur-zen/vignettes-moto/demandes/suivi-public").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/type-profiles/**"
                        ).authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/type-profiles/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api-assur-zen/type-profiles/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api-assur-zen/type-profiles/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api-assur-zen/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api-assur-zen/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api-assur-zen/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/monitoring/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/logs/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/audit/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/audit-logs/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/historique-connexions/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api-assur-zen/configurations/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api-assur-zen/commissions/**"
                        ).hasRole("ADMIN")
                        // Commission commerciale interne (contrat "Commission commerciale interne",
                        // 2026-09-08) - meme convention wholesale ADMIN que /commissions/** ci-dessus
                        // (contient des montants a verser). Mutations de la configuration par produit
                        // reservees a l'admin egalement (lecture ouverte au catch-all plus bas, comme
                        // /produits-commission-taux/** qui n'a jamais eu de regle dediee).
                        .requestMatchers(
                                "/api-assur-zen/commercial-commissions/**"
                        ).hasRole("ADMIN")
                        // Integration SycaPay (paiement reel des commissions commerciales, 2026-09-22) -
                        // meme convention wholesale ADMIN que /commissions/** et /commercial-commissions/**
                        // ci-dessus (solde marchand, historique de virements, reconciliation). Double
                        // garde-fou au niveau service (SycaPayController -> requireAdmin() a ajouter la ou
                        // necessaire, meme pattern que le reste du projet).
                        .requestMatchers(
                                "/api-assur-zen/sycapay/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/produits-commission-commercial").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/produits-commission-commercial/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/produits-commission-commercial/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/partner-commissions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/partner-commissions/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/partner-commissions/*").hasRole("ADMIN")
                        // Meme protection en double au niveau service (PrimeObjectifService.requireAdmin()) -
                        // la simulation (GET /partenaires/{id}/prime-objectif/simulation) est nested sous
                        // /partenaires/** et suit donc ses propres regles (ouverte a tout compte authentifie
                        // au niveau gateway, restreinte a l'admin au niveau service).
                        .requestMatchers(
                                "/api-assur-zen/primes-objectif/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api-assur-zen/ledger/**"
                        ).hasRole("ADMIN")
                        // Vignette moto (contrat "Vignette Moto", 2026-09-11) : grille tarifaire et zones de
                        // livraison sont des donnees de configuration sensibles (§20), reservees a l'admin -
                        // meme convention que primes-objectif ci-dessus.
                        .requestMatchers(
                                "/api-assur-zen/vignettes-moto/tarifs/**",
                                "/api-assur-zen/vignettes-moto/livraisons/**"
                        ).hasRole("ADMIN")
                        // Commission apporteur vignette (2026-09-15) - meme convention que
                        // /commissions/** et /commercial-commissions/** (registres de commission
                        // deja ADMIN-only dans ce projet), double garde-fou via
                        // VignetteCommissionService.requireAdmin().
                        .requestMatchers(
                                "/api-assur-zen/vignettes-moto/commissions/**"
                        ).hasRole("ADMIN")
                        // Demandes de vignette moto - audit de securite du 2026-09-12 : passees en ADMIN-
                        // only le jour meme, PUIS revenu en arriere le jour meme sur confirmation du
                        // frontend que c'est un vrai self-service deja livre (pas un flux backoffice comme
                        // suppose initialement). Le scoping par proprietaire se fait desormais au niveau
                        // service (DemandeVignetteMotoService.verifierAccesDemandeCible(), via
                        // AuditableEntity.createdBy) plutot qu'ici - ouvert a tout compte authentifie,
                        // comme a l'origine, catch-all plus bas. Tarifs/livraisons restent ADMIN (regle
                        // ci-dessus, inchangee). Exception confirmee par le frontend le jour meme : rejeter
                        // sa propre demande n'a pas de sens pour un createur - reserve a l'admin (double
                        // garde-fou, meme convention que Tarifs/Livraisons : DemandeVignetteMotoService.
                        // requireAdmin() cote service en plus de la regle ci-dessous).
                        .requestMatchers(HttpMethod.PATCH,
                                "/api-assur-zen/vignettes-moto/demandes/*/rejeter"
                        ).hasRole("ADMIN")
                        // Progression manuelle du cycle de vie + gestion du lien de suivi (contrat "Suivi
                        // d'evolution des demandes de vignettes" / "Suivi public securise par token",
                        // 2026-09-17) - reservees a l'admin/gestionnaire, meme convention que /rejeter
                        // ci-dessus. Double garde-fou cote service (requireAdmin() dans
                        // DemandeVignetteMotoService et LienSuiviVignetteService).
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/vignettes-moto/demandes/*/statut",
                                "/api-assur-zen/vignettes-moto/demandes/*/lien-suivi/regeneration",
                                "/api-assur-zen/vignettes-moto/demandes/*/lien-suivi/revocation"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/dashboard/kpis-back-office"
                        ).hasRole("ADMIN")
                        // Module "KPI Dashboard" (2026-09-11) - meme convention que kpis-back-office
                        // ci-dessus, double garde-fou au niveau service via requireAdmin() dans chacun des
                        // 4 services (Vignette/SortieFonds/Assure/Global).
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/dashboard/vignettes",
                                "/api-assur-zen/dashboard/sorties-fonds",
                                "/api-assur-zen/dashboard/assures",
                                "/api-assur-zen/dashboard/global"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/insureds/*/fiche-sinistralite"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api-assur-zen/contracts/*/paiements"
                        ).hasRole("ADMIN")
                        // /partenaires/** n'avait AUCUNE restriction avant le 2026-08-23 (audit de
                        // securite pre-production) : n'importe quel compte connecte pouvait lister,
                        // consulter, creer, modifier (numeroWaveCommission inclus - la cible de fraude
                        // la plus sensible du module) ou supprimer N'IMPORTE QUEL partenaire. La lecture
                        // (GET liste/detail/export, sous-ressources contrats/commissions) reste ouverte
                        // a tout compte authentifie (les commerciaux en ont besoin au quotidien, et
                        // numeroWaveCommission est deja masque en lecture pour les non-admins - cf.
                        // PartenaireService.numeroWaveVisible) ; seules les mutations de la fiche
                        // partenaire elle-meme sont desormais reservees aux administrateurs. Meme
                        // protection en double, au niveau service (PartenaireService.requireAdmin()).
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/partenaires").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/partenaires/import").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/partenaires/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/partenaires/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api-assur-zen/partenaires/*/objectif").hasRole("ADMIN")
                        // Module Gestion de flotte : lecture ouverte a tout compte authentifie (catch-all
                        // plus bas), seules les mutations sont reservees a l'admin - meme convention que
                        // /sinistres et /partenaires ci-dessus (pas de moteur de permissions granulaires
                        // dans ce projet).
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/fleets").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/fleets/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/fleets/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api-assur-zen/fleets/*/activate",
                                "/api-assur-zen/fleets/*/suspend",
                                "/api-assur-zen/fleets/*/close"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/fleets/*/vehicles",
                                "/api-assur-zen/fleets/*/vehicles/remove",
                                "/api-assur-zen/fleets/*/vehicles/replace"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/fleets/*/vehicles/*/transfer").hasRole("ADMIN")
                        // Rattachement d'un contrat mono existant a une flotte (2026-09-14, gap remonte
                        // par le frontend) - meme convention que les autres mutations de vehicule de
                        // flotte ci-dessus.
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/fleets/*/contracts/attach").hasRole("ADMIN")
                        // Import CSV/XLSX de vehicules (Phase 2) : meme convention, mutations reservees
                        // a l'admin, lecture (historique/rapport/template) ouverte a tout authentifie.
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/fleets/*/vehicles/import/preview",
                                "/api-assur-zen/fleets/*/vehicles/import/*/confirm"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/vehicules").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/vehicules/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/vehicules/*").hasRole("ADMIN")
                        // Module GED (gestion electronique des documents) - audit de securite du
                        // 2026-09-12 : la lecture/le telechargement etaient ouverts a tout compte
                        // authentifie SANS aucune verification du lien entre l'appelant et l'entite
                        // metier associee au document (contrat, demande de vignette...) - IDOR /
                        // exposition de documents sensibles (CNI, justificatifs). Restreint a l'admin
                        // en attendant une verification de perimetre fine par entite liee.
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/ged/documents/*/versions",
                                "/api-assur-zen/ged/documents/*/download",
                                "/api-assur-zen/ged/documents/*/versions/*/download",
                                "/api-assur-zen/ged/documents/*",
                                "/api-assur-zen/ged/documents/search",
                                "/api-assur-zen/ged/documents/*/links",
                                "/api-assur-zen/ged/clients/*/documents",
                                "/api-assur-zen/ged/contracts/*/documents",
                                "/api-assur-zen/ged/vehicles/*/documents",
                                "/api-assur-zen/ged/fleets/*/documents"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/ged/documents",
                                "/api-assur-zen/ged/documents/*/versions",
                                "/api-assur-zen/ged/documents/*/validate",
                                "/api-assur-zen/ged/documents/*/reject",
                                "/api-assur-zen/ged/documents/*/archive",
                                "/api-assur-zen/ged/documents/*/links"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/ged/documents/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api-assur-zen/ged/documents/*",
                                "/api-assur-zen/ged/documents/*/links/*"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/ged/document-types",
                                "/api-assur-zen/ged/document-categories"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api-assur-zen/ged/document-types/*",
                                "/api-assur-zen/ged/document-categories/*"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api-assur-zen/ged/document-types/*",
                                "/api-assur-zen/ged/document-categories/*"
                        ).hasRole("ADMIN")
                        // Module Sorties de fonds / Prets partenaires - audit de securite du 2026-09-12 :
                        // la lecture etait ouverte a tout compte authentifie sans distinction (montants,
                        // beneficiaires exposes a n'importe qui de connecte). Restreint a l'admin (decision
                        // produit du 2026-09-12 - pas de notion de "beneficiaire self-service" existante).
                        // Mutations deja ADMIN (une seule etape de validation, pas de separation des
                        // taches technique dans cette phase - inchange).
                        .requestMatchers(HttpMethod.GET,
                                "/api-assur-zen/fund-outflows",
                                "/api-assur-zen/fund-outflows/*",
                                "/api-assur-zen/fund-outflows/search",
                                "/api-assur-zen/fund-outflows/pending-approvals",
                                "/api-assur-zen/fund-outflows/*/history",
                                "/api-assur-zen/fund-outflows/*/audit",
                                "/api-assur-zen/loans/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/fund-outflows",
                                "/api-assur-zen/fund-outflows/*/submit",
                                "/api-assur-zen/fund-outflows/*/review",
                                "/api-assur-zen/fund-outflows/*/approve",
                                "/api-assur-zen/fund-outflows/*/reject",
                                "/api-assur-zen/fund-outflows/*/authorize",
                                "/api-assur-zen/fund-outflows/*/disburse",
                                "/api-assur-zen/fund-outflows/*/cancel",
                                "/api-assur-zen/fund-outflows/*/close"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/fund-outflows/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/fund-outflows/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/loans/*/repayments",
                                "/api-assur-zen/loans/*/early-repayment"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/fund-outflow-types").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/fund-outflow-types/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api-assur-zen/fund-outflow-types/*").hasRole("ADMIN")
                        // Module Commercial : meme convention (mutations reservees a l'admin, lecture
                        // ouverte au catch-all plus bas) MAIS avec une nuance - /commercials/me et
                        // /commercials/{id} (soi-meme) doivent rester accessibles a tout compte
                        // COMMERCIAL authentifie, donc PAS de regle GET bloquante ici : le controle
                        // fin (soi-meme vs administrateur, remuneration incluse) est fait cote service
                        // (CommercialService.verifierAccesCommercialCible/requireAdmin), meme pattern que
                        // PartenaireResponse.numeroWaveVisible.
                        // Demandes de paiement de commission (2026-09-25) : self-service reserve au role
                        // COMMERCIAL (contrat : tout autre role -> 403), le commercial etant deduit du JWT.
                        .requestMatchers("/api-assur-zen/commercials/me/commissions/payment-requests").hasRole("COMMERCIAL")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/commercials").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api-assur-zen/commercials/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api-assur-zen/commercials/*/statut").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api-assur-zen/commercials/*/objectives",
                                "/api-assur-zen/commercials/*/monthly-objectives"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api-assur-zen/commercials/*/objectives/*",
                                "/api-assur-zen/commercials/*/monthly-objectives/*"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/commercials/*/performance/*/recalculate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api-assur-zen/commercials/*/performance/*/statut").hasRole("ADMIN")
                        // Paiement reel du bonus mensuel (virement SycaPay) et son historique, 2026-10-02.
                        .requestMatchers(HttpMethod.POST, "/api-assur-zen/commercials/*/performance/*/bonus/pay").hasRole("ADMIN")
                        .requestMatchers("/api-assur-zen/commercial-performance/bonus-payments").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(auditLoggingFilter, BearerTokenAuthenticationFilter.class)
                // Liste blanche du role CLIENT (compte mobile, 2026-11-02) : 403 hors /auth, /mobile, /i18n.
                .addFilterAfter(new ClientRoleRestrictionFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
