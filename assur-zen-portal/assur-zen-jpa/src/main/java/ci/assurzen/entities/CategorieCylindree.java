package ci.assurzen.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CategorieCylindree {
    MOINS_125_CM3,
    @JsonProperty("125_CM3_ET_PLUS")
    CM3_125_ET_PLUS
}
