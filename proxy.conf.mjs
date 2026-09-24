const stripOriginHeaders = (proxy) => {
  // The gateway rejects requests whose Origin/Referer don't match its own
  // allow-list. Strip them so the proxied request looks same-origin.
  proxy.on('proxyReq', (proxyReq) => {
    proxyReq.removeHeader('origin');
    proxyReq.removeHeader('referer');
  });
};

export default {
  '/api-assur-zen': {
    target: 'https://localhost:9190',
    secure: false,
    changeOrigin: true,
    configure: stripOriginHeaders,
  },
  '/actuator': {
    target: 'https://localhost:9190',
    secure: false,
    changeOrigin: true,
    configure: stripOriginHeaders,
  },
};
