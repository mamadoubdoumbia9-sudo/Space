/**
 * Sécurité de la passerelle.
 *
 * Chaque requête du backend est signée en HMAC-SHA256 sur (méthode, chemin,
 * horodatage, corps). Une requête sans signature valide, ou vieille de plus de
 * 5 minutes, est rejetée. La passerelle n'écoute QUE sur la boucle locale :
 * elle n'est jamais exposée sur Internet (voir README du dossier gateway).
 */
import crypto from 'node:crypto';

export const MAX_SKEW_SECONDS = 300;

export function sign(secret, method, path, body, ts = Math.floor(Date.now() / 1000)) {
  const mac = crypto
    .createHmac('sha256', secret)
    .update(`${method}\n${path}\n${ts}\n`)
    .update(body)
    .digest('hex');
  return { timestamp: String(ts), signature: mac };
}

export function verify(secret, method, path, body, timestamp, signature) {
  const ts = Number.parseInt(timestamp ?? '', 10);
  if (!Number.isFinite(ts)) return false;
  if (Math.abs(Math.floor(Date.now() / 1000) - ts) > MAX_SKEW_SECONDS) return false;
  const expected = crypto
    .createHmac('sha256', secret)
    .update(`${method}\n${path}\n${ts}\n`)
    .update(body)
    .digest('hex');
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(String(signature ?? ''), 'utf8');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function randomRef(prefix = 'ref') {
  return `${prefix}-${crypto.randomBytes(8).toString('hex')}`;
}
