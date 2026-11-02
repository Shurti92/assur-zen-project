package ci.assurzen.entities;

/** null sur DynamicConfiguration.environment = s'applique a tous les environnements. */
public enum ConfigEnvironment {
    DEV,
    TEST,
    UAT,
    PREPROD,
    PRODUCTION
}
