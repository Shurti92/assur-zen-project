package ci.assurzen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Liste blanche pour le role CLIENT (compte mobile, 2026-11-02) : un client authentifie n'a acces qu'a
 * /auth/**, /mobile/**, /i18n/** et aux points techniques publics. Tout le reste est refuse en 403, y compris
 * les endpoints back-office seulement proteges par .authenticated() (tout role confondu) : sans ce filtre,
 * n'importe qui pourrait creer un compte mobile puis lire des donnees du back-office.
 * Un utilisateur qui porte aussi un autre role (ADMIN, COMMERCIAL, USER) n'est pas concerne.
 */
public class ClientRoleRestrictionFilter extends OncePerRequestFilter {

    private static final String ROLE_CLIENT = "ROLE_CLIENT";
    private static final String BASE = "/api-assur-zen/";
    private static final List<String> PREFIXES_AUTORISES = List.of(
            BASE + "auth/", BASE + "mobile/", BASE + "public/", BASE + "i18n/", BASE + "me/support-contact",
            "/v3/api-docs", "/swagger-ui", "/actuator/health", "/error");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && estUniquementClient(authentication)
                && !estAutorise(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Acc\\u00e8s non autoris\\u00e9 pour ce type de compte\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean estUniquementClient(Authentication authentication) {
        boolean client = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ROLE_CLIENT.equals(authority.getAuthority())) {
                client = true;
            } else {
                return false;
            }
        }
        return client;
    }

    static boolean estAutorise(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefixe : PREFIXES_AUTORISES) {
            if (uri.startsWith(prefixe)) {
                return true;
            }
        }
        return uri.equals("/actuator/health");
    }
}
