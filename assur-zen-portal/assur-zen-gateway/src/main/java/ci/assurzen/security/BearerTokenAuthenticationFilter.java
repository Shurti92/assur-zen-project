package ci.assurzen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ci.assurzen.security.JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                AuthenticatedUserPrincipal principal = jwtTokenService.toPrincipal(header.substring(7).trim());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, resolveAuthorities(principal.roles()));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException e) {
                // Bug recurrent signale par l'equipe frontend (audit 2026-08-25) : en se contentant
                // de vider le contexte puis de laisser filterChain.doFilter continuer, la requete
                // retombait "anonyme" jusqu'aux regles d'autorisation - sur une route .hasRole(...)
                // (la grande majorite des routes admin de cette appli), Spring Security refuse un
                // principal anonyme avec 403 (AccessDeniedException), PAS 401, quelle que soit la
                // VRAIE cause (jeton expire, revoque, ou compte desactive via UserActiveStatusCache).
                // Seules les routes .authenticated() (ex. /auth/get-me-data) rendaient deja le 401
                // correct. On coupe donc la requete ICI avec un 401 explicite des qu'un Bearer token
                // etait present mais rejete, avant meme d'atteindre le controle de role - jamais
                // execute pour une requete legitimement anonyme (sans header Authorization du tout,
                // ex. /auth/login), qui n'entre pas dans ce bloc.
                // WARN (pas DEBUG) : ce court-circuit saute AuditLoggingFilter (positionne apres ce
                // filtre dans la chaine, addFilterAfter(..., BearerTokenAuthenticationFilter.class)) -
                // sans ce log applicatif, ces rejets de token ne laisseraient plus AUCUNE trace nulle
                // part (ni AuditFlux, ni ce fichier de log), rendant un futur diagnostic impossible.
                log.warn("Bearer token rejete sur {} {} ({}) - reponse 401 immediate", request.getMethod(), request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"Jeton invalide, expir\\u00e9 ou r\\u00e9voqu\\u00e9\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(String roles) {
        if (!StringUtils.hasText(roles)) {
            return List.of();
        }

        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
