package ci.assurzen.dto;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Periode des KPIs du dashboard : periodes CALENDAIRES (UTC). Contrats crees : Contract.createdAt ;
 * production et contrats signes : date de signature (signedAt, a defaut createdAt) depuis le 2026-10-04.
 * quarter/year ajoutes le 2026-10-03 (refonte Espace Partenaire), avec la periode precedente pour
 * calculer les tendances.
 */
public enum DashboardPeriod {
    month,
    quarter,
    year,
    all;

    /** Bornes (mois inclus) de la periode contenant `reference` ; null pour all. */
    public Bornes bornes(YearMonth reference) {
        return switch (this) {
            case month -> new Bornes(reference, reference);
            case quarter -> {
                YearMonth debut = YearMonth.of(reference.getYear(), ((reference.getMonthValue() - 1) / 3) * 3 + 1);
                yield new Bornes(debut, debut.plusMonths(2));
            }
            case year -> new Bornes(YearMonth.of(reference.getYear(), 1), YearMonth.of(reference.getYear(), 12));
            case all -> null;
        };
    }

    public record Bornes(YearMonth debut, YearMonth fin) {

        public boolean contient(Instant instant) {
            if (instant == null) {
                return false;
            }
            YearMonth mois = YearMonth.from(instant.atZone(ZoneOffset.UTC));
            return !mois.isBefore(debut) && !mois.isAfter(fin);
        }

        public boolean contient(java.time.LocalDate date) {
            if (date == null) {
                return false;
            }
            YearMonth mois = YearMonth.from(date);
            return !mois.isBefore(debut) && !mois.isAfter(fin);
        }

        /** Periode de meme duree juste avant celle-ci (mois precedent, trimestre precedent...). */
        public Bornes precedente() {
            long duree = debut.until(fin, java.time.temporal.ChronoUnit.MONTHS) + 1;
            return new Bornes(debut.minusMonths(duree), fin.minusMonths(duree));
        }
    }
}
