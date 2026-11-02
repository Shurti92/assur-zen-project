package ci.assurzen.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Intercepte tout appel a une methode annotee @Auditable et l'enregistre dans
 * HistoriqueModification via FunctionalAuditRecorder, que l'appel reussisse ou echoue.
 * Ne fait AUCUN diff de champs (voir javadoc de @Auditable) - uniquement action/module/entite/resultat.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    /** Les DTO XxxResponse de ce projet sont des records : accesseurs sans prefixe "get". */
    private static final String[] RESULT_ID_ACCESSORS = {"id", "reference", "code"};

    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String entityIdAvantAppel = extractEntityIdFromArgs(args);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            String entityId = entityIdAvantAppel != null ? entityIdAvantAppel : null;
            try {
                functionalAuditRecorder.recordActionFailure(auditable.module(), auditable.entityName(), entityId,
                        auditable.action(), t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            } catch (RuntimeException auditFailure) {
                log.warn("Echec enregistrement audit (branche echec) pour {}: {}", joinPoint.getSignature(), auditFailure.getMessage());
            }
            throw t;
        }

        String entityId = entityIdAvantAppel != null ? entityIdAvantAppel : extractEntityIdFromResult(result);
        try {
            functionalAuditRecorder.recordAction(auditable.module(), auditable.entityName(), entityId,
                    auditable.action(), auditable.entityName() + " : " + auditable.action());
        } catch (RuntimeException auditFailure) {
            log.warn("Echec enregistrement audit (branche succes) pour {}: {}", joinPoint.getSignature(), auditFailure.getMessage());
        }
        return result;
    }

    /** Convention : le premier argument Long de la methode est l'id cible (update(Long id, ...), delete(Long id)). */
    private String extractEntityIdFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long longId) {
                return String.valueOf(longId);
            }
        }
        return null;
    }

    /** Repli pour create() : l'id n'existe qu'apres l'appel, lu par reflection sur la valeur de retour. */
    private String extractEntityIdFromResult(Object result) {
        if (result == null) {
            return null;
        }
        for (String accessorName : RESULT_ID_ACCESSORS) {
            try {
                Method accessor = result.getClass().getMethod(accessorName);
                Object value = accessor.invoke(result);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // cet accesseur n'existe pas sur ce type de retour, on essaie le suivant
            }
        }
        return null;
    }
}
