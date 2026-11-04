const stripOriginHeaders = (proxy) => {
  // The gateway rejects requests whose Origin/Referer don't match its own
  // allow-list. Strip them so the proxied request looks same-origin.
  proxy.on('proxyReq', (proxyReq) => {
    proxyReq.removeHeader('origin');
    proxyReq.removeHeader('referer');
  });
};

// Le gateway dev sert le portail en HTTPS (certificat auto-signé, d'où secure:false) sur 9190 ;
// un port HTTP clair 9191 existe en plus pour le téléphone. Pour changer de cible :
//   GATEWAY_TARGET=http://localhost:9191 npm start
const target = process.env.GATEWAY_TARGET || 'https://localhost:9190';

export default {
  '/api-assur-zen': {
    target,
    secure: false,
    changeOrigin: true,
    configure: stripOriginHeaders,
  },
  '/actuator': {
    target,
    secure: false,
    changeOrigin: true,
    configure: stripOriginHeaders,
  },
  // Page publique de consultation du devis (lien envoyé par SMS) : servie par le gateway à la racine.
  '^/d/': {
    target,
    secure: false,
    changeOrigin: true,
    configure: stripOriginHeaders,
  },
};
