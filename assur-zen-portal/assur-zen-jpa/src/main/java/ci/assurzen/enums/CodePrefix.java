package ci.assurzen.enums;

public enum CodePrefix {

    PROFILE("PRF"),
    USER("USR"),
    PARTNER("AZCI"),
    CONTRAT("CTR"),
    SINISTRE("SIN"),
    DEVIS("DEV"),
    VILLE("VIL"),
    COMPAGNIE("CIE"),
    ECRITURE("ECR"),
    CAMPAGNE("CMP"),
    RECOMPENSE_ECHANGE("RCE"),
    ENQUETE("ENQ"),
    IMPORT_SATISFACTION("IMS"),
    RECLAMATION("REC"),
    CROSS_SELL_OPPORTUNITY("OPP"),
    PARRAINAGE("PRN"),
    CODE_PARRAINAGE("CPR"),
    HISTORIQUE_MODIFICATION("AUD"),
    PRIME_OBJECTIF("PRO"),
    FLOTTE("FLT"),
    GED("GED"),
    FUND_OUTFLOW("DEC"),
    LOAN("PRET"),
    DISBURSEMENT("FOD"),
    LOAN_REPAYMENT("RMB"),
    COMMERCIAL("COM"),
    VIGNETTE_MOTO("VGN"),
    SYCAPAY_TRANSACTION("SYC");

    private final String value;

    CodePrefix(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
