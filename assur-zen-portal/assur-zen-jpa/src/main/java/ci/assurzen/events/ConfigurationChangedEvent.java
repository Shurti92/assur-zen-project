package ci.assurzen.events;

/**
 * Publie apres commit (voir DynamicConfigurationService) quand la valeur d'une configuration change.
 * Les modules metier peuvent s'abonner via @EventListener pour reagir immediatement sans redemarrage.
 */
public record ConfigurationChangedEvent(String configKey, String oldValue, String newValue) {
}
