package ci.assurzen.dto;

import ci.assurzen.entities.SegmentType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mise a jour partielle : seuls les champs non-null sont appliques. */
public record SegmentPatchRequest(
        @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        SegmentType type,
        Integer priorite,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String couleur,
        Boolean active
) {
}
