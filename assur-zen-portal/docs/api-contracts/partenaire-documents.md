# Contrat d'interface — Documents joints à un partenaire (contrat apporteur, CNI, ...)

> ✅ **Les 3 endpoints initiaux livrés et vérifiés en direct le 2026-09-01, exactement conformes à ce contrat** — `GET/POST /partenaires/{id}/documents`, `GET .../{documentId}`, mêmes noms de champs sur `PartenaireDocumentResponse`, mêmes catégories `type`. Seule précision apportée par le backend, sans impact sur ce back-office : accès `Authentifié*` avec la même restriction que `GET /partenaires/{id}` (un compte partenaire ne verrait que sa propre fiche). Frontend câblé et testé de bout en bout dans le navigateur (upload réel, liste, "Voir" ouvrant le fichier).
>
> ✅ **`DELETE` livré et confirmé en direct le 2026-09-02** (`GET`/`DELETE` tous deux présents sur `/partenaires/{id}/documents/{documentId}` dans `/v3/api-docs`) — voir §2.3. Suppression physique simple (ligne + fichier disque), aucune règle de blocage type 409 comme annoncé. Frontend déjà câblé (bouton corbeille + confirmation dans l'onglet Documents) — reste à tester en direct dans le navigateur.
>
> Demande de l'utilisateur (2026-09-01) : « au niveau du partenaire donner la possibilité de charger les documents afférents (contrat_apporteur.pdf, copie cni...) au partenaire après création ».
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Contexte

Un partenaire d'affaire fournit des documents administratifs (contrat d'apporteur signé, copie de la CNI, RIB, justificatif de domicile, etc.) qui doivent pouvoir être joints à sa fiche et reconsultés plus tard par un admin — pas seulement au moment de la création, mais à tout moment depuis la fiche partenaire déjà existante (`views/partenaire-affaire/`).

## 2. Proposition — 3 endpoints, identiques dans l'esprit à `/sinistres/{id}/photos`

```
GET  /api-assur-zen/partenaires/{id}/documents              → liste des documents joints
POST /api-assur-zen/partenaires/{id}/documents              → upload d'un document (multipart)
GET  /api-assur-zen/partenaires/{id}/documents/{documentId} → téléchargement du fichier (blob)
```

### 2.1 `POST .../documents` — requête (`multipart/form-data`)

| Partie | Type | Description |
|---|---|---|
| `file` | fichier | Le document (PDF/image). |
| `type` | string | Catégorie du document — voir liste fermée ci-dessous. |

Catégories proposées (extensible sans casser le contrat, le frontend affiche une valeur inconnue telle quelle plutôt que de planter) :

```
CONTRAT_APPORTEUR
CNI
RIB
JUSTIFICATIF_DOMICILE
AUTRE
```

### 2.2 Réponse — `PartenaireDocumentResponse` (identique sur les 3 endpoints, sauf le `GET .../{documentId}` qui renvoie le fichier brut)

```json
{
  "id": 12,
  "partenaireId": 42,
  "type": "CNI",
  "fileName": "cni_kouassi.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 184320,
  "uploadedBy": "Administrateur AssurZen Core",
  "createdAt": "2026-09-01T10:15:00Z"
}
```

`GET /partenaires/{id}/documents` renvoie `PartenaireDocumentResponse[]` (pas de pagination nécessaire — un partenaire n'a jamais des dizaines de documents).

`GET /partenaires/{id}/documents/{documentId}` renvoie le fichier lui-même (`Content-Type` = celui stocké), pour être ouvert dans un nouvel onglet côté frontend — exactement comme `downloadPhoto()`/`downloadPieceJointe()` déjà en place.

### 2.3 `DELETE` — besoin confirmé le 2026-09-01

> 🆕 **Non livré** — absent du schéma `/v3/api-docs` vérifié le 2026-09-01 (seuls `get`/`post` présents sur la collection, `get` seul sur l'élément). Initialement omis par choix (cohérence avec `/sinistres/{id}/photos` et `/reclamations/{id}/pieces-jointes`, qui n'ont pas non plus de `DELETE`), mais le besoin s'est confirmé à l'usage (ex. document erroné/mauvais type uploadé par erreur).

```
DELETE /api-assur-zen/partenaires/{id}/documents/{documentId}
```

Suppression physique simple (pas de règle de blocage particulière identifiée à ce jour, contrairement à `DELETE /reclamations/{id}` qui refuse en `409` si commentaire/pièce jointe — un document partenaire n'a pas d'entité liée de ce type). Réponse : `204 No Content`, cohérent avec les autres `DELETE` déjà livrés dans l'API (ex. `/partenaires/{id}`, `/reclamations/{id}`).

Frontend déjà prêt : `PartenaireService.deleteDocument()`, bouton de suppression (icône corbeille) à côté de "Voir" dans l'onglet Documents, avec confirmation avant suppression — en attente de l'endpoint pour fonctionner (affiche actuellement un message d'erreur générique tant que l'endpoint n'existe pas).

## 3. Ce qui est déjà prêt côté frontend

- `core/partenaire/models/partenaire.model.ts` : `PartenaireDocumentResponse` + type `PartenaireDocumentType`.
- `core/partenaire/partenaire.service.ts` : `listDocuments()`/`uploadDocument()`/`downloadDocument()`, mêmes signatures que `SinistreService`/`ReclamationService`.
- `views/partenaire-affaire/` : nouvel onglet "Documents" dans la modale de détail du partenaire (à côté de Contrats/Primes non reversées/Historique/Avenants) — sélection du type + fichier, upload, liste avec nom/taille/auteur/date, bouton "Voir" qui ouvre le blob dans un nouvel onglet.
- Le tout est câblé et attend juste que les 3 endpoints existent côté backend — bascule sans refonte une fois livré.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
