package ci.assurzen.dto;

import jakarta.validation.constraints.Size;

/** Motif optionnel pour une annulation de parrainage ou de recompense. */
public record ParrainageMotifRequest(
        @Size(max = 300) String motif
) {
}
