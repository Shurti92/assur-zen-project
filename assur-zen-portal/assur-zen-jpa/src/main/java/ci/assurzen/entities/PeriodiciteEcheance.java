package ci.assurzen.entities;

public enum PeriodiciteEcheance {
    MENSUELLE(1),
    TRIMESTRIELLE(3),
    SEMESTRIELLE(6),
    ANNUELLE(12);

    private final int moisEntreEcheances;

    PeriodiciteEcheance(int moisEntreEcheances) {
        this.moisEntreEcheances = moisEntreEcheances;
    }

    public int getMoisEntreEcheances() {
        return moisEntreEcheances;
    }
}
