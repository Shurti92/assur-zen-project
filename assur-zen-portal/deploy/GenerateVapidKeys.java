import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Genere une paire de cles VAPID pour le Web Push de l'application EP (2026-09-26), au format de
 * `npx web-push generate-vapid-keys` : publique = point P-256 non compresse (65 octets), privee =
 * scalaire (32 octets), en base64url sans padding.
 *
 * Usage (JDK 21, aucune dependance) :   java deploy/GenerateVapidKeys.java
 *
 * UNE paire par environnement (local, recette, production). VAPID_PRIVATE_KEY reste sur le serveur
 * (variables d'environnement du service), jamais dans Git, jamais transmise au frontend. La cle publique
 * va au frontend (VITE_VAPID_PUBLIC_KEY) ET au backend (VAPID_PUBLIC_KEY). Changer la paire invalide
 * tous les abonnements existants.
 */
public class GenerateVapidKeys {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair paire = generateur.generateKeyPair();

        ECPublicKey publique = (ECPublicKey) paire.getPublic();
        byte[] pointNonCompresse = new byte[65];
        pointNonCompresse[0] = 0x04;
        System.arraycopy(sur32Octets(publique.getW().getAffineX()), 0, pointNonCompresse, 1, 32);
        System.arraycopy(sur32Octets(publique.getW().getAffineY()), 0, pointNonCompresse, 33, 32);

        byte[] scalaire = sur32Octets(((ECPrivateKey) paire.getPrivate()).getS());

        Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();
        System.out.println("VAPID_PUBLIC_KEY=" + base64Url.encodeToString(pointNonCompresse));
        System.out.println("VAPID_PRIVATE_KEY=" + base64Url.encodeToString(scalaire));
        System.out.println("VAPID_SUBJECT=mailto:<adresse technique de l'equipe>");
    }

    private static byte[] sur32Octets(BigInteger valeur) {
        byte[] brut = valeur.toByteArray();
        byte[] resultat = new byte[32];
        int longueur = Math.min(brut.length, 32);
        System.arraycopy(brut, brut.length - longueur, resultat, 32 - longueur, longueur);
        return resultat;
    }
}
