package ci.assurzen.audit;

import ci.assurzen.entities.ActionAudit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Trace automatiquement (via AuditAspect) un appel de methode de service dans
 * HistoriqueModification, sans diff de champs (CREATE/DELETE/ACTIVATE/APPROVE/CANCEL/...).
 * Pour un UPDATE avec avant/apres, appeler FunctionalAuditRecorder.recordUpdate(...) explicitement
 * depuis le service (il possede deja les deux etats en main) plutot que d'utiliser cette annotation :
 * un aspect generique ne peut pas deviner de facon fiable "l'etat avant" pour une signature de
 * methode arbitraire.
 *
 * L'identifiant d'entite est resolu automatiquement : premier argument Long de la methode
 * (typiquement l'id sur update/delete), sinon via getId()/getReference()/getCode() sur la valeur
 * de retour (typiquement sur create()).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    ActionAudit action();

    /** Ex. "CONTRATS", "PARRAINAGE" - libre, pas un enum : la liste des modules est amenee a grandir. */
    String module();

    /** Ex. "Contract", "Parrainage". */
    String entityName();
}
