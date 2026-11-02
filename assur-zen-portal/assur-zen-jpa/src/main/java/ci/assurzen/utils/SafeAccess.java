package ci.assurzen.utils;

import jakarta.persistence.EntityNotFoundException;
import org.hibernate.proxy.HibernateProxy;

import java.util.function.Supplier;

/**
 * Une association @ManyToOne lazy dont la FK reste non-null mais dont la ligne ciblee a ete
 * supprimee (Insured/Contract/Compagnie/Produit/Partenaire/ExerciceComptable/User sont tous
 * hard-delete dans ce projet) ne renvoie JAMAIS un proxy null - seule la lecture d'un champ AUTRE
 * que l'id declenche l'initialisation du proxy et leve EntityNotFoundException. Point d'acces
 * unique pour ce garde-fou (auparavant duplique independamment dans ContractsService et
 * CommissionService, puis constate manquant dans 8 autres services lors d'un audit du 2026-09-14
 * declenche par un bug frontend reel sur ContractResponse.insuredName).
 */
public final class SafeAccess {

    private SafeAccess() {
    }

    public static <T> T safe(Supplier<T> fieldAccessor) {
        try {
            return fieldAccessor.get();
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    /**
     * Identifiant d'une association lazy, lu dans le proxy SANS l'initialiser. Constate le 2026-09-23
     * (Hibernate 7, GET /commissions/payments en 500) : contrairement a ce que suppose la Javadoc de
     * cette classe, getId() sur le proxy d'un Partenaire supprime initialise le proxy et leve
     * EntityNotFoundException. L'id reste pourtant connu (c'est la FK) : on le garde pour l'affichage.
     */
    public static Long idOf(Object entity, Supplier<Long> idGetter) {
        if (entity instanceof HibernateProxy proxy
                && proxy.getHibernateLazyInitializer().getInternalIdentifier() instanceof Long id) {
            return id;
        }
        return safe(idGetter);
    }
}
