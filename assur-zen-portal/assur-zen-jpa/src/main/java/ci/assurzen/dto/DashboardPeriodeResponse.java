package ci.assurzen.dto;

import java.time.LocalDate;

public record DashboardPeriodeResponse(
        LocalDate dateDebut,
        LocalDate dateFin
) {
}
