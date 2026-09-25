/**
 * Serveur HTTP de la passerelle locale SignalPro.
 *
 * Écoute UNIQUEMENT sur 127.0.0.1 par défaut : la session WhatsApp de
 * l'utilisateur n'est jamais joignable depuis Internet. Toute requête doit être
 * signée par le backend (HMAC-SHA256 avec GATEWAY_SHARED_SECRET).
 */
import 'dotenv/config';
import http from 'node:http';
import process from 'node:process';

import { SessionManager } from './session.js';
import { verify } from './security.js';

const PORT = Number.parseInt(process.env.GATEWAY_PORT ?? '8787', 10);
const HOST = process.env.GATEWAY_HOST ?? '127.0.0.1';
const SHARED_SECRET = process.env.GATEWAY_SHARED_SECRET ?? '';
const SESSION_DIR = process.env.GATEWAY_SESSION_DIR ?? './sessions';
// Désactivé par défaut : voir docs/RISQUES_ET_LIMITES.md avant de l'activer.
const NATIVE_REPORT = ['1', 'true', 'yes'].includes(String(process.env.GATEWAY_ENABLE_NATIVE_REPORT ?? '').toLowerCase());

if (!SHARED_SECRET || SHARED_SECRET.length < 24) {
  console.error(
    '[FATAL] GATEWAY_SHARED_SECRET manquant ou trop court (24 caractères minimum).\n' +
      '        Générez-le avec : python -m app.config  (champ CONNECTOR_SHARED_SECRET)\n' +
      '        puis reportez la MÊME valeur côté backend.',
  );
  process.exit(1);
}

const manager = new SessionManager({ sessionDir: SESSION_DIR, nativeReport: NATIVE_REPORT });

function send(res, code, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(code, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 5 * 1024 * 1024) {
        reject(Object.assign(new Error('Requête trop volumineuse.'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname;
  const method = req.method ?? 'GET';

  try {
    const body = method === 'GET' || method === 'DELETE' ? Buffer.alloc(0) : await readBody(req);

    if (path === '/health') {
      return send(res, 200, {
        status: 'ok',
        gateway: 'signalpro-gateway',
        version: '1.0.0',
        native_report_enabled: NATIVE_REPORT,
        sessions: manager.sessions.size,
      });
    }

    if (path === '/v1/capabilities' && method === 'GET') {
      return send(res, 200, { version: '1.0.0', capabilities: manager.capabilities() });
    }

    // Toutes les autres routes exigent une signature valide.
    const ok = verify(
      SHARED_SECRET,
      method,
      path,
      body,
      req.headers['x-signalpro-timestamp'],
      req.headers['x-signalpro-signature'],
    );
    if (!ok) {
      return send(res, 401, { error: 'Signature invalide ou expirée.' });
    }

    const payload = body.length ? JSON.parse(body.toString('utf8')) : {};

    if (path === '/v1/sessions' && method === 'POST') {
      const session = manager.create({ userRef: payload.user_ref, label: payload.label });
      await new Promise((resolve) => setTimeout(resolve, 400)); // laisse apparaître un QR
      const state = await session.state();
      return send(res, 200, {
        session_ref: session.id,
        status: state.status,
        pairing_payload: (await session.pairingPayload()) ?? null,
        expires_in: 120,
      });
    }

    let match = path.match(/^\/v1\/sessions\/([^/]+)$/);
    if (match && method === 'GET') {
      return send(res, 200, await manager.get(match[1]).state());
    }
    if (match && method === 'DELETE') {
      return send(res, 200, await manager.destroy(match[1]));
    }

    match = path.match(/^\/v1\/sessions\/([^/]+)\/contacts\/sync$/);
    if (match && method === 'POST') {
      return send(res, 200, await manager.get(match[1]).syncContacts());
    }

    match = path.match(/^\/v1\/sessions\/([^/]+)\/actions\/report$/);
    if (match && method === 'POST') {
      const session = manager.get(match[1]);
      const result = await session.report(payload.peer_jid, payload.category, payload.message_ids, payload.note);
      return send(res, 200, result);
    }

    match = path.match(/^\/v1\/sessions\/([^/]+)\/actions\/(block|unblock)$/);
    if (match && method === 'POST') {
      const session = manager.get(match[1]);
      const result = match[2] === 'block' ? await session.block(payload.peer_jid) : await session.unblock(payload.peer_jid);
      return send(res, 200, result);
    }

    match = path.match(/^\/v1\/sessions\/([^/]+)\/messages\/recent$/);
    if (match && method === 'POST') {
      return send(res, 200, await manager.get(match[1]).messages(payload.peer_jid, payload.limit ?? 5));
    }

    match = path.match(/^\/v1\/events$/);
    if (match && method === 'GET') {
      return send(res, 200, { events: manager.events.slice(-100) });
    }

    return send(res, 404, { error: `Route inconnue : ${method} ${path}` });
  } catch (error) {
    const code = error.statusCode ?? 500;
    if (code >= 500) console.error('[gateway] erreur', error);
    if (error.retryAfter) res.setHeader('retry-after', String(error.retryAfter));
    return send(res, code, { error: error.message ?? 'Erreur interne de la passerelle.' });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`[gateway] Passerelle SignalPro à l'écoute sur http://${HOST}:${PORT}`);
  console.log(`[gateway] Signalement natif : ${NATIVE_REPORT ? 'ACTIVÉ (expérimental)' : 'désactivé (parcours guidé)'}`);
  console.log(`[gateway] Sessions stockées dans : ${SESSION_DIR}`);
  console.log('[gateway] La session WhatsApp reste sur cette machine : ne l’exposez jamais sur Internet.');
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    console.log(`[gateway] ${signal} reçu : fermeture des sessions.`);
    for (const session of manager.sessions.values()) {
      await session.destroy({ purge: false });
    }
    server.close(() => process.exit(0));
  });
}
