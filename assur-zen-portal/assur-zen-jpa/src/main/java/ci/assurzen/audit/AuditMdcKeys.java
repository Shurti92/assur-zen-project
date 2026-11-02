package ci.assurzen.audit;

/**
 * Cles MDC (SLF4J) utilisees pour propager le correlationId/l'IP client depuis
 * AuditLoggingFilter (assur-zen-gateway, couche servlet) jusqu'aux services metier
 * (assur-zen-jpa, qui ne depend volontairement pas de jakarta.servlet/HttpServletRequest).
 * MDC est un ThreadLocal SLF4J standard : aucune dependance servlet requise cote jpa.
 */
public final class AuditMdcKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER_AGENT = "userAgent";

    private AuditMdcKeys() {
    }
}
