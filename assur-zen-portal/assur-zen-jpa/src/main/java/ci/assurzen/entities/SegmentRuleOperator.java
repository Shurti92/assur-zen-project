package ci.assurzen.entities;

public enum SegmentRuleOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    CONTAINS,
    IS_NULL,
    IS_NOT_NULL,
    /** Uniquement pour les champs date : valeur = nombre de jours ("expire dans moins de N jours"). */
    EXPIRE_SOUS_N_JOURS
}
