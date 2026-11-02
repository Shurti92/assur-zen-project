package ci.assurzen.dto;

import java.util.Map;

public record RepartitionNotesResponse(
        Map<Integer, Long> parNote,
        long total
) {
}
