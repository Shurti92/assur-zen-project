# Rapport de bug — `PUT /partenaires/{id}` renvoie une erreur HTTP alors que la conversion en REPRESENTANT réussit réellement

> ✅ **Corrigé côté backend et reproduit avec succès le 2026-08-27** — nouveau partenaire de test créé DIRECT sans compte, puis converti en REPRESENTANT + accès commercial activé via le formulaire réel : `PUT /partenaires/{id}` répond directement `200` avec l'objet à jour (`typeProfileId`, `telephone`, `accesActiviteCommerciale` tous persistés, revérifié en rouvrant la fiche), aucun `500` ni `403`, aucun second appel nécessaire. Le garde-fou frontend ajouté ce même jour (revérification automatique de l'état réel via `GET /partenaires/{id}` si un `500`/`403` survient malgré tout) reste en place par prudence mais n'a plus été sollicité lors de ce test.
>
> 🐞 **Bug backend confirmé et RECTIFIÉ le 2026-08-25** — correction importante par rapport à la première version de ce rapport (ci-dessous, conservée en historique §3bis). La conversion **fonctionne réellement** côté serveur (profil + compte de connexion créés), mais l'API répond `500` puis `403` comme si elle avait échoué — un faux négatif trompeur, pas un vrai échec.
>
> ⚠️ **Effet de bord sérieux toujours valable en théorie, non re-testé** : le `403` du 2ᵉ essai avait fait perdre la session de l'administrateur connecté — voir §4. Non re-vérifié le 2026-08-27 puisque le `403` initial ne s'est plus produit.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Contexte

Question initiale de l'utilisateur (2026-08-25) : *"Est-il possible de créer des accès pour un partenaire déjà créé qui n'a pas de compte en état actuel ?"* — **réponse : oui, ça fonctionne déjà**, mais l'API le cache derrière une erreur HTTP qui donne l'impression du contraire.

## 2. Reproduction

1. Partenaire existant `AZCI2026000266` ("CasA DirectSansCompte", id interne `2`), type `DIRECT`, sans `typeProfileId`, sans `telephone` — jamais eu de compte de connexion.
2. Ouvrir "Modifier", renseigner Exercice comptable/Ville (vides à l'origine, voir §5 de l'ancienne version), Type Profil `REPRESENTANT`, Téléphone `0701234567`, Numéro Wave `0701234567`, toggle "Accès activité commerciale" activé, "Profil associé" laissé sur `— Aucun —`.
3. Cliquer "Enregistrer" → `PUT /api-assur-zen/partenaires/2` → **`500 Internal Server Error`** côté réponse HTTP.
4. Re-cliquer "Enregistrer" (mêmes données) → **`403 Forbidden`**, qui déclenche la perte de session côté frontend (voir §4).

## 3. ⚠️ Découverte du 2026-08-25 (plus tard le même jour) — la mutation a réellement réussi

En rouvrant le formulaire "Modifier" de ce même partenaire un peu plus tard (après reconnexion), les champs affichaient déjà les valeurs saisies lors de la tentative en échec :
- **Type Profil : `REPRESENTANT`**
- **Téléphone : `0701234567`**
- **Profil associé : `DirectSansCompte CasA (PRF2026000279)`** — un nouveau profil a bien été créé automatiquement, comme prévu.

Vérification définitive : le bouton "Renvoyer les accès" (icône cadenas) a été utilisé sur ce partenaire → `GET /users?q=0701234567` a bien trouvé un compte de connexion réel, et `PATCH /users/{id}/reset-password` a réussi, générant un nouveau mot de passe temporaire valide.

**Conclusion révisée : le `PUT` du point 3 (§2) a réellement appliqué tous les changements en base — nouveau profil créé, `typeProfileId`/`telephone`/`accesActiviteCommerciale` persistés, compte de connexion créé — malgré la réponse `500` reçue par le frontend.** Le `500` (et le `403` du second essai, probablement une tentative de recréer un profil/compte déjà existant en doublon) sont des **faux négatifs** : la requête a fonctionné, seule la réponse HTTP ment sur son propre résultat.

## 4. Effet de bord sur la session (toujours valable)

Après le `403` du 2ᵉ essai, l'application a redirigé l'utilisateur vers l'écran de connexion, avec une tentative déjà affichée comme échouée. **Cause confirmée côté frontend** (`core/interceptors/auth.interceptor.ts`, lignes 28-34) : contournement déjà en place et documenté depuis un précédent incident — *"The backend rejects aged/refreshed access tokens on some routes with a bare 403 (no refresh hint) instead of 401 — in practice the only fix that has ever worked is a fresh login, so treat it the same as an expired session."* Un `403` sur une requête authentifiée est donc **volontairement** traité comme une session expirée côté frontend, ce qui est correct et ne doit pas changer — mais ça signifie que ce `403` bien réel côté serveur (probablement une exception de contrainte d'unicité sur le 2ᵉ essai, cf. §3) a des conséquences disproportionnées côté client.

## 5. Impact révisé

- **Fonctionnel : faible** (contrairement à la première version de ce rapport) — la fonctionnalité demandée par l'utilisateur fonctionne déjà. Aucune action bloquante côté produit.
- **Fiabilité de l'API : élevé** — une réponse `500`/`403` sur une mutation qui a réellement réussi est trompeuse et dangereuse : un utilisateur qui voit une erreur peut légitimement re-soumettre (créant potentiellement un doublon de profil/compte, ou déclenchant le `403` en cascade observé ici), alors qu'il ne devrait rien refaire.
- **Session/sécurité** : le `403` du second essai peut faire perdre une session admin valide (voir §4), avec le risque de consommer une tentative du quota anti-brute-force si le navigateur soumet un mot de passe obsolète en autofill.

## 6. Comportement attendu

1. Si `PUT /partenaires/{id}` réussit réellement à appliquer les changements (création du profil/compte inclus), **répondre `200`** avec le `PartenaireResponse` à jour — pas `500`.
2. Un second appel identique, après un premier succès, ne devrait pas non plus planter en `500`/`403` — idéalement il devrait être idempotent (retourner l'état actuel sans dupliquer profil/compte), ou à défaut renvoyer un `409 Conflict` explicite ("profil/compte déjà créé pour ce partenaire").
3. Vérifier les logs serveur du 2026-08-25 autour de `PUT /partenaires/2` (deux appels rapprochés) pour identifier l'exception exacte à l'origine du `500` puis du `403` — la mutation elle-même s'est visiblement bien exécutée (côté données), donc l'erreur vient probablement de la construction de la réponse HTTP après coup (sérialisation, ou un second traitement redondant qui, lui, échoue).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes". Historique complet de l'investigation (première hypothèse, invalidée) disponible dans l'historique git de ce fichier si besoin.
