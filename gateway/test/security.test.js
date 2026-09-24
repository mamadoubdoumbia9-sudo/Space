/**
 * Tests de la passerelle (Node test runner) : signature HMAC, fenêtres de
 * limitation de débit, cohérence avec le backend Python.
 *
 * Ces tests ne nécessitent PAS whatsapp-web.js : ils couvrent le code qui
 * protège l'accès à la session de l'utilisateur.
 */
import assert from 'node:assert/strict';
import test from 'node:test';

import { SlidingWindowLimiter } from '../src/rateLimit.js';
import { MAX_SKEW_SECONDS, sign, verify } from '../src/security.js';

const SECRET = 'secret-de-test-partage-avec-le-backend';

test('une signature valide est acceptée', () => {
  const body = Buffer.from(JSON.stringify({ peer_jid: '22361234567@s.whatsapp.net' }));
  const { timestamp, signature } = sign(SECRET, 'POST', '/v1/sessions/x/actions/report', body);
  assert.equal(verify(SECRET, 'POST', '/v1/sessions/x/actions/report', body, timestamp, signature), true);
});

test('un corps modifié invalide la signature', () => {
  const body = Buffer.from(JSON.stringify({ amount: 1 }));
  const { timestamp, signature } = sign(SECRET, 'POST', '/v1/sessions', body);
  const tampered = Buffer.from(JSON.stringify({ amount: 999 }));
  assert.equal(verify(SECRET, 'POST', '/v1/sessions', tampered, timestamp, signature), false);
});

test('un secret différent est rejeté', () => {
  const body = Buffer.from('{}');
  const { timestamp, signature } = sign(SECRET, 'POST', '/v1/sessions', body);
  assert.equal(verify('mauvais-secret-mauvais-secret-xx', 'POST', '/v1/sessions', body, timestamp, signature), false);
});

test('une signature périmée est rejetée', () => {
  const body = Buffer.from('{}');
  const old = Math.floor(Date.now() / 1000) - (MAX_SKEW_SECONDS + 60);
  const { signature } = sign(SECRET, 'POST', '/v1/sessions', body, old);
  assert.equal(verify(SECRET, 'POST', '/v1/sessions', body, String(old), signature), false);
});

test('les limites de débit sont appliquées et non contournables par le client', () => {
  const limiter = new SlidingWindowLimiter({ perMinute: 10, reportsPerHour: 5, reportsPerDay: 20 });
  for (let i = 0; i < 5; i += 1) {
    assert.equal(limiter.check('report').allowed, true);
    limiter.record('report');
  }
  const verdict = limiter.check('report');
  assert.equal(verdict.allowed, false);
  assert.match(verdict.reason, /signalements par heure/);
  assert.ok(verdict.retry_after > 0);
});

test('la limite de 10 actions par minute s\'applique aussi au blocage', () => {
  const limiter = new SlidingWindowLimiter({ perMinute: 10 });
  for (let i = 0; i < 10; i += 1) {
    assert.equal(limiter.check('action').allowed, true);
    limiter.record('action');
  }
  assert.equal(limiter.check('action').allowed, false);
});

test('le compteur de limitations reste cohérent', () => {
  const limiter = new SlidingWindowLimiter({ perMinute: 10, reportsPerHour: 5, reportsPerDay: 20 });
  limiter.record('report');
  limiter.record('report');
  const snap = limiter.snapshot();
  assert.equal(snap.reports_last_hour, 2);
  assert.equal(snap.reports_per_day, 20);
});
