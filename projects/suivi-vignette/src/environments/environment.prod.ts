export const environment = {
  production: true,
  envName: 'production',
  // Déploiement séparé (assurzen.ci), pas de proxy same-origin possible — appel direct et
  // cross-origin vers le gateway (même URL que assur-zen-core en prod). CORS vérifié le 2026-09-24
  // (prod et recette) : https://assurzen.ci et https://www.assurzen.ci autorisées (variable serveur
  // CORS_ALLOWED_ORIGINS) — à mettre à jour côté serveur si le domaine du frontend public change.
  apiUrl: 'https://medicis-apps.com:9443/assur-zen-gateway/api-assur-zen',
};
