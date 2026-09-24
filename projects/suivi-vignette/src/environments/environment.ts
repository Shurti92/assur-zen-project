export const environment = {
  production: false,
  envName: 'local',
  // Relative — proxié vers le gateway local par proxy.conf.mjs (même config que assur-zen-core,
  // voir la racine du workspace) pour éviter le CORS en dev.
  apiUrl: '/api-assur-zen',
};
