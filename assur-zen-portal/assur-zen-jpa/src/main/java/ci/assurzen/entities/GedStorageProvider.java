package ci.assurzen.entities;

/** Seul LOCAL a une implementation dans ce projet (cf. LocalDocumentStorageService) - les autres
 * valeurs existent pour que le champ storage_provider stocke deja la bonne donnee le jour ou un
 * provider cloud est branche, sans migration de colonne. */
public enum GedStorageProvider {
    LOCAL,
    S3,
    AZURE_BLOB,
    MINIO
}
