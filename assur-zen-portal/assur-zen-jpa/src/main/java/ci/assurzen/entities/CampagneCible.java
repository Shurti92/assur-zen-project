package ci.assurzen.entities;

public enum CampagneCible {
    TOUS_CLIENTS,
    CLIENTS_FIDELES,
    ECHEANCE_PROCHE,
    NON_RENOUVELES,
    SEGMENT_PERSONNALISE,
    /** Resolu via Segment (module Segments/Templates) plutot qu'une regle figee. */
    SEGMENT_DYNAMIQUE
}
