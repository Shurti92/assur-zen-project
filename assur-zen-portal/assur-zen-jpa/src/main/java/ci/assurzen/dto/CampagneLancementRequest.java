package ci.assurzen.dto;

import java.util.List;

public record CampagneLancementRequest(
        List<Long> insuredIds
) {
}
