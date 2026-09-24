# CORS bloqué — environnement de recette

> 🆕 **Nécessite une configuration côté backend (gateway).** Aucun code frontend ne peut résoudre ce problème : c'est le serveur qui doit renvoyer l'en-tête `Access-Control-Allow-Origin`, le navigateur bloque la requête avant même qu'elle n'atteigne l'application Angular.

## Erreur observée

```
Access to XMLHttpRequest at 'https://medicis-apps.com:9443/assur-zen-gateway-recette/api-assur-zen/auth/login'
from origin 'https://medicis-apps.com' has been blocked by CORS policy: Response to preflight request
doesn't pass access control check: No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

## Pourquoi

Le frontend est servi depuis `https://medicis-apps.com/assur-zen-core-recette/` (port 443 implicite) et appelle l'API sur `https://medicis-apps.com:9443/...` — même nom d'hôte, mais **port différent**. Pour le navigateur, l'origine est `scheme + host + port` : ces deux URLs sont donc considérées comme des origines différentes, et toute requête cross-origin (surtout un `POST` avec `Content-Type: application/json`, qui déclenche un préflight `OPTIONS`) exige que le serveur réponde avec les en-têtes CORS appropriés.

## Configuration demandée côté gateway (`assur-zen-gateway-recette`)

Sur toutes les routes exposées sous `/api-assur-zen/*` (et `/actuator/*` si utilisé par le frontend) :

- `Access-Control-Allow-Origin: https://medicis-apps.com` (origine exacte du frontend — pas de wildcard `*` si les identifiants/cookies sont utilisés)
- `Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS`
- `Access-Control-Allow-Headers: Content-Type, Authorization`
- Réponse `200`/`204` sur les requêtes `OPTIONS` (préflight), sans exiger d'authentification sur cette méthode
- Si le futur build de production (`assur-zen-core-prod`) est servi depuis le même domaine, la même origine `https://medicis-apps.com` suffit pour les deux environnements (seul le chemin diffère : `/assur-zen-core-recette/` vs `/assur-zen-core-prod/`, le port/origine de la page est identique)

## Contexte

Le frontend n'envoie pas de cookies (authentification par `Authorization: Bearer <token>` uniquement, voir `core/interceptors/auth.interceptor.ts`) — `Access-Control-Allow-Credentials` n'est donc a priori pas nécessaire, sauf si le gateway utilise un mécanisme de session/cookie en interne.
