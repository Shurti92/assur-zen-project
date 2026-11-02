package ci.assurzen.webpush;

import ci.assurzen.webpush.config.WebPushProperties;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** Cles de test generees a la volee (jamais de cle reelle dans les tests) : paire VAPID de
 * l'application et cles d'un "navigateur" (p256dh/auth), au format npm web-push / PushSubscription. */
public final class VapidTestKeys {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private VapidTestKeys() {
    }

    public record Paire(String publique, String privee) {
    }

    public static Paire nouvellePaire() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
            generateur.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair paire = generateur.generateKeyPair();
            return new Paire(B64.encodeToString(pointNonCompresse((ECPublicKey) paire.getPublic())),
                    B64.encodeToString(sur32Octets(((ECPrivateKey) paire.getPrivate()).getS())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Proprietes initialisees avec une paire VAPID valide. */
    public static WebPushProperties proprietesConfigurees() {
        Paire paire = nouvellePaire();
        WebPushProperties properties = new WebPushProperties();
        properties.getVapid().setPublicKey(paire.publique());
        properties.getVapid().setPrivateKey(paire.privee());
        properties.getVapid().setSubject("mailto:tech@assurzen.ci");
        properties.init();
        return properties;
    }

    /** Cle "auth" d'un navigateur (16 octets aleatoires). */
    public static String authNavigateur() {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return B64.encodeToString(auth);
    }

    private static byte[] pointNonCompresse(ECPublicKey cle) {
        byte[] point = new byte[65];
        point[0] = 0x04;
        System.arraycopy(sur32Octets(cle.getW().getAffineX()), 0, point, 1, 32);
        System.arraycopy(sur32Octets(cle.getW().getAffineY()), 0, point, 33, 32);
        return point;
    }

    private static byte[] sur32Octets(BigInteger valeur) {
        byte[] brut = valeur.toByteArray();
        byte[] resultat = new byte[32];
        int longueur = Math.min(brut.length, 32);
        System.arraycopy(brut, brut.length - longueur, resultat, 32 - longueur, longueur);
        return resultat;
    }
}
