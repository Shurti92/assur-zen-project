package ci.assurzen.webpush.config;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** Generation d'une paire VAPID P-256 au format npm web-push (meme algorithme que
 * deploy/GenerateVapidKeys.java) : publique = point non compresse (65 octets), privee = scalaire
 * (32 octets), en base64url sans padding. */
public final class VapidKeyGenerator {

    public record Paire(String publique, String privee) {
        @Override
        public String toString() {
            return "Paire{publique=" + publique + ", privee=****}";
        }
    }

    private VapidKeyGenerator() {
    }

    public static Paire generer() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
            generateur.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair paire = generateur.generateKeyPair();
            ECPublicKey publique = (ECPublicKey) paire.getPublic();
            byte[] point = new byte[65];
            point[0] = 0x04;
            System.arraycopy(sur32Octets(publique.getW().getAffineX()), 0, point, 1, 32);
            System.arraycopy(sur32Octets(publique.getW().getAffineY()), 0, point, 33, 32);
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return new Paire(b64.encodeToString(point),
                    b64.encodeToString(sur32Octets(((ECPrivateKey) paire.getPrivate()).getS())));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Generation de la paire VAPID impossible", e);
        }
    }

    private static byte[] sur32Octets(BigInteger valeur) {
        byte[] brut = valeur.toByteArray();
        byte[] resultat = new byte[32];
        int longueur = Math.min(brut.length, 32);
        System.arraycopy(brut, brut.length - longueur, resultat, 32 - longueur, longueur);
        return resultat;
    }
}
