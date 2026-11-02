package ci.assurzen.dto;

import ci.assurzen.entities.SegmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SegmentRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @NotNull SegmentType type,
        Integer priorite,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String couleur,
        Boolean active
) {
}
