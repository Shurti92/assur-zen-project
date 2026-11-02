package ci.assurzen.security;

import ci.assurzen.audit.AuditMdcKeys;
import ci.assurzen.audit.AuditRecord;
import ci.assurzen.audit.AuditService;
import ci.assurzen.audit.PayloadRedactor;
import ci.assurzen.enums.Direction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> EXCLUDED_PATTERNS = List.of(
            "/actuator/**", "/swagger-ui/**", "/swagger-ui.html",
            "/v3/api-docs/**", "/v3/api-docs", "/webjars/**", "/error"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_REQUEST_CAPTURE_BYTES = 32 * 1024;

    private final AuditService auditService;

    @Value("${env.ws:/api-assur-zen/}")
    private String apiPrefix;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path) || !path.startsWith(normalizedPrefix())) {
            return true;
        }
        return EXCLUDED_PATTERNS.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    private String normalizedPrefix() {
        return apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String code = UUID.randomUUID().toString();
        boolean multipart = isMultipart(request.getContentType());
        long startNanos = System.nanoTime();

        HttpServletRequest requestToUse = multipart ? request : new ContentCachingRequestWrapper(request, MAX_REQUEST_CAPTURE_BYTES);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // MDC propage correlationId/IP jusqu'aux services metier (assur-zen-jpa, sans dependance
        // servlet) pour l'audit fonctionnel HistoriqueModification. MDC.clear() est obligatoire :
        // les threads Tomcat sont reutilises entre requetes, une fuite contaminerait la suivante.
        MDC.put(AuditMdcKeys.CORRELATION_ID, code);
        MDC.put(AuditMdcKeys.CLIENT_IP, resolveIpAddress(request));
        // MDC.put refuse une valeur null (leve IllegalArgumentException selon l'implementation) -
        // le header User-Agent est absent pour certains clients (ex. appels serveur-a-serveur).
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            MDC.put(AuditMdcKeys.USER_AGENT, userAgent);
        }

        Throwable captured = null;
        try {
            chain.doFilter(requestToUse, responseWrapper);
        } catch (ServletException | IOException | RuntimeException | Error t) {
            captured = t;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordAudit(code, request, requestToUse, responseWrapper, multipart, captured, durationMs);
            responseWrapper.copyBodyToResponse();
            MDC.remove(AuditMdcKeys.CORRELATION_ID);
            MDC.remove(AuditMdcKeys.CLIENT_IP);
            MDC.remove(AuditMdcKeys.USER_AGENT);
        }
    }

    private void recordAudit(String code, HttpServletRequest original, HttpServletRequest requestToUse,
                              ContentCachingResponseWrapper responseWrapper, boolean multipart, Throwable captured,
                              long durationMs) {
        String method = original.getMethod();
        String path = original.getRequestURI();
        String route = method + " " + path;
        AuthenticatedUserPrincipal principal = resolvePrincipal();
        String username = principal != null ? principal.username() : "anonymous";
        Long userId = principal != null ? principal.userId() : null;
        String ipAddress = resolveIpAddress(original);
        // When an exception truly escapes chain.doFilter (vs. being resolved into a normal error
        // response upstream), the container writes the real error status only after this filter's
        // finally block has already run — responseWrapper.getStatus() would still read the default 200.
        int statusCode = captured != null ? 500 : responseWrapper.getStatus();
        boolean otpRoute = PayloadRedactor.isOtpRoute(path);

        byte[] requestBytes = multipart || !(requestToUse instanceof ContentCachingRequestWrapper wrapper)
                ? null
                : wrapper.getContentAsByteArray();

        String requestFlux = multipart
                ? "[multipart/form-data, contentType=" + original.getContentType()
                    + ", length=" + original.getContentLengthLong() + " bytes, not captured]"
                : PayloadRedactor.redactJson(requestBytes, original.getContentType(), otpRoute);

        byte[] responseBytes = responseWrapper.getContentAsByteArray();
        // otpRoute redaction only applies to the request body: the actual OTP value is only ever
        // submitted by the client, never echoed back in a response, so masking "code" here would
        // only hide the unrelated, non-sensitive ErrorResponse.code label (e.g. "INVALID_OTP_CODE").
        String responseFlux = PayloadRedactor.redactJson(responseBytes, responseWrapper.getContentType(), false);

        String exceptionMessage;
        if (captured != null) {
            exceptionMessage = captured.getMessage() != null ? captured.getMessage() : captured.getClass().getSimpleName();
        } else if (responseWrapper.getStatus() >= 400) {
            exceptionMessage = extractErrorMessage(responseBytes, responseWrapper.getStatus());
        } else {
            exceptionMessage = null;
        }

        String numeroContrat = multipart
                ? null
                : PayloadRedactor.extractNumeroContrat(requestBytes, original.getContentType(), original.getParameterMap());

        auditService.recordAsync(new AuditRecord(code, Direction.INPUT, method, requestFlux, exceptionMessage,
                username, numeroContrat, route, statusCode, ipAddress, userId, durationMs));
        auditService.recordAsync(new AuditRecord(code, Direction.OUTPUT, method, responseFlux, exceptionMessage,
                username, numeroContrat, route, statusCode, ipAddress, userId, durationMs));
    }

    private AuthenticatedUserPrincipal resolvePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractErrorMessage(byte[] responseBytes, int status) {
        if (responseBytes != null && responseBytes.length > 0) {
            try {
                JsonNode root = MAPPER.readTree(responseBytes);
                JsonNode message = root.has("message") ? root.get("message") : root.get("error");
                if (message != null && !message.isNull()) {
                    return message.asText();
                }
            } catch (Exception ignored) {
                // response body not JSON / not parseable — fall back to status label below
            }
        }
        return "HTTP " + status;
    }

    private boolean isMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}
