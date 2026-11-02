package ci.assurzen.dto;

import java.time.Instant;

public record SegmentMembreResponse(
        Long id,
        Long segmentId,
        Long insuredId,
        String insuredName,
        Instant createdAt
) {
}
