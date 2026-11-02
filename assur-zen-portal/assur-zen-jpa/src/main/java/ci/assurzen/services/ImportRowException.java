package ci.assurzen.services;

import ci.assurzen.entities.ImportErrorType;

/** Exception interne au moteur d'import : une ligne CSV n'a pas pu etre traitee, avec assez de contexte pour peupler SatisfactionImportError. */
class ImportRowException extends RuntimeException {

    private final ImportErrorType type;
    private final String colonne;
    private final String valeurBrute;

    ImportRowException(ImportErrorType type, String colonne, String valeurBrute, String message) {
        super(message);
        this.type = type;
        this.colonne = colonne;
        this.valeurBrute = valeurBrute;
    }

    ImportErrorType getType() {
        return type;
    }

    String getColonne() {
        return colonne;
    }

    String getValeurBrute() {
        return valeurBrute;
    }
}
