package ci.assurzen.security;

import java.util.Set;

/** Force minimale d'un code PIN à 6 chiffres (mobile, 2026-11-02) : le serveur fait foi. */
public final class PinPolicy {

    private static final Set<String> INTERDITS = Set.of(
            "123123", "112233", "121212", "123321", "456456", "159753", "147258", "258369",
            "135790", "246810", "102030", "010203", "654321", "000000", "202020", "101010");

    private PinPolicy() {
    }

    public static boolean estTropFaible(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            return true;
        }
        if (INTERDITS.contains(pin)) {
            return true;
        }
        boolean identiques = true;
        boolean croissante = true;
        boolean decroissante = true;
        for (int i = 1; i < pin.length(); i++) {
            int precedent = pin.charAt(i - 1);
            int courant = pin.charAt(i);
            identiques &= courant == precedent;
            croissante &= courant == precedent + 1;
            decroissante &= courant == precedent - 1;
        }
        // 5 chiffres identiques ou plus (111112) sont aussi trop simples
        int[] occurrences = new int[10];
        for (char c : pin.toCharArray()) {
            occurrences[c - '0']++;
        }
        int maxOccurrences = 0;
        for (int n : occurrences) {
            maxOccurrences = Math.max(maxOccurrences, n);
        }
        return identiques || croissante || decroissante || maxOccurrences >= 5;
    }
}
