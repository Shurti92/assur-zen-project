package ci.assurzen.events;

/** configKey == null signifie un rafraichissement global (toutes les configurations). */
public record ConfigurationRefreshedEvent(String configKey) {
}
