# Contrat d'interface — Pièce justificative sur un paiement d'échéance (contrats fractionnés)

> 🆕 **Aucune API existante — proposition de contrat, à transmettre au backend.** Vérifié dans le code frontend (2026-09-02) : `PaiementRequest`/`PaiementResponse` (`core/contracts/models/contract.model.ts`) n'ont aucun champ ni méthode liés à une pièce jointe. Reprend le même schéma que les mécanismes déjà livrés ailleurs dans l'API (`/sinistres/{id}/photos`, `/reclamations/{id}/pieces-jointes`, `/partenaires/{id}/documents`) — pas de nouveau concept à valider côté backend.
>
> Demande de l'utilisateur (2026-09-02), suite à une question sur la fiabilité de la validation des paiements fractionnés : aujourd'hui, un admin déclare un paiement (`POST /contracts/{id}/paiements`) sans aucune preuve jointe — juste une saisie de confiance (date, moyen de paiement en texte libre, référence optionnelle). Objectif : pouvoir attacher un justificatif (reçu, capture d'écran de virement, etc.) à un paiement enregistré.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Contexte

`Contrats → Contrats fractionnés` (`views/contrats/fractionnes/`) permet à un admin d'enregistrer un paiement sur une échéance `EN_ATTENTE` : `POST /contracts/{id}/paiements` avec `{ montant, datePaiement, moyenPaiement, reference?, echeanceId? }`, qui crée un `PaiementResponse` et bascule l'échéance en `PAYEE`. Aucune pièce justificative n'est demandée ni possible aujourd'hui — le paiement repose entièrement sur la déclaration de l'admin.

## 2. Proposition — 3 endpoints, sur le paiement déjà créé (pas de changement sur `POST .../paiements`)

```
GET  /api-assur-zen/contracts/{contractId}/paiements/{paiementId}/pieces-jointes              → liste (généralement 0 ou 1)
POST /api-assur-zen/contracts/{contractId}/paiements/{paiementId}/pieces-jointes              → upload (multipart "file")
GET  /api-assur-zen/contracts/{contractId}/paiements/{paiementId}/pieces-jointes/{pieceId}    → téléchargement (blob)
```

Volontairement une **sous-ressource séparée** plutôt qu'un champ multipart sur `POST .../paiements` : garde l'endpoint de création existant inchangé (toujours JSON), cohérent avec le flux actuel du frontend (le paiement est créé, *puis* le justificatif est joint dans la foulée si l'admin en a un). Autorise aussi de joindre un justificatif après coup, sur un paiement déjà ancien.

### 2.1 Réponse — `PaiementPieceJointeResponse` (identique sur les 3 endpoints, sauf le `GET .../{pieceId}` qui renvoie le fichier brut)

```json
{
  "id": 7,
  "paiementId": 21,
  "fileName": "recu_virement.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 92160,
  "uploadedBy": "Administrateur AssurZen Core",
  "createdAt": "2026-09-02T09:30:00Z"
}
```

Même forme que `SinistrePhotoResponse`/`ReclamationPieceJointeResponse` — pas de champ `type` ici (contrairement à `PartenaireDocumentResponse`), un justificatif de paiement n'a pas de catégorie à distinguer.

### 2.2 Pas de suppression dans cette proposition

Cohérent avec le choix initial sur `/sinistres/{id}/photos` et `/reclamations/{id}/pieces-jointes` — à ajouter plus tard si le besoin se confirme (comme ça a été le cas pour les documents partenaire, livré le lendemain de la demande).

## 3. Ce qui est déjà prêt côté frontend

- `core/contracts/models/contract.model.ts` : `PaiementPieceJointeResponse`.
- `core/contracts/contract.service.ts` : `listPaiementPiecesJointes()`/`uploadPaiementPieceJointe()`/`downloadPaiementPieceJointe()`.
- `views/contrats/fractionnes/` : champ fichier optionnel directement dans le formulaire "Enregistrer un paiement" (upload enchaîné juste après la création du paiement) ; pour une échéance déjà `PAYEE`, bouton "Justificatif" ouvrant le même panneau (liste + upload) via le `paiementId` de l'échéance.
- Le tout est câblé et attend juste que les 3 endpoints existent côté backend.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
