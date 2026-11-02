package ci.assurzen.dto;

import java.util.Map;

/** Valeurs d'exemple pour chaque code de variable (ex. "nom_client" -> "Yao"), utilisees pour la previsualisation. */
public record TemplatePreviewRequest(
        Map<String, String> valeurs
) {
}
